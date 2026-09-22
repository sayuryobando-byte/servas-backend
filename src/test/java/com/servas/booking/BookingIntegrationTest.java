package com.servas.booking;

import com.servas.application.booking.ReservationService;
import com.servas.application.booking.ReservationService.CancelReservationCommand;
import com.servas.application.booking.ReservationService.ClientInput;
import com.servas.application.booking.ReservationService.CreateReservationCommand;
import com.servas.application.booking.ReservationService.ModifyReservationCommand;
import com.servas.application.catalog.ServiceAdminService;
import com.servas.application.catalog.ServiceAdminService.CreateServiceCommand;
import com.servas.application.catalog.ServiceAdminService.UpdateServiceCommand;
import com.servas.application.schedule.BlockedDateService;
import com.servas.application.schedule.BlockedDateService.BlockDateCommand;
import com.servas.application.schedule.ScheduleService;
import com.servas.application.schedule.ScheduleService.ScheduleItem;
import com.servas.common.constant.Errors;
import com.servas.domain.entity.Reservation;
import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.CancelledBy;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.enumeration.Modality;
import com.servas.domain.enumeration.ReservationStatus;
import com.servas.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private BlockedDateService blockedDateService;
    @Autowired
    private ServiceAdminService serviceAdminService;

    private UUID ownerId;
    private UUID companyId;
    private Service service;

    @BeforeEach
    void fixture() {
        var provider = persistProvider();
        ownerId = provider.getId();
        var company = persistCompany(provider);
        companyId = company.getId();
        service = persistService(company, Modality.VIRTUAL, 60);
        scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(2, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(3, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(4, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(5, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(6, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new ScheduleItem(7, LocalTime.of(9, 0), LocalTime.of(13, 0))));
    }

    @Test
    void calculatesAndReservesSlots() {
        var date = bookingDate();
        var available = scheduleService.availableSlots(service.getId(), date);
        assertThat(available.isSuccess()).isTrue();
        assertThat(available.valueUnchecked()).hasSize(4);

        var created = reservationService.create(createCommand(date));
        assertThat(created.isSuccess()).isTrue();
        assertThat(created.valueUnchecked().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        var after = scheduleService.availableSlots(service.getId(), date).valueUnchecked();
        assertThat(after).hasSize(3);
        assertThat(after).noneMatch(slot -> slot.startTime().equals(LocalTime.of(9, 0)));

        assertThat(reservationService.detail(created.valueUnchecked().getId()).isSuccess()).isTrue();
    }

    @Test
    void rejectsDoubleBookingOfSameSlot() {
        var date = bookingDate();
        reservationService.create(createCommand(date));
        var duplicate = reservationService.create(createCommand(date));
        assertThat(duplicate.isFailure()).isTrue();
        assertThat(duplicate.error().code()).isEqualTo(Errors.SLOT_NOT_AVAILABLE.code());
    }

    @Test
    void modifiesAndCancels() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var id = created.valueUnchecked().getId();
        var document = created.valueUnchecked().getClient().getDocumentNumber();

        var moved = reservationService.modify(id, new ModifyReservationCommand(date,
            LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThat(moved.isSuccess()).isTrue();
        assertThat(moved.valueUnchecked().getStartTime()).isEqualTo(LocalTime.of(10, 0));

        var cancelled = reservationService.cancel(id, new CancelReservationCommand(CancelledBy.CLIENT, "Imprevisto"));
        assertThat(cancelled.isSuccess()).isTrue();
        assertThat(cancelled.valueUnchecked().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.valueUnchecked().getCancelledBy()).isEqualTo(CancelledBy.CLIENT);

        var again = reservationService.cancel(id, new CancelReservationCommand(CancelledBy.CLIENT, "x"));
        assertThat(again.error().code()).isEqualTo(Errors.RESERVATION_NOT_ACTIVE.code());

        assertThat(reservationService.byDocument(document).isSuccess()).isTrue();
    }

    @Test
    void blockedDateClearsAvailability() {
        var date = bookingDate();
        var blocked = blockedDateService.block(ownerId, new BlockDateCommand(companyId, null, date, "Feriado"));
        assertThat(blocked.isSuccess()).isTrue();
        assertThat(scheduleService.availableSlots(service.getId(), date).valueUnchecked()).isEmpty();
    }

    @Test
    void providerAgendaListsOwnReservations() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var agenda = reservationService.providerAgenda(ownerId, ReservationStatus.CONFIRMED, date);
        assertThat(agenda.isSuccess()).isTrue();
        assertThat(agenda.valueUnchecked())
            .extracting(r -> r.getId())
            .contains(created.valueUnchecked().getId());
    }

    @Test
    void replacesSchedules() {
        var replaced = scheduleService.replace(ownerId, service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(8, 0), LocalTime.of(11, 0))));
        assertThat(replaced.isSuccess()).isTrue();
        assertThat(replaced.valueUnchecked())
            .extracting(schedule -> schedule.getDayOfWeek())
            .containsOnly(1);

        var monday = LocalDate.now().plusWeeks(4).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        assertThat(scheduleService.availableSlots(service.getId(), monday).valueUnchecked())
            .hasSize(3);

        var tuesday = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        assertThat(scheduleService.availableSlots(service.getId(), tuesday).isFailure()).isTrue();
    }

    @Test
    void clientHistoryListsActiveAndCancelled() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var reservation = created.valueUnchecked();
        var id = reservation.getId();
        var document = reservation.getClient().getDocumentNumber();
        var clientInput = new ClientInput(reservation.getClient().getDocumentType(), document,
            reservation.getClient().getFirstName(), reservation.getClient().getLastName(),
            reservation.getClient().getPhone(), reservation.getClient().getEmail());

        var second = reservationService.create(new CreateReservationCommand(service.getId(),
            date.plusWeeks(1), LocalTime.of(9, 0), LocalTime.of(10, 0), clientInput));
        assertThat(second.isSuccess()).isTrue();

        reservationService.cancel(id, new CancelReservationCommand(CancelledBy.CLIENT, "Cambio de plan"));

        var history = reservationService.byDocument(document);
        assertThat(history.isSuccess()).isTrue();
        assertThat(history.valueUnchecked())
            .extracting(r -> r.getStatus())
            .contains(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED);
    }

    @Test
    void rejectsReservationOutsideServiceValidity() {
        var date = bookingDate();
        serviceAdminService.update(ownerId, service.getId(), new UpdateServiceCommand(
            null, null, null, null, null, null, null, date.plusDays(30), date.plusDays(60), null));

        var beforeValidity = reservationService.create(createCommand(date));
        assertThat(beforeValidity.isFailure()).isTrue();
        assertThat(beforeValidity.error().code()).isEqualTo(Errors.DATE_OUT_OF_RANGE.code());

        var beforeValiditySlots = scheduleService.availableSlots(service.getId(), date);
        assertThat(beforeValiditySlots.isFailure()).isTrue();
        assertThat(beforeValiditySlots.error().code()).isEqualTo(Errors.DATE_OUT_OF_RANGE.code());

        var withinValidity = reservationService.create(createCommand(date.plusDays(35)));
        assertThat(withinValidity.isSuccess()).isTrue();
        assertThat(withinValidity.valueUnchecked().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        var withinValiditySlots = scheduleService.availableSlots(service.getId(), date.plusDays(35));
        assertThat(withinValiditySlots.isSuccess()).isTrue();
        assertThat(withinValiditySlots.valueUnchecked()).isNotEmpty();

        var afterValidity = reservationService.create(createCommand(date.plusDays(70)));
        assertThat(afterValidity.isFailure()).isTrue();
        assertThat(afterValidity.error().code()).isEqualTo(Errors.DATE_OUT_OF_RANGE.code());

        var afterValiditySlots = scheduleService.availableSlots(service.getId(), date.plusDays(70));
        assertThat(afterValiditySlots.isFailure()).isTrue();
        assertThat(afterValiditySlots.error().code()).isEqualTo(Errors.DATE_OUT_OF_RANGE.code());
    }

    @Test
    void cancelledReservationFreesSlot() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var id = created.valueUnchecked().getId();
        assertThat(scheduleService.availableSlots(service.getId(), date).valueUnchecked()).hasSize(3);

        reservationService.cancel(id, new CancelReservationCommand(CancelledBy.CLIENT, "Imprevisto"));

        var after = scheduleService.availableSlots(service.getId(), date).valueUnchecked();
        assertThat(after).hasSize(4);
        assertThat(after).anyMatch(slot -> slot.startTime().equals(LocalTime.of(9, 0)));
    }

    @Test
    void serviceLevelBlockClearsAvailability() {
        var date = bookingDate();
        var blocked = blockedDateService.block(ownerId,
            new BlockDateCommand(companyId, service.getId(), date, "Mantenimiento"));
        assertThat(blocked.isSuccess()).isTrue();
        assertThat(scheduleService.availableSlots(service.getId(), date).valueUnchecked()).isEmpty();
    }

    @Test
    void rejectsBookingOnInactiveService() {
        var date = bookingDate();
        serviceAdminService.changeStatus(ownerId, service.getId(), false);

        var attempt = reservationService.create(createCommand(date));
        assertThat(attempt.isFailure()).isTrue();
        assertThat(attempt.error().code()).isEqualTo(Errors.SERVICE_INACTIVE.code());

        var availability = scheduleService.availableSlots(service.getId(), date);
        assertThat(availability.isFailure()).isTrue();
        assertThat(availability.error().code()).isEqualTo(Errors.SERVICE_INACTIVE.code());
    }

    @Test
    void rejectsBookingOnMisalignedEndTime() {
        var date = bookingDate();
        var mismatch = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(9, 0), LocalTime.of(10, 30), clientInput()));
        assertThat(mismatch.isFailure()).isTrue();
        assertThat(mismatch.error().code()).isEqualTo(Errors.END_TIME_MISMATCH.code());
    }

    @Test
    void rejectsBookingOutsideAttentionHours() {
        var date = bookingDate();
        var outside = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(14, 0), LocalTime.of(15, 0), clientInput()));
        assertThat(outside.isFailure()).isTrue();
        assertThat(outside.error().code()).isEqualTo(Errors.OUTSIDE_ATTENTION_HOURS.code());
    }

    @Test
    void rejectsBookingMisalignedWithServiceDuration() {
        var date = bookingDate();
        var misaligned = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(9, 30), LocalTime.of(10, 30), clientInput()));
        assertThat(misaligned.isFailure()).isTrue();
        assertThat(misaligned.error().code()).isEqualTo(Errors.OUTSIDE_ATTENTION_HOURS.code());
    }

    @Test
    void acceptsNonCollidingSlotAfterBookingLater() {
        var date = bookingDate();
        var later = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(11, 0), LocalTime.of(12, 0), clientInput()));
        assertThat(later.isSuccess()).isTrue();

        var earlier = reservationService.create(createCommand(date));
        assertThat(earlier.isSuccess()).isTrue();

        var slots = scheduleService.availableSlots(service.getId(), date).valueUnchecked();
        assertThat(slots).anyMatch(slot -> slot.startTime().equals(LocalTime.of(10, 0)));
        assertThat(slots).noneMatch(slot -> slot.startTime().equals(LocalTime.of(11, 0)));
    }

    @Test
    void rejectsBookingBeforeOpeningHours() {
        var date = bookingDate();
        var tooEarly = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(8, 0), LocalTime.of(9, 0), clientInput()));
        assertThat(tooEarly.isFailure()).isTrue();
        assertThat(tooEarly.error().code()).isEqualTo(Errors.OUTSIDE_ATTENTION_HOURS.code());
    }

    @Test
    void rejectsModifyWithMisalignedEndTime() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var id = created.valueUnchecked().getId();
        var outcome = reservationService.modify(id,
            new ModifyReservationCommand(date, LocalTime.of(10, 0), LocalTime.of(10, 30)));
        assertThat(outcome.isFailure()).isTrue();
        assertThat(outcome.error().code()).isEqualTo(Errors.END_TIME_MISMATCH.code());
    }

    @Test
    void rejectsModifyIntoOccupiedSlot() {
        var date = bookingDate();
        var first = reservationService.create(createCommand(date)).valueUnchecked();
        var second = reservationService.create(new CreateReservationCommand(service.getId(), date,
            LocalTime.of(11, 0), LocalTime.of(12, 0), clientInput())).valueUnchecked();
        assertThat(second.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        var moveCollides = reservationService.modify(first.getId(),
            new ModifyReservationCommand(date, LocalTime.of(11, 0), LocalTime.of(12, 0)));
        assertThat(moveCollides.isFailure()).isTrue();
        assertThat(moveCollides.error().code()).isEqualTo(Errors.SLOT_NOT_AVAILABLE.code());
    }

    @Test
    void rejectsBookingOnServiceWithoutSchedule() {
        var unscheduled = serviceAdminService.create(ownerId, new CreateServiceCommand(companyId, null,
                unique("sin-agenda"), Modality.VIRTUAL, BigDecimal.valueOf(5_000), 60, null, null, null, null))
            .valueUnchecked();
        var attempt = reservationService.create(new CreateReservationCommand(unscheduled.getId(),
            bookingDate(), LocalTime.of(9, 0), LocalTime.of(10, 0), clientInput()));
        assertThat(attempt.isFailure()).isTrue();
        assertThat(attempt.error().code()).isEqualTo(Errors.SERVICE_WITHOUT_SCHEDULE.code());

        var availability = scheduleService.availableSlots(unscheduled.getId(), bookingDate());
        assertThat(availability.error().code()).isEqualTo(Errors.SERVICE_WITHOUT_SCHEDULE.code());
    }

    @Test
    void rejectsBookingOnServiceBlockedDate() {
        var date = bookingDate();
        blockedDateService.block(ownerId, new BlockDateCommand(companyId, service.getId(), date, "Mantenimiento"));
        var attempt = reservationService.create(createCommand(date));
        assertThat(attempt.isFailure()).isTrue();
        assertThat(attempt.error().code()).isEqualTo(Errors.DATE_BLOCKED.code());
    }

    @Test
    void rejectsUnknownResources() {
        var ghost = UUID.randomUUID();

        assertThat(reservationService.create(new CreateReservationCommand(ghost, bookingDate(),
            LocalTime.of(9, 0), LocalTime.of(10, 0), clientInput())).error().code())
            .isEqualTo(Errors.SERVICE_NOT_FOUND.code());
        assertThat(reservationService.detail(ghost).error().code()).isEqualTo(Errors.RESERVATION_NOT_FOUND.code());
        assertThat(reservationService.modify(ghost,
                new ModifyReservationCommand(bookingDate(), LocalTime.of(10, 0), LocalTime.of(11, 0)))
            .error().code()).isEqualTo(Errors.RESERVATION_NOT_FOUND.code());
        assertThat(reservationService.cancel(ghost, new CancelReservationCommand(CancelledBy.CLIENT, "x"))
            .error().code()).isEqualTo(Errors.RESERVATION_NOT_FOUND.code());
        assertThat(reservationService.byDocument("NO-EXISTE").error().code()).isEqualTo(Errors.CLIENT_NOT_FOUND.code());
    }

    @Test
    void rejectsModifyOfCancelledReservation() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date));
        var id = created.valueUnchecked().getId();
        reservationService.cancel(id, new CancelReservationCommand(CancelledBy.CLIENT, "Imprevisto"));

        var again = reservationService.modify(id,
            new ModifyReservationCommand(date, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThat(again.isFailure()).isTrue();
        assertThat(again.error().code()).isEqualTo(Errors.RESERVATION_NOT_ACTIVE.code());
    }

    @Test
    void rejectsInvalidScheduleConfigurations() {
        assertThat(scheduleService.configure(ownerId, service.getId(), List.of()).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(ownerId, service.getId(), null).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(8, LocalTime.of(9, 0), LocalTime.of(10, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(0, LocalTime.of(9, 0), LocalTime.of(10, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(10, 0), LocalTime.of(9, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new ScheduleItem(1, LocalTime.of(9, 30), LocalTime.of(10, 30)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.replace(ownerId, service.getId(), List.of()).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.replace(ownerId, service.getId(), null).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
    }

    @Test
    void acceptsAdjacentNonOverlappingSchedules() {
        var configured = scheduleService.configure(ownerId, service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(11, 0), LocalTime.of(12, 0)),
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new ScheduleItem(2, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        assertThat(configured.isSuccess()).isTrue();
        assertThat(configured.valueUnchecked()).hasSize(3);
    }

    @Test
    void rejectsScheduleOfForeignService() {
        var otherProvider = persistProvider();
        var result = scheduleService.configure(otherProvider.getId(), service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.SERVICE_NOT_FOUND.code());

        var replaced = scheduleService.replace(otherProvider.getId(), service.getId(), List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        assertThat(replaced.isFailure()).isTrue();
        assertThat(replaced.error().code()).isEqualTo(Errors.SERVICE_NOT_FOUND.code());
    }

    @Test
    void persistedReservationKeepsProvidedCreatedAt() {
        var date = bookingDate();
        var created = reservationService.create(createCommand(date)).valueUnchecked();
        var createdAt = Instant.ofEpochMilli(1L);
        var kept = reservations.save(Reservation.builder()
            .service(service)
            .client(created.getClient())
            .reservationDate(date)
            .startTime(LocalTime.of(12, 0))
            .endTime(LocalTime.of(13, 0))
            .status(ReservationStatus.CONFIRMED)
            .createdAt(createdAt)
            .build());
        assertThat(kept.getCreatedAt()).isEqualTo(createdAt);
    }

    private CreateReservationCommand createCommand(LocalDate date) {
        return new CreateReservationCommand(service.getId(), date, LocalTime.of(9, 0), LocalTime.of(10, 0),
            clientInput());
    }

    private ClientInput clientInput() {
        return new ClientInput(DocumentType.CC, "DOC-" + unique("c"), "Ana", "Perez", "3000000000", unique("cx") + "@m.com");
    }

    private LocalDate bookingDate() {
        return LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    }
}