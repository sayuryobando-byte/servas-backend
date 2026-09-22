# Pruebas de performance con k6 (Docker)

Suite de pruebas de carga/rendimiento del backend **Servas**, implementada con
[Grafana k6](https://grafana.com/docs/k6/) ejecutado **via Docker** (imagen
`grafana/k6`). Cubre los 7 tipos de prueba estándar más pruebas funcionales por
endpoint, con especial foco en el escenario recomendado en `docs/DESIGN.md`
(sección 3): **reserva concurrente sobre el mismo slot y latencia p95**.

---

## 1. Requisitos

- Docker Desktop / Docker Engine con `docker compose` (clave para levantar la app).
- Backend levantado con datos de seed:

  ```bash
  cd back
  cp .env.example .env
  docker compose up -d --build db redis app
  ```

  La app expone la API en `http://localhost:8080`.
- Ningún binario de k6 instalado: se usa la imagen `grafana/k6` (se descarga
  automáticamente la primera vez).

## 2. Estructura

```
perf/
├── README.md                <- este archivo
├── .env.example             <- variables de entorno para k6
├── run.ps1 / run.sh         <- lanzadores Docker (Windows PowerShell / bash)
├── lib/                     <- codigo compartido
│   ├── settings.js          <- base URL, umbrales de carga, think-time
│   ├── data.js              <- generadores (documentos, emails, fechas...)
│   ├── auth.js              <- login de proveedores demo (verificados)
│   └── flows.js             <- flujos reales: catalogo, disponibilidad,
│                                reservas, backoffice del proveedor
├── scenarios/               <- tipos de prueba
│   ├── smoke.test.js        <- humo / funcionalidad minima
│   ├── load.test.js         <- carga sostenida (3 perfiles simultaneos)
│   ├── stress.test.js       <- escalado hasta la saturacion
│   ├── spike.test.js        <- pico repentino + recuperacion
│   ├── soak.test.js         <- estabilidad prolongada (detecta fugas)
│   ├── breakpoint.test.js   <- punto de quiebre (escalonado)
│   └── concurrent.test.js   <- ATAQUE sobre el MISMO slot (DESIGN 3)
└── endpoints/               <- foco por endpoint
    ├── health.test.js       ├── auth.test.js
    ├── catalog.test.js      ├── availability.test.js
    ├── booking.test.js      ├── provider.test.js
    └── reports.test.js
```

Géneros no se escriben datos en el repo: el `setup()` de cada script descubre
dinámicamente servicios activos y franjas libres via la API (nada mano).

## 3. Como ejecutar

Windows (PowerShell):

```powershell
# Humo
.\perf\run.ps1 scenarios\smoke.test.js

# Carga (con reporte JSON)
.\perf\run.ps1 scenarios\load.test.js -SaveJson

# Con variables extra
.\perf\run.ps1 scenarios\stress.test.js -BaseUrl http://192.168.1.20:8080 -ExtraEnv "STRESS_MAX_VUS=150"

# Endpoint
.\perf\run.ps1 endpoints\booking.test.js
```

Bash / WSL:

```bash
./perf/run.sh scenarios/smoke.test.js
./perf/run.sh scenarios/load.test.js http://localhost:8080 "BROWSER_VUS=10" 1
```

Sin wrappers (docker directo):

```bash
docker run --rm -v "$PWD/perf:/perf" --add-host host.docker.internal:host-gateway \
  -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run /perf/scenarios/load.test.js
```

### Nota sobre `BASE_URL`

- En **Docker Desktop** el contenedor de k6 llega al host con
  `http://host.docker.internal:8080` (valor por defecto).
- Si k6 corre en otra máquina o contra un entorno remoto, usa
  `-BaseUrl http://<host>:8080`.
- En Linux nativo, `--add-host host.docker.internal:host-gateway` (ya incluido
  en los lanzadores) hace que ese hostname resuelva.

## 4. Que cubre cada prueba

### 4.1 Escenarios (tipos de prueba)

