import { copyFile, mkdir } from 'node:fs/promises';
import { dirname, delimiter } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const raiz = dirname(fileURLToPath(import.meta.url));
const tooling = new URL('./.tooling/', import.meta.url);
await mkdir(tooling, { recursive: true });
await copyFile(new URL('../../../firestore.rules', import.meta.url), new URL('firestore.rules', tooling));
// Firebase CLI requiere que el archivo de reglas este dentro de su proyecto.
// Copiamos la version actual en cada ejecucion; no hay una segunda fuente manual.
const resultado = spawnSync(process.execPath, [
  fileURLToPath(new URL('./node_modules/firebase-tools/lib/bin/firebase.js', import.meta.url)),
  'emulators:exec', '--project', 'demo-iot-hoteles', '--config', 'firebase.json',
  '--only', 'firestore', 'node --test --test-concurrency=1 hoteles.test.mjs',
], {
  cwd: raiz, stdio: 'inherit',
  env: {
    ...process.env,
    PATH: `${dirname(process.execPath)}${delimiter}${process.env.PATH ?? ''}`,
    FIREBASE_EMULATORS_PATH: fileURLToPath(new URL('emulators/', tooling)),
    CI: 'true',
  },
});
if (resultado.error) throw resultado.error;
process.exit(resultado.status ?? 1);
