import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc,
  Timestamp, updateDoc, writeBatch,
} from 'firebase/firestore';

const projectId = 'demo-iot-hoteles';
const endpoint = process.env.FIRESTORE_EMULATOR_HOST;
if (!['127.0.0.1:18080', 'localhost:18080'].includes(endpoint)) {
  throw new Error('Estas pruebas requieren exclusivamente el emulador local en 18080. Ejecuta npm test.');
}
const [host, port] = endpoint.split(':');
let entorno;
const fecha = Timestamp.fromMillis(1700000000000);
const ref = (db, path) => doc(db, path);
const dbPara = uid => entorno.authenticatedContext(uid, { email: `${uid}@correo.com` }).firestore();

function perfil(uid, rol, extra = {}) {
  return {
    nombres: uid, apellidos: 'Prueba', email: `${uid}@correo.com`, rol, activo: true,
    hotelId: null, creadoEn: fecha, actualizadoEn: fecha, ...extra,
  };
}

function hotel(extra = {}) {
  return {
    nombre: 'Hotel Lima', ciudad: 'Lima', distrito: 'Miraflores', direccion: 'Av. Prueba 123',
    latitud: -12.12, longitud: -77.03, publicado: false, administradorId: null,
    creadoPor: 'sa', creadoEn: fecha, actualizadoEn: fecha, ultimoEventoId: 'semilla', ...extra,
  };
}

function nuevoHotel(extra = {}) {
  return hotel({ creadoEn: serverTimestamp(), actualizadoEn: serverTimestamp(), ...extra });
}

function auditoria(hotelId, administradorId, accion = 'ASIGNAR_ADMINISTRADOR', extra = {}) {
  return {
    actorUid: 'sa', actorNombre: 'sa Prueba', evento: 'ACCION_ADMINISTRATIVA', accion,
    detalle: 'Operacion administrativa de prueba', hotelId, administradorId,
    timestamp: serverTimestamp(), ...extra,
  };
}

function habitacion(extra = {}) {
  return {
    id: 'r1', hotelId: 'h1', tipo: 'Doble', numero: '101', piso: 1,
    capacidadAdultos: 2, capacidadNinos: 1, areaM2: 24, precioNoche: 150,
    disponible: true, fotos: [], ...extra,
  };
}

async function sembrar(datos) {
  await entorno.withSecurityRulesDisabled(async contexto => {
    const db = contexto.firestore();
    const batch = writeBatch(db);
    for (const [path, valor] of Object.entries(datos)) batch.set(ref(db, path), valor);
    await batch.commit();
  });
}

async function sembrarHotelAdministrado(extra = {}) {
  await sembrar({
    'usuarios/admin1': perfil('admin1', 'ADMIN_HOTEL', { hotelId: 'h1' }),
    'hoteles/h1': hotel({ administradorId: 'admin1', ...extra }),
  });
}

function editarHotelComoAdmin(db, cambios, eventoId) {
  const batch = writeBatch(db);
  batch.update(ref(db, 'hoteles/h1'), {
    ...cambios, actualizadoEn: serverTimestamp(), ultimoEventoId: eventoId,
  });
  batch.set(ref(db, `auditoria/${eventoId}`), auditoria('h1', 'admin1', 'ACTUALIZAR_HOTEL', {
    actorUid: 'admin1', actorNombre: 'admin1 Prueba',
  }));
  return batch.commit();
}

function alta(db, id, cambiosHotel = {}, cambiosAuditoria = {}) {
  const batch = writeBatch(db);
  batch.set(ref(db, `hoteles/${id}`), nuevoHotel({ ultimoEventoId: `alta_${id}`, ...cambiosHotel }));
  batch.set(ref(db, `auditoria/alta_${id}`), auditoria(id, null, 'ALTA_HOTEL', cambiosAuditoria));
  return batch.commit();
}

