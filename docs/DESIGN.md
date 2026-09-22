# Diseño Estratégico / Táctico — Servas Backend

## 1. Diseño estratégico (cuadrantes y delimitación)

El dominio se estructura en un **unico bounded context**: la asignación de citas a servicios profesionales (`Servas`).
No se dividió en servicios/microservicios porque el alcance actual (un agregado central, `Reservation`) no lo justifica;
Spring Boot modular por paquetes mantiene los límites.

### Ubicuidad / lenguaje del dominio

| Termino                             | Definicion                                                                             |
|-------------------------------------|----------------------------------------------------------------------------------------|
| **Proveedor**                       | Profesional/empresa que ofrece servicios. Tiene cuenta (`User`) + perfil (`Provider`). |
| **Empresa** (`Company`)             | Entidad comercial del proveedor; la que figura en la reserva.                          |
| **Servicio** (`Service`)            | Oferta reservable: costo, duracion, modalidad (VIRTUAL/PRESENCIAL), comuna.            |
| **Horario** (`Schedule`)            | Franja semanal de atencion de un servicio (`day_of_week`, inicio/fin).                 |
| **Fecha bloqueada** (`BlockedDate`) | Dia sin atencion (por empresa y/o servicio).                                           |
| **Cliente** (`Client`)              | Persona que reserva. Sin cuenta; se identifica por documento.                          |
| **Reserva** (`Reservation`)         | Compromiso de atencion en franja/servicio/estado.                                      |

### Reglas invariantes del agregado `Reservation`

1. `endTime == startTime + durationMinutes(service)`.
2. La franja cae dentro de un `Schedule` del día y alineada a la duración (`(start - schedule.start) % duration == 0`).
3. La fecha no esté bloqueada (empresa o servicio).
4. No se superponga con otra reserva activa del mismo servicio.
5. La fecha este dentro de `[startDate, endDate]` del servicio y el servicio este activo.
6. Solo reservas `PENDING` o `CONFIRMED` son modificables/cancelables.

Estas reglas viven en `ReservationService.validateSlot` y en los metodos de estado de la entidad (`isActive`, `cancel`,
`confirmDates`).

## 2. Diseño táctico

### Capas

```
web (controllers)  ->  application (services)  ->  domain (entidades + reglas)
                                        |-->  domain.repository (Spring Data)
                                        |-->  common (Result<T>, Errors)
```

### Patron `Result<T>` (monada)

Los servicios de aplicación **no lanzan excepciones de negocio**. Devuelven `Result.success(value)` o
`Result.failure(AppError)`:

```java
var outcome = authResolver.requireProvider(authorization)
    .flatMap(provider -> serviceAdminService.create(provider.getId(), request.toCommand()));
return Api.

resolve(outcome, HttpStatus.CREATED);
```

* `Result.flatMap(fn)`: encadena operaciones que pueden fallar sin propagar excepciones.
* `Result.map(fn)`: transforma el valor de éxito.
* `Api.resolve(result, status)`: traduce `Result` a `ResponseEntity` con el código HTTP por dominio (`Api.statusOf`).
* `AppError`: par `(code, message)` (ej. `SLOT_NOT_AVAILABLE`) → mapa a `HttpStatus`.

> Ventajas: flujo funcional explicito, DTO limpios en los controllers, errores uniformes en JSON `{code, message}`,
> testabilidad CLI pura.

### Entidades ricas (behavioral entities)

El comportamiento de estado NO está disperso en servicios:

```java
reservation.isActive();                    // PENDING o CONFIRMED
reservation.

cancel(cancelledBy, reason);   // muta estado + motivo
reservation.

confirmDates(date, start, end);
user.

markVerified();
company.

updateProfile(...);
service.

updateDetails(...);
service.

changeActive(active);
```

### Estructura de paquetes (arquitectura hexagonal ligera)

| Capa        | Paquete                                                         | Depende de           |
|-------------|-----------------------------------------------------------------|----------------------|
| Web         | `com.servas.web`                                                | application + common |
| Application | `com.servas.application.{auth,booking,catalog,schedule,report}` | domain + common      |
| Domain      | `com.servas.domain.{entity,enumeration,repository}`             | common               |
| Common      | `com.servas.common.{constant,functional}`                       | —                    |
| Config      | `com.servas.config`                                             | spring security      |
| Seed        | `com.servas.seed`                                               | domain               |

Regla de dependencia: **las capas sempre apuntan hacia adentro** (web→application→domain). No hay dependencias cíclicas
entre paquetes de aplicación (cada módulo de `application` es independiente).

### Transacciones

* `@Transactional` por método de servicio (comando), `@Transactional(readOnly = true)` para consultas.
* `open-in-view = false`: las entidades no se serializan con lazy-loading; los controllers proyectan a DTO (`view(...)`)
  en la transacción.
* `ddl-auto: validate` + Flyway: el esquema es fuente de verdad y se valida contra las entidades al arrancar.

### DB tabla por agregado

* `reservations` es la tabla raiz clave; indices orientados a la validación de choques (`idx_reservations_service_date`)
  y a las agendas por proveedor.
* Ocurrencialidad: 1 `Service` → N `Schedule`/`BlockedDate`/`Reservation`; 1 `Company` → N `Service`; 1 `Client` → N
  `Reservation`.

