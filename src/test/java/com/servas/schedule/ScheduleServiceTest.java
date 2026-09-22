package com.servas.schedule;

import com.servas.application.schedule.ScheduleService;
import com.servas.application.schedule.ScheduleService.ScheduleItem;
import com.servas.common.constant.Errors;
import com.servas.domain.entity.Company;
import com.servas.domain.entity.Provider;
import com.servas.domain.entity.Service;
import com.servas.domain.repository.BlockedDateRepository;
import com.servas.domain.repository.ReservationRepository;
import com.servas.domain.repository.ScheduleRepository;
import com.servas.domain.repository.ServiceRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScheduleServiceTest {

    private static final UUID PROVIDER = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private BlockedDateRepository blockedDateRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @InjectMocks
    private ScheduleService scheduleService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        var company = Company.builder().provider(Provider.builder().id(PROVIDER).build()).build();
        when(serviceRepository.findById(SERVICE)).thenReturn(Optional.of(
            Service.builder().id(SERVICE).company(company).build()));
        when(scheduleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejectsNullAndEmptyItems() {
        assertThat(scheduleService.configure(PROVIDER, SERVICE, null).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(PROVIDER, SERVICE, List.of()).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.replace(PROVIDER, SERVICE, null).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.replace(PROVIDER, SERVICE, List.of()).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
    }

    @Test
    void rejectsStructurallyInvalidItems() {
        assertThat(scheduleService.configure(PROVIDER, SERVICE,
            List.of(new ScheduleItem(0, LocalTime.of(9, 0), LocalTime.of(10, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(PROVIDER, SERVICE,
            List.of(new ScheduleItem(8, LocalTime.of(9, 0), LocalTime.of(10, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
        assertThat(scheduleService.configure(PROVIDER, SERVICE,
            List.of(new ScheduleItem(1, LocalTime.of(10, 0), LocalTime.of(9, 0)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
    }

    @Test
    void rejectsOverlappingSameDayItems() {
        assertThat(scheduleService.configure(PROVIDER, SERVICE, List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new ScheduleItem(1, LocalTime.of(9, 30), LocalTime.of(10, 30)))).error().code())
            .isEqualTo(Errors.INVALID_SCHEDULE.code());
    }

    @Test
    void acceptsValidAdjacentSchedules() {
        var created = scheduleService.configure(PROVIDER, SERVICE, List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            new ScheduleItem(1, LocalTime.of(10, 0), LocalTime.of(11, 0)),
            new ScheduleItem(1, LocalTime.of(11, 0), LocalTime.of(12, 0)),
            new ScheduleItem(2, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        assertThat(created.isSuccess()).isTrue();
        assertThat(created.valueUnchecked()).hasSize(4);

        var replaced = scheduleService.replace(PROVIDER, SERVICE, List.of(
            new ScheduleItem(1, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        assertThat(replaced.isSuccess()).isTrue();
        assertThat(replaced.valueUnchecked()).hasSize(1);
    }
}