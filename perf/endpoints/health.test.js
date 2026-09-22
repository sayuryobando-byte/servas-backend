import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, think } from '../lib/settings.js';

export const options = {
  scenarios: {
    health: {
      executor: 'constant-vus',
      vus: 3,
      duration: '1m',
    },
  },
  thresholds: {
    'http_req_failed{scenario:health}': ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const PATHS = ['/actuator/health', '/actuator/health/readiness', '/actuator/health/liveness', '/actuator/metrics'];

export default function () {
  for (const path of PATHS) {
    const r = http.get(`${BASE_URL}${path}`, { tags: { name: `health_${path}` } });
    check(r, { [`${path} 200`]: (res) => res.status === 200 });
  }
  const prom = http.get(`${BASE_URL}/actuator/prometheus`, { tags: { name: 'health_prometheus' } });
  check(prom, { '/actuator/prometheus 200': (res) => res.status === 200 });
  think(1000);
}