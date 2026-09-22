package com.servas.application.schedule;

import com.servas.common.constant.Errors;
import com.servas.common.constant.WeekDays;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Schedule;
import com.servas.domain.entity.Service;
import com.servas.domain.repository.BlockedDateRepository;
import com.servas.domain.repository.ReservationRepository;
import com.servas.domain.repository.ScheduleRepository;
import com.servas.domain.repository.ServiceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ServiceRepository serviceRepository;
    private final BlockedDateRepository blockedDateRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public Result<List<Schedule>> configure(UUID providerId, UUID serviceId, List<ScheduleItem> items) {
        var serviceResult = ownedService(providerId, serviceId);
        if (serviceResult.isFailure()) {
            return Result.failure(serviceResult.error());
        }
        var validation = validateItems(items);
        if (validation.isFailure()) {
            return Result.failure(validation.error());
        }
        var service = serviceResult.valueUnchecked();
        var created = items.stream()
            .map(item -> Schedule.builder()
                .service(service)
                .dayOfWeek(item.dayOfWeek())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .build())
            .toList();
        return Result.value(scheduleRepository.saveAll(created));
    }

    @Transactional
    public Result<List<Schedule>> replace(UUID providerId, UUID serviceId, List<ScheduleItem> items) {
        var serviceResult = ownedService(providerId, serviceId);
        if (serviceResult.isFailure()) {
            return Result.failure(serviceResult.error());
        }
        var validation = validateItems(items);
        if (validation.isFailure()) {
            return Result.failure(validation.error());
        }
        scheduleRepository.deleteByServiceId(serviceId);
        var service = serviceResult.valueUnchecked();
        var created = items.stream()
            .map(item -> Schedule.builder()
                .service(service)
                .dayOfWeek(item.dayOfWeek())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .build())
            .toList();
        return Result.value(scheduleRepository.saveAll(created));
    }

    @Transactional(readOnly = true)
    public Result<List<AvailabilitySlot>> availableSlots(UUID serviceId, LocalDate date) {
        return serviceRepository.findById(serviceId)
            .map(service -> !service.isActive()
                ? Result.<List<AvailabilitySlot>>failure(Errors.SERVICE_INACTIVE)
                : computeSlots(service, date))
            .orElse(Result.failure(Errors.SERVICE_NOT_FOUND));
    }

    private Result<List<AvailabilitySlot>> computeSlots(Service service, LocalDate date) {
        if ((service.getStartDate() != null && date.isBefore(service.getStartDate()))
            || (service.getEndDate() != null && date.isAfter(service.getEndDate()))) {
            return Result.failure(Errors.DATE_OUT_OF_RANGE);
        }
        var schedules = scheduleRepository.findByServiceId(service.getId()).stream()
            .filter(schedule -> schedule.getDayOfWeek() == date.getDayOfWeek().getValue())
            .toList();
        if (schedules.isEmpty()) {
            return Result.failure(Errors.SERVICE_WITHOUT_SCHEDULE);
        }
        var companyId = service.getCompany().getId();
        var blocked = !blockedDateRepository.findByCompanyIdAndServiceIsNullAndBlockDate(companyId, date).isEmpty()
            || !blockedDateRepository.findByServiceIdAndBlockDate(service.getId(), date).isEmpty();
        if (blocked) {
            return Result.value(List.of());
        }
        var active = reservationRepository.findByServiceIdAndReservationDate(service.getId(), date).stream()
            .filter(reservation -> reservation.isActive())
            .toList();
        var slots = new ArrayList<AvailabilitySlot>();
        for (var schedule : schedules) {
            var lastStart = schedule.getEndTime().minusMinutes(service.getDurationMinutes());
            for (var cursor = schedule.getStartTime();
                 !cursor.isAfter(lastStart);
                 cursor = cursor.plusMinutes(service.getDurationMinutes())) {
                var start = cursor;
                var end = start.plusMinutes(service.getDurationMinutes());
                var free = active.stream()
                    .noneMatch(reservation -> start.isBefore(reservation.getEndTime())
                        && end.isAfter(reservation.getStartTime()));
                if (free) {
                    slots.add(new AvailabilitySlot(start, end));
                }
            }
        }
        return Result.value(slots);
    }

    private Result<Void> validateItems(List<ScheduleItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.failure(Errors.INVALID_SCHEDULE);
        }
        var structurallyInvalid = items.stream().anyMatch(item ->
            item.dayOfWeek() < WeekDays.MONDAY
                || item.dayOfWeek() > WeekDays.SUNDAY
                || !item.startTime().isBefore(item.endTime()));
        if (structurallyInvalid) {
            return Result.failure(Errors.INVALID_SCHEDULE);
        }
        for (int index = 0; index < items.size(); index++) {
            for (int other = index + 1; other < items.size(); other++) {
                var first = items.get(index);
                var second = items.get(other);
                if (first.dayOfWeek() == second.dayOfWeek()
                    && first.startTime().isBefore(second.endTime())
                    && first.endTime().isAfter(second.startTime())) {
                    return Result.failure(Errors.INVALID_SCHEDULE);
                }
            }
        }
        return Result.value(null);
    }

    private Result<Service> ownedService(UUID providerId, UUID serviceId) {
        return serviceRepository.findById(serviceId)
            .filter(service -> service.getCompany().getProvider().getId().equals(providerId))
            .map(Result::value)
            .orElse(Result.failure(Errors.SERVICE_NOT_FOUND));
    }

    public record ScheduleItem(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public record AvailabilitySlot(LocalTime startTime, LocalTime endTime) {
    }
}