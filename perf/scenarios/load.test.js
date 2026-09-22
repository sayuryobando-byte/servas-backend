import { Counter } from 'k6/metrics';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { clientData, pick } from '../lib/data.js';
import { TUNE, think } from '../lib/settings.js';

const bookingConflicts = new Counter('booking_conflicts');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      exec: 'browse',
      startVUs: 0,
      stages: [
        { duration: TUNE.warmup, target: TUNE.browserVUs },
        { duration: TUNE.hold, target: TUNE.browserVUs },
        { duration: TUNE.rampdown, target: 0 },
      ],
      gracefulStop: '30s',
    },
    booking: {
      executor: 'ramping-vus',
      exec: 'booking',
      startVUs: 0,
      stages: [
        { duration: TUNE.warmup, target: TUNE.bookingVUs },
        { duration: TUNE.hold, target: TUNE.bookingVUs },
        { duration: TUNE.rampdown, target: 0 },
      ],
      gracefulStop: '30s',
    },
    backoffice: {
      executor: 'ramping-vus',
      exec: 'backoffice',
      startVUs: 0,
      stages: [
        { duration: TUNE.warmup, target: TUNE.backofficeVUs },
        { duration: TUNE.hold, target: TUNE.backofficeVUs },
        { duration: TUNE.rampdown, target: 0 },
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    'http_req_failed{scenario:/(browse|booking|backoffice)/}': ['rate<0.05'],
    http_req_duration: ['p(95)<2000', 'p(99)<4500'],
    'http_req_duration{name:reservation_create}': ['p(95)<1500'],
    'http_req_duration{scenario:backoffice}': ['p(95)<2500'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const targets = flows.prepareTargets(services, Math.max(TUNE.bookingVUs * 5, 10));
  const tokens = auth.openSessions(5);
  return { targets, tokens };
}

export function browse(data) {
  flows.getComunas();
  const found = flows.activeServices(flows.searchServices());
  if (found.length) {
    flows.serviceDetail(pick(found).id);
  }
  // Disponibilidad sobre una fecha VALIDA del catalogo (target descubierto en setup)
  if (data.targets.length && Math.random() < 0.4) {
    const t = data.targets[__ITER % data.targets.length];
    flows.availability(t.serviceId, t.date);
  }
  think();
}

export function booking(data) {
  const target = data.targets.length ? data.targets[__ITER % data.targets.length] : null;
  if (!target) return;
  const created = flows.createReservation(target, clientData());
  if (created.status === 201) {
    const body = created.json();
    flows.reservationsByDocument(body.clientDocumentNumber);
    flows.reservationDetail(body.id);
    flows.cancelReservation(body.id);
  } else if (created.status === 409) {
    bookingConflicts.add(1);
  }
  think(200);
}

export function backoffice(data) {
  const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
  if (!token) return;
  flows.providerAgenda(token);
  flows.providerAgenda(token, 'CONFIRMED');
  flows.report(token, 'reservations');
  flows.report(token, 'occupancy');
  flows.report(token, 'demand');
  think();
}