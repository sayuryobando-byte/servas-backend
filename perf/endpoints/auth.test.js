import http from 'k6/http';
import { check, group } from 'k6';
import { BASE_URL, PASSWORD, jsonHeaders, think } from '../lib/settings.js';
import * as flows from '../lib/flows.js';

export const options = {
  scenarios: {
    auth: {
      executor: 'constant-vus',
      vus: 4,
      duration: '1m',
    },
  },
  thresholds: {
    // 4 de cada 6 requests son negaciones intencionales (401 login/revoke, 403
    // no verificado, 401 inexistente): por eso el umbral global es alto y la
    // calidad se mide sobre el camino positivo (login 200) y logout.
    'http_req_failed{scenario:auth}': ['rate<0.80'],
    'http_req_failed{name:auth_login}': ['rate<0.01'],
    'http_req_failed{name:auth_logout}': ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
    'http_req_duration{name:auth_login}': ['p(95)<1000'],
  },
};

export default function () {
  group('login valido', () => {
    const r = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: 'proveedor2@demo.servas', password: PASSWORD }),
      { headers: jsonHeaders(), tags: { name: 'auth_login' } }
    );
    check(r, { 'login 200 con token': (res) => res.status === 200 && !!res.json().token });

    if (r.status === 200) {
      const token = r.json().token;
      check(flows.logout(token), { 'logout 200': (res) => res.status === 200 });
      // El token ya no debe servir (negacion intencional 401)
      const revoked = http.get(`${BASE_URL}/provider/reservations`, {
        headers: jsonHeaders(token),
        tags: { name: 'auth_revoked' },
      });
      check(revoked, { 'token revocado => 401': (res) => res.status === 401 });
    }
  });

  group('login invalido', () => {
    const bad = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: 'proveedor2@demo.servas', password: 'clave-incorrecta' }),
      { headers: jsonHeaders(), tags: { name: 'auth_login_invalid' } }
    );
    check(bad, { 'credenciales invalidas 401': (res) => res.status === 401 });

    const unverified = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: 'proveedor1@demo.servas', password: PASSWORD }),
      { headers: jsonHeaders(), tags: { name: 'auth_login_unverified' } }
    );
    check(unverified, { 'email no verificado 403': (res) => res.status === 403 });

    const inexistente = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: 'no.existe@demo.servas', password: PASSWORD }),
      { headers: jsonHeaders(), tags: { name: 'auth_login_missing' } }
    );
    check(inexistente, { 'usuario inexistente 401': (res) => res.status === 401 });
  });

  think(500);
}