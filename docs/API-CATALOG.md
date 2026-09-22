# Catálogo de Servicios Expuestos — API REST

Base URL: `http://localhost:8080`

Los endpoints que requieren autenticación usan el header:

```
Authorization: Bearer <token-de-sesion>
```

Formato de error estándar del API (HTTP 4xx):

```json
{
  "code": "SLOT_NOT_AVAILABLE",
  "message": "La franja ya no esta disponible."
}
```

| HTTP status | Significado                                                                                                                                       |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `401`       | `INVALID_CREDENTIALS`, `AUTH_REQUIRED`, `TOKEN_INVALID`                                                                                           |
| `403`       | `EMAIL_NOT_VERIFIED`, `NOT_COMPANY_OWNER`                                                                                                         |
| `404`       | `USER_NOT_FOUND`, `PROVIDER_NOT_FOUND`, `COMPANY_NOT_FOUND`, `SERVICE_NOT_FOUND`, `COMUNA_NOT_FOUND`, `RESERVATION_NOT_FOUND`, `CLIENT_NOT_FOUND` |
| `409`       | `EMAIL_TAKEN`, `NIT_TAKEN`, `SLOT_NOT_AVAILABLE`                                                                                                  |
| `400`       | Resto de errores de dominio                                                                                                                       |

---

## 1. Autenticación (`/auth`)

### 1.1 Registro de proveedor

`POST /auth/register`

```json
{
  "email": "carla.vega@demo.servas",
  "password": "Demo1234!",
  "document_type": "CC",
  "document_number": "45678901",
  "first_name": "Carla",
  "last_name": "Vega",
  "birth_date": "1990-05-12",
  "phone": "3115550199"
}
```

**Respuesta 201**

```json
{
  "email": "carla.vega@demo.servas"
}
```

**Errores**: `EMAIL_TAKEN` (409).

### 1.2 Verificar email (OTP)

`POST /auth/verify-email`

```json
{
  "email": "carla.vega@demo.servas",
  "otp_code": "123456"
}
```

**Respuesta 200**: `{ "email": "..." }`

**Errores**: `INVALID_OTP`, `OTP_ATTEMPTS_EXCEEDED` (400), `USER_NOT_FOUND` (404).

### 1.3 Reenviar código OTP

`POST /auth/verify-email/resend`

```json
{
  "email": "carla.vega@demo.servas"
}
```

**Respuesta 200**: `{ "email": "..." }`

> Nota: el OTP no se envia por email real (no hay SMTP configurado); se imprime en el log de la app (`DEBUG`). Para
> pruebas funcionales, la colección lee el OTP desde `docker compose logs app`.

### 1.4 Login

`POST /auth/login`

```json
{
  "email": "proveedor1@demo.servas",
  "password": "Demo1234!"
}
```

**Respuesta 200**

```json
{
  "token": "2c8a60b9-...",
  "user_id": "ae8d...",
  "email": "proveedor1@demo.servas"
}
```

**Errores**: `INVALID_CREDENTIALS` (401), `EMAIL_NOT_VERIFIED` (403).

### 1.5 Logout

`POST /auth/logout` — Header `Authorization`.

**Respuesta 200**: `{}`

### 1.6 Recuperar password (emite OTP)

`POST /auth/forgot-password`

```json
{
  "email": "proveedor1@demo.servas"
}
```

**Respuesta 200**: `{ "email": "..." }`. Errores: `USER_NOT_FOUND` (404).

---

## 2. Catálogo público

### 2.1 Listar comunas

`GET /comunas`

**Respuesta 200**: `[ { "id": 1, "name": "El Poblado" }, ... ]`

### 2.2 Buscar servicios

`GET /services?companyId=&comunaId=&modality=`

Filtros opcionales: `companyId` (UUID), `comunaId` (int), `modality` (`VIRTUAL` | `PRESENCIAL`). Solo devuelve servicios
activos.

**Respuesta 200**

```json
[
  {
    "id": "c8b5...",
    "companyId": "ae8d...",
    "companyName": "Estética Aura",
    "comunaId": 1,
    "comunaName": "El Poblado",
    "name": "Corte de cabello 1",
    "modality": "PRESENCIAL",
    "cost": 25000.00,
    "durationMinutes": 30,
    "description": "...",
    "recommendations": "...",
    "startDate": "2026-09-07",
    "endDate": "2026-12-13",
    "isActive": true
  }
]
```

### 2.3 Detalle de servicio

`GET /services/{id}`

