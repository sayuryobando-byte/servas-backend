package com.servas.catalog;

import com.servas.application.catalog.CatalogService;
import com.servas.application.catalog.CompanyService;
import com.servas.application.catalog.CompanyService.CreateCompanyCommand;
import com.servas.application.catalog.CompanyService.UpdateCompanyCommand;
import com.servas.application.catalog.ServiceAdminService;
import com.servas.application.catalog.ServiceAdminService.CreateServiceCommand;
import com.servas.application.catalog.ServiceAdminService.UpdateServiceCommand;
import com.servas.common.constant.Errors;
import com.servas.domain.enumeration.Modality;
import com.servas.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private ServiceAdminService serviceAdminService;
    @Autowired
    private CatalogService catalogService;

    @Test
    void createsAndUpdatesCompany() {
        var provider = persistProvider();
        var created = companyService.create(provider.getId(), new CreateCompanyCommand(
            "NIT-" + unique("n"), "Mi Negocio", "Desc", "Calle 1", null, null));
        assertThat(created.isSuccess()).isTrue();
        var id = created.valueUnchecked().getId();

        var updated = companyService.update(provider.getId(), id, new UpdateCompanyCommand(
            "Nuevo Nombre", null, null, null, null));
        assertThat(updated.isSuccess()).isTrue();
        assertThat(updated.valueUnchecked().getName()).isEqualTo("Nuevo Nombre");
    }

    @Test
    void rejectsRepeatedNit() {
        var provider = persistProvider();
        var command = new CreateCompanyCommand("NIT-" + unique("n"), "A", "D", "C", null, null);
        companyService.create(provider.getId(), command);
        var second = companyService.create(provider.getId(), command);
        assertThat(second.isFailure()).isTrue();
        assertThat(second.error().code()).isEqualTo(Errors.NIT_TAKEN.code());
    }

    @Test
    void rejectsCompanyUpdateFromForeignProvider() {
        var owner = persistProvider();
        var intruder = persistProvider();
        var companyId = companyService.create(owner.getId(), new CreateCompanyCommand(
            "NIT-" + unique("n"), "A", null, "C", null, null)).valueUnchecked().getId();

        var result = companyService.update(intruder.getId(), companyId,
            new UpdateCompanyCommand("Hack", null, null, null, null));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void serviceRequiresComunaWhenPresencial() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var result = serviceAdminService.create(provider.getId(), serviceCommand(company.getId(), null,
            Modality.PRESENCIAL));
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().code()).isEqualTo(Errors.COMUNA_REQUIRED.code());
    }

    @Test
    void serviceVirtualWithoutComunaSucceeds() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var result = serviceAdminService.create(provider.getId(), serviceCommand(company.getId(), null,
            Modality.VIRTUAL));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void rejectsForeignServiceManagement() {
        var owner = persistProvider();
        var intruder = persistProvider();
        var company = persistCompany(owner);
        var serviceId = serviceAdminService.create(owner.getId(), serviceCommand(company.getId(),
            presetComuna().getId(), Modality.PRESENCIAL)).valueUnchecked().getId();

        var update = serviceAdminService.update(intruder.getId(), serviceId, new UpdateServiceCommand(
            null, "Rename", null, null, null, null, null, null, null, null));
        assertThat(update.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());

        var status = serviceAdminService.changeStatus(intruder.getId(), serviceId, Boolean.FALSE);
        assertThat(status.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void togglesAndReadsDetail() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var serviceId = serviceAdminService.create(provider.getId(), serviceCommand(company.getId(),
            presetComuna().getId(), Modality.PRESENCIAL)).valueUnchecked().getId();

        assertThat(serviceAdminService.changeStatus(provider.getId(), serviceId, Boolean.FALSE)
            .valueUnchecked().isActive()).isFalse();
        var detail = catalogService.detail(serviceId);
        assertThat(detail.isSuccess()).isTrue();
        assertThat(detail.valueUnchecked().isActive()).isFalse();
        assertThat(detail.valueUnchecked().getCompany().getName()).isEqualTo("Empresa de Proveedor");
    }

    @Test
    void updatesServiceDetails() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var serviceId = serviceAdminService.create(provider.getId(), serviceCommand(company.getId(),
            presetComuna().getId(), Modality.PRESENCIAL)).valueUnchecked().getId();

        var updated = serviceAdminService.update(provider.getId(), serviceId, new UpdateServiceCommand(
            null, "Servicio Renombrado", null, BigDecimal.valueOf(25_000), 30, null, null, null, null, null));
        assertThat(updated.isSuccess()).isTrue();
        assertThat(updated.valueUnchecked().getName()).isEqualTo("Servicio Renombrado");
        assertThat(updated.valueUnchecked().getDurationMinutes()).isEqualTo(30);
        assertThat(updated.valueUnchecked().getCost()).isEqualByComparingTo("25000");

        var detail = catalogService.detail(serviceId).valueUnchecked();
        assertThat(detail.getName()).isEqualTo("Servicio Renombrado");
        assertThat(detail.getDurationMinutes()).isEqualTo(30);
    }

    private CreateServiceCommand serviceCommand(UUID companyId, Integer comunaId, Modality modality) {
        return new CreateServiceCommand(companyId, comunaId, "Servicio-" + unique("s"), modality,
            BigDecimal.valueOf(20_000), 45, "Desc", null, null, null);
    }
}