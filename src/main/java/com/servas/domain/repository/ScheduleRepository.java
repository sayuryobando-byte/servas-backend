package com.servas.domain.repository;

import com.servas.domain.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByServiceId(UUID serviceId);

    void deleteByServiceId(UUID serviceId);
}