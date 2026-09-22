import http from 'k6/http';
import { b64decode } from 'k6/encoding';
import { BASE_URL, bearerHeaders, jsonHeaders } from './settings.js';
import { toISODate, addDays, today } from './data.js';

function asJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Catalogo publico
// ---------------------------------------------------------------------------

export function getComunas(tags = {}) {
  return http.get(`${BASE_URL}/comunas`, {
    headers: bearerHeaders(),
    tags: { name: 'catalog_comunas', ...tags },
  });
}

export function searchServices(filter = {}, tags = {}) {
  const qs = [];
  if (filter.companyId) qs.push(`companyId=${filter.companyId}`);
  if (filter.comunaId) qs.push(`comunaId=${filter.comunaId}`);
  if (filter.modality) qs.push(`modality=${filter.modality}`);
  const q = qs.length ? `?${qs.join('&')}` : '';
  return http.get(`${BASE_URL}/services${q}`, {
    headers: bearerHeaders(),
    tags: { name: 'catalog_search', ...tags },
  });
}

export function serviceDetail(serviceId, tags = {}) {
  return http.get(`${BASE_URL}/services/${serviceId}`, {
    headers: bearerHeaders(),
    tags: { name: 'catalog_detail', ...tags },
  });
}

export function activeServices(res) {
  if (res.status !== 200) return [];
  return (asJson(res) || []).filter((s) => s.isActive === true);
}

// ---------------------------------------------------------------------------
// Disponibilidad
// ---------------------------------------------------------------------------

export function availability(serviceId, date, tags = {}) {
  return http.get(`${BASE_URL}/services/${serviceId}/availability?date=${date}`, {
    headers: bearerHeaders(),
    tags: { name: 'availability', ...tags },
  });
}

// Sondea (en paralelo) los proximos `days` dias para encontrar la primera
// franja libre. Devuelve un target de reserva o null si no hay disponibilidad.
export function probeTarget(service, opts = {}) {
  const days = opts.days || 14;
  const from = opts.from || addDays(today(), 1);
  const reqs = [];
  for (let i = 0; i < days; i++) {
    const date = toISODate(addDays(from, i));
    reqs.push({
      method: 'GET',
      url: `${BASE_URL}/services/${service.id}/availability?date=${date}`,
      // Sondeo de descubrimiento: un 400 (SERVICE_WITHOUT_SCHEDULE) es un
      // resultado esperado y NO debe contar como error en umbrales.
      params: { headers: bearerHeaders(), tags: { name: 'availability_probe' }, expectedResponse: false },
    });
  }
  const resps = http.batch(reqs);
  for (let i = 0; i < resps.length; i++) {
    const r = resps[i];
    if (r.status === 200) {
      const slots = asJson(r);
      if (Array.isArray(slots) && slots.length > 0) {
        return {
          serviceId: service.id,
          companyId: service.companyId,
          serviceName: service.name,
          durationMinutes: service.durationMinutes,
          date: toISODate(addDays(from, i)),
          startTime: slots[0].startTime,
          endTime: slots[0].endTime,
        };
      }
    }
  }
  return null;
}

export function prepareTargets(services, targetCount, opts = {}) {
  const targets = [];
  for (const service of services) {
    if (targets.length >= targetCount) break;
    const t = probeTarget(service, opts);
    if (t) targets.push(t);
  }
  return targets;
}

export function findTarget(services) {
  const t = prepareTargets(services, 1, { days: 14 });
  return t.length ? t[0] : null;
}

// ---------------------------------------------------------------------------
// Reservas (publico)
// ---------------------------------------------------------------------------

export function createReservation(target, client, tags = {}) {
  return http.post(
    `${BASE_URL}/reservations`,
    JSON.stringify({
      service_id: target.serviceId,
      reservation_date: target.date,
      start_time: target.startTime,
      end_time: target.endTime,
      client,
    }),
    { headers: jsonHeaders(), tags: { name: 'reservation_create', ...tags } }
  );
}

export function reservationsByDocument(documentNumber, tags = {}) {
  return http.get(`${BASE_URL}/reservations/client/${documentNumber}`, {
    headers: bearerHeaders(),
    tags: { name: 'reservation_by_document', ...tags },
  });
}

export function reservationDetail(id, tags = {}) {
  return http.get(`${BASE_URL}/reservations/${id}`, {
    headers: bearerHeaders(),
    tags: { name: 'reservation_detail', ...tags },
  });
}

