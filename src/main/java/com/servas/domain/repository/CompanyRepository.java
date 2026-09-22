package com.servas.domain.repository;

import com.servas.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByProviderId(UUID providerId);

    boolean existsByNit(String nit);
}