import { check, group } from 'k6';
import * as flows from '../lib/flows.js';
import * as auth from '../lib/auth.js';
import { toISODate, addDays, today } from '../lib/data.js';
import { think } from '../lib/settings.js';

export const options = {
  scenarios: {
    provider: {
      executor: 'shared-iterations',
      vus: 2,
      iterations: 4,
      maxDuration: '3m',
    },
  },
  thresholds: {
    'http_req_failed{scenario:provider}': ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    'http_req_duration{name:company_create}': ['p(95)<2500'],
  },
};

export function setup() {
  return { nums: auth.verifiedProviderNumbers() };
}

export default function (data) {
  const n = data.nums[__ITER % data.nums.length];
  const session = auth.loginProvider(n);
  check(session, { 'login proveedor': (s) => !!s });
  if (!session) return;

  group('alta de empresa (multipart)', () => {
    const c = flows.createCompany(session.token, `U${__ITER}`);
    check(c, { 'empresa creada 201': (r) => r.status === 201 });
    if (c.status !== 201) return;
    const companyId = c.json().id;

    group('alta de servicio', () => {
      const s = flows.createService(session.token, companyId, `S${__ITER}`);
      check(s, { 'servicio creado 201': (r) => r.status === 201 });
      if (s.status !== 201) return;
      const serviceId = s.json().id;

      group('horarios', () => {
        check(flows.configureSchedules(session.token, serviceId), {
          'horario creado 201': (r) => r.status === 201,
        });
        check(flows.replaceSchedules(session.token, serviceId), {
          'horario reemplazado 200': (r) => r.status === 200,
        });
      });

      group('fecha bloqueada', () => {
        check(flows.blockDate(session.token, companyId, serviceId, toISODate(addDays(today(), 45))), {
          'fecha bloqueada 201': (r) => r.status === 201,
        });
      });

      group('activar / desactivar servicio', () => {
        check(flows.changeServiceStatus(session.token, serviceId, false), {
          'desactivado 200': (r) => r.status === 200 && r.json().isActive === false,
        });
        check(flows.changeServiceStatus(session.token, serviceId, true), {
          'reactivado 200': (r) => r.status === 200 && r.json().isActive === true,
        });
      });
    });
  });

  check(flows.logout(session.token), { 'logout 200': (r) => r.status === 200 });
  think(400);
}