package com.servas.application.booking;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Client;
import com.servas.domain.entity.Reservation;
import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.CancelledBy;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.enumeration.ReservationStatus;
import com.servas.domain.repository.BlockedDateRepository;
import com.servas.domain.repository.ClientRepository;
import com.servas.domain.repository.ReservationRepository;
import com.servas.domain.repository.ScheduleRepository;
import com.servas.domain.repository.ServiceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ServiceRepository serviceRepository;
    private final ClientRepository clientRepository;
    private final ScheduleRepository scheduleRepository;
    private final BlockedDateRepository blockedDateRepository;

    @Transactional
    public Result<Reservation> create(CreateReservationCommand command) {
        var service = serviceRepository.findById(command.serviceId()).orElse(null);
        if (service == null) {
            return Result.failure(Errors.SERVICE_NOT_FOUND);
        }
        if (!command.endTime().equals(command.startTime().plusMinutes(service.getDurationMinutes()))) {
            return Result.failure(Errors.END_TIME_MISMATCH);
        }
        var slot = validateSlot(service, command.reservationDate(), command.startTime(), null);
        if (slot.isFailure()) {
            return Result.failure(slot.error());
        }
        var client = findOrCreateClient(command.client());
        var reservation = reservationRepository.save(Reservation.builder()
            .service(service)
            .client(client)
            .reservationDate(command.reservationDate())
            .startTime(command.startTime())
            .endTime(command.endTime())
            .status(ReservationStatus.CONFIRMED)
            .build());
        return Result.value(reservation);
    }

    @Transactional(readOnly = true)
    public Result<List<Reservation>> byDocument(String documentNumber) {
        var clients = clientRepository.findByDocumentNumber(documentNumber);
        if (clients.isEmpty()) {
            return Result.failure(Errors.CLIENT_NOT_FOUND);
        }
        var reservations = clients.stream()
            .flatMap(client -> reservationRepository.findWithDetailsByClientId(client.getId()).stream())
            .sorted(Comparator.comparing(Reservation::getReservationDate).reversed()
                .thenComparing(Reservation::getStartTime))
            .toList();
        return Result.value(reservations);
    }

    @Transactional(readOnly = true)
    public Result<Reservation> detail(UUID reservationId) {
        return reservationRepository.findWithDetailsById(reservationId)
            .map(Result::value)
            .orElse(Result.failure(Errors.RESERVATION_NOT_FOUND));
    }

    @Transactional
    public Result<Reservation> modify(UUID reservationId, ModifyReservationCommand command) {
        var reservation = reservationRepository.findWithDetailsById(reservationId).orElse(null);
        if (reservation == null) {
            return Result.failure(Errors.RESERVATION_NOT_FOUND);
        }
        if (!reservation.isActive()) {
            return Result.failure(Errors.RESERVATION_NOT_ACTIVE);
        }
        var service = reservation.getService();
        var endTime = command.startTime().plusMinutes(service.getDurationMinutes());
        if (!command.endTime().equals(endTime)) {
            return Result.failure(Errors.END_TIME_MISMATCH);
        }
        var slot = validateSlot(service, command.reservationDate(), command.startTime(), reservation);
        if (slot.isFailure()) {
            return Result.failure(slot.error());
        }
        reservation.confirmDates(command.reservationDate(), command.startTime(), endTime);
        return Result.value(reservationRepository.save(reservation));
    }

    @Transactional
    public Result<Reservation> cancel(UUID reservationId, CancelReservationCommand command) {
        var reservation = reservationRepository.findWithDetailsById(reservationId).orElse(null);
        if (reservation == null) {
            return Result.failure(Errors.RESERVATION_NOT_FOUND);
        }
        if (!reservation.isActive()) {
            return Result.failure(Errors.RESERVATION_NOT_ACTIVE);
        }
        reservation.cancel(command.cancelledBy(), command.cancellationReason());
        return Result.value(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public Result<List<Reservation>> providerAgenda(UUID providerId, ReservationStatus status, LocalDate date) {
        return Result.value(reservationRepository.findByProvider(providerId, status, date));
    }

    private Result<Void> validateSlot(Service service, LocalDate date, LocalTime startTime, Reservation exclude) {
        if (!service.isActive()) {
            return Result.failure(Errors.SERVICE_INACTIVE);
        }
        if ((service.getStartDate() != null && date.isBefore(service.getStartDate()))
            || (service.getEndDate() != null && date.isAfter(service.getEndDate()))) {
            return Result.failure(Errors.DATE_OUT_OF_RANGE);
        }
        var duration = service.getDurationMinutes();
        var endTime = startTime.plusMinutes(duration);
        var schedules = scheduleRepository.findByServiceId(service.getId()).stream()
            .filter(schedule -> schedule.getDayOfWeek() == date.getDayOfWeek().getValue())
            .toList();
        if (schedules.isEmpty()) {
            return Result.failure(Errors.SERVICE_WITHOUT_SCHEDULE);
        }
        var inside = schedules.stream().anyMatch(schedule ->
            !startTime.isBefore(schedule.getStartTime())
                && !endTime.isAfter(schedule.getEndTime())
                && ChronoUnit.MINUTES.between(schedule.getStartTime(), startTime) % duration == 0);
        if (!inside) {
            return Result.failure(Errors.OUTSIDE_ATTENTION_HOURS);
        }
        var companyId = service.getCompany().getId();
        var blocked = !blockedDateRepository.findByCompanyIdAndServiceIsNullAndBlockDate(companyId, date).isEmpty()
            || !blockedDateRepository.findByServiceIdAndBlockDate(service.getId(), date).isEmpty();
        if (blocked) {
            return Result.failure(Errors.DATE_BLOCKED);
        }
        var collision = reservationRepository.findByServiceIdAndReservationDate(service.getId(), date).stream()
            .filter(Reservation::isActive)
            .filter(reservation -> exclude == null || !reservation.getId().equals(exclude.getId()))
            .anyMatch(reservation -> startTime.isBefore(reservation.getEndTime())
                && endTime.isAfter(reservation.getStartTime()));
        if (collision) {
            return Result.failure(Errors.SLOT_NOT_AVAILABLE);
        }
        return Result.value(null);
    }

    private Client findOrCreateClient(ClientInput input) {
        return clientRepository.findByDocumentTypeAndDocumentNumber(input.documentType(), input.documentNumber())
            .orElseGet(() -> clientRepository.save(Client.builder()
                .documentType(input.documentType())
                .documentNumber(input.documentNumber())
                .firstName(input.firstName())
                .lastName(input.lastName())
                .phone(input.phone())
                .email(input.email())
                .build()));
    }

    public record ClientInput(DocumentType documentType, String documentNumber, String firstName,
                              String lastName, String phone, String email) {
    }

    public record CreateReservationCommand(UUID serviceId, LocalDate reservationDate, LocalTime startTime,
                                           LocalTime endTime, ClientInput client) {
    }

    public record ModifyReservationCommand(LocalDate reservationDate, LocalTime startTime, LocalTime endTime) {
    }

    public record CancelReservationCommand(CancelledBy cancelledBy, String cancellationReason) {
    }
}