import { copyFile, mkdir } from 'node:fs/promises';
import { dirname, delimiter, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const carpeta = dirname(fileURLToPath(import.meta.url));
const repositorio = fileURLToPath(new URL('../../../', import.meta.url));
const tooling = new URL('./.tooling/', import.meta.url);
const serial = 'emulator-5554';
const projectId = 'demo-iot-hoteles';
const argumentos = process.argv.slice(2);
const ejecutarGradle = argumentos.length === 1 && argumentos[0] === '--gradle';
if (argumentos.length && !ejecutarGradle) throw new Error('Uso: node run-android-tests.mjs');

if (!process.env.JAVA_HOME || !process.env.ANDROID_HOME) {
  throw new Error('Configura JAVA_HOME y ANDROID_HOME antes de ejecutar las pruebas Android.');
}

const entorno = {
  ...process.env,
  PATH: [
    dirname(process.execPath), join(process.env.JAVA_HOME, 'bin'),
    join(process.env.ANDROID_HOME, 'platform-tools'), process.env.PATH ?? '',
  ].join(delimiter),
  ANDROID_SERIAL: serial,
  FIREBASE_EMULATORS_PATH: fileURLToPath(new URL('emulators/', tooling)),
  CI: 'true',
};

function ejecutar(comando, args, opciones = {}) {
  const resultado = spawnSync(comando, args, { env: entorno, ...opciones });
  if (resultado.error) throw resultado.error;
  return resultado;
}

function exigirUnicoEmulador() {
  const adb = join(process.env.ANDROID_HOME, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  const listado = ejecutar(adb, ['devices'], { encoding: 'utf8' });
  if (listado.status !== 0) throw new Error(`No se pudo consultar ADB: ${listado.stderr}`);
  const dispositivos = listado.stdout.split(/\r?\n/).map(linea => linea.trim())
    .filter(linea => linea && !linea.startsWith('List of devices attached') && !linea.startsWith('*'))
    .map(linea => linea.split(/\s+/));
  if (dispositivos.length !== 1 || dispositivos[0][0] !== serial || dispositivos[0][1] !== 'device') {
    throw new Error(`Conecta solamente ${serial}, iniciado y autorizado. Dispositivos detectados: ${listado.stdout.trim()}`);
  }
  const qemu = ejecutar(adb, ['-s', serial, 'shell', 'getprop', 'ro.kernel.qemu'], { encoding: 'utf8' });
  if (qemu.status !== 0 || qemu.stdout.trim() !== '1') {
    throw new Error(`${serial} no se identifico como emulador Android. No se instalaran pruebas en un dispositivo fisico.`);
  }
}

exigirUnicoEmulador();

if (ejecutarGradle) {
  // Solo Firebase CLI puede abrir esta fase con ambos endpoints locales listos.
  if (process.env.GCLOUD_PROJECT !== projectId
      || process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18080'
      || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:19099') {
    throw new Error('Gradle requiere los emuladores demo locales Auth 19099 y Firestore 18080 iniciados por este runner.');
  }
  const tareas = [':app:connectedDebugAndroidTest', '--no-daemon', '--console=plain'];
  const resultado = process.platform === 'win32'
    // El texto de cmd es constante; la ruta del proyecto se pasa solamente como cwd.
    ? ejecutar(join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'cmd.exe'),
      ['/d', '/s', '/c', 'gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain'],
      { cwd: repositorio, stdio: 'inherit' })
    : ejecutar('./gradlew', tareas, { cwd: repositorio, stdio: 'inherit' });
  process.exit(resultado.status ?? 1);
}

await mkdir(tooling, { recursive: true });
await copyFile(new URL('../../../firestore.rules', import.meta.url), new URL('firestore.rules', tooling));
const resultado = ejecutar(process.execPath, [
  fileURLToPath(new URL('./node_modules/firebase-tools/lib/bin/firebase.js', import.meta.url)),
  'emulators:exec', '--project', projectId, '--config', 'android.firebase.json',
  '--only', 'auth,firestore', 'node run-android-tests.mjs --gradle',
], { cwd: carpeta, stdio: 'inherit' });
process.exit(resultado.status ?? 1);
