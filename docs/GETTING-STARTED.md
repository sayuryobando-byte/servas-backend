# Getting Started — Servas Backend

Guía para levantar el backend de **Servas** (Plataforma de Reservas de Servicios) en local usando Docker Compose.

---

## Requisitos previos

| Herramienta                                  | Version minima | Verificacion             |
|----------------------------------------------|----------------|--------------------------|
| Docker Desktop / Docker Engine               | 24+            | `docker --version`       |
| Docker Compose                               | v2 (integrado) | `docker compose version` |
| Java 21 (solo para desarrollo sin Docker)    | 21+            | `java --version`         |
| Maven 3.9+ (solo para desarrollo sin Docker) | 3.9+           | `mvn --version`          |

---

## 1. Arranque rapido con Docker Compose (recomendado)

```bash
cd back/

# 1. Copiar el archivo de variables de entorno
cp .env.example .env

# 2. (Opcional) Ajustar valores en .env si es necesario

# 3. Levantar los servicios
docker compose up -d --build
```

Los contenedores que se levantan:

| Servicio       | Puerto | Descripcion            |
|----------------|--------|------------------------|
| `servas-app`   | `8080` | Backend Spring Boot    |
| `servas-db`    | `5432` | PostgreSQL 17 (Alpine) |
| `servas-redis` | `6379` | Redis 7 (Alpine)       |

**Servicios opcionales** (monitorización y calidad):

| Servicio                 | Puerto | Descripcion                          |
|--------------------------|--------|--------------------------------------|
| `servas-grafana`         | `3000` | Dashboards de observabilidad         |
| `servas-victoriametrics` | `8428` | Base de datos de metricas Prometheus |
| `servas-victorialogs`    | `9428` | Almacenamiento de logs estructurados |
| `servas-sonarqube`       | `9000` | Analisis de calidad de codigo        |

Para levantar solo los servicios core:

```bash
docker compose up -d --build db redis app
```

### Verificar que funcione

```bash
# Health check (debería retornar "UP")
curl http://localhost:8080/actuator/health

# Ver comunas (no requiere autenticación)
curl http://localhost:8080/comunas
```

### Ver logs

```bash
docker compose logs -f app
```

### Detener

```bash
docker compose down          # detiene los contenedores
docker compose down -v       # detiene y elimina los volumes (borra datos)
```

---

## 2. Variables de entorno

Todas las variables tienen valores por defecto en `docker-compose.yml`. El archivo `.env` (cargado automáticamente por
Docker Compose) permite sobreescribirlos.

### Base de datos

| Variable      | Defecto                            | Descripcion                                                                     |
|---------------|------------------------------------|---------------------------------------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://db:5432/servas` | URL JDBC de PostgreSQL. En local con Docker usar el nombre del servicio (`db`). |
| `DB_USER`     | `servas`                           | Usuario de PostgreSQL                                                           |
| `DB_PASSWORD` | `servas`                           | Password de PostgreSQL                                                          |

### Redis

| Variable     | Defecto | Descripcion                                                     |
|--------------|---------|-----------------------------------------------------------------|
| `REDIS_HOST` | `redis` | Host de Redis. En Docker usar el nombre del servicio (`redis`). |
| `REDIS_PORT` | `6379`  | Puerto de Redis                                                 |

### Aplicación

| Variable               | Defecto                                       | Descripcion                                                                         |
|------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------|
| `SEED_ENABLED`         | `true` (docker) / `false` (local)             | Habilita el seeder que carga datos de demo al arrancar. Usar `false` en produccion. |
| `SERVER_PORT`          | `8080`                                        | Puerto del servidor HTTP                                                            |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origenes permitidos por CORS, separados por coma                                    |

### Observabilidad (opcional)

| Variable                 | Defecto | Descripcion                             |
|--------------------------|---------|-----------------------------------------|
| `VM_RETENTION`           | `2w`    | Periodo de retencion de VictoriaMetrics |
| `VL_RETENTION`           | `2w`    | Periodo de retencion de VictoriaLogs    |
| `GRAFANA_ADMIN_USER`     | `admin` | Usuario admin de Grafana                |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Password admin de Grafana               |

### SonarQube (opcional)

| Variable            | Defecto | Descripcion                    |
|---------------------|---------|--------------------------------|
| `SONAR_DB_USER`     | `sonar` | Usuario de la BD de SonarQube  |
| `SONAR_DB_PASSWORD` | `sonar` | Password de la BD de SonarQube |
| `SONAR_PORT`        | `9000`  | Puerto de SonarQube            |

