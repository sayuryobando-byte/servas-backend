package com.servas.application.catalog;

import com.servas.common.constant.Errors;
import com.servas.common.functional.Result;
import com.servas.domain.entity.Company;
import com.servas.domain.repository.CompanyRepository;
import com.servas.domain.repository.ProviderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public Result<Company> findByProviderId(UUID providerId) {
        if (!providerRepository.existsById(providerId)) {
            return Result.failure(Errors.PROVIDER_NOT_FOUND);
        }
        return companyRepository.findByProviderId(providerId)
            .map(Result::value)
            .orElseGet(() -> Result.failure(Errors.COMPANY_NOT_FOUND));
    }

    @Transactional
    public Result<Company> create(UUID providerId, CreateCompanyCommand command) {
        if (!providerRepository.existsById(providerId)) {
            return Result.failure(Errors.PROVIDER_NOT_FOUND);
        }
        if (companyRepository.existsByNit(command.nit())) {
            return Result.failure(Errors.NIT_TAKEN);
        }
        var company = companyRepository.save(Company.builder()
            .provider(providerRepository.findById(providerId).orElseThrow())
            .nit(command.nit())
            .name(command.name())
            .description(command.description())
            .address(command.address())
            .socialMedia(command.socialMedia())
            .logoUrl(command.logoUrl())
            .build());
        return Result.value(company);
    }

    @Transactional
    public Result<Company> update(UUID providerId, UUID companyId, UpdateCompanyCommand command) {
        var company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return Result.failure(Errors.COMPANY_NOT_FOUND);
        }
        if (!company.getProvider().getId().equals(providerId)) {
            return Result.failure(Errors.NOT_COMPANY_OWNER);
        }
        company.updateProfile(
            or(command.name(), company.getName()),
            or(command.description(), company.getDescription()),
            or(command.address(), company.getAddress()),
            or(command.socialMedia(), company.getSocialMedia()),
            or(command.logoUrl(), company.getLogoUrl()));
        return Result.value(companyRepository.save(company));
    }

    private <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    public record CreateCompanyCommand(String nit, String name, String description, String address,
                                       String socialMedia, String logoUrl) {
    }

    public record UpdateCompanyCommand(String name, String description, String address,
                                       String socialMedia, String logoUrl) {
    }
}