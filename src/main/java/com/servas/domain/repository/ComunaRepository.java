package com.servas.domain.repository;

import com.servas.domain.entity.Comuna;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComunaRepository extends JpaRepository<Comuna, Integer> {

    Optional<Comuna> findByName(String name);
}