| Script       | Tipo             | Que valida                                                                                           | Carga por defecto              | Umbrales clave                    |
|--------------|------------------|------------------------------------------------------------------------------------------------------|--------------------------------|-----------------------------------|
| `smoke`      | Humo             | Funcionalidad minima de catalogo, reserva completa (crear→consultar→cancelar) y reportes             | 2 VUs, 4 iteraciones           | p95<800ms, 0 errores              |
| `load`       | Carga            | Comportamiento con trafico tipico: navegacion (browse) + reservas (booking) + backoffice en paralelo | 15 / 5 / 3 VUs, ~7m            | p95<2s, p99<4.5s, errores<5%      |
| `stress`     | Estres           | Escalado incremental hasta encontrar degradacion                                                     | 0→120 VUs escalonado           | p95<3s, errores<10%               |
| `spike`      | Pico             | Arranque brusco de 150 VUs y verificacion de recuperacion posterior                                  | 0→150 VUs en 10s               | p95<4s, readiness OK post-pico    |
| `soak`       | Resistencia      | Estabilidad prolongada: deteccion de fugas de memoria/conexiones                                     | 12/3/2 VUs ~30m                | p95<1.2s, p99<2.5s, 0 errores     |
| `breakpoint` | Punto de quiebre | Capacidad maxima real por escalones                                                                  | 0→250 VUs                      | informativo (p95<5s, errores<50%) |
| `concurrent` | Concurrencia     | **50 VUs contra el mismo slot** (segun DESIGN.md) + reservas dispersas para throughput               | burst 50 VUs + spread 10 req/s | p95 (create)<2.5s, >=1 adquirida  |

> **`concurrent` se ejecuta SOLO luego de todos los demás** (0x el seed consume
> franjas). Es el escenario estrella de `DESIGN.md` sección 3.

### 4.2 Endpoints (foco funcional + latencia)

| Script         | Endpoints                                                             | Detalle                                                                                              |
|----------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `health`       | `/actuator/*`                                                         | health, readiness, liveness, metrics, prometheus                                                     |
| `auth`         | `/auth/*`                                                             | login valido, credenciales invalidas (401), email sin verificar (403), logout y token revocado (401) |
| `catalog`      | `/comunas`, `/services`, `/services/{id}`                             | busqueda, filtros (modality/comuna), detalle, 404                                                    |
| `availability` | `/services/{id}/availability`                                         | franja libre, fecha fuera de rango (400), barrido de robustez sin 5xx                                |
| `booking`      | `/reservations*`                                                      | ciclo completo: crear → por documento → detalle → modificar → cancelar → confirmar cancelacion       |
| `provider`     | `/companies` (multipart), `/services`, `/schedules`, `/blocked-dates` | alta completa de empresa+servicio+horarios+fecha bloqueada+status                                    |
| `reports`      | `/provider/reservations`, `/reports/*`                                | agenda (con filtros) y los 3 reportes                                                                |

## 5. Variables de ajuste

Definibles por entorno (`-e`, ver `.env.example` y los lanzadores):

| Variable                                              | Default                            | Uso                             |
|-------------------------------------------------------|------------------------------------|---------------------------------|
| `BASE_URL`                                            | `http://host.docker.internal:8080` | URL de la API                   |
| `PASSWORD`                                            | `Demo1234!`                        | Password de proveedores demo    |
| `BROWSER_VUS` / `BOOKING_VUS` / `BACKOFFICE_VUS`      | 15 / 5 / 3                         | VUs de cada perfil en `load`    |
| `CONCURRENT_VUS`                                      | 50                                 | VUs del burst en `concurrent`   |
| `SPIKE_VUS`                                           | 150                                | Pico en `spike`                 |
| `STRESS_MAX_VUS`                                      | 120                                | Tope de `stress`                |
| `BREAKPOINT_MAX_VUS`                                  | 250                                | Tope de `breakpoint`            |
| `SOAK_DURATION`                                       | 30m                                | Duracion sostenida de `soak`    |
| `WARMUP_DURATION`/`HOLD_DURATION`/`RAMPDOWN_DURATION` | 1m/5m/1m                           | Fases de `load`                 |
| `THINK_TIME_MS`                                       | 800                                | Pausa simulada entre peticiones |
| `HTTP_TIMEOUT`                                        | 30s                                | Timeout de peticiones           |

Los **umbrales** están definidos en el bloque `options.thresholds` de cada
script y son puntos de partida razonables para un entorno local: ajústalos a tu
SLA antes de usarlos como gate de CI (por ejemplo, p95<500 ms si la reserva debe
responder más rapido).

## 5.1 Resultados de referencia (validados 2026-09-14)

