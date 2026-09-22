package com.servas.application.catalog;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Comuna;
import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.Modality;
import com.servas.domain.repository.CompanyRepository;
import com.servas.domain.repository.ComunaRepository;
import com.servas.domain.repository.ServiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ServiceAdminService {

    private final ServiceRepository serviceRepository;
    private final CompanyRepository companyRepository;
    private final ComunaRepository comunaRepository;

    @Transactional
    public Result<Service> create(UUID providerId, CreateServiceCommand command) {
        if (command.companyId() == null) {
            return Result.failure(Errors.COMPANY_NOT_FOUND);
        }
        var company = companyRepository.findById(command.companyId()).orElse(null);
        if (company == null) {
            return Result.failure(Errors.COMPANY_NOT_FOUND);
        }
        if (!company.getProvider().getId().equals(providerId)) {
            return Result.failure(Errors.NOT_COMPANY_OWNER);
        }
        var comuna = resolveComuna(command.comunaId());
        if (comuna == null && command.modality() == Modality.PRESENCIAL) {
            return Result.failure(Errors.COMUNA_REQUIRED);
        }
        if (comuna == null && command.comunaId() != null) {
            return Result.failure(Errors.COMUNA_NOT_FOUND);
        }
        var service = serviceRepository.save(Service.builder()
            .company(company)
            .comuna(comuna)
            .name(command.name())
            .modality(command.modality())
            .cost(command.cost())
            .durationMinutes(command.durationMinutes())
            .description(command.description())
            .recommendations(command.recommendations())
            .startDate(command.startDate())
            .endDate(command.endDate())
            .build());
        return Result.value(service);
    }

    @Transactional
    public Result<Service> update(UUID providerId, UUID serviceId, UpdateServiceCommand command) {
        var service = serviceRepository.findWithDetailsById(serviceId).orElse(null);
        if (service == null) {
            return Result.failure(Errors.SERVICE_NOT_FOUND);
        }
        if (!service.getCompany().getProvider().getId().equals(providerId)) {
            return Result.failure(Errors.NOT_COMPANY_OWNER);
        }
        var modality = or(command.modality(), service.getModality());
        var comuna = command.comunaId() != null
            ? comunaRepository.findById(command.comunaId()).orElse(null)
            : service.getComuna();
        if (comuna == null && command.comunaId() != null) {
            return Result.failure(Errors.COMUNA_NOT_FOUND);
        }
        if (modality == Modality.PRESENCIAL && comuna == null) {
            return Result.failure(Errors.COMUNA_REQUIRED);
        }
        if (modality == Modality.VIRTUAL) {
            comuna = null;
        }
        service.updateDetails(
            or(command.name(), service.getName()),
            modality,
            or(command.cost(), service.getCost()),
            or(command.durationMinutes(), service.getDurationMinutes()),
            or(command.description(), service.getDescription()),
            or(command.recommendations(), service.getRecommendations()),
            or(command.startDate(), service.getStartDate()),
            or(command.endDate(), service.getEndDate()),
            comuna);
        return Result.value(serviceRepository.save(service));
    }

    @Transactional
    public Result<Service> changeStatus(UUID providerId, UUID serviceId, boolean isActive) {
        var service = serviceRepository.findWithDetailsById(serviceId).orElse(null);
        if (service == null) {
            return Result.failure(Errors.SERVICE_NOT_FOUND);
        }
        if (!service.getCompany().getProvider().getId().equals(providerId)) {
            return Result.failure(Errors.NOT_COMPANY_OWNER);
        }
        service.changeActive(isActive);
        return Result.value(serviceRepository.save(service));
    }

    private Comuna resolveComuna(Integer comunaId) {
        return comunaId == null ? null : comunaRepository.findById(comunaId).orElse(null);
    }

    private <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    public record CreateServiceCommand(UUID companyId, Integer comunaId, String name, Modality modality,
                                       BigDecimal cost, Integer durationMinutes, String description,
                                       String recommendations, LocalDate startDate, LocalDate endDate) {
    }

    public record UpdateServiceCommand(Integer comunaId, String name, Modality modality, BigDecimal cost,
                                       Integer durationMinutes, String description, String recommendations,
                                       LocalDate startDate, LocalDate endDate, Boolean isActive) {
    }
}