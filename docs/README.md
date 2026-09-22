# Documentación del Backend — Servas (Plataforma de Reservas de Servicios)

Indice de la documentación técnica del backend (`back/`).

---

## Documentos principales

| Documento                                                | Contenido                                                                                                                               |
|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| [GETTING-STARTED.md](GETTING-STARTED.md)                 | Como levantar el backend en local (Docker Compose), variables de entorno, secretos, arranque y solucion de problemas. **Empiece aqui.** |
| [ARCHITECTURE.md](ARCHITECTURE.md)                       | Diagrama de contexto, de componentes, de dominio y Entidad-Relacion (Mermaid); capas, patrones de diseno y stack.                       |
| [USE-CASES.md](USE-CASES.md)                             | Actores, 27 casos de uso funcionales, diagramas de casos de uso y de secuencia, reglas de negocio.                                      |
| [API-CATALOG.md](API-CATALOG.md)                         | Catalogo de servicios expuestos: todos los endpoints, payloads de request/response y codigos de error HTTP.                             |
| [api-servas-swagger.yaml](api-servas-swagger.yaml)       | Especificacion **OpenAPI 3.2.0** de todos los endpoints, alineada a las historias de usuario (HU-001 a HU-040)                          |
| [DATABASE.md](DATABASE.md)                               | Modelo de base de datos, script SQL consolidado (PostgreSQL) y diagrama ER.                                                             |
| [DESIGN.md](DESIGN.md)                                   | Diseno estrategico/tactico (ADR's, patrones), hojas de datos de performance y calidad.                                                  |
| [consultas-sql.sql](consultas-sql.sql)                   | Script SQL de utilidad (consultas de verificacion y limpieza).                                                                          |
| [diagrama_base_de_datos.svg](diagrama_base_de_datos.svg) | Diagrama Entidad-Relacion (SVG, versiones PNG y SVG).                                                                                   |

---

## Pruebas funcionales

| Recurso                                                                          | Formato                 | Herramientas                                                                     |
|----------------------------------------------------------------------------------|-------------------------|----------------------------------------------------------------------------------|
| [http/SERVAS.http](http/SERVAS.http)                                             | `.http`                 | IntelliJ IDEA (HTTP Client), VS Code (REST Client), cualquier cliente compatible |
| [postman/Servas.postman_collection.json](postman/Servas.postman_collection.json) | Postman Collection v2.1 | Postman, Insomnia (importar v2.1), Thunder Client                                |

Ambos siguen **el mismo orden numerado** con pasos encadenados por variables:

```text
1. Health → 2. Catalogo (comunas, servicios) → 3. Auth (login proveedor1)
   → 4. Empresa (crear) → 5. Servicios (crear) → 6. Horarios (Lunes 09:00-11:00)
   → 7. Disponibilidad (Lunes 2026-09-21) → 8. Reservas (crear, consultar, modificar, cancelar)
   → 9. Fechas bloqueadas → 10. Agenda proveedor → 11. Reportes → 12. Logout
   + EXTRA: Registro + verificacion OTP (requiere leer OTP de Redis/logs)
```

Datos de referencia: usuario `proveedor1@demo.servas` / `Demo1234!` (ya verificado), 50 comunas, servicios con horarios
10/30/45/60/75/90 min, fechas bloqueadas y reservas en la BD de demo.

---

## Puntos de la propuesta que no aplican

| Punto                      | Estado    | Motivo                                                                                                                                                                  |
|----------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Documentacion **AsyncAPI** | No aplica | No hay broker de mensajeria ni eventos asincronos. Redis se usa como store de sesiones y OTP, no como bus de eventos. Toda la comunicacion es REST sincrona.            |
| **Secretos AWS/cloud**     | No aplica | No hay integraciones con AWS/GCP/Azure en el backend. Los unicos secrets en CI son `NVD_API_KEY` y `SONAR_HOST`/`SONAR_TOKEN` (configurados como secrets del pipeline). |

---

## Estructura del proyecto

```text
back/
├── src/main/java/com/servas/
│   ├── application/   → servicios de caso de uso (auth, booking, catalog, schedule, report)
│   ├── config/        → seguridad (BCrypt, CORS, SecurityFilterChain)
│   ├── domain/
│   │   ├── entity/    → entidades JPA: User, Provider, Company, Service, Schedule,
│   │   │                BlockedDate, Comuna, Client, Reservation
│   │   ├── enumerations/ → Modality, ReservationStatus, CancelledBy, DocumentType
│   │   └── repository/   → Spring Data Repositories
│   ├── seed/          → DataSeeder (datos de demo, SEED_ENABLED=true)
│   ├── web/           → controllers REST + DTOs + Api.resolve()
│   └── common/
│       ├── constant/  → Errors, DbTables, WeekDays
│       └── functional/ → Result<T>, AppError
├── src/main/resources/
│   ├── application.yml         → config base
│   ├── application-docker.yml  → config Docker (profile docker)
│   └── db/migration/           → V1__init_schema.sql, V2__providers_birth_date_nullable.sql
├── src/test/java/              → 84 tests (Testcontainers, JaCoCo gate 100% instrucciones+ramas)
├── Dockerfile                  → multi-stage: Maven 3.9 + JRE 21 Alpine
├── docker-compose.yml          → app + db + redis + victoriametrics + grafana + sonarqube
├── pom.xml                     → Spring Boot 4.1.1, JaCoCo, OWASP-DC, CycloneDX
└── docs/                       → esta documentacion
```