## 3. Hojas de datos de performance

### 3.1 Batería de pruebas (k6)

Suite de pruebas de carga en [`perf/`](../perf/) ejecutada desde Docker (`grafana/k6`), sin dependencias locales.
Lanzador:
`.\perf\run.ps1 <script>` (Windows) o `./perf/run.sh` (Unix). Ver [`perf/README.md`](../perf/README.md) para la matriz,
umbrales y variables de tunning.

| Escenario / script                                                                      | Qué valida                                                                                                                                             |
|-----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `scenarios/smoke.test.js`                                                               | Humo end-to-end del ciclo: catálogo → disponibilidad → reserva → consultas → cancelación                                                               |
| `scenarios/load.test.js`                                                                | Carga sostenida (browse/booking/backoffice) sobre la app real                                                                                          |
| `scenarios/stress.test.js`, `spike.test.js`                                             | Búsqueda del límite bajo degradación controlada                                                                                                        |
| `scenarios/soak.test.js`                                                                | Estabilidad en períodos largos (memoria/conexiones)                                                                                                    |
| `scenarios/breakpoint.test.js`                                                          | Identificación del punto de quiebre                                                                                                                    |
| `scenarios/concurrent.test.js`                                                          | **Reserva concurrente sobre el mismo slot** (recomendación §3): racha de 50 VUs + disperso, mide conflictos 409, errores y latencia p95 de adquisición |
| `endpoints/*.test.js` (health, auth, catalog, availability, booking, provider, reports) | Validación funcional por endpoint con umbrales estrictos y negaciones intencionales etiquetadas                                                        |

**Resultados de referencia** (validados contra la app local, 2026-09-14):

| Métrica                         | Valor medido             |
|---------------------------------|--------------------------|
| Smoke: checks                   | 100% (32/32), p95 27ms   |
| Concurrent: adquisiciones (201) | 305                      |
| Concurrent: conflictos (409)    | 46 (esperados)           |
| Concurrent: errores no 201/409  | 0                        |
| Concurrent: latencia adqu. p95  | 42ms                     |
| Load: p95                       | 52ms, http_req_failed 0% |
| Soak (5m): p95 / p99            | 145ms / 203ms, fallas 0% |

Datos objetivos del runtime (para SLA):

| Aspecto                  | Valor / hecho medible                                                                                             |
|--------------------------|-------------------------------------------------------------------------------------------------------------------|
| Stack de runtime         | Spring Boot 4.1.1, Tomcat 11.0.25, Java 21 (JRE alpine en imagen)                                                 |
| Startup + migraciones    | Flyway corre al arranque (2 migraciones); seeder inserta 50 filas x tabla                                         |
| Complejidad de consultas | `findAll` en ReportService procesa en memoria el total de reservas (mejorable a SQL agregado si el volumen crece) |
| Servicios de datos       | PostgreSQL 17, Redis 7 (TTL sesion 8h, OTP 10min)                                                                 |
| Metricas                 | `/actuator/metrics`, `/actuator/prometheus` (Micrometer) + VictoriaMetrics/Grafana en compose                     |
| Limites conocidos        | `providerAgenda` (filtros en BD), `byDocument` (2 queries por cliente), `search` (filtros simples sin paginacion) |

> La validación de reserva concurrente sobre el mismo slot (choke point del dominio) está cubierta por
> `scenarios/concurrent.test.js`; el diseño soporta correctamente colisiones (409 vs 201) sin errores de servidor.

## 4. Calidad y seguridad (hoja de datos)

| Fase          | Herramienta                    | Umbral                                                    |
|---------------|--------------------------------|-----------------------------------------------------------|
| Cobertura     | JaCoCo                         | 1.00 instrucciones y 1.00 ramas (gate en `verify`)        |
| SCA           | Trivy `sbom` sobre CycloneDX   | build bloquea si HIGH/CRITICAL                            |
| Imagen        | Trivy                          | HIGH/CRITICAL bloquean                                    |
| SAST estatico | SpotBugs + FindSecurityBugs    | reporte por build (no bloquea; effort Max, threshold Low) |
| SBOM          | CycloneDX (`makeAggregateBom`) | genera `META-INF/sbom/application.cdx.json` en cada build |

## 5. Decisiones de diseño (ADRs ligeros)

| Decision                                      | Alternativa descartada            | Razon                                                                     |
|-----------------------------------------------|-----------------------------------|---------------------------------------------------------------------------|
| Sesiones opacas en Redis (UUID) en vez de JWT | JWT stateless                     | Revocacion inmediata (logout) y control de sesiones sin secretos de firma |
| `Result<T>` en vez de excepciones de negocio  | Excepciones + `@ControllerAdvice` | Flujo explicito, cero sorpresas, errores de dominio centralizados         |
| Cliente sin cuenta                            | Cuentas de cliente                | Reduccion de friccion de onboarding; identidad por documento              |
| Un solo bounded context                       | Microservicios                    | Escala pequeña; la frontera de la reserva es cohesiva                     |
| Cifrado BCrypt para password                  | Argon2                            | Default estandar de Spring Security; suficiente para el alcance           |
| Token TTL 8h                                  | TTL corto/largo                   | Balance comodidad/seguridad para sesion de proveedor                      |