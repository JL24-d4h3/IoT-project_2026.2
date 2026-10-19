# Hoteles, asignacion administrativa y catalogo publicado

Esta fase guarda hoteles, su asignacion y su contenido en Cloud Firestore.
Requiere el acceso con Firebase Authentication y los perfiles `usuarios/{uid}`
descritos en [FIREBASE_AUTH.md](FIREBASE_AUTH.md). El UID real de Authentication
identifica al administrador; su correo no concede permisos.

## Configuracion necesaria

1. Preparar el perfil del superadministrador con `rol: "SUPERADMIN"` y
   `activo: true`, y el del administrador con `rol: "ADMIN_HOTEL"` y
   `activo: true`, desde Firebase Console o un backend autorizado.
2. Abrir [firestore.rules](../firestore.rules), seleccionar y copiar **todo el
   archivo**, desde `rules_version = '2';` hasta la ultima llave. En Firebase
   Console, elegir el proyecto correspondiente a `app/google-services.json`,
   entrar a **Firestore Database > Rules**, reemplazar **todo** el texto del
   editor y pulsar **Publicar**. El archivo ya incluye autenticacion, perfiles,
   hoteles y auditoria: no pegar solo un bloque ni anexarlo a reglas anteriores.
   Compilar o instalar Android no publica estas reglas. Esta entrega prepara
   los archivos; no los despliega en tu proyecto.
3. Iniciar sesion con el superadministrador y pulsar **Crear hotel** en Inicio
   o en Hoteles. Ambos botones siguen visibles durante la carga, con la lista
   vacia o cuando falla la consulta. Completar el formulario y guardar; el hotel
   aparece en Hoteles sin publicar y sin administrador. Abrir su ficha y pulsar
   **Asignar administrador** para elegir una cuenta administradora activa libre.
4. Iniciar sesion con la cuenta administradora. En **Mi hotel** completar los
   datos, agregar fotografias, habitaciones, servicios y lugares cercanos.
   Publicar cuando tenga al menos cuatro fotos y una habitacion. Al entrar con
   una cuenta cliente activa, el catalogo consulta exclusivamente hoteles
   publicados.

La publicacion de reglas y la preparacion inicial de roles se realizan manualmente.
El administrador puede consultar el hotel asignado desde Perfil y Mi hotel y
editar su contenido. Su tablero de reservas/ingresos, reservas, clientes,
cargos, reportes y chat que todavia no se hayan migrado muestran su estado
pendiente; no deben fabricar cifras o clientes ni buscar el hotel real en los
datos de demostracion. La publicacion del hotel no implementa por si sola el
proceso de reservas, cobros o disponibilidad por fechas.

## Datos persistentes

`hoteles/{hotelId}` utiliza este esquema base:

| Campo | Tipo y significado |
| --- | --- |
| `nombre`, `ciudad`, `distrito` | Texto con contenido |
| `direccion` | Texto; puede quedar vacio en el alta |
| `latitud`, `longitud` | Numeros finitos, rangos [-90,90] y [-180,180]; (0,0) se rechaza |
| `publicado` | Booleano; `false` durante el alta |
| `administradorId` | UID de Authentication o `null` |
| `creadoPor` | UID del superadministrador que realiza el alta |
| `creadoEn`, `actualizadoEn` | Timestamps de servidor |
| `ultimoEventoId` | ID del evento de auditoria del ultimo cambio |

Los campos opcionales de contenido son `descripcion` (string), `fotos`,
`habitaciones`, `servicios`, `lugaresCercanos` (listas) y `precioDesde` (numero).
Una lista ausente equivale a vacia; `null` no sustituye una lista. El precio
anunciado debe ser el minimo de `precioNoche` entre las habitaciones, o cero si
no hay habitaciones.

