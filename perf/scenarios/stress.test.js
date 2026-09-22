import { Counter } from 'k6/metrics';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { clientData, pick } from '../lib/data.js';
import { TUNE, think } from '../lib/settings.js';

const bookingConflicts = new Counter('booking_conflicts');

function stressStages(max) {
  const stages = [];
  const chunk = Math.max(10, Math.round(max / 5));
  for (let cur = chunk; cur < max; cur += chunk) {
    stages.push({ duration: '30s', target: cur });
  }
  stages.push({ duration: '30s', target: max });
  stages.push({ duration: '2m', target: max });
  stages.push({ duration: '30s', target: 0 });
  return stages;
}

export const options = {
  setupTimeout: '5m',
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      exec: 'mix',
      startVUs: 0,
      stages: stressStages(TUNE.stressMaxVUs),
      gracefulStop: '30s',
    },
  },
  thresholds: {
    'http_req_failed{scenario:stress}': ['rate<0.10'],
    http_req_duration: ['p(95)<3000', 'p(99)<6000'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const targets = flows.prepareTargets(services, 20);
  const tokens = auth.openSessions(5);
  return { targets, tokens };
}

export function mix(data) {
  const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
  const found = flows.activeServices(flows.searchServices());

  flows.getComunas();
  if (found.length) {
    flows.serviceDetail(pick(found).id);
  }
  if (data.targets.length && Math.random() < 0.5) {
    const t = data.targets[__ITER % data.targets.length];
    flows.availability(t.serviceId, t.date);
  }

  if (data.targets.length && Math.random() < 0.25) {
    const created = flows.createReservation(data.targets[__ITER % data.targets.length], clientData());
    if (created.status === 201) {
      const body = created.json();
      flows.reservationsByDocument(body.clientDocumentNumber);
      flows.reservationDetail(body.id);
      flows.cancelReservation(body.id);
    } else if (created.status === 409) {
      bookingConflicts.add(1);
    }
  }

  if (token) {
    flows.providerAgenda(token);
    flows.report(token, 'reservations');
    flows.report(token, 'occupancy');
  }

  think(300);
}