# Autenticacion y perfiles en Firebase

Esta fase conecta correo y contrasena con Firebase Authentication y guarda el
perfil en Cloud Firestore, en `usuarios/{uid}`. El UID es el identificador real de
Authentication. Los otros modulos de negocio conservan sus repositorios simulados
hasta su migracion; iniciar sesion no convierte esos datos en persistentes.

## Configuracion de la consola

1. En el proyecto que corresponde a `app/google-services.json`, habilitar
   **Authentication > Sign-in method > Email/Password**.
2. Crear la base de datos **Cloud Firestore** si todavia no existe. Elegir su
   ubicacion antes de crearla.
3. Revisar `firestore.rules` y publicar ese contenido en **Firestore Database >
   Rules** para habilitar los perfiles. No usar reglas generales de prueba. Este
   cambio no se publica automaticamente al compilar o instalar la app.
4. En **Authentication > Templates**, revisar el mensaje de recuperacion de
   contrasena. El flujo usa el enlace enviado por Authentication; la app no
   almacena ni consulta contrasenas en Firestore.

Una cuenta ordinaria puede leer unicamente su propio documento, incluso si esta
inactivo, para mostrar el motivo del bloqueo. El superadministrador activo puede
listar perfiles y asignar hoteles mediante las transacciones descritas en
`FIREBASE_HOTELES.md`. Una cuenta activa puede editar sus nombres, apellidos, telefono,
direccion y foto. La app envia `actualizadoEn` con `FieldValue.serverTimestamp()`.
El registro crea exclusivamente `CLIENTE`, `activo: true`, con el correo del
token y timestamps de servidor en `creadoEn` y `actualizadoEn`.

El cliente no puede cambiar `email`, `rol`, `activo` o `hotelId` ni borrar perfiles.
Los cambios de rol y habilitacion se realizan en la consola o en un backend
autorizado con Admin SDK. La asignacion de hotel se realiza desde la app del
superadministrador, actualizando ambos documentos de forma atomica. La navegacion Android
usa el perfil para elegir la interfaz; la autorizacion de datos debe mantenerse
tambien en las reglas o en el backend de cada modulo que se migre.

## Preparar las tres cuentas indicadas

La forma mas rapida, una vez creada Firestore y publicadas las reglas, es iniciar
sesion con cada cuenta existente. Si no tiene documento, la app crea su perfil
como `CLIENTE`, con sus timestamps. En **Firestore > usuarios**, identificar el
documento por su campo `email` y cambiar `rol` desde la consola segun la tabla.
El listener de sesion recoge el cambio; tambien se puede cerrar y volver a
iniciar sesion. Ningun cambio de rol se realiza desde el formulario Android.
El administrador puede permanecer con `hotelId: null` hasta asignarle hotel.

Para provisionarlos antes del primer acceso, seguir el procedimiento manual
de debajo usando los datos preparados en el JSON.

| Correo en Authentication | Rol del perfil | hotelId inicial |
| --- | --- | --- |
| `cliente@example.com` | `CLIENTE` | `null` |
| `superadmin@example.com` | `SUPERADMIN` | `null` |
| `admin@example.com` | `ADMIN_HOTEL` | `null` (sin hotel asignado) |

1. Localizar cada cuenta en **Authentication > Users**. Si no existe, crearla
   por el procedimiento administrativo habitual. No hace falta compartir su
   contrasena para preparar el perfil.
2. Copiar su **UID** exacto. Crear en Firestore un documento de la coleccion
   `usuarios` cuyo ID sea ese UID, no el correo ni un ID generado automaticamente.
3. Copiar los campos `perfil` de la entrada correspondiente en
   `tools/firebase/perfiles-iniciales.json`. Los nombres y apellidos son ejemplos:
   reemplazarlos por los que corresponda. Conservar el correo de Authentication.
4. Agregar `creadoEn` y `actualizadoEn` con tipo **timestamp** y la fecha/hora de
   creacion. En un backend con Admin SDK usar `FieldValue.serverTimestamp()`.
   `activo` debe ser booleano y `hotelId` debe ser string o null, nunca el texto
   `"null"`.
5. Verificar el rol guardado antes de iniciar sesion. Si falta un perfil y la app
   lo crea para una cuenta existente, el perfil nuevo siempre es `CLIENTE`.
   Preparar los perfiles administrativos antes del primer acceso o modificar su
   rol despues desde la consola/backend, y volver a iniciar sesion.

Ninguna regla ni lista de correos en la app otorga privilegios. Registrar una
cuenta con uno de esos correos no la convierte en administradora. El
administrador de hotel queda sin hotel hasta asignarle un `hotelId` valido desde
un entorno autorizado; no se debe usar un hotel ficticio para habilitarlo.