---

## 3. Secretos

Este proyecto **no requiere secretos externos** (AWS, GCP, etc.) para funcionar en local ni en CI/CD.

El pipeline CI es **100% offline**: usa JaCoCo, SpotBugs + FindSecurityBugs (SAST sin dependencias externas), Trivy sobre CycloneDX (SCA sin NVD) y Trivy image scan. No requiere credenciales ni API keys.

---

## 4. Desarrollo local sin Docker (alternativa)

Si se prefiere correr la app directamente en la maquina:

```bash
cd back/

# 1. Levantar solo PostgreSQL y Redis
docker compose up -d db redis

# 2. Correr la app con Spring profile docker (usa los mismos puertos)
mvn spring-boot:run -Dspring-boot.run.profiles=docker

# O sin Docker, usando puertos directos
export DB_URL=jdbc:postgresql://localhost:5432/servas
export REDIS_HOST=localhost
mvn spring-boot:run
```

### Ejecutar tests

Los tests usan Testcontainers (no requiere Docker externo, lo levanta automáticamente):

```bash
mvn verify
```

Esto ejecuta los 84 tests, genera el reporte de cobertura JaCoCo y verifica que se cumpla el gate del 100%.

---

## 5. Datos de demo

Cuando `SEED_ENABLED=true`, al arrancar la app se cargan automáticamente:

- **50 comunas** (barrios reales de Medellín y municipios del Valle de Aburrá: El Poblado, Laureles, Envigado, Sabaneta, etc.)
- **50 usuarios proveedores** con email `proveedor{N}@demo.servas` y password `Demo1234!`
- **50 empresas** (una por proveedor)
- **50 servicios** (corte de cabello, yoga, asesoría nutricional, etc.)
- **50 horarios** distribuidos en los dias de la semana
- **50 fechas bloqueadas**
- **50 clientes** con documentos de prueba
- **50 reservas** (mix de CONFIRMED, PENDING y CANCELLED)

> **Nota:** Los usuarios proveedores con `is_verified=false` (1 de cada 5, empezando en 1: proveedor1, 6, 11, 16...) no
> pueden hacer login hasta verificar su email. Para login de pruebas use proveedor2, 3, 4, 5, 7... (`Demo1234!`).

---

## 5.1 Pruebas de performance (k6)

La suite de performance vive en [`perf/`](../perf/) y corre desde Docker (imagen `grafana/k6`): no requiere k6 instalado.

```powershell
# Smoke rápido (valida el ciclo completo contra la app local)
.\perf\run.ps1 scenarios\smoke.test.js

# Concurrencia sobre el mismo slot (validación de choques del dominio)
.\perf\run.ps1 scenarios\concurrent.test.js

# Batería funcional por endpoint
.\perf\run.ps1 endpoints\health.test.js
.\perf\run.ps1 endpoints\booking.test.js

# Ajustar carga con variables de entorno (opcional)
.\perf\run.ps1 scenarios\load.test.js -ExtraEnv "BROWSER_VUS=10,HOLD_DURATION=3m"

# Guardar resumen JSON en perf\output\
.\perf\run.ps1 scenarios\load.test.js -SaveJson
```

Version Unix: `./perf/run.sh scenarios/smoke.test.js`. Ver [`perf/README.md`](../perf/README.md) para la matriz completa de
escenarios, umbrales y datos de referencia.

---

## 6. Endpoints de salud

```
GET /actuator/health          → Estado general (UP/DOWN)
GET /actuator/health/readiness → Listo para recibir trafico
GET /actuator/info             → Informacion de la app
GET /actuator/metrics          → Metricas Micrometer
GET /actuator/prometheus       → Metricas en formato Prometheus
```

---

## Solución de problemas

### La app no arranca

```
docker compose logs app | tail -50
```

Verificar que PostgreSQL y Redis están healthy antes de que la app intente conectarse (él `depends_on` con
`condition: service_healthy` debería manejar esto).

### Puerto 5432 ya en uso

Cambiar el mapeo en `docker-compose.yml` o `.env`:

```bash
# En .env
DB_PORT=5433
```

### Tests fallan con "Could not find a valid Docker environment"

Verificar que Docker está corriendo y accesible. Los tests usan Testcontainers que necesita Docker.
