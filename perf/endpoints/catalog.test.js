import { check, group } from 'k6';
import http from 'k6/http';
import * as flows from '../lib/flows.js';
import { pick } from '../lib/data.js';
import { BASE_URL, jsonHeaders, think } from '../lib/settings.js';

export const options = {
  scenarios: {
    catalog: {
      executor: 'constant-vus',
      vus: 5,
      duration: '1m',
    },
  },
  thresholds: {
    // El 404 (id inexistente) es una negacion intencional, no una falla de
    // calidad; por eso el umbral global es amplio y el de detalle positivo es estricto.
    'http_req_failed{scenario:catalog}': ['rate<0.25'],
    'http_req_failed{name:catalog_detail}': ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{name:catalog_search}': ['p(95)<1200'],
  },
};

export default function () {
  group('comunas', () => {
    check(flows.getComunas(), { 'comunas 200': (r) => r.status === 200 && Array.isArray(r.json()) });
  });

  group('busqueda de servicios', () => {
    check(flows.searchServices(), { 'todos los activos 200': (r) => r.status === 200 });
    check(flows.searchServices({ modality: 'VIRTUAL' }), { 'filtro virtual 200': (r) => r.status === 200 });
    check(flows.searchServices({ modality: 'PRESENCIAL' }), { 'filtro presencial 200': (r) => r.status === 200 });
    check(flows.searchServices({ comunaId: 1 }), { 'filtro comuna 200': (r) => r.status === 200 });
  });

  group('detalle', () => {
    const found = flows.activeServices(flows.searchServices());
    if (found.length) {
      const one = pick(found);
      check(flows.serviceDetail(one.id), {
        detalle: (r) => r.status === 200 && r.json().id === one.id,
      });
      // Negacion intencional (404): etiquetada distinto para no contaminar umbrales.
      check(
        http.get(`${BASE_URL}/services/00000000-0000-0000-0000-000000000000`, {
          headers: jsonHeaders(),
          tags: { name: 'catalog_missing' },
        }),
        { 'id inexistente 404': (r) => r.status === 404 }
      );
    }
  });

  think();
}