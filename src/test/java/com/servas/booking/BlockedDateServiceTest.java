package com.servas.booking;

import com.servas.application.schedule.BlockedDateService;
import com.servas.application.schedule.BlockedDateService.BlockDateCommand;
import com.servas.common.constant.Errors;
import com.servas.domain.enumeration.Modality;
import com.servas.support.IntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedDateServiceTest extends IntegrationTestBase {

    @Autowired
    private BlockedDateService blockedDateService;

    @Test
    void blockRejectsUnknownCompany() {
        var provider = persistProvider();
        var outcome = blockedDateService.block(provider.getId(),
            new BlockDateCommand(UUID.randomUUID(), null, LocalDate.now(), "x"));
        assertThat(outcome.error().code()).isEqualTo(Errors.COMPANY_NOT_FOUND.code());
    }

    @Test
    void blockRejectsForeignCompany() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var foreign = persistProvider();
        var outcome = blockedDateService.block(foreign.getId(),
            new BlockDateCommand(company.getId(), null, LocalDate.now(), "x"));
        assertThat(outcome.error().code()).isEqualTo(Errors.NOT_COMPANY_OWNER.code());
    }

    @Test
    void blockRejectsUnknownService() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var outcome = blockedDateService.block(provider.getId(),
            new BlockDateCommand(company.getId(), UUID.randomUUID(), LocalDate.now(), "x"));
        assertThat(outcome.error().code()).isEqualTo(Errors.SERVICE_NOT_FOUND.code());
    }

    @Test
    void blockRejectsServiceOfAnotherCompany() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);
        var otherProvider = persistProvider();
        var otherCompany = persistCompany(otherProvider);

        var outcome = blockedDateService.block(otherProvider.getId(),
            new BlockDateCommand(otherCompany.getId(), service.getId(), LocalDate.now(), "x"));
        assertThat(outcome.error().code()).isEqualTo(Errors.SERVICE_NOT_IN_COMPANY.code());
    }

    @Test
    void blockAndListForCompany() {
        var provider = persistProvider();
        var company = persistCompany(provider);
        var service = persistService(company, Modality.VIRTUAL);

        var blocked = blockedDateService.block(provider.getId(),
            new BlockDateCommand(company.getId(), service.getId(), LocalDate.now(), "Mantenimiento"));
        assertThat(blocked.isSuccess()).isTrue();
        assertThat(blocked.valueUnchecked().getService().getId()).isEqualTo(service.getId());

        assertThat(blockedDateService.listForCompany(company.getId()))
            .extracting(b -> b.getService().getId())
            .containsExactly(service.getId());
    }
}