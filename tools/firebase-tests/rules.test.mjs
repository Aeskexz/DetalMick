import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Local-only endpoints, separate namespace, and synthetic users. No production writes.
const db = 'http://127.0.0.1:9000';
const ns = 'demo-detalmick-default-rtdb';
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
let alice, bob, staff, admin, reception;
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
  [alice, bob, staff, admin, reception] = await Promise.all(['alice', 'bob', 'staff','admin','reception'].map(account));
  await allowed(await request('', 'PATCH', {
    [`roles/${staff.localId}/personal`]: true,
    [`roles/${admin.localId}`]: {admin:true,activo:true},
    [`roles/${reception.localId}`]: {recepcionista:true,activo:true},
    [`clinica/dentistas/${staff.localId}`]: {nombre:'Dentista de prueba'},
    [`citas/${alice.localId}/fixture`]: {id:'fixture',uid:alice.localId,dentistaId:staff.localId,estado:'confirmada',fecha:'2026-12-01'},
    [`usuarios/${alice.localId}`]: { nombre: 'Ana Paciente', telefono: '', direccion: '' },
    [`usuarios/${bob.localId}`]: { nombre: 'Otro Paciente' },
    [`pagos/${alice.localId}/p1`]: { concepto: 'Limpieza', totalCentavos: 5000, abonadoCentavos: 0,
      fechaLimite: '2026-12-15', estado: 'pendiente', creadoEn: Date.now(), actualizadoEn: Date.now(), registradoPor: staff.localId },
    [`recordatorios/${alice.localId}/r1`]: { mensaje: 'Tu cita', leido: false },
    'servicios/limpieza': { nombre: 'Limpieza dental' },
    'inventario': null,
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
  await allowed(await request(`pagos/${alice.localId}/p1`, 'PATCH', { abonadoCentavos: 5000, estado: 'pagado',
    actualizadoEn: { '.sv': 'timestamp' }, 'abonos/a1': { montoCentavos: 5000, creadoEn: { '.sv': 'timestamp' }, registradoPor: staff.localId } }, staff.idToken));
  await denied(await request(`citas/${alice.localId}/c1`, 'PUT', { estado: 'confirmada' }, staff.idToken));
});

test('dentist profiles are separate and patients cannot impersonate dentists', async () => {
  await allowed(await request(`dentistas/${staff.localId}`, 'PUT', { nombre: 'Dentista de prueba' }, staff.idToken));
  await denied(await request(`dentistas/${alice.localId}`, 'PUT', { nombre: 'Falso dentista' }, alice.idToken));
  await denied(await request(`dentistas/${staff.localId}`, 'GET', undefined, alice.idToken));
  await denied(await request(`usuarios/${staff.localId}`, 'PUT', { nombre: 'Perfil equivocado' }, staff.idToken));
});

test('treatments validate state, date, patient and audit fields', async () => {
  const value = { titulo: 'Limpieza dental', descripcion: 'Retirar la placa de los dientes.', estado: 'programado',
    fecha: '2026-12-01', creadoEn: { '.sv': 'timestamp' }, actualizadoEn: { '.sv': 'timestamp' }, registradoPor: staff.localId };
  const path = `tratamientos/${alice.localId}/t1`;
  await allowed(await request(path, 'PUT', value, staff.idToken));
  await allowed(await request(path, 'GET', undefined, alice.idToken));
  await denied(await request(path, 'GET', undefined, bob.idToken));
  await allowed(await request(path, 'PATCH', { estado: 'realizado', actualizadoEn: { '.sv': 'timestamp' } }, staff.idToken));
  await denied(await request(path, 'PATCH', { estado: 'falso' }, staff.idToken));
  await denied(await request(path, 'PATCH', { fecha: 'sin fecha' }, staff.idToken));
  await denied(await request(path, 'PATCH', { registradoPor: alice.localId }, staff.idToken));
  await denied(await request(path, 'DELETE', undefined, staff.idToken));
  await denied(await request('tratamientos/inexistente/t1', 'PUT', value, staff.idToken));
});

test('charges and partial payments reject invalid amounts, overpayment and contradictory states', async () => {
  const path = `pagos/${alice.localId}/charge`;
  const charge = { concepto: 'Tratamiento de prueba', citaId:'fixture', totalCentavos: 10000, abonadoCentavos: 0, estado: 'pendiente',
    fechaLimite: '2026-12-20', creadoEn: { '.sv': 'timestamp' }, actualizadoEn: { '.sv': 'timestamp' }, registradoPor: staff.localId };
  await allowed(await request(path, 'PUT', charge, staff.idToken));
  await denied(await request(path, 'PATCH', { totalCentavos: 20000 }, staff.idToken));
  await denied(await request(path, 'PATCH', { abonadoCentavos: -1 }, staff.idToken));
  await denied(await request(path, 'PATCH', { abonadoCentavos: 10001 }, staff.idToken));
  await denied(await request(path, 'PATCH', { estado: 'pagado' }, staff.idToken));
  const receipt = { montoCentavos: 2500, creadoEn: { '.sv': 'timestamp' }, registradoPor: staff.localId };
  await allowed(await request(path, 'PATCH', { abonadoCentavos: 2500, 'abonos/partial': receipt, actualizadoEn: { '.sv': 'timestamp' } }, staff.idToken));
  await denied(await request(path, 'PATCH', { abonadoCentavos: 0 }, staff.idToken));
  await denied(await request(`${path}/abonos/partial/montoCentavos`, 'PUT', 1, staff.idToken));
  await denied(await request(`pagos/${alice.localId}`, 'GET', undefined, bob.idToken));
  await denied(await request(path, 'DELETE', undefined, staff.idToken));
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

function appointment(actor, id, hour=9) {
  // Future weekday; test fixtures do not depend on a wall-clock delivery deadline.
  const date = new Date(Date.now()+30*86400000);
  while(date.getUTCDay()===0) date.setUTCDate(date.getUTCDate()+1);
  const day=date.toISOString().slice(0,10);
  return {id,uid:actor.localId,pacienteNombre:'Paciente de prueba',dentistaId:staff.localId,dentistaNombre:'Dentista de prueba',
    servicioId:'limpieza',servicioNombre:'Limpieza dental',fecha:day,horario:`${String(hour).padStart(2,'0')}:00 - ${hour+1}:00`,
    slotKey:`${String(hour).padStart(2,'0')}00`,anio:date.getUTCFullYear(),mes:date.getUTCMonth()+1,dia:date.getUTCDate(),inicioEn:Date.parse(`${day}T${String(hour).padStart(2,'0')}:00:00-06:00`),
    estado:'confirmada',asistenciaConfirmada:false};
}
const slot = c => `agenda/${c.dentistaId}/${c.fecha}/${c.slotKey}`;
const path = c => `citas/${c.uid}/${c.id}`;
const booking = c => ({[path(c)]:c,[slot(c)]:{uid:c.uid,id:c.id}});

test('atomic booking rejects orphans and permits exactly one concurrent reservation', async () => {
  const a=appointment(alice,'race-a'); const b=appointment(bob,'race-b');
  await denied(await request(path(a),'PUT',a,alice.idToken));
  await denied(await request(slot(a),'PUT',{uid:a.uid,id:a.id},alice.idToken));
  const responses=await Promise.all([request('','PATCH',booking(a),alice.idToken),request('','PATCH',booking(b),bob.idToken)]);
  assert.deepEqual(responses.map(r=>r.status).sort(),[200,401]);
  const winner=responses[0].status===200 ? a : b;
  const loser=responses[0].status===200 ? b : a;
  const token=winner.uid===alice.localId ? alice.idToken : bob.idToken;
  const losingToken=loser.uid===alice.localId ? alice.idToken : bob.idToken;
  assert.equal(await (await request(path(loser),'GET',undefined,losingToken)).json(),null);
  await denied(await request(`${path(winner)}/estado`,'PUT','cancelada',token));
  await denied(await request(slot(winner),'DELETE',undefined,token));
  await allowed(await request(`${path(winner)}/asistenciaConfirmada`,'PUT',true,token));
  const moved={...winner,horario:'10:00 - 11:00',slotKey:'1000',inicioEn:winner.inicioEn+3600000,estado:'reprogramada',asistenciaConfirmada:false};
  await denied(await request(path(moved),'PUT',moved,token));
  await allowed(await request('','PATCH',{...booking(moved),[slot(winner)]:null},token));
  await allowed(await request('','PATCH',{[`${path(moved)}/estado`]:'cancelada',[slot(moved)]:null},token));
  await denied(await request('','PATCH',booking(moved),token));
});

test('appointment schema rejects spoofed patients, missing services and invalid slots', async () => {
  const good=appointment(alice,'validation',11);
  for(const patch of [{uid:bob.localId},{servicioId:'missing'},{dentistaId:'missing'},{horario:'25:00 - 26:00'},{slotKey:'1200'},{fecha:'2000-01-01'},{mes:2,dia:30},{inicioEn:0},{estado:'cancelada'}]) {
    const bad={...good,...patch};
    await denied(await request('','PATCH',booking(bad),alice.idToken));
  }
  await allowed(await request('','PATCH',booking(good),alice.idToken));
  await denied(await request(`${path(good)}/servicioNombre`,'PUT','Changed',alice.idToken));
  await denied(await request(path(good),'GET',undefined,bob.idToken));
});

test('reception manages appointments and billing without clinical privileges', async () => {
  const c=appointment(bob,'reception',12);
  await allowed(await request('','PATCH',booking(c),reception.idToken));
  await allowed(await request('usuarios','GET',undefined,reception.idToken));
  await denied(await request(`tratamientos/${alice.localId}`,'GET',undefined,reception.idToken));
  await denied(await request(`odontogramas/${alice.localId}`,'GET',undefined,reception.idToken));
  const charge={concepto:'Consulta',totalCentavos:1000,abonadoCentavos:0,estado:'pendiente',citaId:c.id,fechaLimite:c.fecha,
    creadoEn:{'.sv':'timestamp'},actualizadoEn:{'.sv':'timestamp'},registradoPor:reception.localId};
  await allowed(await request(`pagos/${bob.localId}/invoice`,'PUT',charge,reception.idToken));
  await denied(await request(`pagos/${bob.localId}/invalid`,'PUT',{...charge,citaId:'nonexistent'},reception.idToken));
  await denied(await request(`roles/${alice.localId}`,'PUT',{admin:true,activo:true},reception.idToken));
});

test('odontogram validates author, revision and tooth state; patient has read-only access', async () => {
  const chart={dientes:{11:'sano',12:'caries'},observaciones:'Control',revision:1,actualizadoEn:{'.sv':'timestamp'},registradoPor:staff.localId};
  const location=`odontogramas/${alice.localId}`;
  await allowed(await request(location,'PUT',chart,staff.idToken));
  await allowed(await request(location,'GET',undefined,alice.idToken));
  await denied(await request(location,'GET',undefined,bob.idToken));
  await denied(await request(location,'PUT',{...chart,revision:2},alice.idToken));
  await denied(await request(location,'PUT',{...chart,revision:2,dientes:{99:'sano'}},staff.idToken));
  await denied(await request(location,'PUT',{...chart,revision:2,dientes:{11:'inventado'}},staff.idToken));
  await denied(await request(location,'PUT',chart,staff.idToken));
  await allowed(await request(location,'PUT',{...chart,revision:2},staff.idToken));
});

test('administrator configures clinic and inventory, cannot remove own privileges', async () => {
  await allowed(await request('clinica/telefono','PUT','50371234567',admin.idToken));
  await denied(await request(`roles/${admin.localId}/activo`,'PUT',false,admin.idToken));
  await denied(await request(`roles/${admin.localId}`,'DELETE',undefined,admin.idToken));
  const item={nombre:'Guantes',stock:2,minimo:5,revision:1,actualizadoEn:{'.sv':'timestamp'}};
  await allowed(await request('inventario/gloves','PUT',item,admin.idToken));
  await denied(await request('inventario/gloves','PUT',{...item,stock:-1,revision:2},admin.idToken));
  await denied(await request('inventario/gloves','GET',undefined,alice.idToken));
  await denied(await request('inventario/gloves','PUT',{...item,revision:2},staff.idToken));
});

test('application rejection events are append-only and administrative', async () => {
  const event={codigo:'administracion_denegada',creadoEn:{'.sv':'timestamp'}};
  const location=`eventosSeguridad/${alice.localId}/event`;
  await allowed(await request(location,'PUT',event,alice.idToken));
  await denied(await request(location,'PUT',event,alice.idToken));
  await denied(await request('eventosSeguridad','GET',undefined,alice.idToken));
  await allowed(await request('eventosSeguridad','GET',undefined,admin.idToken));
});

test('suspension revokes database access even with a previously issued authentication token', async () => {
  await allowed(await request(`roles/${staff.localId}/activo`,'PUT',false,'owner'));
  await denied(await request('usuarios','GET',undefined,staff.idToken));
  await denied(await request(`pagos/${alice.localId}`,'GET',undefined,staff.idToken));
  await denied(await request('inventario/gloves','PUT',{nombre:'x'},staff.idToken));
  await allowed(await request(`roles/${staff.localId}/activo`,'PUT',true,'owner'));
});
