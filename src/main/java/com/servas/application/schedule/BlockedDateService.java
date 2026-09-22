package com.servas.application.schedule;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.BlockedDate;
import com.servas.domain.entity.Service;
import com.servas.domain.repository.BlockedDateRepository;
import com.servas.domain.repository.CompanyRepository;
import com.servas.domain.repository.ServiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BlockedDateService {

    private final BlockedDateRepository blockedDateRepository;
    private final CompanyRepository companyRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public Result<BlockedDate> block(UUID providerId, BlockDateCommand command) {
        var company = companyRepository.findById(command.companyId()).orElse(null);
        if (company == null) {
            return Result.failure(Errors.COMPANY_NOT_FOUND);
        }
        if (!company.getProvider().getId().equals(providerId)) {
            return Result.failure(Errors.NOT_COMPANY_OWNER);
        }
        Service service = null;
        if (command.serviceId() != null) {
            service = serviceRepository.findById(command.serviceId()).orElse(null);
            if (service == null) {
                return Result.failure(Errors.SERVICE_NOT_FOUND);
            }
            if (!service.getCompany().getId().equals(company.getId())) {
                return Result.failure(Errors.SERVICE_NOT_IN_COMPANY);
            }
        }
        var blocked = blockedDateRepository.save(BlockedDate.builder()
            .company(company)
            .service(service)
            .blockDate(command.blockDate())
            .reason(command.reason())
            .build());
        return Result.value(blocked);
    }

    @Transactional(readOnly = true)
    public List<BlockedDate> listForCompany(UUID companyId) {
        return blockedDateRepository.findByCompanyId(companyId);
    }

    public record BlockDateCommand(UUID companyId, UUID serviceId, LocalDate blockDate, String reason) {
    }
}