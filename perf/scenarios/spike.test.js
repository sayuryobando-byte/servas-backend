import { Counter } from 'k6/metrics';
import { check } from 'k6';
import http from 'k6/http';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { clientData, pick } from '../lib/data.js';
import { BASE_URL, TUNE, think } from '../lib/settings.js';

const bookingConflicts = new Counter('booking_conflicts');
const spikeErrors = new Counter('spike_errors');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      exec: 'spike',
      startVUs: 0,
      stages: [
        { duration: '10s', target: TUNE.spikeVUs },
        { duration: '2m', target: TUNE.spikeVUs },
        { duration: '30s', target: 0 },
      ],
      gracefulStop: '30s',
    },
    recovery: {
      executor: 'ramping-vus',
      exec: 'recovery',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 20 },
        { duration: '1m', target: 20 },
        { duration: '10s', target: 0 },
      ],
      startTime: '3m30s',
    },
  },
  thresholds: {
    'http_req_failed{scenario:spike}': ['rate<0.15'],
    http_req_duration: ['p(95)<4000'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const targets = flows.prepareTargets(services, 10);
  const tokens = auth.openSessions(4);
  return { targets, tokens };
}

export function spike(data) {
  const found = flows.activeServices(flows.searchServices());

  flows.getComunas();
  if (found.length) {
    flows.serviceDetail(pick(found).id);
  }
  if (data.targets.length && Math.random() < 0.6) {
    const t = data.targets[__ITER % data.targets.length];
    flows.availability(t.serviceId, t.date);
  }

  if (data.targets.length && Math.random() < 0.35) {
    const created = flows.createReservation(data.targets[__ITER % data.targets.length], clientData());
    if (created.status === 201) {
      flows.cancelReservation(created.json().id);
    } else if (created.status === 409) {
      bookingConflicts.add(1);
    } else {
      spikeErrors.add(1);
    }
  }

  const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
  if (token) flows.providerAgenda(token);

  think(200);
}

export function recovery() {
  const r = http.get(`${BASE_URL}/actuator/health/readiness`, {
    tags: { name: 'spike_recovery_health' },
  });
  check(r, { 'readiness UP tras el pico': (res) => res.status === 200 });
  flows.getComunas();
  think();
}