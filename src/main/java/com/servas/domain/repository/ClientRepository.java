package com.servas.domain.repository;

import com.servas.domain.entity.Client;
import com.servas.domain.enumeration.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    List<Client> findByDocumentNumber(String documentNumber);
}