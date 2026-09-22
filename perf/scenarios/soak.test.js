import { Counter } from 'k6/metrics';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { clientData, pick } from '../lib/data.js';
import { TUNE, think } from '../lib/settings.js';

const bookingConflicts = new Counter('booking_conflicts');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    soakBrowse: {
      executor: 'ramping-vus',
      exec: 'browse',
      startVUs: 0,
      stages: [
        { duration: '5m', target: 12 },
        { duration: TUNE.soakDuration, target: 12 },
        { duration: '2m', target: 0 },
      ],
    },
    soakBooking: {
      executor: 'ramping-vus',
      exec: 'booking',
      startVUs: 0,
      stages: [
        { duration: '5m', target: 3 },
        { duration: TUNE.soakDuration, target: 3 },
        { duration: '2m', target: 0 },
      ],
    },
    soakBackoffice: {
      executor: 'ramping-vus',
      exec: 'backoffice',
      startVUs: 0,
      stages: [
        { duration: '5m', target: 2 },
        { duration: TUNE.soakDuration, target: 2 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_failed{scenario:/(soakBrowse|soakBooking|soakBackoffice)/}': ['rate<0.01'],
    http_req_duration: ['p(95)<1200', 'p(99)<2500'],
    http_req_waiting: ['p(95)<1000'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const targets = flows.prepareTargets(services, Math.max(TUNE.bookingVUs * 10, 10));
  const tokens = auth.openSessions(3);
  return { targets, tokens };
}

export function browse(data) {
  flows.getComunas();
  const found = flows.activeServices(flows.searchServices());
  if (found.length) {
    flows.serviceDetail(pick(found).id);
  }
  if (data.targets.length && Math.random() < 0.3) {
    const t = data.targets[__ITER % data.targets.length];
    flows.availability(t.serviceId, t.date);
  }
  think(1500);
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
  think(800);
}

export function backoffice(data) {
  const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
  if (!token) return;
  flows.providerAgenda(token);
  flows.providerAgenda(token, 'CONFIRMED');
  flows.report(token, 'reservations');
  flows.report(token, 'occupancy');
  flows.report(token, 'demand');
  think(1500);
}