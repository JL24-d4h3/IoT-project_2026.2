# Pruebas Android con Firebase local

Desde esta carpeta, con las dependencias de `package-lock.json` instaladas y
`JAVA_HOME`/`ANDROID_HOME` configurados:

```powershell
node run-android-tests.mjs
```

El unico dispositivo conectado debe ser **`emulator-5554`**, iniciado y
autorizado. El runner verifica su identidad de emulador, fija `ANDROID_SERIAL`
y vuelve a comprobarlo justo antes de Gradle. Rechaza telefonos fisicos y
cualquier dispositivo adicional.
Gradle instala los APK de desarrollo y los retira al terminar; este comando
debe ejecutarse en un AVD de pruebas. Para volver al uso normal de la app,
ejecutarla nuevamente desde Android Studio o instalar `app-debug.apk`.

El comando copia las reglas actuales a `.tooling/firestore.rules`, inicia Auth
en **19099** y Firestore en **18080** con el proyecto ficticio
**`demo-iot-hoteles`**, y ejecuta en la raiz del repositorio:

```text
gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
```

El runner inicializa la FirebaseApp predeterminada con opciones demo antes de
los proveedores de contenido. En `Application.onCreate`, cuando ya existe el
contexto de aplicacion, conecta Auth/Firestore a `10.0.2.2:19099` y
`10.0.2.2:18080`, antes de crear pantallas o repositorios. Los guards cancelan la
prueba si la configuracion no es demo. Iniciar los emuladores por si solo no
redirige una app configurada con Firebase real.

No se exportan/importan datos, no se publican reglas y no se modifica la
configuracion Firebase de uso normal. Firebase CLI detiene los servicios locales
cuando termina Gradle. Los resultados quedan en
`app/build/reports/androidTests/connected/debug/`.

Las capturas posteriores al acceso quedan en `/sdcard/Download/iot-e2e/` del
emulador para conservarlas tras la limpieza de los APK de prueba. Incluyen los
botones en Inicio y Hoteles, el error de conexion, el hotel asignado y el perfil
del administrador. No incluyen las pantallas con contrasenas. Se pueden copiar
al equipo con `adb -s emulator-5554 pull /sdcard/Download/iot-e2e/`.
