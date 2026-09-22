package com.servas.domain.repository;

import com.servas.domain.entity.BlockedDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BlockedDateRepository extends JpaRepository<BlockedDate, UUID> {

    List<BlockedDate> findByCompanyId(UUID companyId);

    List<BlockedDate> findByCompanyIdAndServiceIsNullAndBlockDate(UUID companyId, LocalDate blockDate);

    List<BlockedDate> findByServiceIdAndBlockDate(UUID serviceId, LocalDate blockDate);
}