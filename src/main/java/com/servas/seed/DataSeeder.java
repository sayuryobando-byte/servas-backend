package com.servas.seed;

import com.servas.domain.entity.BlockedDate;
import com.servas.domain.entity.Client;
import com.servas.domain.entity.Company;
import com.servas.domain.entity.Comuna;
import com.servas.domain.entity.Provider;
import com.servas.domain.entity.Reservation;
import com.servas.domain.entity.Schedule;
import com.servas.domain.entity.Service;
import com.servas.domain.entity.User;
import com.servas.domain.enumeration.CancelledBy;
import com.servas.domain.enumeration.DocumentType;
import com.servas.domain.enumeration.Modality;
import com.servas.domain.enumeration.ReservationStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final int TARGET_SIZE = 50;
    private static final String DEMO_EMAIL_DOMAIN = "demo.servas";
    private static final String PROVIDER_EMAIL_PREFIX = "proveedor";
    private static final String CLIENT_EMAIL_PREFIX = "cliente";
    private static final String FAKE_PASSWORD_HASH = "{noop}Demo1234!";

    private static final String[] COMUNA_NAMES = {
        // Barrios y comunas de Medellín + municipios del Valle de Aburrá (reales)
        "El Poblado", "Provenza", "Manila", "San Diego", "Boston", "Prado",
        "La Candelaria", "Laureles", "Estadio", "Suramérica", "La América", "San Javier",
        "Belén", "Altavista", "Santa Elena", "San Antonio de Prado", "San Cristóbal", "Palmitas",
        "Guayabal", "Buenos Aires", "Miraflores", "Veinte de Julio", "La Sierra", "Villa Hermosa",
        "Aranjuez", "Moravia", "Manrique", "Campo Valdés", "Las Granjas", "Santa Cruz",
        "La Rosa", "Villa del Socorro", "Andalucía", "La Francia", "Castilla", "Florencia",
        "Tejelo", "Doce de Octubre", "Picacho", "Pedregal", "Robledo", "El Popular",
        "Santo Domingo Savio", "Granizal", "Bello", "Itagüí", "Envigado", "Sabaneta",
        "La Estrella", "Copacabana"
    };

    private static final String[] FIRST_NAMES = {
        "Ana", "Luis", "Carlos", "María", "José", "Lucía", "Pedro", "Carmen",
        "Andrés", "Paola", "Jorge", "Camila", "Miguel", "Valentina", "Rodrigo", "Catalina",
        "Sebastián", "Fernanda", "Cristóbal", "Antonia"
    };

    private static final String[] LAST_NAMES = {
        "González", "Rodríguez", "Fernández", "López", "Martínez", "Pérez",
        "García", "Sánchez", "Ramírez", "Torres", "Flores", "Rojas", "Molina", "Castro",
        "Ríos", "Morales"
    };

    private static final String[] COMPANY_PREFIXES = {
        "Estética", "Taller", "Academia", "Clínica", "Estudio", "Salón", "Centro", "Spa"
    };

    private static final String[] COMPANY_SUFFIXES = {
        "Aura", "Luz", "Nova", "Pro", "Vida", "Creativa", "Plus", "Premium"
    };

    private static final String[] SERVICE_NAMES = {
        "Corte de cabello", "Manicure y pedicure", "Asesoría nutricional", "Clase de yoga",
        "Mantenimiento automotriz", "Diseño de uñas", "Chequeo dental", "Sesión de masaje",
        "Depilación láser", "Tutoría académica", "Revisión técnica", "Atención psicológica"
    };

    private static final String[] REASONS = {
        "Cambio de planes", "Emergencia laboral", "Reserva duplicada", "Problemas de salud", "Compromiso familiar"
    };

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final CompanyRepository companyRepository;
    private final ComunaRepository comunaRepository;
    private final ServiceRepository serviceRepository;
    private final ScheduleRepository scheduleRepository;
    private final BlockedDateRepository blockedDateRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Demo data already present ({} users), skipping seed.", userRepository.count());
            return;
        }

        var now = LocalDate.now();
        var comunas = seedComunas();
        var users = seedUsers();
        var providers = seedProviders(users);
        var companies = seedCompanies(providers);
        var services = seedServices(companies, comunas, now);
        var schedules = seedSchedules(services, now);
        seedBlockedDates(companies, services, now);
        var clients = seedClients();
        seedReservations(services, schedules, clients, now);

        log.info("Demo data seeded: {} comunas, {} users, {} providers, {} companies, {} services, "
                + "{} schedules, {} blocked dates, {} clients, {} reservations.",
            comunas.size(), users.size(), providers.size(), companies.size(), services.size(),
            schedules.size(), 50, clients.size(), 50);
    }

    private List<Comuna> seedComunas() {
        var list = new ArrayList<Comuna>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(Comuna.builder().id(i + 1).name(COMUNA_NAMES[i]).build());
        }
        return comunaRepository.saveAll(list);
    }

    private List<User> seedUsers() {
        var list = new ArrayList<User>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(User.builder()
                .email(PROVIDER_EMAIL_PREFIX + (i + 1) + "@" + DEMO_EMAIL_DOMAIN)
                .passwordHash(FAKE_PASSWORD_HASH)
                .isVerified(i % 5 != 0)
                .build());
        }
        return userRepository.saveAll(list);
    }

    private List<Provider> seedProviders(List<User> users) {
        var list = new ArrayList<Provider>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(Provider.builder()
                .user(users.get(i))
                .documentType(DocumentType.values()[i % DocumentType.values().length])
                .documentNumber("P" + String.format("%09d", i + 1))
                .firstName(FIRST_NAMES[i % FIRST_NAMES.length])
                .lastName(LAST_NAMES[i % LAST_NAMES.length])
                .birthDate(LocalDate.of(1980 + i % 20, 1 + i % 12, 1 + i % 28))
                .phone(String.format("%011d", 31100000000L + i))
                .build());
        }
        return providerRepository.saveAll(list);
    }

    private List<Company> seedCompanies(List<Provider> providers) {
        var list = new ArrayList<Company>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(Company.builder()
                .provider(providers.get(i))
                .nit(String.format("900%08d", i + 1))
                .name(COMPANY_PREFIXES[i % COMPANY_PREFIXES.length] + " "
                    + COMPANY_SUFFIXES[(i / COMPANY_PREFIXES.length) % COMPANY_SUFFIXES.length]
                    + " " + (i + 1))
                .description("Empresa demo " + (i + 1))
                .address("Calle " + (i + 1) + " # " + (10 + i))
                .socialMedia("@servas" + (i + 1))
                .logoUrl("https://images.demo.servas/logo-" + (i + 1) + ".png")
                .build());
        }
        return companyRepository.saveAll(list);
    }

    private List<Service> seedServices(List<Company> companies, List<Comuna> comunas, LocalDate now) {
        var list = new ArrayList<Service>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            var modality = i % 2 == 0 ? Modality.PRESENCIAL : Modality.VIRTUAL;
            list.add(Service.builder()
                .company(companies.get(i))
                .comuna(modality == Modality.PRESENCIAL ? comunas.get(i % comunas.size()) : null)
                .name(SERVICE_NAMES[i % SERVICE_NAMES.length] + " " + (i / SERVICE_NAMES.length + 1))
                .modality(modality)
                .cost(BigDecimal.valueOf(25_000 + (i % 20) * 5_000).setScale(2))
                .durationMinutes(30 + (i % 6) * 15)
                .description("Descripción del servicio " + (i + 1))
                .recommendations("Recomendación " + (i % 3 + 1) + " del servicio " + (i + 1))
                .startDate(now.minusDays(7))
                .endDate(now.plusDays(90))
                .isActive(i % 17 != 0)
                .build());
        }
        return serviceRepository.saveAll(list);
    }

    private List<Schedule> seedSchedules(List<Service> services, LocalDate now) {
        var list = new ArrayList<Schedule>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            var service = services.get(i);
            var workday = now.plusDays(2 + i % 21);
            var start = LocalTime.of(9 + i % 9, i % 2 == 0 ? 0 : 30);
            list.add(Schedule.builder()
                .service(service)
                .dayOfWeek(workday.getDayOfWeek().getValue())
                .startTime(start)
                .endTime(start.plusMinutes(service.getDurationMinutes()))
                .build());
        }
        return scheduleRepository.saveAll(list);
    }

    private void seedBlockedDates(List<Company> companies, List<Service> services, LocalDate now) {
        var list = new ArrayList<BlockedDate>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(BlockedDate.builder()
                .company(companies.get(i))
                .service(i % 3 == 0 ? services.get((i + 5) % services.size()) : null)
                .blockDate(now.plusDays(40 + i % 20))
                .reason(i % 2 == 0 ? null : REASONS[i % REASONS.length])
                .build());
        }
        blockedDateRepository.saveAll(list);
    }

    private List<Client> seedClients() {
        var list = new ArrayList<Client>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            list.add(Client.builder()
                .documentType(DocumentType.values()[i % (DocumentType.values().length - 1)])
                .documentNumber("C" + String.format("%09d", i + 1))
                .firstName(FIRST_NAMES[(i + 7) % FIRST_NAMES.length])
                .lastName(LAST_NAMES[(i + 5) % LAST_NAMES.length])
                .phone(String.format("%011d", 32000000000L + i))
                .email(CLIENT_EMAIL_PREFIX + (i + 1) + "@" + DEMO_EMAIL_DOMAIN)
                .build());
        }
        return clientRepository.saveAll(list);
    }

    private void seedReservations(List<Service> services, List<Schedule> schedules,
                                  List<Client> clients, LocalDate now) {
        var list = new ArrayList<Reservation>(TARGET_SIZE);
        for (int i = 0; i < TARGET_SIZE; i++) {
            var service = services.get(i);
            var schedule = schedules.get(i);
            var cancelled = i % 13 == 0;
            list.add(Reservation.builder()
                .service(service)
                .client(clients.get(i))
                .reservationDate(now.plusDays(2 + i % 21))
                .startTime(schedule.getStartTime())
                .endTime(schedule.getStartTime().plusMinutes(service.getDurationMinutes()))
                .status(cancelled ? ReservationStatus.CANCELLED
                    : i % 5 == 0 ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED)
                .cancelledBy(cancelled ? CancelledBy.CLIENT : null)
                .cancellationReason(cancelled ? REASONS[i % REASONS.length] : null)
                .build());
        }
        reservationRepository.saveAll(list);
    }
}