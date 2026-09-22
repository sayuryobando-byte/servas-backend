import { check, group } from 'k6';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { clientData, pick } from '../lib/data.js';
import { think } from '../lib/settings.js';

export const options = {
  setupTimeout: '5m',
  thresholds: {
    'http_req_failed{scenario:smoke}': ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
  scenarios: {
    smoke: {
      executor: 'shared-iterations',
      vus: 2,
      iterations: 4,
      maxDuration: '3m',
    },
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const targets = flows.prepareTargets(services, 3, { days: 7 });
  const tokens = auth.openSessions(2);
  return { targets, tokens };
}

export default function (data) {
  if (!data.targets.length) {
    throw new Error('No se encontraron franjas disponibles. Verifica SEED_ENABLED=true y que el seed no este consumido.');
  }
  const target = pick(data.targets);

  group('catalogo publico', () => {
    check(flows.getComunas(), { 'comunas 200': (r) => r.status === 200 && Array.isArray(r.json()) });

    const found = flows.activeServices(flows.searchServices());
    check(found, { 'servicios activos encontrados': (list) => list.length > 0 });

    if (found.length) {
      check(flows.serviceDetail(found[0].id), {
        'detalle servicio 200': (r) => r.status === 200 && r.json().id === found[0].id,
      });
    }
    check(flows.availability(target.serviceId, target.date), {
      'availability 200': (r) => r.status === 200,
    });
  });

  group('ciclo de reserva', () => {
    const created = flows.createReservation(target, clientData());
    check(created, { 'reserva creada 201': (r) => r.status === 201 });

    if (created.status === 201) {
      const body = created.json();
      check(flows.reservationsByDocument(body.clientDocumentNumber), {
        'reservas por documento 200': (r) => r.status === 200,
      });
      check(flows.reservationDetail(body.id), {
        'detalle reserva 200': (r) => r.status === 200 && r.json().id === body.id,
      });
      check(flows.cancelReservation(body.id), {
        'reserva cancelada 200': (r) => r.status === 200 && r.json().status === 'CANCELLED',
      });
    }
  });

  group('backoffice proveedor', () => {
    const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
    if (token) {
      check(flows.providerAgenda(token), { 'agenda proveedor 200': (r) => r.status === 200 });
      check(flows.providerAgenda(token, 'CONFIRMED'), { 'agenda filtrada 200': (r) => r.status === 200 });
      check(flows.report(token, 'reservations'), { 'reporte reservas 200': (r) => r.status === 200 });
      check(flows.report(token, 'occupancy'), { 'reporte ocupacion 200': (r) => r.status === 200 });
      check(flows.report(token, 'demand'), { 'reporte demanda 200': (r) => r.status === 200 });
    }
  });

  think();
}