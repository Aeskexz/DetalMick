import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Local-only endpoints, separate namespace, and synthetic users. No production writes.
const db = 'http://127.0.0.1:9000';
const ns = 'demo-detalmick-default-rtdb';
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
let alice, bob, staff;
async function account(label) {
  const response = await fetch(`${auth}/accounts:signUp?key=local-test`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `${label}-${Date.now()}@example.test`, password: 'Prueba123!', returnSecureToken: true })
  });
  assert.equal(response.status, 200, await response.clone().text());
  return response.json();
}
async function request(path, method = 'GET', body, token) {
  const url = `${db}/${path}.json?ns=${ns}${token && token !== 'owner' ? `&auth=${encodeURIComponent(token)}` : ''}`;
  return fetch(url, { method, headers: { 'Content-Type': 'application/json', ...(token === 'owner' ? { Authorization: 'Bearer owner' } : {}) },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
}
const allowed = async response => assert.equal(response.status, 200, await response.text());
const denied = async response => assert.ok([400, 401, 403].includes(response.status), `Unexpected status ${response.status}: ${await response.text()}`);

before(async () => {
  const rules = JSON.parse((await readFile(new URL('../../database.rules.json', import.meta.url), 'utf8')).replace(/^\uFEFF/, ''));
  await allowed(await request('.settings/rules', 'PUT', rules, 'owner'));
  [alice, bob, staff] = await Promise.all(['alice', 'bob', 'staff'].map(account));
  await allowed(await request('', 'PATCH', {
    [`roles/${staff.localId}/personal`]: true,
    [`usuarios/${alice.localId}`]: { nombre: 'Ana Paciente', telefono: '', direccion: '' },
    [`usuarios/${bob.localId}`]: { nombre: 'Otro Paciente' },
    [`pagos/${alice.localId}/p1`]: { monto: 50, estado: 'pendiente' },
    [`recordatorios/${alice.localId}/r1`]: { mensaje: 'Tu cita', leido: false },
    'servicios/limpieza': { nombre: 'Limpieza dental' },
    'clinica/nombre': 'Clínica de prueba'
  }, 'owner'));
});

test('anonymous access is limited to public clinic and services', async () => {
  await allowed(await request('servicios'));
  await allowed(await request('clinica'));
  await denied(await request('usuarios'));
  await denied(await request(`usuarios/${alice.localId}`));
  await denied(await request(`usuarios/${alice.localId}`, 'PUT', { nombre: 'Intruso' }));
});
test('patient can read and update their profile, never another patient', async () => {
  await allowed(await request(`usuarios/${alice.localId}`, 'GET', undefined, alice.idToken));
  await allowed(await request(`usuarios/${alice.localId}`, 'PUT', { nombre: 'Ana Actualizada', telefono: '+503 7123-4567', direccion: 'San Salvador' }, alice.idToken));
  await denied(await request(`usuarios/${bob.localId}`, 'GET', undefined, alice.idToken));
  await denied(await request(`usuarios/${bob.localId}`, 'PUT', { nombre: 'Intruso' }, alice.idToken));
  await denied(await request('usuarios', 'GET', undefined, alice.idToken));
});
test('schema rejects unknown privileges, invalid types, missing names and deletion', async () => {
  for (const value of [{ nombre: 'Ana', personal: true }, { nombre: 123 }, { telefono: '71234567' }, { nombre: '' }, { nombre: 'a'.repeat(121) }, { nombre: 'Ana', direccion: 'a'.repeat(301) }, null]) {
    await denied(await request(`usuarios/${alice.localId}`, 'PUT', value, alice.idToken));
  }
  await denied(await request(`roles/${alice.localId}/personal`, 'PUT', true, alice.idToken));
  await denied(await request(`roles/${bob.localId}/personal`, 'PUT', true, staff.idToken));
});
test('patient cannot change payments, clinical history, confirmed appointments or public catalog', async () => {
  await allowed(await request(`pagos/${alice.localId}`, 'GET', undefined, alice.idToken));
  await denied(await request(`pagos/${alice.localId}/p1`, 'PUT', { monto: 0 }, alice.idToken));
  for (const collection of ['citas', 'tratamientos']) await denied(await request(`${collection}/${alice.localId}/x`, 'PUT', { dato: 'falso' }, alice.idToken));
  await denied(await request('servicios/falso', 'PUT', { nombre: 'Falso' }, alice.idToken));
});
test('authorized staff can manage clinical records and read profiles', async () => {
  await allowed(await request('usuarios', 'GET', undefined, staff.idToken));
  await allowed(await request(`pagos/${alice.localId}/p1`, 'PUT', { monto: 50, estado: 'pagado' }, staff.idToken));
  await allowed(await request(`citas/${alice.localId}/c1`, 'PUT', { estado: 'confirmada' }, staff.idToken));
});
test('patient marks only their existing reminders as read', async () => {
  await allowed(await request(`recordatorios/${alice.localId}/r1/leido`, 'PUT', true, alice.idToken));
  await denied(await request(`recordatorios/${alice.localId}/r1/mensaje`, 'PUT', 'Falso', alice.idToken));
  await denied(await request(`recordatorios/${alice.localId}/nuevo/leido`, 'PUT', true, alice.idToken));
  await denied(await request(`recordatorios/${alice.localId}/r1/leido`, 'PUT', 'sí', alice.idToken));
  await denied(await request(`recordatorios/${alice.localId}`, 'GET', undefined, bob.idToken));
});
test('patient requests require their profile, a real service, future date and server timestamp', async () => {
  const path = `solicitudesCitas/${alice.localId}/s1`;
  const value = { servicioId: 'limpieza', fechaSolicitada: Date.now() + 86400000, creadoEn: { '.sv': 'timestamp' } };
  await allowed(await request(path, 'PUT', value, alice.idToken));
  await denied(await request(path, 'PUT', value, alice.idToken));
  await denied(await request(`solicitudesCitas/${alice.localId}/s2`, 'PUT', { ...value, servicioId: 'inexistente' }, alice.idToken));
  await denied(await request(`solicitudesCitas/${alice.localId}/s3`, 'PUT', { ...value, fechaSolicitada: 0 }, alice.idToken));
  await denied(await request(`solicitudesCitas/${bob.localId}/s4`, 'PUT', value, alice.idToken));
});