**Respuesta 200**: objeto `ServiceView` (misma forma que arriba). **Errores**: `SERVICE_NOT_FOUND` (404).

### 2.4 Disponibilidad de un servicio

`GET /services/{id}/availability?date=2026-09-16`

**Respuesta 200**

```json
[
  {
    "startTime": "09:00",
    "endTime": "09:30"
  },
  {
    "startTime": "09:30",
    "endTime": "10:00"
  }
]
```

**Errores**: `SERVICE_WITHOUT_SCHEDULE`, `DATE_OUT_OF_RANGE`, `DATE_BLOCKED`, `SERVICE_NOT_FOUND` (400/404),
`SERVICE_INACTIVE` (400).

---

## 3. Gestion del proveedor (requieren `Authorization`)

### 3.1 Crear empresa

`POST /companies` — `multipart/form-data` campos: `nit`, `name`, `description` (opc), `address`, `social_media` (opc),
`logo` (archivo, opc).

**Respuesta 201**

```json
{
  "id": "a1b2...",
  "nit": "901234567",
  "name": "Estética Aura",
  "description": "...",
  "address": "Cra 43A # 1-50, El Poblado",
  "socialMedia": "@esteticaaura",
  "logoUrl": null
}
```

**Errores**: `PROVIDER_NOT_FOUND` (404), `NIT_TAKEN` (409), `AUTH_REQUIRED` (401), `TOKEN_INVALID` (401).

### 3.2 Actualizar empresa

`PUT /companies/{id}` — `multipart/form-data` con campos parciales.

**Respuesta 200**: `CompanyView`. **Errores**: `COMPANY_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403).

### 3.3 Crear servicio

`POST /services`

```json
{
  "company_id": "a1b2...",
  "comuna_id": 1,
  "name": "Manicure y pedicure",
  "modality": "PRESENCIAL",
  "cost": 35000,
  "duration_minutes": 60,
  "description": "...",
  "recommendations": "...",
  "start_date": "2026-09-08",
  "end_date": "2026-12-13"
}
```

**Respuesta 201**: `ServiceView`. **Errores**: `COMPANY_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403), `COMUNA_REQUIRED`
(400), `COMUNA_NOT_FOUND` (404).

### 3.4 Actualizar servicio

`PUT /services/{id}` — campos parciales: `comuna_id`, `name`, `modality`, `cost`, `duration_minutes`, `description`,
`recommendations`, `start_date`, `end_date`, `is_active`.

**Respuesta 200**: `ServiceView`. Errores: `SERVICE_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403), `COMUNA_REQUIRED` /
`COMUNA_NOT_FOUND` (400/404).

### 3.5 Activar / desactivar servicio

`PATCH /services/{id}/status`

```json
{
  "is_active": false
}
```

**Respuesta 200**: `ServiceView`. Errores: `SERVICE_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403).

### 3.6 Configurar horarios

`POST /services/{id}/schedules`

```json
[
  {
    "day_of_week": 1,
    "start_time": "09:00",
    "end_time": "12:00"
  },
  {
    "day_of_week": 3,
    "start_time": "14:00",
    "end_time": "18:00"
  }
]
```

Convencion: `day_of_week` 1 = lunes ... 7 = domingo.

**Respuesta 201**

```json
[
  {
    "id": "sched...",
    "dayOfWeek": 1,
    "startTime": "09:00",
    "endTime": "12:00"
  },
  {
    "id": "sched...",
    "dayOfWeek": 3,
    "startTime": "14:00",
    "endTime": "18:00"
  }
]
```