| Lista | Elementos y limite |
| --- | --- |
| `fotos` | Hasta 12 URLs HTTP/HTTPS distintas, de hasta 2048 caracteres |
| `habitaciones` | Hasta 10 mapas con `id`, `hotelId`, `tipo`, `numero`, `piso`, `capacidadAdultos`, `capacidadNinos`, `areaM2`, `precioNoche`, `disponible` y `fotos` |
| `habitaciones[].fotos` | Hasta 4 URLs HTTP/HTTPS distintas por habitacion |
| `servicios` | Hasta 12 mapas `{serviceId, included, price}` de IDs del catalogo; incluido exige precio 0 y adicional exige precio positivo hasta 1000000 |
| `lugaresCercanos` | Hasta 10 mapas `{nombre, tipo, distanciaKm}` |

Cada habitacion debe pertenecer al mismo `hotelId` del documento. Adultos y
ninos son enteros en los rangos 1..20 y 0..20; piso es entero entre -10 y 200.
Area y precio por noche deben ser positivos, con maximos de 10000 m2 y 1000000.
Los servicios adicionales tienen el mismo maximo de precio; la distancia de un
lugar debe estar entre mas de 0 y 1000 km. Nombre del hotel, ciudad, distrito y
nombres/tipos de lugares admiten hasta 120 caracteres; descripcion hasta 5000,
direccion hasta 240, y tipo/numero de habitacion hasta 80.

## Habitaciones embebidas

Las habitaciones se guardan en `hoteles/{hotelId}.habitaciones`, sin una
coleccion separada. Este ejemplo muestra el mapa completo de una habitacion;
`hotelId` debe coincidir con el ID del documento contenedor y `id` es el UUID
estable que conserva el formulario al reintentar:

```json
[
  {
    "id": "ef4efac9-cfea-4235-99e1-6b1e8c765386",
    "hotelId": "ID_DEL_HOTEL",
    "tipo": "Doble",
    "numero": "201",
    "piso": 2,
    "capacidadAdultos": 2,
    "capacidadNinos": 1,
    "areaM2": 24,
    "precioNoche": 180,
    "disponible": true,
    "fotos": []
  }
]
```

Una escritura puede agregar **una** habitacion al final, o editar **una**
posicion existente conservando su `id` y `hotelId`. Las otras posiciones deben
permanecer exactamente iguales. No se permite borrar, reordenar ni sustituir
la identidad de habitaciones; la aplicacion actual no realiza esas operaciones.
Esto tambien permite crear la primera habitacion cuando la lista aun no existe.
Cada lista debe tener IDs unicos y numeros unicos despues de quitar espacios
de los extremos y convertir a minusculas: `201`, ` 201 ` y `A1`, `a1` no pueden
identificar habitaciones distintas del mismo hotel. Una inconsistencia antigua
en esos datos requiere correccion administrativa antes de continuar editando.

## Validacion y limites de las listas

Cada transaccion modifica como maximo una de las cuatro listas del hotel, o
sus datos generales, o su publicacion, junto con metadatos y auditoria. Las
listas que no cambian se conservan sin volver a recorrerlas. Para habitaciones,
las reglas usan `List.removeAll()` para aislar el mapa nuevo o editado y lo
validan por completo, incluidas sus cuatro fotos posibles. Tambien extraen
las diez identidades y los diez numeros para comprobar su unicidad. Para
servicios se validan todos los mapas presentes, hasta doce, y sus doce IDs;
no se admite repetir un `serviceId`. Las posiciones ausentes usan un centinela
vacio al comprobar unicidad, que ningun ID o numero valido puede utilizar.