Corridas contra la app local (`docker compose up -d db redis app`, seed activo), Docker Desktop:

| Prueba               | Checks | p95        | http_req_failed / umbral clave         | Estado |
|----------------------|--------|------------|----------------------------------------|--------|
| `smoke`              | 32/32  | ~27ms      | 0.00% (rate<0.05)                      | OK     |
| `concurrent` (burst) | 351/351| 42ms (adq.)| adquiridas 305, conflictos 46, errores 0 | OK     |
| `load` (7m)          | —      | 52ms       | 0.00% (rate<0.05)                      | OK     |
| `spike` (60 VUs)     | 1741/1741 | 260ms   | 1.98% (rate<0.15)                      | OK     |
| `stress` (60 VUs)    | —      | 1.96s      | 0.31% (rate<0.10)                      | OK     |
| `breakpoint` (100 VUs)| —     | 469ms      | 0.59% (rate<0.50)                      | OK     |
| `soak` (5m)          | —      | 145ms      | p99 203ms, 0.00% (rate<0.01)           | OK     |
| `endpoints/*` (7)    | 100%   | —          | umbrales estrictos OK (negaciones etiquetadas con `name` propio) | OK |

Notas de la validación 2026-09-14:

- **Umbrales y negaciones:** `expectedResponse:false` NO excluye la petición de
  `http_req_failed`; por eso las negaciones intencionales (404/401/403) se etiquetan
  con `tags: { name: ... }` propio y los gates de calidad se definen por
  `{scenario:...}` amplio + `{name:...}` estricto sobre el camino positivo.
- **Estabilidad red:** bajo carga Docker Desktop puede soltar conexiones
  (`dial: i/o timeout`, status 0); `resilientAvailability()` reintenta 1 vez.
- **Bug de app detectado y corregido:** el login de proveedores fallaba porque el
  seed guarda `{noop}Demo1234!` y el encoder era `BCryptPasswordEncoder` puro; se
  cambió a `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.

## 6. Datos: como funciona el `setup()`

Cada script usa `setup()` (se ejecuta una vez) para:

1. `GET /services` y filtrar **activos**.
2. Sondeas `GET /services/{id}/availability` (en paralelo, hasta +14 días) y
   arma una lista de **targets válidos** `{serviceId, fecha, inicio, fin}`.
3. Para backoffice, abre sesiones de proveedores **verificados** del seed (`proveedor{N}@demo.servas` con `(N-1) % 5 != 0`; no verificados: 1, 6, 11, 16...).

Con esto las pruebas son autocontenidas: no dependen de ID hardcodeados ni de
datos manuales. El cliente de cada reserva se genera con documento unico.

## 7. Reseteo de datos entre ejecuciones

Las pruebas de reserva **consumen franjas** (crean reservas). Para volver al
estado limpio del seed:

```bash
docker compose down -v && docker compose up -d --build db redis app
```

> En `load`, `stress`, `soak`, `smoke`, `booking` el flujo cancela la reserva al
> terminar, liberando el slot para la siguiente iteración. En `concurrent` y
> `spike` deliberadamente se fuerza la colisión (esperado) y se crean reservas
> sin cancelar todas: ejecútalas al final y resetea el seed luego.

## 8. Reportes e integración con la observabilidad

- El resumen de k6 (métricas por escenario, checks, umbrales) se imprime en
  consola. Con `-SaveJson` / `SAVE_JSON=1` se guarda un JSON bajo `perf/output/`.
- La app ya expone `/actuator/prometheus` → VictoriaMetrics → Grafana (`docker compose up -d`). Puedes correlacionar las
  métricas de k6 (throughput, errores) con las del backend (JVM, Tomcat, DB, Redis) mientras
  corre cada prueba.
- Alternativa: retransmitir métricas de k6 a VictoriaMetrics con el runner de
  la nube o un agente externo (fuera de alcance de esta carpeta).

## 9. Limitaciones conocidas

- El seed crea **1 horario por servicio** (1 sola franja por día), por lo que la
  capacidad de reserva por target es limitada: por eso el flujo cancela al
  cerrar el ciclo y `concurrent` se ejecuta al final.
- `reportService.findAll` procesa las reservas en memoria (documentado en
  DESIGN.md); `reports` y `backoffice` pueden degradar antes que el resto.
- Los tiempos absolutos dependen del hardware local: los umbrales son de arranque.