export function modifyReservation(id, target, tags = {}) {
  return http.put(
    `${BASE_URL}/reservations/${id}`,
    JSON.stringify({
      reservation_date: target.date,
      start_time: target.startTime,
      end_time: target.endTime,
    }),
    { headers: jsonHeaders(), tags: { name: 'reservation_modify', ...tags } }
  );
}

export function cancelReservation(id, tags = {}) {
  return http.patch(
    `${BASE_URL}/reservations/${id}/cancel`,
    JSON.stringify({ cancelled_by: 'CLIENT', cancellation_reason: 'Prueba de performance' }),
    { headers: jsonHeaders(), tags: { name: 'reservation_cancel', ...tags } }
  );
}

// ---------------------------------------------------------------------------
// Backoffice del proveedor
// ---------------------------------------------------------------------------

export function providerAgenda(token, status = '', date = '', tags = {}) {
  const qs = [];
  if (status) qs.push(`status=${status}`);
  if (date) qs.push(`date=${date}`);
  const q = qs.length ? `?${qs.join('&')}` : '';
  return http.get(`${BASE_URL}/provider/reservations${q}`, {
    headers: bearerHeaders(token),
    tags: { name: 'provider_agenda', ...tags },
  });
}

export function report(token, kind, tags = {}) {
  return http.get(`${BASE_URL}/reports/${kind}`, {
    headers: bearerHeaders(token),
    tags: { name: `report_${kind}`, ...tags },
  });
}

const PNG_1PX =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
const LOGO = new Uint8Array(b64decode(PNG_1PX));

export function createCompany(token, label) {
  const body = {
    nit: `901${String(Date.now()).slice(-7)}${String(Math.floor(Math.random() * 100)).padStart(2, '0')}`,
    name: `Perf ${label} ${String(Date.now()).slice(-5)}`,
    description: 'Empresa creada por prueba de performance',
    address: 'Av. Perfimismo 1234',
    social_media: `@perf_${label}`,
    logo: http.file(LOGO, 'logo.png', 'image/png'),
  };
  return http.post(`${BASE_URL}/companies`, body, {
    headers: bearerHeaders(token),
    tags: { name: 'company_create' },
  });
}

export function createService(token, companyId, label) {
  const body = JSON.stringify({
    company_id: companyId,
    comuna_id: 1,
    name: `Perf Service ${label} ${String(Date.now()).slice(-5)}`,
    modality: 'PRESENCIAL',
    cost: 25000,
    duration_minutes: 30,
    description: 'Perf',
    recommendations: 'Perf',
    start_date: toISODate(addDays(today(), -1)),
    end_date: toISODate(addDays(today(), 60)),
  });
  return http.post(`${BASE_URL}/services`, body, {
    headers: jsonHeaders(token),
    tags: { name: 'service_create' },
  });
}

export function configureSchedules(token, serviceId) {
  const body = JSON.stringify([{ day_of_week: 1, start_time: '09:00', end_time: '12:00' }]);
  return http.post(`${BASE_URL}/services/${serviceId}/schedules`, body, {
    headers: jsonHeaders(token),
    tags: { name: 'schedule_configure' },
  });
}

export function replaceSchedules(token, serviceId) {
  const body = JSON.stringify([
    { day_of_week: 1, start_time: '09:00', end_time: '12:00' },
    { day_of_week: 3, start_time: '14:00', end_time: '18:00' },
  ]);
  return http.put(`${BASE_URL}/services/${serviceId}/schedules`, body, {
    headers: jsonHeaders(token),
    tags: { name: 'schedule_replace' },
  });
}

export function blockDate(token, companyId, serviceId, date) {
  const body = JSON.stringify({
    company_id: companyId,
    service_id: serviceId,
    block_date: date,
    reason: 'Perf block',
  });
  return http.post(`${BASE_URL}/blocked-dates`, body, {
    headers: jsonHeaders(token),
    tags: { name: 'blocked_date' },
  });
}

export function changeServiceStatus(token, serviceId, isActive) {
  return http.patch(
    `${BASE_URL}/services/${serviceId}/status`,
    JSON.stringify({ is_active: isActive }),
    { headers: jsonHeaders(token), tags: { name: 'service_status' } }
  );
}

export function logout(token) {
  return http.post(`${BASE_URL}/auth/logout`, '{}', {
    headers: jsonHeaders(token),
    tags: { name: 'auth_logout' },
  });
}