package com.servas.domain.repository;

import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.Modality;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"company", "comuna"})
    List<Service> findByCompanyIdAndIsActiveTrue(UUID companyId);

    @EntityGraph(attributePaths = {"company", "comuna"})
    List<Service> findByModality(Modality modality);

    @EntityGraph(attributePaths = {"company", "comuna"})
    List<Service> findByIsActiveTrue();

    @EntityGraph(attributePaths = {"company", "comuna"})
    Optional<Service> findWithDetailsById(UUID id);
}