Este es el analisis estatico del coste: la rama de habitaciones obtiene dos
diferencias de listas, valida una habitacion y extrae veinte claves. Un alta
comprueba una concatenacion exacta y no recorre las diez posiciones para
contar cambios. Una edicion comprueba identidad, residuos iguales y el mismo
indice mediante hasta diez alternativas. La rama de servicios ejecuta
hasta doce validaciones de mapas pequenos y doce extracciones de claves. Ambas
ramas son excluyentes en una misma escritura. Las comprobaciones de relacion,
campos generales, precio minimo y auditoria se mantienen, sin agregar lecturas
de documentos por elemento. Estas cantidades describen operaciones del codigo,
no un conteo medido de expresiones del motor. Firebase limita a 1000 las
expresiones evaluadas por solicitud; no se ha ejecutado una medicion, emulador,
compilacion ni prueba en esta entrega. Ampliar los limites exige revisar ese
presupuesto; para inventarios mayores conviene usar subcolecciones con reglas
por documento. Referencias oficiales: [limites de Security Rules](https://firebase.google.com/docs/firestore/quotas#security_rules),
[`String.trim()` y `String.lower()`](https://firebase.google.com/docs/reference/rules/rules.String)
y [`List.removeAll()`, `concat()` y `toSet()`](https://firebase.google.com/docs/reference/rules/rules.List).

## Servicios incluidos y de pago

La configuracion se guarda dentro de `hoteles/{hotelId}.servicios`, junto al
hotel. Cada asociacion utiliza exactamente estos tres campos:

```json
[
  {"serviceId": "WIFI", "included": true, "price": 0},
  {"serviceId": "DESAYUNO", "included": false, "price": 35}
]
```

`included` es booleano y `price` es numerico. Un servicio incluido siempre
tiene precio cero. Un servicio de pago necesita precio mayor que cero y hasta
1000000. No se guarda el nombre libre del servicio, ni un campo `hotelId`
adicional dentro de esta asociacion: el hotel es el documento contenedor.

El catalogo local compartido esta en
[CatalogoServicios.java](../app/src/main/java/org/iot/project/data/CatalogoServicios.java);
contiene 14 opciones, con estos IDs exactos. No hay una coleccion de servicios
en Firestore:

| ID | Servicio |
| --- | --- |
| `WIFI` | Wi-Fi de alta velocidad |
| `DESAYUNO` | Desayuno buffet |
| `RESTAURANTE` | Restaurante |
| `BAR` | Bar y lounge |
| `PISCINA` | Piscina |
| `GIMNASIO` | Gimnasio |
| `SPA` | Spa y sauna |
| `ESTACIONAMIENTO` | Estacionamiento |
| `AIRE_ACONDICIONADO` | Aire acondicionado |
| `LAVANDERIA` | Lavanderia |
| `TRASLADO_AEROPUERTO` | Traslado al aeropuerto |
| `MASCOTAS` | Admite mascotas |
| `SALA_REUNIONES` | Sala de reuniones |
| `RECEPCION_24H` | Recepcion 24 horas |

Un hotel puede asociar hasta 12 de esas opciones. Para agregar una opcion al
catalogo en el futuro hay que actualizar tambien la lista permitida de las
reglas. La interfaz y el repositorio eligen del catalogo; las reglas impiden
enviar IDs inventados directamente al SDK.

## Asignacion y auditoria

El otro extremo de la asignacion vive en `usuarios/{uid}.hotelId`. **Un
administrador administra como maximo un hotel y un hotel tiene como maximo un
administrador.** Ambos documentos deben guardar la misma relacion.

La asignacion actualiza el hotel y el perfil del nuevo administrador dentro de
una transaccion. Si se reemplaza un administrador anterior, libera su `hotelId`
en la misma transaccion; esto tambien se permite si el anterior esta inactivo.
La nueva cuenta debe ser un `ADMIN_HOTEL` activo. Una cuenta ya ocupada no puede
asignarse a otro hotel dejando vigente la relacion anterior. Las reglas
comprueban el estado final de todos los documentos con `getAfter`, por lo que
una actualizacion unilateral se rechaza completa.

Una asignacion ya correcta no necesita escrituras ni una nueva entrada de
auditoria. Los vinculos inconsistentes requieren revision administrativa; la
aplicacion no debe sobrescribirlos para ocultar el conflicto. Las transacciones
y las reglas resuelven conflictos entre solicitudes concurrentes sin dejar dos
hoteles ligados a la misma cuenta.

`auditoria/{eventoId}` conserva `actorUid`, `actorNombre`,
`evento: "ACCION_ADMINISTRATIVA"`, `accion` (`ALTA_HOTEL` o
`ASIGNAR_ADMINISTRADOR`, `ACTUALIZAR_HOTEL`, `PUBLICAR_HOTEL`, `RETIRAR_HOTEL`),
`detalle`, `hotelId`, `administradorId` y `timestamp`.
Se agrega en el mismo commit que el cambio. El UID del actor debe coincidir con
la cuenta autenticada y el timestamp con la hora del servidor. Las reglas
rechazan auditorias sin la transicion correspondiente del hotel y no permiten
editar ni borrar eventos existentes. El hotel debe incluir `ultimoEventoId` y
las reglas exigen que apunte a un evento nuevo, correspondiente al mismo hotel,
actor, accion y administrador, creado en el mismo commit. Omitir la auditoria o
reutilizar un evento previo rechaza tambien la escritura del hotel.

## Permisos de esta fase

| Cuenta | Permisos nuevos |
| --- | --- |
| SUPERADMIN activo | Leer/listar perfiles, hoteles y auditoria; crear hoteles; asignar/liberar administradores; publicar/retirar; agregar auditoria valida |
| ADMIN_HOTEL activo | Leer y editar su hotel con vinculo reciproco; publicar/retirar; agregar la auditoria de esas operaciones |
| CLIENTE activo | Leer/listar solamente hoteles publicados |
| Cuenta inactiva o sin autenticar | Sin acceso al catalogo de hoteles ni a la auditoria |

SUPERADMIN puede publicar o retirar cualquier hotel. Para publicar se exigen
al menos cuatro fotografias, una habitacion y un administrador activo con
vinculo reciproco. Retirar un hotel no exige esos minimos ni que su
administrador siga activo. Una edicion de contenido en un hotel publicado no
puede dejarlo sin los requisitos para publicar. Ninguna cuenta puede borrar
hoteles mediante estas reglas.

El cliente debe consultar `hoteles` con `whereEqualTo("publicado", true)`.
Firestore Rules autoriza consultas completas; no elimina de una consulta los
documentos privados. Una consulta cliente sin ese filtro se rechaza, aunque no
haya hoteles privados en ese momento. Los administradores consultan el documento
de su hotel directamente; el listado administrativo general corresponde a
SUPERADMIN. La lectura de auditoria sigue reservada a SUPERADMIN.

Se mantienen el registro publico exclusivamente como `CLIENTE`, la lectura del
perfil propio incluso inactivo y la edicion personal de nombres, apellidos,
telefono, direccion y foto. Ni el superadministrador de la app ni otro cliente
pueden cambiar roles, habilitacion, correo o fecha de creacion mediante estas
reglas. La asignacion administrativa solo agrega permiso sobre `hotelId` y
`actualizadoEn` de perfiles `ADMIN_HOTEL`. El administrador del hotel solo puede
cambiar nombre, descripcion, direccion, coordenadas y las listas de contenido,
con `precioDesde` derivado, timestamps y evento. No puede alterar ciudad,
distrito, creador, fecha de creacion, asignacion, roles ni calificaciones. El
alta y la asignacion conservan sus acciones de auditoria anteriores; las
ediciones de contenido se registran como `ACTUALIZAR_HOTEL`.

Guardar los mismos valores o pedir una publicacion ya vigente debe resolverse
como una operacion sin cambios. El repositorio compara el contenido y devuelve
exito sin ejecutar escrituras; por eso ese reintento no necesita auditoria
nueva ni una excepcion permisiva en las reglas. Las reglas siguen rechazando
una edicion que solo cambie el timestamp y el evento de auditoria.

## Denegacion al crear o editar habitaciones

El mensaje anterior "No tienes permiso para cambiar este hotel" traducía
cualquier `PERMISSION_DENIED` de la transaccion. No demostraba que el
administrador estuviera mal asignado: podia fallar la lectura del perfil,
la del hotel o la escritura conjunta de hotel y auditoria. La app ahora
distingue esas tres fases y registra el error original en Logcat con la
etiqueta `HotelFirestore`, sin registrar correos ni credenciales.
Antes de escribir contenido tambien identifica campos no admitidos, campos
base ausentes, `creadoEn` con un tipo distinto de timestamp, `creadoPor` no
valido y `numReviews` que no sea entero. Esos casos muestran el campo concreto
en lugar de presentar una incompatibilidad del documento como un fallo de
asignacion. No se inventan ni reparan metadatos de origen desde Android.

Las reglas de habitaciones se simplificaron para quitar el conteo fijo de
diez posiciones por solicitud. Conservan el limite, la validacion del mapa,
la identidad, la unicidad y la prohibicion de borrar/reordenar. Esto reduce
operaciones repetidas; sin una traza del motor no se puede afirmar que el
rechazo anterior agotara el presupuesto de expresiones.

La asignacion requiere que `usuarios/{UID}` tenga `rol: "ADMIN_HOTEL"`,
`activo: true` booleano y el `hotelId` exacto, y que el hotel tenga
`administradorId: UID`. La app usa el UID de Authentication, no el correo.
Si ambas lecturas se permiten pero el guardado se rechaza, revisar el esquema
del hotel y las reglas completas de `hoteles` y `auditoria`: un campo
desconocido o un metadato obligatorio ausente tambien puede impedir guardar.

Se corrigio ademas una incompatibilidad independiente: las reglas exigen
`precioDesde` valido en toda edicion de contenido, pero antes el repositorio
solo lo persistia al cambiar habitaciones. Ahora normaliza el minimo en toda
escritura de contenido con intencion de cambio, a cero si no hay habitaciones.
Las lecturas siguen sin escribir y publicar/retirar conserva sus campos
permitidos. Esta incompatibilidad no explica por si sola el fallo al crear
la primera habitacion, que ya recalculaba el precio.

Para utilizar esta revision hay que volver a publicar **todo** `firestore.rules`
y ejecutar la app con el codigo actualizado. No se han ejecutado la app,
pruebas, emuladores ni despliegues para esta revision; los datos y las reglas
activas del proyecto no se han inspeccionado desde aqui.

## Verificar reglas con un emulador local

La suite esta en `tools/firebase/rules-tests/`. Usa exclusivamente el proyecto
ficticio **`demo-iot-hoteles`** y Firestore en **127.0.0.1:18080**, sin Auth real,
credenciales, cambios en `.firebaserc` ni llamadas a servicios de produccion.
El runner se niega a ejecutarse si no recibe ese endpoint local. Sus contextos
autenticados son identidades simuladas por `@firebase/rules-unit-testing`.

Con Node compatible con las dependencias, npm y Java instalados, ejecutar desde
esa carpeta:

```powershell
npm ci
npm test
```

El comando copia las reglas actuales de la raiz a `.tooling/firestore.rules`,
inicia y detiene Firestore Emulator con la configuracion de la misma carpeta.
Si Java no esta en PATH, configurar
`JAVA_HOME` y agregar su carpeta `bin` a PATH solo para esa terminal. La suite
incluye alta por roles, validacion de coordenadas y timestamps, limites de
edicion de perfiles, asignacion unilateral, reemplazo y liberacion de
administrador inactivo, cuenta ocupada, asignaciones concurrentes, lectura del
hotel propio y proteccion de la auditoria.

Se agregaron seis casos de regresion para habitaciones: primera alta con lista
ausente o vacia, edicion valida, proteccion de identidad y posicion, rechazo de
duplicados, auditoria obligatoria y alta/edicion de la decima habitacion en un
hotel publicado con cuatro fotos. Estos casos **no se han ejecutado**. No cubren
por completo servicios, transiciones de publicacion ni catalogo. Los comandos
anteriores quedan como referencia para una verificacion posterior; las
comprobaciones contra emuladores no sustituyen revisar la interfaz Android
con las cuentas preparadas en Firebase.

## Verificar el flujo Android con Firebase local

[ANDROID_TESTS.md](../tools/firebase/rules-tests/ANDROID_TESTS.md) describe el
comando para las pruebas de interfaz en Android. Usan cuentas ficticias de Auth
y Firestore local para comprobar ambos accesos a Crear hotel, el alta desde
Inicio, la asignacion y el hotel en el perfil del administrador al reabrir la
Activity. Tambien comprueban que el boton de Hoteles se mantenga disponible con
lista vacia y con error de conexion.
