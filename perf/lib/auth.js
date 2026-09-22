import http from 'k6/http';
import { BASE_URL, PASSWORD, PROVIDER_TOTAL, jsonHeaders } from './settings.js';

export function verifiedProviderNumbers(count = PROVIDER_TOTAL) {
  const out = [];
  for (let n = 1; n <= count; n++) {
    // Regla del seed: proveedorN verificado si (n-1) % 5 != 0.
    if ((n - 1) % 5 !== 0) out.push(n);
  }
  return out;
}

export function providerEmail(n) {
  return `proveedor${n}@demo.servas`;
}

export function login(email, password = PASSWORD) {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email, password }), {
    headers: jsonHeaders(),
    tags: { name: 'auth_login' },
  });
  if (res.status !== 200) return null;
  const body = res.json();
  return {
    token: body.token,
    userId: body.userId || body.user_id,
    email: body.email,
  };
}

export function loginProvider(n, password = PASSWORD) {
  return login(providerEmail(n), password);
}

export function openSessions(count = 10) {
  const nums = verifiedProviderNumbers();
  const tokens = [];
  for (let i = 0; i < Math.min(count, nums.length); i++) {
    const s = login(providerEmail(nums[i]), PASSWORD);
    if (s) tokens.push(s.token);
  }
  return tokens;
}