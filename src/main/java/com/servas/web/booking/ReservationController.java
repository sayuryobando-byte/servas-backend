package com.servas.web.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.servas.application.booking.ReservationService;
import com.servas.application.booking.ReservationService.CancelReservationCommand;
import com.servas.application.booking.ReservationService.ClientInput;
import com.servas.application.booking.ReservationService.CreateReservationCommand;
import com.servas.application.booking.ReservationService.ModifyReservationCommand;
import com.servas.domain.entity.Reservation;
import com.servas.domain.enumeration.CancelledBy;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.enumeration.ReservationStatus;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final AuthResolver authResolver;

    @PostMapping("/reservations")
    public ResponseEntity<?> create(@RequestBody CreateReservationRequest request) {
        var client = request.client();
        var outcome = reservationService.create(new CreateReservationCommand(
            request.serviceId(),
            request.reservationDate(),
            request.startTime(),
            request.endTime(),
            new ClientInput(client.documentType(), client.documentNumber(), client.firstName(),
                client.lastName(), client.phone(), client.email())));
        return Api.resolve(outcome.map(ReservationController::view), HttpStatus.CREATED);
    }

    @GetMapping("/reservations/client/{documentNumber}")
    public ResponseEntity<?> byDocument(@PathVariable String documentNumber) {
        return Api.resolve(reservationService.byDocument(documentNumber).map(
            reservations -> reservations.stream().map(ReservationController::view).toList()), HttpStatus.OK);
    }

    @GetMapping("/reservations/{id}")
    public ResponseEntity<?> detail(@PathVariable UUID id) {
        return Api.resolve(reservationService.detail(id).map(ReservationController::view), HttpStatus.OK);
    }

    @PutMapping("/reservations/{id}")
    public ResponseEntity<?> modify(@PathVariable UUID id, @RequestBody ModifyReservationRequest request) {
        var outcome = reservationService.modify(id, new ModifyReservationCommand(
            request.reservationDate(), request.startTime(), request.endTime()));
        return Api.resolve(outcome.map(ReservationController::view), HttpStatus.OK);
    }

    @PatchMapping("/reservations/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID id, @RequestBody CancelReservationRequest request) {
        var outcome = reservationService.cancel(id, new CancelReservationCommand(
            request.cancelledBy(), request.cancellationReason()));
        return Api.resolve(outcome.map(ReservationController::view), HttpStatus.OK);
    }

    @GetMapping("/provider/reservations")
    public ResponseEntity<?> providerAgenda(@RequestHeader(name = "Authorization", required = false) String authorization,
                                            @RequestParam(required = false) ReservationStatus status,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> reservationService.providerAgenda(provider.getId(), status, date)
                .map(reservations -> reservations.stream().map(ReservationController::view).toList()));
        return Api.resolve(outcome, HttpStatus.OK);
    }

    private static ReservationView view(Reservation reservation) {
        var client = reservation.getClient();
        return new ReservationView(reservation.getId(),
            reservation.getService().getId(),
            reservation.getService().getName(),
            client.getDocumentType(),
            client.getDocumentNumber(),
            client.getFirstName() + " " + client.getLastName(),
            reservation.getReservationDate(),
            reservation.getStartTime(),
            reservation.getEndTime(),
            reservation.getStatus(),
            reservation.getCancelledBy(),
            reservation.getCancellationReason());
    }

    public record ReservationView(UUID id, UUID serviceId, String serviceName, DocumentType clientDocumentType,
                                  String clientDocumentNumber, String clientName, LocalDate reservationDate,
                                  @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                  @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                  ReservationStatus status, CancelledBy cancelledBy, String cancellationReason) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateReservationRequest(UUID serviceId, LocalDate reservationDate,
                                           @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                           @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                           ClientRequest client) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ClientRequest(DocumentType documentType, String documentNumber, String firstName,
                                String lastName, String phone, String email) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ModifyReservationRequest(LocalDate reservationDate,
                                           @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                           @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CancelReservationRequest(CancelledBy cancelledBy, String cancellationReason) {
    }
}