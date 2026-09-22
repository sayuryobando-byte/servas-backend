let seq = 0;

function pad(n, w = 2) {
  return String(n).padStart(w, '0');
}

export function toISODate(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d, days) {
  const copy = new Date(d.getTime());
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function today() {
  return new Date();
}

export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function uniqueCode(prefix = 'K6', width = 6) {
  seq += 1;
  // k6 aísla el estado por VU: incluir __VU garantiza unicidad global.
  const vu = typeof __VU !== 'undefined' ? __VU : 0;
  const rnd = String(Math.random()).slice(2, 7);
  return `${prefix}${vu}-${pad(seq, width)}-${rnd}`;
}

export function randomPhone() {
  return `3${pad(Math.floor(Math.random() * 1e9), 9)}`;
}

export function clientData() {
  const doc = uniqueCode('P');
  return {
    document_type: 'CC',
    document_number: doc,
    first_name: 'Perf',
    last_name: `Test${seq}`,
    phone: randomPhone(),
    email: `perf.${doc}@servas.test`,
  };
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}