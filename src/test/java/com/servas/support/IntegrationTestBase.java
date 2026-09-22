package com.servas.support;

import com.servas.domain.entity.Company;
import com.servas.domain.entity.Provider;
import com.servas.domain.entity.Service;
import com.servas.domain.entity.User;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.enumeration.Modality;
import com.servas.domain.repository.BlockedDateRepository;
import com.servas.domain.repository.ClientRepository;
import com.servas.domain.repository.CompanyRepository;
import com.servas.domain.repository.ComunaRepository;
import com.servas.domain.repository.ProviderRepository;
import com.servas.domain.repository.ReservationRepository;
import com.servas.domain.repository.ScheduleRepository;
import com.servas.domain.repository.ServiceRepository;
import com.servas.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest
public abstract class IntegrationTestBase {

    protected static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:17")
        .withEnv("POSTGRES_DB", "servas_test")
        .withEnv("POSTGRES_USER", "servas")
        .withEnv("POSTGRES_PASSWORD", "servas")
        .withExposedPorts(5432);

    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/servas_test");
        registry.add("spring.datasource.username", () -> "servas");
        registry.add("spring.datasource.password", () -> "servas");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.seed.enabled", () -> "true");
    }

    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected ProviderRepository providers;
    @Autowired
    protected CompanyRepository companies;
    @Autowired
    protected ComunaRepository comunas;
    @Autowired
    protected ServiceRepository services;
    @Autowired
    protected ScheduleRepository scheduleRepository;
    @Autowired
    protected ReservationRepository reservations;
    @Autowired
    protected ClientRepository clients;
    @Autowired
    protected BlockedDateRepository blockedDates;
    @Autowired
    protected StringRedisTemplate redis;

    protected String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected User persistUser() {
        return users.save(User.builder()
            .email(unique("user") + "@servas.test")
            .passwordHash(passwordEncoder.encode("Pass1234!"))
            .isVerified(Boolean.TRUE)
            .build());
    }

    protected Provider persistProvider() {
        var user = persistUser();
        return providers.save(Provider.builder()
            .user(user)
            .documentType(DocumentType.CC)
            .documentNumber("DOC-" + unique("d"))
            .firstName("Proveedor")
            .lastName("Uno")
            .phone("3000000000")
            .build());
    }

    protected Company persistCompany(Provider provider) {
        return companies.save(Company.builder()
            .provider(provider)
            .nit("NIT-" + unique("n"))
            .name("Empresa de " + provider.getFirstName())
            .address("Calle Falsa 123")
            .build());
    }

    protected Service persistService(Company company, Modality modality) {
        return persistService(company, modality, 60);
    }

    protected Service persistService(Company company, Modality modality, Integer durationMinutes) {
        boolean presencial = modality == Modality.PRESENCIAL;
        var comuna = presetComuna();
        return services.save(Service.builder()
            .company(company)
            .comuna(presencial ? comuna : null)
            .name(unique("servicio"))
            .modality(modality)
            .cost(BigDecimal.valueOf(10_000))
            .durationMinutes(durationMinutes)
            .description("Servicio de prueba")
            .build());
    }

    protected com.servas.domain.entity.Comuna presetComuna() {
        return comunas.findAll().stream().findFirst().orElse(null);
    }

    protected void persistWeekSchedules(UUID serviceId, LocalTime startTime, LocalTime endTime) {
        var schedules = java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(dayOfWeek -> com.servas.domain.entity.Schedule.builder()
                .service(services.findById(serviceId).orElseThrow())
                .dayOfWeek(dayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .build())
            .toList();
        scheduleRepository.saveAll(schedules);
    }

    protected String otpOf(String email) {
        return redis.keys("otp:email:" + email).stream()
            .findFirst()
            .map(redis.opsForValue()::get)
            .orElseThrow();
    }
}