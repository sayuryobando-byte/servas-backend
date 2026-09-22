import { check, group } from 'k6';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { think } from '../lib/settings.js';

export const options = {
  setupTimeout: '3m',
  scenarios: {
    reports: {
      executor: 'constant-vus',
      vus: 3,
      duration: '1m',
    },
  },
  thresholds: {
    'http_req_failed{scenario:reports}': ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
    'http_req_duration{scenario:reports}': ['p(95)<2000'],
  },
};

export function setup() {
  const tokens = auth.openSessions(4);
  return { tokens };
}

export default function (data) {
  const token = data.tokens.length ? data.tokens[__VU % data.tokens.length] : null;
  if (!token) return;

  group('agenda', () => {
    check(flows.providerAgenda(token), { 'agenda sin filtros 200': (r) => r.status === 200 });
    check(flows.providerAgenda(token, 'CONFIRMED'), { 'agenda CONFIRMED 200': (r) => r.status === 200 });
    check(flows.providerAgenda(token, '', '2026-01-01'), { 'agenda por fecha 200': (r) => r.status === 200 });
  });

  group('reportes', () => {
    check(flows.report(token, 'reservations'), { 'reporte reservas 200': (r) => r.status === 200 && typeof r.json().total === 'number' });
    check(flows.report(token, 'occupancy'), { 'reporte ocupacion 200': (r) => r.status === 200 && Array.isArray(r.json().services) });
    check(flows.report(token, 'demand'), { 'reporte demanda 200': (r) => r.status === 200 && Array.isArray(r.json()) });
  });

  think();
}