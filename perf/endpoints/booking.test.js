import { check, group } from 'k6';
import * as flows from '../lib/flows.js';
import { clientData, toISODate, addDays, today } from '../lib/data.js';
import { think } from '../lib/settings.js';

export const options = {
  setupTimeout: '5m',
  scenarios: {
    booking: {
      executor: 'shared-iterations',
      vus: 2,
      iterations: 6,
      maxDuration: '2m',
    },
  },
  thresholds: {
    'http_req_failed{scenario:booking}': ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
    'http_req_duration{name:reservation_create}': ['p(95)<1200'],
  },
};

function collectTargets(service, days = 63) {
  const from = addDays(today(), 1);
  const targets = [];
  for (let i = 0; i < days; i++) {
    const date = toISODate(addDays(from, i));
    const r = flows.availability(service.id, date);
    if (r.status === 200) {
      const slots = r.json();
      if (slots.length) {
        targets.push({
          serviceId: service.id,
          companyId: service.companyId,
          durationMinutes: service.durationMinutes,
          date,
          startTime: slots[0].startTime,
          endTime: slots[0].endTime,
        });
        if (targets.length >= 8) break;
      }
    }
  }
  return targets;
}

export function setup() {
  const services = flows.activeServices(flows.searchServices());
  for (const service of services) {
    const targets = collectTargets(service);
    if (targets.length >= 8) {
      // Cada VU usa su propia fecha para evitar colisiones (409) entre VUs.
      return { targets };
    }
  }
  throw new Error('No se encontraron suficientes franjas libres para el servicio. Resetea el seed.');
}

export default function (data) {
  const target = data.targets[(__VU - 1) % data.targets.length];
  group('crear reserva', () => {
    const r = flows.createReservation(target, clientData());
    check(r, { 'creada 201': (res) => res.status === 201 });
    if (r.status === 201) {
      const body = r.json();

      group('consultas', () => {
        check(flows.reservationsByDocument(body.clientDocumentNumber), {
          'por documento 200': (res) => res.status === 200,
        });
        check(flows.reservationDetail(body.id), {
          'detalle 200': (res) => res.status === 200 && res.json().id === body.id,
        });
      });

      group('modificar a otra franja', () => {
        const targetB = data.targets[(__VU + 1) % data.targets.length];
        const mod = flows.modifyReservation(body.id, targetB);
        check(mod, { 'modificada 200': (res) => res.status === 200 });
        if (mod.status === 200) {
          check(mod.json().reservationDate, {
            'fecha actualizada': (d) => d === targetB.date,
          });
        }
      });

      group('cancelar', () => {
        check(flows.cancelReservation(body.id), {
          'cancelada 200': (res) => res.status === 200 && res.json().status === 'CANCELLED',
        });
        check(flows.reservationDetail(body.id), {
          'detalle confirma cancelacion': (res) => res.status === 200 && res.json().status === 'CANCELLED',
        });
      });
    }
  });

  think(300);
}