package com.servas.domain.repository;

import com.servas.domain.entity.Reservation;
import com.servas.domain.enumeration.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByClientId(UUID clientId);

    List<Reservation> findByServiceIdAndReservationDate(UUID serviceId, LocalDate reservationDate);

    @EntityGraph(attributePaths = {"service", "client"})
    Optional<Reservation> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"service", "client"})
    List<Reservation> findWithDetailsByClientId(UUID clientId);

    @Query("""
        select r from Reservation r
        join fetch r.service
        join fetch r.client
        where r.service.company.provider.id = :providerId
          and coalesce(:status, r.status) = r.status
          and coalesce(:date, r.reservationDate) = r.reservationDate
        order by r.reservationDate desc, r.startTime asc
        """)
    List<Reservation> findByProvider(@Param("providerId") UUID providerId,
                                     @Param("status") ReservationStatus status,
                                     @Param("date") LocalDate date);
}