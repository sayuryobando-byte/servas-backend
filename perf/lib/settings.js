import { sleep } from 'k6';

const num = (name, dflt) => {
  const v = Number(__ENV[name]);
  return Number.isFinite(v) ? v : dflt;
};

export const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/+$/, '');
export const PASSWORD = __ENV.PASSWORD || 'Demo1234!';
export const PROVIDER_TOTAL = num('PROVIDER_COUNT', 50);
export const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || '30s';
export const THINK_TIME_MS = num('THINK_TIME_MS', 800);

export const TUNE = {
  browserVUs: num('BROWSER_VUS', 15),
  bookingVUs: num('BOOKING_VUS', 5),
  backofficeVUs: num('BACKOFFICE_VUS', 3),
  concurrentVUs: num('CONCURRENT_VUS', 50),
  spikeVUs: num('SPIKE_VUS', 150),
  stressMaxVUs: num('STRESS_MAX_VUS', 120),
  breakpointMaxVUs: num('BREAKPOINT_MAX_VUS', 250),
  soakDuration: __ENV.SOAK_DURATION || '30m',
  warmup: __ENV.WARMUP_DURATION || '1m',
  hold: __ENV.HOLD_DURATION || '5m',
  rampdown: __ENV.RAMPDOWN_DURATION || '1m',
};

export function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

export function bearerHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function think(ms = THINK_TIME_MS, jitter = 0.4) {
  const safe = Math.max(0, Math.floor(ms));
  const delta = safe * jitter * (Math.random() - 0.5);
  sleep(Math.max(0, safe + delta) / 1000);
}