El JSON es una preparacion organizada por correo, no una exportacion de
Firestore ni una importacion lista para ejecutar. No contiene UIDs, timestamps,
contrasenas o credenciales administrativas. No agregar claves de servicio al APK
ni al repositorio. Admin SDK debe ejecutarse en un backend con credenciales
administradas, nunca en Android.

## Esquema de usuarios/{uid}

| Campo | Tipo |
| --- | --- |
| `nombres`, `apellidos`, `email` | string |
| `telefono`, `direccion`, `fotoUrl` | string o null; opcionales |
| `rol` | `CLIENTE`, `ADMIN_HOTEL` o `SUPERADMIN` |
| `activo` | boolean |
| `hotelId` | string o null; opcional |
| `creadoEn`, `actualizadoEn` | timestamp |

Los conductores y sus datos pertenecen a la API REST del backend web separado.
No crear perfiles `CONDUCTOR` en esta coleccion ni conectar su modulo
directamente a Firestore como parte de esta autenticacion.

## Verificacion local sin datos de produccion

`firebase.json` configura Auth en 9099, Firestore en 8080 y Emulator UI en 4000.
Desde la raiz del repositorio, con Firebase CLI y sus requisitos instalados:

```powershell
firebase emulators:start --only auth,firestore --project demo-iot-auth
```

Usar siempre el proyecto `demo-iot-auth` para estas pruebas; el prefijo `demo-`
evita disponer de recursos de produccion. Iniciar los emuladores no redirige una
app configurada con el proyecto real.

Los repositorios llaman a `FirebaseAuth.getInstance()` y
`FirebaseFirestore.getInstance()`, que usan la FirebaseApp **predeterminada**.
Preparar una variante o harness aislado que reemplace la configuracion generada
por Google Services para esa variante y cree la app predeterminada con
`FirebaseOptions` del proyecto `demo-iot-auth`, una API key de prueba y un app ID
de prueba. Hacerlo antes de que `FirebaseInitProvider`, la Activity,
`ServiceLocator` o cualquier repositorio pueda inicializar Firebase con los
recursos reales. La variante de pruebas debe excluir o sustituir esos recursos;
conservar sin cambios la configuracion de la variante de uso normal y
`.firebaserc`.

Antes del primer uso de los repositorios, configurar las instancias
predeterminadas de Auth y Firestore con `useEmulator("10.0.2.2", 9099)` y
`useEmulator("10.0.2.2", 8080)`, respectivamente. `10.0.2.2` es el host del equipo
desde el emulador Android. Verificar que
`FirebaseApp.getInstance().getOptions().getProjectId()` sea `demo-iot-auth` y
detener el harness si no coincide. Una FirebaseApp secundaria con nombre no
redirige estos repositorios. Ejecutar registro, recuperacion y cualquier escritura
solo cuando esta variante aislada este configurada y ambos emuladores esten
disponibles; no ejecutar estas pruebas con recursos del proyecto real.

La suite JUnit `PerfilFirebaseTest` valida el mapeo y los limites del perfil sin
Android, peticiones de red ni credenciales. No sustituye la comprobacion de reglas
con los emuladores. La matriz de reglas que debe verificarse es:

| Operacion | Resultado esperado |
| --- | --- |
| Sin autenticar: leer o escribir cualquier perfil | Denegado |
| Cuenta A: get de su perfil, incluso inactivo | Permitido |
| Cuenta A: get del perfil B o listar usuarios | Denegado |
| Crear perfil propio CLIENTE activo con correo del token y timestamps de servidor | Permitido |
| Crear perfil propio SUPERADMIN/ADMIN_HOTEL o hotelId no nulo | Denegado |
| Crear otro UID, correo distinto, campo extra o timestamp manipulado | Denegado |
| Cuenta activa: editar solo campos de perfil permitidos y actualizadoEn de servidor | Permitido |
| Editar email, rol, activo, hotelId o creadoEn | Denegado |
| Cuenta inactiva: editar su perfil | Denegado |
| Borrar perfil o acceder a otras colecciones | Denegado |

Para el flujo completo comprobar registro, cierre, reapertura y recuperacion;
perfil ausente/inactivo/invalido; red no disponible; y una respuesta de perfil
que llega despues de cerrar sesion o de entrar con otra cuenta. Una respuesta
atrasada nunca debe reabrir la sesion anterior.

Referencias oficiales: [Authentication con correo y contrasena](https://firebase.google.com/docs/auth/android/password-auth),
[manejo de usuarios](https://firebase.google.com/docs/auth/android/manage-users),
[AuthStateListener](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseAuth.AuthStateListener)
y [emulador de Authentication](https://firebase.google.com/docs/emulator-suite/connect_auth).
