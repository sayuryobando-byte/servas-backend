package com.servas.application.catalog;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Comuna;
import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.Modality;
import com.servas.domain.repository.ComunaRepository;
import com.servas.domain.repository.ServiceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CatalogService {

    private final ComunaRepository comunaRepository;
    private final ServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public List<Comuna> comunas() {
        return comunaRepository.findAll().stream()
            .sorted(Comparator.comparing(Comuna::getName))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Service> search(UUID companyId, Integer comunaId, Modality modality) {
        return serviceRepository.findByIsActiveTrue().stream()
            .filter(service -> companyId == null || service.getCompany().getId().equals(companyId))
            .filter(service -> comunaId == null
                || (service.getComuna() != null && service.getComuna().getId().equals(comunaId)))
            .filter(service -> modality == null || service.getModality() == modality)
            .sorted(Comparator.comparing(Service::getName))
            .toList();
    }

    @Transactional(readOnly = true)
    public Result<Service> detail(UUID serviceId) {
        return serviceRepository.findWithDetailsById(serviceId)
            .map(Result::value)
            .orElse(Result.failure(Errors.SERVICE_NOT_FOUND));
    }
}