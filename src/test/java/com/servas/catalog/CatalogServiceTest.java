package com.servas.catalog;

import com.servas.application.catalog.CatalogService;
import com.servas.application.catalog.ServiceAdminService;
import com.servas.application.catalog.ServiceAdminService.CreateServiceCommand;
import com.servas.domain.enumeration.Modality;
import com.servas.support.IntegrationTestBase;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogServiceTest extends IntegrationTestBase {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private ServiceAdminService serviceAdminService;

    @Test
    void searchAppliesEveryOptionalFilter() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var otherProvider = persistProvider();
        var otherCompany = persistCompany(otherProvider);
        var comuna = presetComuna();

        var presencial = serviceAdminService.create(provider.getId(),
            command(company.getId(), comuna.getId(), "Presencial", Modality.PRESENCIAL)).valueUnchecked();
        var virtual = serviceAdminService.create(otherProvider.getId(),
            command(otherCompany.getId(), null, "Virtual", Modality.VIRTUAL)).valueUnchecked();
        var inactive = serviceAdminService.create(provider.getId(),
            command(company.getId(), null, "Inactivo", Modality.VIRTUAL)).valueUnchecked();
        serviceAdminService.changeStatus(provider.getId(), inactive.getId(), false);

        var all = catalogService.search(null, null, null);
        assertThat(all).extracting(s -> s.getId())
            .contains(presencial.getId(), virtual.getId()).doesNotContain(inactive.getId());

        var byCompany = catalogService.search(company.getId(), null, null);
        assertThat(byCompany).extracting(s -> s.getId())
            .contains(presencial.getId()).doesNotContain(virtual.getId(), inactive.getId());

        var byOtherCompany = catalogService.search(otherCompany.getId(), null, null);
        assertThat(byOtherCompany).extracting(s -> s.getId())
            .contains(virtual.getId()).doesNotContain(presencial.getId());

        var byComuna = catalogService.search(null, comuna.getId(), null);
        assertThat(byComuna).extracting(s -> s.getId())
            .contains(presencial.getId()).doesNotContain(virtual.getId());

        var byModality = catalogService.search(null, null, Modality.VIRTUAL);
        assertThat(byModality).extracting(s -> s.getId())
            .contains(virtual.getId()).doesNotContain(presencial.getId());

        var byUnknownComuna = catalogService.search(null, 999_999, null);
        assertThat(byUnknownComuna).extracting(s -> s.getId())
            .doesNotContain(presencial.getId(), virtual.getId());

        var combos = catalogService.search(company.getId(), null, Modality.VIRTUAL);
        assertThat(combos).extracting(s -> s.getId()).doesNotContain(presencial.getId());
    }

    private CreateServiceCommand command(java.util.UUID companyId, Integer comunaId, String name, Modality modality) {
        return new CreateServiceCommand(companyId, comunaId, name, modality,
            BigDecimal.valueOf(9_999), 60, "Descripción", null, null, null);
    }
}