**Errores**: `INVALID_SCHEDULE` (400), `SERVICE_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403).

### 3.7 Reemplazar horarios

`PUT /services/{id}/schedules` — mismo cuerpo que 3.6. Borra los horarios existentes y crea los nuevos.

### 3.8 Bloquear fecha

`POST /blocked-dates`

```json
{
  "company_id": "a1b2...",
  "service_id": "c8b5...",
  "block_date": "2026-09-21",
  "reason": "Mantenimiento"
}
```

`service_id` puede ser `null` para bloquear toda la empresa ese día.

**Respuesta 201**

```json
{
  "id": "bd..",
  "companyId": "a1b2...",
  "serviceId": "c8b5...",
  "blockDate": "2026-09-21",
  "reason": "Mantenimiento"
}
```

**Errores**: `COMPANY_NOT_FOUND` (404), `NOT_COMPANY_OWNER` (403), `AUTH_REQUIRED` (401).

### 3.9 Agenda de reservas

`GET /provider/reservations?status=CONFIRMED&date=2026-09-16`

Filtros opcionales: `status` (`PENDING` | `CONFIRMED` | `CANCELLED` | `COMPLETED`), `date` (ISO `yyyy-MM-dd`).

**Respuesta 200**: `[ReservationView, ...]` (ver formato en sección 4).

---

## 4. Reservas (publico)

### 4.1 Crear reserva

`POST /reservations`

```json
{
  "service_id": "c8b5...",
  "reservation_date": "2026-09-16",
  "start_time": "09:00",
  "end_time": "09:30",
  "client": {
    "document_type": "CC",
    "document_number": "1122334455",
    "first_name": "Sofia",
    "last_name": "Rojas",
    "phone": "3205550123",
    "email": "sofia.rojas@mail.com"
  }
}
```

**Respuesta 201**

```json
{
  "id": "r1...",
  "serviceId": "c8b5...",
  "serviceName": "Manicure y pedicure",
  "clientDocumentType": "CC",
  "clientDocumentNumber": "1122334455",
  "clientName": "Sofia Rojas",
  "reservationDate": "2026-09-16",
  "startTime": "09:00",
  "endTime": "09:30",
  "status": "CONFIRMED",
  "cancelledBy": null,
  "cancellationReason": null
}
```

**Errores**: `END_TIME_MISMATCH`, `SERVICE_INACTIVE`, `DATE_OUT_OF_RANGE`, `SERVICE_WITHOUT_SCHEDULE`,
`OUTSIDE_ATTENTION_HOURS`, `DATE_BLOCKED`, `SLOT_NOT_AVAILABLE` (400), `SERVICE_NOT_FOUND` (404).

### 4.2 Reservas por documento

`GET /reservations/client/{documentNumber}`

**Respuesta 200**: `[ReservationView, ...]`. **Errores**: `CLIENT_NOT_FOUND` (404).

### 4.3 Detalle de reserva

`GET /reservations/{id}`

**Respuesta 200**: `ReservationView`. Errores: `RESERVATION_NOT_FOUND` (404).

### 4.4 Modificar reserva

`PUT /reservations/{id}`

```json
{
  "reservation_date": "2026-09-17",
  "start_time": "10:00",
  "end_time": "10:30"
}
```

**Respuesta 200**: `ReservationView` con fechas actualizadas. **Errores**: `RESERVATION_NOT_ACTIVE`,`END_TIME_MISMATCH`,
`SLOT_NOT_AVAILABLE`, etc. (400), `RESERVATION_NOT_FOUND` (404).

### 4.5 Cancelar reserva

`PATCH /reservations/{id}/cancel`

```json
{
  "cancelled_by": "CLIENT",
  "cancellation_reason": "Cambio de planes"
}
```

`cancelled_by`: `CLIENT` | `PROVIDER`.

**Respuesta 200**: `ReservationView` con `status: "CANCELLED"`. **Errores**: `RESERVATION_NOT_ACTIVE` (400),
`RESERVATION_NOT_FOUND` (404).

---

## 5. Reportes (requieren `Authorization` de proveedor)

### 5.1 Reporte de reservas

`GET /reports/reservations`

**Respuesta 200**

```json
{
  "total": 50,
  "byStatus": {
    "CONFIRMED": 40,
    "PENDING": 5,
    "CANCELLED": 5
  }
}
```

### 5.2 Reporte de ocupación

`GET /reports/occupancy`

**Respuesta 200**

```json
{
  "services": [
    {
      "serviceId": "c8b5...",
      "name": "Corte de cabello 1",
      "booked": 3,
      "capacity": 20,
      "occupancyPercent": 15.0
    }
  ],
  "overallPercent": 5.6
}
```

### 5.3 Reporte de demanda (top 10)

`GET /reports/demand`

**Respuesta 200**

```json
[
  {
    "serviceId": "c8b5...",
    "serviceName": "Corte de cabello 1",
    "reservations": 4
  }
]
```

---

## 6. Actuator (operaciones)

| Endpoint                         | Descripcion                    |
|----------------------------------|--------------------------------|
| `GET /actuator/health`           | Estado de salud                |
| `GET /actuator/health/readiness` | Readiness probe                |
| `GET /actuator/health/liveness`  | Liveness probe                 |
| `GET /actuator/metrics`          | Metricas disponibles           |
| `GET /actuator/prometheus`       | Metricas en formato Prometheus |
