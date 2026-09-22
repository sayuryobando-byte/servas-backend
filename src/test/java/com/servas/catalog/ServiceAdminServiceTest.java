package com.servas.catalog;

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

class ServiceAdminServiceTest extends IntegrationTestBase {

    @Autowired
    private ServiceAdminService serviceAdminService;

    @Test
    void createRejectsUnknownCompany() {
        var outcome = serviceAdminService.create(UUID.randomUUID(), command(UUID.randomUUID(), null, Modality.VIRTUAL));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMPANY_NOT_FOUND.code());
    }

    @Test
    void createRejectsForeignCompany() {
        var other = persistProvider();
        var company = persistCompany(other);
        var owner = persistProvider();
        var outcome = serviceAdminService.create(owner.getId(), command(company.getId(), null, Modality.VIRTUAL));
        assertThat(outcome.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void createPresencialRequiresComuna() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = serviceAdminService.create(provider.getId(),
            command(company.getId(), null, Modality.PRESENCIAL));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMUNA_REQUIRED.code());
    }

    @Test
    void createRejectsUnknownComuna() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = serviceAdminService.create(provider.getId(),
            command(company.getId(), 999_999, Modality.VIRTUAL));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMUNA_NOT_FOUND.code());
    }

    @Test
    void createPresencialWithComunaSucceeds() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var comuna = presetComuna();
        var outcome = serviceAdminService.create(provider.getId(),
            command(company.getId(), comuna.getId(), Modality.PRESENCIAL));
        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.valueUnchecked().getComuna().getId()).isEqualTo(comuna.getId());
    }

    @Test
    void updateRejectsUnknownService() {
        var provider = persistProvider();
        var outcome = serviceAdminService.update(provider.getId(), UUID.randomUUID(),
            new UpdateServiceCommand(null, null, null, null, null, null, null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.SERVICE_NOT_FOUND.code());
    }

    @Test
    void updateRejectsForeignProvider() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);
        var foreign = persistProvider();
        var outcome = serviceAdminService.update(foreign.getId(), service.getId(),
            new UpdateServiceCommand(null, "Nuevo", null, null, null, null, null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void updateRejectsUnknownComuna() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);
        var outcome = serviceAdminService.update(provider.getId(), service.getId(),
            new UpdateServiceCommand(999_999, null, null, null, null, null, null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMUNA_NOT_FOUND.code());
    }

    @Test
    void updateToPresencialWithoutComunaFails() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);
        var outcome = serviceAdminService.update(provider.getId(), service.getId(),
            new UpdateServiceCommand(null, null, Modality.PRESENCIAL, null, null, null, null, null, null, null));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMUNA_REQUIRED.code());
    }

    @Test
    void updateToVirtualClearsComuna() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.PRESENCIAL);
        var outcome = serviceAdminService.update(provider.getId(), service.getId(),
            new UpdateServiceCommand(null, null, Modality.VIRTUAL, null, null, null, null, null, null, null));
        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.valueUnchecked().getComuna()).isNull();
    }

    @Test
    void changeStatusRejectsUnknownAndForeign() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);

        assertThat(serviceAdminService.changeStatus(provider.getId(), UUID.randomUUID(), false)
            .error().code()).isEqualTo(Errors.SERVICE_NOT_FOUND.code());

        var foreign = persistProvider();
        assertThat(serviceAdminService.changeStatus(foreign.getId(), service.getId(), false)
            .error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    private CreateServiceCommand command(UUID companyId, Integer comunaId, Modality modality) {
        return new CreateServiceCommand(companyId, comunaId, unique("servicio"), modality,
            BigDecimal.valueOf(10_000), 60, "Descripción", "Recomendación", null, null);
    }
}