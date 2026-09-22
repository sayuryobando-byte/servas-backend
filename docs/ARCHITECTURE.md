# Arquitectura — Servas Backend

## 1. Diagrama de contexto

```mermaid
flowchart LR
    subgraph Clientes
        Client[Cliente final] -->|" Busca y reserva servicios "| API
        C3[Cliente final] -->|" Consulta sus reservas por documento "| API
    end

    subgraph Proveedores
        Prov[Proveedor] -->|" Gestiona su empresa, horarios y bloqueos "| API
        Prov2[Proveedor ayuda] -->|" Consulta agenda y reportes "| API
    end

    API[Servas Backend]
    API -->|" Persistencia "| PG[(PostgreSQL 17)]
    API -->|" Sesiones, OTP, cache "| RD[(Redis 7)]
    VM[VictoriaMetrics] -->|" Mete /metrics "| API
    Grafana[Grafana] -->|" Consulta "| VM
```

## 2. Diagrama de componentes

```mermaid
flowchart TB
subgraph Backend["servas-backend (Spring Boot 4.1.1 / Java 21)"]
direction TB
Web[Web Layer / REST Controllers]
AuthResolver[AuthResolver / sesiones]
AuthMod[Auth Module]

subgraph App["Application Layer (services)"]
AuthS[AuthService]
OTP[OtpService]
Token[TokenService]
CatS[CatalogService]
CompS[CompanyService]
AdmS[ServiceAdminService]
SchS[ScheduleService]
BlkS[BlockedDateService]
ResS[ReservationService]
RepS[ReportService]
end

subgraph Domain["Domain Layer"]
UserE[User]
ProvE[Provider]
CompE[Company]
ServE[Service]
ComuE[Comuna]
SchedE[Schedule]
BlkE[BlockedDate]
CliE[Client]
ResE[Reservation]
Enums[Enumerations: Modality, ReservationStatus, CancelledBy, DocumentType]
end

subgraph Infra["Infrastructure"]
Repos[Spring Data Repositories]
Flyway[Flyway Migrations]
Actuator[Actuator / Prometheus]
Security[SecurityBeansConfig / CORS]
end

subgraph Common["Common"]
Result[Result&lt;T&gt; functional]
Errors[Errores de dominio]
ApiHelper[Api.resolve / statusOf]
end

Web --> App
AuthResolver --> AuthMod
Web --> Common
end

Controllers -->|" map/translate "|Repos
Repos -->|"JPA/Hibernate "|PG[(PostgreSQL)]
TokenService --> RD[(Redis)]
OtpService --> RD[(Redis)]

App --> Domain
Domain --> Repos
Flyway --> PG
```

## 3. Diagrama de dominio

```mermaid
classDiagram
    class User {
        +UUID id
        +String email
        +String passwordHash
        +boolean isVerified
        +Instant createdAt
        +markVerified()
    }

    class Provider {
        +UUID id
        +User user
        +DocumentType documentType
        +String documentNumber
        +String firstName
        +String lastName
        +LocalDate birthDate
        +String phone
    }

    class Company {
        +UUID id
        +Provider provider
        +String nit
        +String name
        +String description
        +String address
        +String socialMedia
        +String logoUrl
        +updateProfile()
    }

    class Service {
        +UUID id
        +Company company
        +Comuna comuna
        +String name
        +Modality modality
        +BigDecimal cost
        +int durationMinutes
        +String description
        +String recommendations
        +LocalDate startDate
        +LocalDate endDate
        +boolean active
        +updateDetails()
        +changeActive()
    }

    class Comuna {
        +Integer id
        +String name
    }

    class Schedule {
        +UUID id
        +Service service
        +Integer dayOfWeek
        +LocalTime startTime
        +LocalTime endTime
    }

    class BlockedDate {
        +UUID id
        +Company company
        +Service service
        +LocalDate blockDate
        +String reason
    }

    class Client {
        +UUID id
        +DocumentType documentType
        +String documentNumber
        +String firstName
        +String lastName
        +String phone
        +String email
    }

    class Reservation {
        +UUID id
        +Service service
        +Client client
        +LocalDate reservationDate
        +LocalTime startTime
        +LocalTime endTime
        +ReservationStatus status
        +Instant createdAt
        +CancelledBy cancelledBy
        +String cancellationReason
        +isActive()
        +confirmDates()
        +cancel()
    }

    User "1" -- "1" Provider: "se autentica como"
    Provider "1" -- "0..*" Company: "es dueno de"
    Company "1" -- "0..*" Service: "ofrece"
    Service "*" --> "0..1" Comuna: "se ubica en"
    Service "1" -- "0..*" Schedule: "atiende segun"
    Service "1" -- "0..*" BlockedDate: "restringido por"
    Company "1" -- "0..*" BlockedDate: "bloqueda fechas de"
    Service "1" -- "0..*" Reservation: "recibe"
    Client "1" -- "0..*" Reservation: "hace"
```

## 4. Diagrama Entidad-Relación (modelo de base de datos)