function asignar(db, hotelId, adminId, anterior = null, eventoId = `${hotelId}_${adminId}`) {
  const batch = writeBatch(db);
  batch.update(ref(db, `hoteles/${hotelId}`), {
    administradorId: adminId, actualizadoEn: serverTimestamp(), ultimoEventoId: eventoId,
  });
  batch.update(ref(db, `usuarios/${adminId}`), {
    hotelId, actualizadoEn: serverTimestamp(),
  });
  if (anterior) batch.update(ref(db, `usuarios/${anterior}`), {
    hotelId: null, actualizadoEn: serverTimestamp(),
  });
  batch.set(ref(db, `auditoria/${eventoId}`), auditoria(hotelId, adminId));
  return batch.commit();
}

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId,
    firestore: {
      host, port: Number(port),
      rules: await readFile(new URL('../../../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await entorno.clearFirestore();
  await sembrar({
    'usuarios/sa': perfil('sa', 'SUPERADMIN'),
    'usuarios/saInactivo': perfil('saInactivo', 'SUPERADMIN', { activo: false }),
    'usuarios/cliente': perfil('cliente', 'CLIENTE'),
    'usuarios/admin1': perfil('admin1', 'ADMIN_HOTEL'),
    'usuarios/admin2': perfil('admin2', 'ADMIN_HOTEL'),
    'usuarios/adminInactivo': perfil('adminInactivo', 'ADMIN_HOTEL', { activo: false }),
    'hoteles/h1': hotel(),
    'hoteles/h2': hotel({ nombre: 'Otro hotel' }),
  });
});

after(async () => { if (entorno) await entorno.cleanup(); });

test('sin autenticar se deniegan perfiles, hoteles y auditoria', async () => {
  const db = entorno.unauthenticatedContext().firestore();
  for (const path of ['usuarios/cliente', 'hoteles/h1', 'auditoria/evento']) {
    await assertFails(getDoc(ref(db, path)));
  }
  await assertFails(setDoc(ref(db, 'hoteles/nuevo'), nuevoHotel()));
});

test('solamente SUPERADMIN activo puede listar usuarios, hoteles y auditoria', async () => {
  for (const nombre of ['usuarios', 'hoteles', 'auditoria']) {
    await assertSucceeds(getDocs(collection(dbPara('sa'), nombre)));
    for (const uid of ['cliente', 'admin1', 'saInactivo']) {
      await assertFails(getDocs(collection(dbPara(uid), nombre)));
    }
  }
  await assertSucceeds(getDoc(ref(dbPara('sa'), 'usuarios/admin1')));
});

test('se conserva lectura propia incluso inactiva, sin permitir perfiles ajenos', async () => {
  await assertSucceeds(getDoc(ref(dbPara('cliente'), 'usuarios/cliente')));
  await assertSucceeds(getDoc(ref(dbPara('adminInactivo'), 'usuarios/adminInactivo')));
  await assertFails(getDoc(ref(dbPara('cliente'), 'usuarios/admin1')));
  await assertFails(getDoc(ref(dbPara('admin1'), 'usuarios/cliente')));
});

test('registro propio crea CLIENTE y rechaza privilegios, correo ajeno o campos extra', async () => {
  const db = dbPara('nuevo');
  const datos = perfil('nuevo', 'CLIENTE', {
    creadoEn: serverTimestamp(), actualizadoEn: serverTimestamp(),
  });
  for (const cambio of [
    { rol: 'SUPERADMIN' }, { rol: 'ADMIN_HOTEL' }, { hotelId: 'h1' },
    { email: 'ajeno@correo.com' }, { privilegio: true }, { creadoEn: fecha },
  ]) await assertFails(setDoc(ref(db, 'usuarios/nuevo'), { ...datos, ...cambio }));
  await assertSucceeds(setDoc(ref(db, 'usuarios/nuevo'), datos));
});

test('edicion personal conserva rol, activo, correo, hotel y creadoEn protegidos', async () => {
  const db = dbPara('cliente');
  await assertSucceeds(updateDoc(ref(db, 'usuarios/cliente'), {
    nombres: 'Nombre cambiado', telefono: '999123456', actualizadoEn: serverTimestamp(),
  }));
  for (const cambio of [
    { rol: 'SUPERADMIN' }, { activo: false }, { email: 'otro@correo.com' },
    { hotelId: 'h1' }, { creadoEn: serverTimestamp() },
  ]) await assertFails(updateDoc(ref(db, 'usuarios/cliente'), {
    ...cambio, actualizadoEn: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref(dbPara('adminInactivo'), 'usuarios/adminInactivo'), {
    nombres: 'Nuevo', actualizadoEn: serverTimestamp(),
  }));
});

test('SUPERADMIN no obtiene permisos para alterar roles o habilitacion', async () => {
  const db = dbPara('sa');
  for (const cambio of [{ rol: 'SUPERADMIN' }, { activo: false }, { nombres: 'Ajeno' }]) {
    await assertFails(updateDoc(ref(db, 'usuarios/admin1'), {
      ...cambio, actualizadoEn: serverTimestamp(),
    }));
  }
  await assertFails(deleteDoc(ref(db, 'usuarios/admin1')));
});

test('SUPERADMIN crea hotel privado sin administrador y auditoria atomica', async () => {
  await assertSucceeds(alta(dbPara('sa'), 'nuevo'));
  const creado = (await getDoc(ref(dbPara('sa'), 'hoteles/nuevo'))).data();
  assert.equal(creado.publicado, false);
  assert.equal(creado.administradorId, null);
  assert.equal(creado.creadoPor, 'sa');
  assert.ok(creado.creadoEn instanceof Timestamp);
  await assertSucceeds(getDoc(ref(dbPara('sa'), 'auditoria/alta_nuevo')));
  await assertSucceeds(alta(dbPara('sa'), 'sin_direccion', { direccion: '' }));
});

test('cliente, administrador e inactivo no crean hoteles', async () => {
  for (const uid of ['cliente', 'admin1', 'saInactivo']) {
    await assertFails(alta(dbPara(uid), `nuevo_${uid}`, { creadoPor: uid }));
  }
});

test('crear o asignar hotel requiere una auditoria nueva enlazada en el mismo commit', async () => {
  const db = dbPara('sa');
  await assertFails(setDoc(ref(db, 'hoteles/sin_auditoria'), nuevoHotel({ ultimoEventoId: 'faltante' })));
  const batch = writeBatch(db);
  batch.update(ref(db, 'hoteles/h1'), {
    administradorId: 'admin1', actualizadoEn: serverTimestamp(), ultimoEventoId: 'faltante',
  });
  batch.update(ref(db, 'usuarios/admin1'), { hotelId: 'h1', actualizadoEn: serverTimestamp() });
  await assertFails(batch.commit());
  await assertFails(alta(db, 'puntero_ajeno', { ultimoEventoId: 'alta_otro_hotel' }));
});

test('coordenadas fuera de rango, no finitas y (0,0) se rechazan', async () => {
  const db = dbPara('sa');
  for (const [i, coords] of [
    { latitud: 91 }, { latitud: -91 }, { longitud: 181 }, { longitud: -181 },
    { latitud: NaN }, { longitud: Infinity }, { latitud: -Infinity },
    { latitud: 0, longitud: 0 }, { latitud: '12' },
  ].entries()) await assertFails(alta(db, `invalido_${i}`, coords));
  await assertSucceeds(alta(db, 'limite', { latitud: -90, longitud: 180 }));
});

test('alta no permite publicar, asignar admin, textos vacios o suplantar creador', async () => {
  const db = dbPara('sa');
  for (const [i, cambio] of [
    { publicado: true }, { administradorId: 'admin1' }, { creadoPor: 'cliente' },
    { nombre: '' }, { ciudad: '   ' }, { distrito: null }, { direccion: null },
    { creadoEn: fecha }, { actualizadoEn: fecha }, { campoExtra: true },
  ].entries()) await assertFails(alta(db, `alta_invalida_${i}`, cambio));
});

test('asignacion atomica establece relacion reciproca y auditoria', async () => {
  const db = dbPara('sa');
  await assertSucceeds(asignar(db, 'h1', 'admin1'));
  assert.equal((await getDoc(ref(db, 'hoteles/h1'))).data().administradorId, 'admin1');
  assert.equal((await getDoc(ref(db, 'usuarios/admin1'))).data().hotelId, 'h1');
  await assertSucceeds(getDoc(ref(dbPara('admin1'), 'hoteles/h1')));
});

test('asignacion unilateral y asignacion hecha por otro rol se rechazan', async () => {
  const db = dbPara('sa');
  await assertFails(updateDoc(ref(db, 'hoteles/h1'), {
    administradorId: 'admin1', actualizadoEn: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref(db, 'usuarios/admin1'), {
    hotelId: 'h1', actualizadoEn: serverTimestamp(),
  }));
  await assertFails(asignar(dbPara('admin1'), 'h1', 'admin1'));
  await assertFails(asignar(dbPara('cliente'), 'h1', 'admin1'));
});

test('nuevo administrador debe tener rol ADMIN_HOTEL y estar activo', async () => {
  const db = dbPara('sa');
  for (const uid of ['cliente', 'sa', 'adminInactivo']) {
    await assertFails(asignar(db, 'h1', uid));
  }
});

test('reemplazo exige liberar el perfil anterior en el mismo commit', async () => {
  await sembrar({
    'hoteles/h1': hotel({ administradorId: 'admin1' }),
    'usuarios/admin1': perfil('admin1', 'ADMIN_HOTEL', { hotelId: 'h1' }),
  });
  const db = dbPara('sa');
  await assertFails(asignar(db, 'h1', 'admin2'));
  await assertSucceeds(asignar(db, 'h1', 'admin2', 'admin1'));
  assert.equal((await getDoc(ref(db, 'usuarios/admin1'))).data().hotelId, null);
});

test('se puede liberar un administrador anterior inactivo al reemplazarlo', async () => {
  await sembrar({
    'hoteles/h1': hotel({ administradorId: 'adminInactivo' }),
    'usuarios/adminInactivo': perfil('adminInactivo', 'ADMIN_HOTEL', { hotelId: 'h1', activo: false }),
  });
  const db = dbPara('sa');
  await assertSucceeds(asignar(db, 'h1', 'admin2', 'adminInactivo'));
  const anterior = (await getDoc(ref(db, 'usuarios/adminInactivo'))).data();
  assert.equal(anterior.hotelId, null);
  assert.equal(anterior.activo, false);
});

test('administrador ocupado no puede quedar ligado a dos hoteles', async () => {
  const db = dbPara('sa');
  await assertSucceeds(asignar(db, 'h1', 'admin1'));
  await assertFails(asignar(db, 'h2', 'admin1'));
  assert.equal((await getDoc(ref(db, 'hoteles/h2'))).data().administradorId, null);
});

test('asignaciones concurrentes al mismo admin conservan una unica relacion', async () => {
  const db = dbPara('sa');
  const resultados = await Promise.allSettled([
    asignar(db, 'h1', 'admin1', null, 'carrera_h1'),
    asignar(db, 'h2', 'admin1', null, 'carrera_h2'),
  ]);
  assert.equal(resultados.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(resultados.filter(r => r.status === 'rejected').length, 1);
  const asignado = (await getDoc(ref(db, 'usuarios/admin1'))).data().hotelId;
  const libre = asignado === 'h1' ? 'h2' : 'h1';
  assert.equal((await getDoc(ref(db, `hoteles/${asignado}`))).data().administradorId, 'admin1');
  assert.equal((await getDoc(ref(db, `hoteles/${libre}`))).data().administradorId, null);
});

test('lectura de admin exige estar activo y tener ambos lados del vinculo', async () => {
  await sembrar({ 'usuarios/admin1': perfil('admin1', 'ADMIN_HOTEL', { hotelId: 'h1' }) });
  await assertFails(getDoc(ref(dbPara('admin1'), 'hoteles/h1')));
  await sembrar({ 'hoteles/h1': hotel({ administradorId: 'admin1' }) });
  await assertSucceeds(getDoc(ref(dbPara('admin1'), 'hoteles/h1')));
  await assertFails(getDoc(ref(dbPara('admin1'), 'hoteles/h2')));
  await sembrar({ 'usuarios/admin1': perfil('admin1', 'ADMIN_HOTEL', { hotelId: 'h1', activo: false }) });
  await assertFails(getDoc(ref(dbPara('admin1'), 'hoteles/h1')));
});

test('administrador crea la primera habitacion con inventario ausente o vacio y auditoria atomica', async () => {
  const db = dbPara('admin1');
  const primera = habitacion();
  for (const [i, inventario] of [{}, { habitaciones: [], precioDesde: 0 }].entries()) {
    await sembrarHotelAdministrado(inventario);
    const eventoId = `primera_habitacion_${i}`;
    await assertSucceeds(editarHotelComoAdmin(db, {
      habitaciones: [primera], precioDesde: primera.precioNoche,
    }, eventoId));
    const guardado = (await getDoc(ref(db, 'hoteles/h1'))).data();
    assert.deepEqual(guardado.habitaciones, [primera]);
    assert.equal(guardado.precioDesde, primera.precioNoche);
    assert.equal(guardado.ultimoEventoId, eventoId);
    const evento = (await getDoc(ref(dbPara('sa'), `auditoria/${eventoId}`))).data();
    assert.equal(evento.actorUid, 'admin1');
    assert.equal(evento.administradorId, 'admin1');
    assert.equal(evento.accion, 'ACTUALIZAR_HOTEL');
    assert.ok(guardado.actualizadoEn.isEqual(evento.timestamp));
  }
});

test('administrador edita una habitacion conservando identidad y posicion con auditoria nueva', async () => {
  const primera = habitacion();
  const segunda = habitacion({ id: 'r2', numero: '102', precioNoche: 200 });
  await sembrarHotelAdministrado({ habitaciones: [primera, segunda], precioDesde: 150 });
  const editada = { ...segunda, tipo: 'Suite', precioNoche: 90, disponible: false };
  const db = dbPara('admin1');
  await assertSucceeds(editarHotelComoAdmin(db, {
    habitaciones: [primera, editada], precioDesde: 90,
  }, 'editar_habitacion'));
  const guardado = (await getDoc(ref(db, 'hoteles/h1'))).data();
  assert.deepEqual(guardado.habitaciones, [primera, editada]);
  assert.equal(guardado.precioDesde, 90);
  await assertSucceeds(getDoc(ref(dbPara('sa'), 'auditoria/editar_habitacion')));
});

test('edicion de habitacion no permite sustituir su identidad, hotel ni reordenar el inventario', async () => {
  const primera = habitacion();
  const segunda = habitacion({ id: 'r2', numero: '102', precioNoche: 200 });
  await sembrarHotelAdministrado({ habitaciones: [primera, segunda], precioDesde: 150 });
  const intentos = [
    [{ ...primera, id: 'identidad_nueva' }, segunda],
    [{ ...primera, hotelId: 'h2' }, segunda],
    [segunda, { ...primera, tipo: 'Suite' }],
  ];
  for (const [i, habitaciones] of intentos.entries()) {
    await assertFails(editarHotelComoAdmin(dbPara('admin1'), {
      habitaciones, precioDesde: 150,
    }, `identidad_invalida_${i}`));
  }
  const guardado = (await getDoc(ref(dbPara('admin1'), 'hoteles/h1'))).data();
  assert.deepEqual(guardado.habitaciones, [primera, segunda]);
});

test('altas y ediciones rechazan ID o numero de habitacion duplicados incluso con espacios o mayusculas', async () => {
  const primera = habitacion({ numero: 'Suite A' });
  const segunda = habitacion({ id: 'r2', numero: '102', precioNoche: 200 });
  await sembrarHotelAdministrado({ habitaciones: [primera, segunda], precioDesde: 150 });
  const intentos = [
    [primera, segunda, habitacion({ id: primera.id, numero: '103' })],
    [primera, segunda, habitacion({ id: 'r3', numero: ' suite a ' })],
    [primera, { ...segunda, id: primera.id }],
    [primera, { ...segunda, numero: ' SUITE A ' }],
  ];
  for (const [i, habitaciones] of intentos.entries()) {
    await assertFails(editarHotelComoAdmin(dbPara('admin1'), {
      habitaciones, precioDesde: 150,
    }, `duplicado_habitacion_${i}`));
  }
  assert.deepEqual((await getDoc(ref(dbPara('admin1'), 'hoteles/h1'))).data().habitaciones,
    [primera, segunda]);
});

test('crear o editar una habitacion sin auditoria en el mismo commit se rechaza', async () => {
  const primera = habitacion();
  const db = dbPara('admin1');
  await sembrarHotelAdministrado();
  await assertFails(updateDoc(ref(db, 'hoteles/h1'), {
    habitaciones: [primera], precioDesde: 150,
    actualizadoEn: serverTimestamp(), ultimoEventoId: 'alta_sin_auditoria',
  }));
  assert.equal((await getDoc(ref(db, 'hoteles/h1'))).data().habitaciones, undefined);

  await sembrarHotelAdministrado({ habitaciones: [primera], precioDesde: 150 });
  await assertFails(updateDoc(ref(db, 'hoteles/h1'), {
    habitaciones: [{ ...primera, precioNoche: 100 }], precioDesde: 100,
    actualizadoEn: serverTimestamp(), ultimoEventoId: 'edicion_sin_auditoria',
  }));
  assert.deepEqual((await getDoc(ref(db, 'hoteles/h1'))).data().habitaciones, [primera]);
});

test('hotel publicado con cuatro fotos admite la decima habitacion y su edicion dentro del presupuesto de reglas', async () => {
  const fotos = Array.from({ length: 4 }, (_, i) => `https://example.com/hotel-${i}.jpg`);
  const habitaciones = Array.from({ length: 9 }, (_, i) => habitacion({
    id: `r${i + 1}`, numero: String(101 + i), precioNoche: 200 + i,
    fotos: Array.from({ length: 4 }, (_, j) => `https://example.com/room-${i}-${j}.jpg`),
  }));
  await sembrarHotelAdministrado({ publicado: true, fotos, habitaciones, precioDesde: 200 });
  const decima = habitacion({
    id: 'r10', numero: '110', precioNoche: 100,
    fotos: Array.from({ length: 4 }, (_, i) => `https://example.com/room-10-${i}.jpg`),
  });
  const completas = [...habitaciones, decima];
  const db = dbPara('admin1');
  // El minimo al final y la edicion de la ultima posicion recorren los limites
  // del inventario, ademas de la autorizacion y auditoria del hotel publicado.
  await assertSucceeds(editarHotelComoAdmin(db, {
    habitaciones: completas, precioDesde: 100,
  }, 'decima_habitacion'));
  const editadas = [...habitaciones, { ...decima, precioNoche: 90, disponible: false }];
  await assertSucceeds(editarHotelComoAdmin(db, {
    habitaciones: editadas, precioDesde: 90,
  }, 'editar_decima_habitacion'));
  const guardado = (await getDoc(ref(db, 'hoteles/h1'))).data();
  assert.equal(guardado.publicado, true);
  assert.deepEqual(guardado.fotos, fotos);
  assert.deepEqual(guardado.habitaciones, editadas);
  assert.equal(guardado.precioDesde, 90);
});

test('hotel no permite editar datos, publicar, borrar ni fingir asignacion sin cambio', async () => {
  const db = dbPara('sa');
  for (const cambio of [{ nombre: 'Otro' }, { publicado: true }, { creadoPor: 'cliente' }]) {
    await assertFails(updateDoc(ref(db, 'hoteles/h1'), { ...cambio, actualizadoEn: serverTimestamp() }));
  }
  await assertFails(updateDoc(ref(db, 'hoteles/h1'), { actualizadoEn: serverTimestamp() }));
  await assertFails(deleteDoc(ref(db, 'hoteles/h1')));
});

test('auditoria no admite suplantacion, eventos sin cambio, edicion ni borrado', async () => {
  const db = dbPara('sa');
  await assertFails(alta(db, 'actor_falso', {}, { actorUid: 'cliente' }));
  await assertFails(alta(db, 'evento_falso', {}, { evento: 'ACCESO' }));
  await assertFails(alta(db, 'accion_falsa', {}, { accion: 'PUBLICAR_HOTEL' }));
  await assertFails(alta(db, 'timestamp_falso', {}, { timestamp: fecha }));
  await assertFails(setDoc(ref(db, 'auditoria/sin_cambio'), auditoria('h1', null)));
  await assertSucceeds(alta(db, 'correcto'));
  await assertFails(updateDoc(ref(db, 'auditoria/alta_correcto'), { detalle: 'Alterado' }));
  await assertFails(deleteDoc(ref(db, 'auditoria/alta_correcto')));
  await assertFails(getDoc(ref(dbPara('cliente'), 'auditoria/alta_correcto')));
});

test('colecciones ajenas continuan cerradas', async () => {
  await assertFails(setDoc(ref(dbPara('sa'), 'reservas/no_autorizada'), { hotelId: 'h1' }));
  await assertFails(getDoc(ref(dbPara('sa'), 'reservas/no_autorizada')));
});
