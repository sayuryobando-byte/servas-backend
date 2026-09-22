package com.servas.application.report;

import com.servas.domain.entity.Reservation;
import com.servas.domain.enumeration.ReservationStatus;
import com.servas.domain.repository.ReservationRepository;
import com.servas.domain.repository.ScheduleRepository;
import com.servas.domain.repository.ServiceRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReportService {

    private final ReservationRepository reservationRepository;
    private final ServiceRepository serviceRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public ReservationReport reservationsReport() {
        var byStatus = reservationRepository.findAll().stream()
            .collect(Collectors.groupingBy(Reservation::getStatus, Collectors.counting()));
        return new ReservationReport(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus);
    }

    @Transactional(readOnly = true)
    public OccupancyReport occupancy() {
        var activeByService = reservationRepository.findAll().stream()
            .filter(Reservation::isActive)
            .collect(Collectors.groupingBy(reservation -> reservation.getService().getId(), Collectors.counting()));
        var items = serviceRepository.findByIsActiveTrue().stream()
            .map(service -> {
                var capacity = weeklyCapacity(service.getId());
                var booked = activeByService.getOrDefault(service.getId(), 0L);
                var percent = capacity == 0 ? 0.0 : Math.min(100.0, 100.0 * booked / capacity);
                return new ServiceOccupancy(service.getId(), service.getName(), booked, capacity, round(percent));
            })
            .sorted(Comparator.comparing(ServiceOccupancy::occupancyPercent).reversed())
            .toList();
        var overall = round(items.stream()
            .mapToDouble(ServiceOccupancy::occupancyPercent)
            .average()
            .orElse(0.0));
        return new OccupancyReport(items, overall);
    }

    @Transactional(readOnly = true)
    public List<DemandItem> topDemand(int limit) {
        return reservationRepository.findAll().stream()
            .collect(Collectors.groupingBy(reservation -> reservation.getService().getId(), Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> new DemandItem(entry.getKey(),
                serviceRepository.findById(entry.getKey()).map(service -> service.getName()).orElse(null),
                entry.getValue()))
            .toList();
    }

    private long weeklyCapacity(UUID serviceId) {
        var duration = serviceRepository.findById(serviceId).stream()
            .findFirst()
            .map(service -> service.getDurationMinutes())
            .orElse(0);
        return scheduleRepository.findByServiceId(serviceId).stream()
            .mapToLong(schedule -> Duration.between(schedule.getStartTime(), schedule.getEndTime()).toMinutes()
                / Math.max(1, duration))
            .sum();
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    public record ReservationReport(long total, Map<ReservationStatus, Long> byStatus) {
    }

    public record OccupancyReport(List<ServiceOccupancy> services, double overallPercent) {
    }

    public record ServiceOccupancy(UUID serviceId, String name, long booked, long capacity, double occupancyPercent) {
    }

    public record DemandItem(UUID serviceId, String serviceName, long reservations) {
    }
}