```mermaid
erDiagram
    USERS ||--o| PROVIDERS: has
    PROVIDERS ||--o{ COMPANIES: owns
    COMPANIES ||--o{ SERVICES: offers
    COMUNAS ||--o{ SERVICES: located_in
    SERVICES ||--o{ SCHEDULES: attends_by
    SERVICES ||--o{ BLOCKED_DATES: restricted_by
    COMPANIES ||--o{ BLOCKED_DATES: blocks_dates_of
    SERVICES ||--o{ RESERVATIONS: receives
    CLIENTS ||--o{ RESERVATIONS: makes

    USERS {
        uuid id PK
        varchar100 email UK
        varchar255 password_hash
        boolean is_verified
        timestamptz created_at
    }
    PROVIDERS {
        uuid id PK
        uuid user_id FK, UK
        document_type document_type
        varchar50 document_number UK
        varchar100 first_name
        varchar100 last_name
        date birth_date
        varchar11 phone
    }
    COMPANIES {
        uuid id PK
        uuid provider_id FK
        varchar50 nit UK
        varchar150 name
        text description
        varchar255 address
        varchar255 social_media
        varchar255 logo_url
    }
    SERVICES {
        uuid id PK
        uuid company_id FK
        int comuna_id FK
        varchar100 name
        modality modality
        numeric102 cost
        int duration_minutes
        text description
        text recommendations
        date start_date
        date end_date
        boolean is_active
    }
    SCHEDULES {
        uuid id PK
        uuid service_id FK
        int day_of_week
        time start_time
        time end_time
    }
    BLOCKED_DATES {
        uuid id PK
        uuid company_id FK
        uuid service_id FK
        date block_date
        varchar255 reason
    }
    COMUNAS {
        int id PK
        varchar100 name
    }
    CLIENTS {
        uuid id PK
        document_type document_type
        varchar50 document_number
        varchar100 first_name
        varchar100 last_name
        varchar15 phone
        varchar100 email
    }
    RESERVATIONS {
        uuid id PK
        uuid service_id FK
        uuid client_id FK
        date reservation_date
        time start_time
        time end_time
        reservation_status status
        timestamptz created_at
        cancelled_by cancelled_by
        text cancellation_reason
    }
```

## 5. Detalle de diseño

### Capas

| Capa            | Paquete                        | Responsabilidad                                            |
|-----------------|--------------------------------|------------------------------------------------------------|
| **Web**         | `com.servas.web`               | Controllers REST, DTOs, resolucion de respuesta `Api`      |
| **Application** | `com.servas.application.*`     | Servicios de caso de uso, logica de negocio                |
| **Domain**      | `com.servas.domain.entity`     | Entidades JPA, enumeraciones                               |
| **Repository**  | `com.servas.domain.repository` | Spring Data Repositories                                   |
| **Config**      | `com.servas.config`            | Beans de seguridad, CORS, criptografia                     |
| **Common**      | `com.servas.common`            | `Result<T>` funcional, errores de dominio, constantes      |
| **Seed**        | `com.servas.seed`              | `DataSeeder` con datos de demo (condicional por propiedad) |

### Patrones utilizados

* **`Result<T>` funcional** (monada `Result`): los servicios no lanzan excepciones de negocio; devuelven
  `Result.success(valor)` o `Result.failure(AppError)`. El adapter web (`Api.resolve`) traduce a HTTP status.
* **Composición con `flatMap`**: encadenamiento seguro (ej. `authResolver.requireProvider(...).flatMap(...)`) evitando
  NPE y manteniendo el flujo funcional.
* **DDD ligero / entidades ricas**: las entidades encapsulan su comportamiento (`Reservation.cancel()`,
  `Company.updateProfile()`, `Service.changeActive()`).
* **Builder de Lombok** para construcción de entidades.
* **Sesiones basadas en Redis**: `TokenService` emite un token UUID almacenado en Redis con TTL de 8 horas. No hay JWT
  stateless.
* **OTP en Redis**: `OtpService` guarda códigos de 6 dígitos con TTL de 10 minutos y máximo 3 intentos.
* **Flyway** para versionado del esquema (`V1__init_schema.sql`, `V2__providers_birth_date_nullable.sql`), con
  `ddl-auto: validate` para validar las entidades contra el esquema.

### Comunicaciones / dependencias

* **PostgreSQL 17** (imagen `postgres:17-alpine`) — persistencia.
* **Redis 7** (imagen `redis:7-alpine`) — sesiones `session:token:*` y OTP `otp:email:*`, `otp:attempts:email:*`.

### Stack de calidad en CI (GitHub Actions y Azure Pipelines)

| Componente                  | Descripcion                                        |
|-----------------------------|----------------------------------------------------|
| JaCoCo 100%                 | Gate de cobertura: instrucciones y ramas al 100%   |
| Trivy `sbom` (SCA)          | Escanea dependencias desde el SBOM (HIGH/CRITICAL) |
| Trivy                       | Escaneo de imagen Docker (HIGH/CRITICAL bloquea)   |
| CycloneDX SBOM              | Generacion de bill of materials en cada build      |
| SpotBugs + FindSecurityBugs | SAST estatico (reporte por build, no bloquea)      |