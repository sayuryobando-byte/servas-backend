import { check, group } from 'k6';
import * as flows from '../lib/flows.js';
import { toISODate, addDays, today } from '../lib/data.js';
import { think } from '../lib/settings.js';

// Docker Desktop a veces suelta conexiones en ráfagas (dial: i/o timeout, status 0).
// Para un test funcional basta reintentar una vez ante fallo de red puro.
function resilientAvailability(serviceId, date, retries = 1) {
  let r = flows.availability(serviceId, date);
  while (r.status === 0 && retries-- > 0) {
    r = flows.availability(serviceId, date);
  }
  return r;
}

export const options = {
  setupTimeout: '5m',
  scenarios: {
    availability: {
      executor: 'constant-vus',
      vus: 4,
      duration: '1m',
    },
  },
  thresholds: {
    // El barrido de robustez genera 400/404 intencionales (SERVICE_WITHOUT_SCHEDULE),
    // por eso el umbral de http_req_failed es alto y la calidad la garantiza el
    // umbral de checks (2xx/4xx sin 5xx, franja 200, fecha pasada 400).
    'http_req_failed{scenario:availability}': ['rate<0.95'],
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{name:availability}': ['p(95)<1200'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const target = flows.findTarget(services);
  const past = toISODate(addDays(today(), -10));
  return { target, past };
}

export default function (data) {
  group('fecha con horario', () => {
    if (data.target) {
      check(resilientAvailability(data.target.serviceId, data.target.date), {
        'availability franja 200': (r) => r.status === 200 && Array.isArray(r.json()) && r.json().length > 0,
      });
    }
  });

  group('fecha fuera de rango', () => {
    if (data.target) {
      const r = resilientAvailability(data.target.serviceId, data.past);
      check(r, { 'fecha pasada 400': (res) => res.status === 400 });
    }
  });

  group('barrido de robustez (sin 5xx)', () => {
    if (data.target) {
      const serviceId = data.target.serviceId;
      // Escalonar por VU evita que 4 VUs golpeen las mismas fechas a la vez.
      const offset = (__VU - 1) * 2;
      const from = addDays(today(), -7 + offset);
      for (let i = 0; i < 14; i++) {
        const date = toISODate(addDays(from, i));
        check(resilientAvailability(serviceId, date), {
          [`availability ${date} 2xx/4xx`]: (res) => res.status === 200 || (res.status >= 400 && res.status < 500),
        });
      }
    }
  });

  think();
}