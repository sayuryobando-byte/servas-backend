package com.servas.catalog;

import com.servas.application.catalog.CompanyService;
import com.servas.application.catalog.CompanyService.CreateCompanyCommand;
import com.servas.application.catalog.CompanyService.UpdateCompanyCommand;
import com.servas.common.constant.Errors;
import com.servas.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyServiceTest extends IntegrationTestBase {

    @Autowired
    private CompanyService companyService;

    @Test
    void createRejectsUnknownProvider() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = companyService.create(UUID.randomUUID(), command(company.getNit()));
        assertThat(outcome.error().code()).isEqualTo(Errors.PROVIDER_NOT_FOUND.code());
    }

    @Test
    void createRejectsDuplicatedNit() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = companyService.create(provider.getId(), command(company.getNit()));
        assertThat(outcome.error().code()).isEqualTo(Errors.NIT_TAKEN.code());
    }

    @Test
    void updateRejectsUnknownCompany() {
        var provider = persistProvider();
        var outcome = companyService.update(provider.getId(), UUID.randomUUID(),
            new UpdateCompanyCommand(null, null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMPANY_NOT_FOUND.code());
    }

    @Test
    void updateRejectsForeignProvider() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var foreign = persistProvider();
        var outcome = companyService.update(foreign.getId(), company.getId(),
            new UpdateCompanyCommand("Otra", null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void updateWithNullsKeepsExistingValues() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = companyService.update(provider.getId(), company.getId(),
            new UpdateCompanyCommand(null, null, null, null, null));
        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.valueUnchecked().getName()).isEqualTo(company.getName());
        assertThat(outcome.valueUnchecked().getAddress()).isEqualTo(company.getAddress());
    }

    private CreateCompanyCommand command(String nit) {
        return new CreateCompanyCommand(nit, "Empresa", "Descripción", "Calle 1", "@red", null);
    }
}