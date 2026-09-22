import { Counter, Trend } from 'k6/metrics';
import { check } from 'k6';
import * as flows from '../lib/flows.js';
import { clientData } from '../lib/data.js';
import { TUNE, think } from '../lib/settings.js';

const successCount = new Counter('slot_acquired');
const conflictCount = new Counter('slot_conflict');
const errorCount = new Counter('slot_error');
const acquireMs = new Trend('slot_acquire_ms');
const conflictMs = new Trend('slot_conflict_ms');

export const options = {
  setupTimeout: '5m',
  scenarios: {
    // Racha simultanea sobre EL MISMO slot: mide la validacion de choque
    // y la latencia p95 bajo colision real (ver DESIGN.md seccion 3).
    burst: {
      executor: 'shared-iterations',
      exec: 'burst',
      vus: TUNE.concurrentVUs,
      iterations: TUNE.concurrentVUs,
      maxDuration: '2m',
    },
    // Disperso: reservas sobre muchos targets distintos para medir throughput
    // sin colision dominante.
    spread: {
      executor: 'constant-arrival-rate',
      exec: 'spread',
      rate: 10,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: TUNE.concurrentVUs,
      maxVUs: TUNE.concurrentVUs * 2,
      startTime: '2m30s',
    },
  },
  thresholds: {
    // Aceptan 409 (conflictos de slot esperados por diseno). Los errores reales
    // (status != 201/409) se controlan con slot_error.
    'http_req_failed{scenario:/(burst|spread)/}': ['rate<0.99'],
    'http_req_duration{name:reservation_create}': ['p(95)<2500'],
    slot_acquired: ['count>=1'],
    slot_error: ['count==0'],
    slot_acquire_ms: ['p(95)<2000'],
  },
};

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  const target = flows.findTarget(services);
  const targets = flows.prepareTargets(services, 20);
  if (!target) {
    throw new Error('No hay franjas libres para la prueba de concurrencia. Resetea el seed.');
  }
  return { target, targets };
}

function attempt(target) {
  const started = Date.now();
  const res = flows.createReservation(target, clientData());
  const elapsed = Date.now() - started;

  if (res.status === 201) {
    successCount.add(1);
    acquireMs.add(elapsed);
    flows.cancelReservation(res.json().id);
  } else if (res.status === 409) {
    conflictCount.add(1);
    conflictMs.add(elapsed);
  } else {
    errorCount.add(1);
  }

  check(res, {
    'aceptada (201) o conflicto esperado (409)': (r) => r.status === 201 || r.status === 409,
  });
  think(100);
}

export function burst(data) {
  attempt(data.target);
}

export function spread(data) {
  const target = data.targets.length ? data.targets[__ITER % data.targets.length] : null;
  if (target) attempt(target);
}