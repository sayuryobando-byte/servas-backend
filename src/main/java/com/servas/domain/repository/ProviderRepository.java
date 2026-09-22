package com.servas.domain.repository;

import com.servas.domain.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    Optional<Provider> findByUserId(UUID userId);

    boolean existsByDocumentNumber(String documentNumber);
}