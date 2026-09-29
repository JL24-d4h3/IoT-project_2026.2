# Contexto del front end — Reservas de alojamiento (1TEL05)

> **Documento de continuidad.** Léelo completo antes de escribir una sola línea
> de UI. Su propósito es que cualquier sesión —humana o IA— produzca pantallas
> que se sientan parte del mismo producto.
>
> Última actualización: 2026-09-15

---

## 1. Qué es este proyecto

Aplicación móvil **Android nativa para reservas de alojamiento en hoteles**, con
un módulo secundario de **taxi al aeropuerto**. Curso **1TEL05 – Servicios y
Aplicaciones para IoT**.

Cuatro roles sobre una misma app: **Cliente**, **Administrador de hotel**,
**Conductor de taxi** y **Superadmin**.

**El entregable en curso es SOLO FRONT END.** No hay backend, no hay NoSQL, no
hay autenticación real. Todo se alimenta de *mock data*; la lógica de negocio
existe únicamente donde hace falta para que la interfaz se comporte de forma
creíble (validaciones, estados, disponibilidad).

---

## 2. Restricciones obligatorias (no negociables)

Incumplir cualquiera de estas invalida el entregable.

| # | Restricción | Origen |
|---|---|---|
| 1 | **Java**, nunca Kotlin | RT-002, regla 1 |
| 2 | **XML**, nunca Jetpack Compose | §3, regla 2 |
| 3 | Android nativo, **API 34 / Android 14 como mínimo** | RT-001, RT-003 |
| 4 | **RecyclerView** para toda lista — prohibido listar con TextViews sueltos | §48 |
| 5 | Calificaciones en escala **1–10**, nunca 1–5 estrellas | §10, regla 9 |
| 6 | Los **mocks viven fuera** de Activities/Fragments/adaptadores | §49, reglas 33-35 |
| 7 | Servicios = **catálogo global con tags**; el admin **no escribe nombres** | §13, §17, regla 17 |
| 8 | Precio y clasificación incluido/adicional pertenecen a la **relación hotel-servicio** | §18, regla 20 |
| 9 | Mínimo **4 fotografías** por hotel | RF-013, regla 14 |
| 10 | Sin reservas superpuestas (ni por habitación ni por cliente) | RF-032, §22 |
| 11 | Chat **solo** con reserva activa | RF-065, regla 13 |
| 12 | Estados de taxi **exactos**: SOLICITADO → ASIGNADO → EN CAMINO → EN TRASLADO → FINALIZADO | §29, regla 22 |
| 13 | **Identidad visual propia.** Booking es referencia de UX, jamás de estética | §3, §4.1, reglas 28-30 |

**Prohibido explícitamente:** logotipo, nombre, colores de marca, textos o
fotografías de Booking. La app no debe parecer Booking, Airbnb ni Uber.

---

## 3. Documentos fuente

| Documento | Qué contiene | Autoridad |
|---|---|---|
| [docs/plan/requisitos_funcionales_calidad_restricciones.md](../plan/requisitos_funcionales_calidad_restricciones.md) | 120 RF + 43 RC + 38 RT | **La más alta.** Si algo contradice esto, esto gana. |
| [docs/project_detail.txt](../project_detail.txt) | 57 secciones de alcance funcional | Alta |
| [docs/views_prompt.txt](../views_prompt.txt) | 86 secciones de diseño UI/UX | **La más alta en lo visual** |

Cuando `views_prompt` y `project_detail` discrepen en estructura de código,
`views_prompt` §73 autoriza explícitamente modificar la estructura propuesta.

---

## 4. Decisiones ya tomadas

No volver a discutirlas sin una razón nueva.

| Decisión | Elección | Por qué |
|---|---|---|
| **Arquitectura de vistas** | Single-Activity + Navigation Component + biblioteca de **Custom Views** | Es la única que hace que las fases 2-5 sean *agregar* código en vez de reescribir pantallas. Los componentes se reutilizan entre los 4 roles (§71) |
| **Estructura de paquetes** | Árbol `ui/<rol>/<feature>/` de §73, no los paquetes planos de §52 | §73 agrupa por rol, que es lo que hace navegable un proyecto de 4 roles. §73 autoriza el cambio |
| **Imágenes** | **Glide** + URLs remotas con caché y placeholder local | §11: "la fotografía es información". Fotos reales dan la credibilidad que §60 exige |
| **Mapas** | Vista de mapa **estilizada propia**, sin SDK externo | Cero API keys, funciona offline, y encaja con "identidad visual propia"(§3) |
| **Paso de datos entre pantallas** | Se pasan **IDs**, no objetos; el Fragment re-consulta el repositorio | Evita `Parcelable` a mano en Java y sobrevive a rotaciones |
| **Alcance de esta etapa** | Design System + **flujo completo del Cliente**; después Taxi, Admin, Conductor, Superadmin | Orden de prioridad de §79 |
| **Formato de una calificación** | `RatingBadgeView.setRating()` (un decimal) **solo** para promedios de hotel; `setValorEntero()` para la nota de una reseña y el deslizador | Son dos cosas distintas. El promedio de un hotel es decimal de verdad (9.6); una reseña es un entero de la escala 1-10 (§10, regla 9). Pintar "9.0" donde el usuario eligió 9 anuncia una precisión que el control no tiene |

---

## 5. Estado actual

### Hecho y verificado

- **Scaffold reconstruido a Java + XML.** Se eliminaron `MainActivity.kt`,
  `ui/theme/*.kt`, los tests Kotlin, el plugin `kotlin.compose`, `buildFeatures
  { compose true }` y las 8 dependencias Compose. Se conservó Gradle wrapper,
  Groovy DSL, `namespace` y `minSdk 34`.
- **Dependencias** (`gradle/libs.versions.toml`): appcompat, material,
  constraintlayout, recyclerview, fragment, navigation-fragment/ui,
  lifecycle-viewmodel/livedata, viewpager2, swiperefreshlayout, glide.
- **Capa de dominio** en `models/` y `utils/`.
- **Sistema de diseño** en `res/values/` (sección 6): paleta, tipografía,
  espaciado, radios, estilos de botón, `TextAppearance.App.*`.
- **Repositorios + `MockData`**: hotel, reserva, usuario, chat, taxi. Interfaces
  + implementación mock, resueltas por `ServiceLocator`.
- **`MainActivity` + navegación** de cliente (`nav_client.xml`, single-Activity,
  bottom bar). El resto de roles conserva su actividad provisional.
- **Componentes** (`ui/components/`): `AppHeaderView`, `HotelCardView`,
  `BookingCardView`, `PriceBreakdownView`, `ChargeListView`, `RatingBadgeView`,
  `EmptyStateView`, `ErrorStateView`, `LoadingSkeletonView`.
- **Pantallas de cliente navegables**: Home, Hotel Detail, Booking Flow, Payment,
  Reservas, Booking Detail, Chat, Checkout, Valorar estadía.
- **Search, Perfil y Taxi** construidos (`SearchFragment` 455 líneas,
  `ProfileFragment` 238, `TaxiFragment` 625).
- **Los cuatro roles comparten `MainActivity`**: `nav_client`, `nav_hotel_admin`,
  `nav_driver`, `nav_superadmin` y `nav_auth` existen, y `Roles` decide el grafo
  y el menú según la sesión (§73).
- **Capa de datos del Bloque C** (hotel admin, §42 a §45), completa:
  - `Message.Autor` (CLIENTE/HOTEL) sustituye al antiguo `boolean propio`. Aquel
    campo significaba una cosa para el cliente y la contraria para el
    administrador —la clase de dato que acaba invertido en una de las dos
    pantallas—, y por eso RF-064 no se podía implementar sin cambiarlo.
    `Conversation.getNumNoLeidosPara(lector)` / `marcarLeidoPara(lector)`.
  - `ChatRepository.responder` y `conversacionesDeHotel` (RF-064, RF-065).
  - `GestionHotelRepository` + `MockGestionHotelRepository`: habitaciones
    (RF-014 a RF-018), servicios (RF-019 a RF-021), datos y fotos (RF-010 a
    RF-013) y el reporte de ingresos (RF-060, RF-061, orden ascendente).
  - `SessionManager.puedeGestionar(hotelId)` centraliza RF-023, con la excepción
    deliberada del superadmin (RF-059, RF-077).
  - `BookingRepository.reservasDeHotel` (RF-041).
  - `ResumenHotel` y `EstadiaEnCurso`: el cruce de reserva + cliente +
    habitación lo hace el repositorio, no la pantalla (reglas 33-35).
- **Bloque C completo** (§42 a §45): las 13 pantallas del rol están construidas
  y todas son alcanzables. La barra inferior lleva las cinco secciones diarias
  —panel, reservas, habitaciones, servicios, perfil— y las otras cinco —datos
  del hotel, clientes, cobros, mensajes, reportes— más el chat y el detalle de
  reserva se alcanzan desde la portada.
  - **Portada** (§43): `AdminHomeFragment` + `AdminHomeViewModel` +
    `EstadiaAdapter` + `item_estadia_admin.xml`. Métricas del día, estadías en
    curso ordenadas por fecha de salida, desglose de ingresos, conversaciones
    recientes y la rejilla de **acciones rápidas** (`AccionRapidaView` +
    `view_accion_rapida.xml` + `Widget.App.Tile`). **No tiene estado vacío a
    propósito**: un hotel sin huéspedes es un tablero en cero, que es una
    respuesta, no una pantalla sin contenido. La portada es el índice de lo que
    no cabe en la barra, y por eso se refresca en silencio al volver a ella: un
    chat abierto marca sus mensajes como leídos (RF-068) y el contador tiene que
    apagarse.
  - **Reportes** (§45, RF-055 a RF-058): `ReportesFragment` +
    `ReportesViewModel` + `PeriodoAdapter` + `item_periodo_reporte.xml`. Un solo
    `GestionHotelRepository.ventas(hotelId, Periodicidad)` sirve los tres
    reportes —diario, mensual y anual—, que solo se diferencian en la
    granularidad. El agrupamiento puro vive en `PeriodoDeVentas.agrupar(...)`
    para poder probarlo sin Android. El selector de periodo va **fuera** del área
    que cambia de estado: si estuviera dentro, elegir un periodo sin reservas
    dejaría la pantalla vacía y sin forma de volver a uno que sí las tenga.
  - El desglose de ingresos por servicio se pinta con `IngresoListView` en la
    portada y en el reporte, para que los dos no puedan dar cifras distintas.
- **Bloque D completo** (§46, RF-085 a RF-111): el conductor tiene portada propia
  y el servicio de taxi ya se puede cerrar, que es lo que el bloque venía a
  arreglar — hasta ahora ningún taxi podía llegar a FINALIZADO.
  - **Las reglas viven en `TaxiService`, no en el repositorio** (§4):
    `puedeAceptarlo(conductor)`, `avanzarPorConductor(siguiente)` y
    `validarCodigo(introducido)`. `avanzarPorConductor` **rechaza FINALIZADO**:
    el servicio se cierra validando el código del cliente y por ninguna otra
    puerta (RF-110, RT-016). Lo que el servicio no puede saber —si el conductor
    ya tiene un viaje en curso— lo comprueba el repositorio, que es el único que
    ve todos los servicios a la vez.
  - **`TaxiRepository` cambia de lado**: `avanzar(...)` y `confirmarQr(...)`
    desaparecen (el primero elegía él mismo al conductor, contra RF-090) y
    entran las seis operaciones del conductor: `perfilDe`, `disponibles`,
    `serviciosEnCursoDe`, `aceptar`, `avanzarComoConductor`, `validarCodigo`.
    Todas reciben el `conductorId`, para que nadie pueda cerrar el viaje de otro
    sabiendo el identificador.
  - **`disponibles` filtra, no valida**, y devuelve `OfertaDeTaxi`: el servicio
    **más** su distancia. La distancia no es propiedad del servicio —el mismo
    viaje está a distinta distancia de cada conductor— y la calcula el
    repositorio porque es el único que ve al conductor y al servicio juntos. El
    radio de atención son 100 km, que cae entre los dos extremos medidos: la base
    de D1 está a 1,9 km de H1 y Lima–Cusco son 569 km.
  - **La ubicación entra por una costura** (`core/FuenteUbicacion`), hoy con
    `FuenteUbicacionSimulada`: un conductor libre está en su base
    (`MockData.BASE_CONDUCTOR`) y con un servicio se acerca al punto de recojo.
    El día que entre el GPS real se sustituye esa pieza y nada más. Es la
    decisión que el usuario tomó: **simulado por ahora, con la puerta abierta**,
    porque al final del curso puede que haya que probar con dos celulares.
  - **Portada del conductor** (`ui/driver/home/`): una sola pantalla con dos
    caras que se excluyen, como pide §46. Libre enseña métricas, su ficha con su
    vehículo y las solicitudes que puede aceptar; ocupada enseña el viaje y
    **nada más** — ni lista ni métricas, porque un conductor que ya va con
    alguien no puede quedarse con otro pedido. `DriverHomeViewModel` no publica
    `UiState.EMPTY` a propósito: sin solicitudes la pantalla sigue teniendo
    contenido. El contador de pendientes espera a las tres consultas, para que
    la cara no parpadee.
  - **`ValidarCodigoSheet` es la única puerta a FINALIZADO** (RF-102 a RF-104,
    RF-110). El conductor **teclea** el código que le dicta el cliente: no hay
    cámara ni escaneo simulado. Un código que no es el de ese servicio no cambia
    nada y la hoja se queda abierta para reintentar.
  - **Datos sembrados** (§8 del spec): `T3.clienteId` pasa de `"U9"` a `"U1"`
    —la errata: T3 es el traslado de B2, y B2 es de U1—, T1 pasa de D1 a D2 para
    que el cliente U1 siga viendo su taxi en camino y D1 quede libre, y se siembra
    **T4**, la solicitud nueva en Lima (reserva B6, Diego Salas Pinto) que D1
    puede aceptar. Código para la demostración: **`TAX-2026-0735`**.
  - `nav_driver.xml` apunta ya a `DriverHomeFragment`; `PanelRolFragment` se queda
    entonces solo con el superadmin.
- **Bloque E completo** (§47): el superadministrador tiene panel propio, y con él
  el ciclo de vida del hotel queda cerrado — **RF-005** (consultar las cuentas
  registradas), **RF-006** (activarlas y desactivarlas, con los
  superadministradores fuera de ese interruptor), **RF-007** (registrar un
  hotel), **RF-008** (asignarle un administrador), **RF-059** (el reporte de
  reservas del hotel, visto desde la plataforma), **RF-077** y **RF-078**
  (aprobar a un conductor y consultar en qué estado está) y **RF-118 a RF-120**
  (la bitácora, que hasta ahora se escribía y nadie podía leer).
  - **Pantallas nuevas** (`ui/superadmin/`): la portada —`SuperadminHome`— con
    las cifras de la plataforma y lo que espera decisión; **Usuarios**, con el
    filtro por rol y el interruptor de cada cuenta; **Conductores**, con la cola
    "Por aprobar" al principio; **Hoteles** y la **ficha del hotel** —datos,
    administrador, publicación y reporte—; el **alta de hotel**; **Auditoría**,
    la bitácora entera, del movimiento más reciente al más antiguo; y el
    **Perfil**, con la cuenta, la insignia de rol, las cifras de la plataforma
    y el cerrar sesión.
  - **La barra es Inicio · Usuarios · Conductores · Hoteles · Perfil.** La
    auditoría no está en ella: se abre desde la portada —el bloque de últimos
    movimientos y su botón— y desde el perfil, y lleva flecha de vuelta, como
    toda pantalla del panel que no es una sección. El perfil entró donde lo
    tienen los otros dos roles que administran algo, que es el último sitio de
    la barra, y por el mismo motivo que en ellos: **es donde vive el cerrar
    sesión**. Antes la única salida era el icono de la cabecera de la portada,
    así que desde las otras cuatro secciones había que volver a Inicio; ese
    icono ya no está.
  - **`nav_superadmin.xml` deja de ser un cartel.** Apuntaba a `PanelRolFragment`,
    el recordatorio de que a este rol le faltaban las pantallas; ahora apunta a
    las suyas, y el recordatorio se borra. Con él, los cuatro roles tienen su
    grafo y su menú completos.
  - **Publicar es del administrador, retirar es del superadministrador** (la
    decisión del usuario). El hotel nace **sin publicar y sin administrador**;
    el administrador carga fotos y habitaciones y lo publica; el
    superadministrador lo retira del catálogo desde la ficha, con motivo. Un
    hotel retirado conserva sus reservas, su contenido y su administrador.
  - **`SessionManager.HOTEL_ADMINISTRADO` ya no existe.** El hotel que administra
    una cuenta se lee del repositorio (`HotelRepository.hotelDeAdministrador`,
    que recorre `MockData.HOTELES` y no la lista publicada: el administrador de un
    hotel sin publicar sigue siendo su administrador, y es quien tiene que
    publicarlo) y **puede ser `null`**: un administrador recién asignado no tiene
    hotel hasta que el superadministrador se lo da. Las pantallas del
    administrador dicen "Todavía no tienes un hotel asignado." en ese caso, y la
    portada lo cuenta como un estado del hotel, no como un error.
  - **`Hotel` tiene estado de publicación y administrador** (`publicado`,
    `administradorId`, `aptoParaPublicar()`): fotos mínimas **y** alguna
    habitación. El catálogo del cliente esconde los que no están publicados —
    búsqueda, recomendados, filtro por ciudad y selector de distritos salen de la
    misma lista publicada—, **salvo `hotel(id)`**, que sigue devolviendo el hotel
    aunque esté retirado: por eso una reserva anterior se sigue pintando.
  - **`MockData.HOTELES` es mutable**: el alta añade un hotel a la lista viva, y
    por eso las pruebas de coherencia tienen que seguir pasando después de un
    alta, no solo sobre los datos sembrados.
- **102 pruebas unitarias en verde**, 0 fallos:

  | Suite | Pruebas | Cubre |
  |---|---|---|
  | `MockDataTest` | 27 | Coherencia de los datos sembrados |
  | `TaxiConductorTest` | 12 | RF-077, RF-090, RF-102, RF-103, RF-110, RF-111 — aceptar, avanzar y validar |
  | `BookingOverlapTest` | 10 | RF-032, RC-012 — solapamiento, día de rotación, canceladas |
  | `PriceFormatterTest` | 9 | §66 — formato `S/ 1,240` |
  | `PublicacionHotelTest` | 9 | RF-007, RF-013, RF-014 — un hotel nace sin publicar y sin administrador, y su "Desde" es su habitación más barata |
  | `HotelServiceTest` | 7 | Reglas 5-8, 18-20 — incluido vs adicional, no duplicar |
  | `TaxiStatusTest` | 7 | RF-106 a RF-111 — flujo exacto, sin saltos ni retrocesos |
  | `VentasPorPeriodoTest` | 5 | RF-055 a RF-058 — agrupación diaria, mensual y anual |
  | `DistanciaTest` | 5 | §5 — haversine y formato legible; fija Lima–Cusco en 569 km |
  | `CrucesDeListaTest` | 4 | "Cruce es una foto, no una ventana" en las cuatro listas |
  | `FuenteUbicacionSimuladaTest` | 4 | §6 — base del conductor libre, acercamiento y que nadie cae en (0,0) |
  | `ReglasDeRolTest` | 3 | RF-006 — `User.esDesactivable` deja fuera al superadministrador |

**Verificado en el emulador** (`Pixel_4`, SDK 34), recorriendo la app de verdad,
no solo compilando: Home · lista de reservas · detalle de reserva · chat (envío
de mensajes, sin `ConcurrentModificationException`) · flujo de reserva · pago ·
checkout · valoración · la valoración aparece en el detalle y en la ficha del
hotel. Aritmética comprobada: 4 × 480 + 120 + 45 = S/ 2,085.

**Verificado en el emulador, Bloque D**: entrar como conductor (D1) · la portada
abre en la cara libre con la solicitud de Lima a 1,9 km · aceptar · "Voy en
camino" → "Inicié el traslado" · la hoja del código rechaza un código falso sin
cambiar nada y cierra el servicio con `TAX-2026-0735` · la portada vuelve sola a
la cara libre, ya con el estado vacío · el cliente U1 sigue viendo su taxi en
camino con D2 y el plano del recorrido.

**El recorrido del Bloque D encontró cuatro cosas que el compilador no ve**,
todas corregidas:
1. **El vehículo salía dos veces** en la cara libre: `DriverCardView` ya lleva
   dentro un `VehicleCardView`, y el layout ponía otro suelto encima.
2. **El estado le hablaba al conductor como si fuera el pasajero**: "Julio
   aceptó tu solicitud", leído por Julio. `TaxiStatusView` nació para el cliente
   (§38), así que ahora tiene `bind(...)` y `bindParaConductor(...)` con las
   mismas cinco frases contadas desde cada lado.
3. **La Tarea 10 dejó al conductor sin poder cerrar sesión**: su única salida era
   `PanelRolFragment`, y la portada lo sustituyó. Se repuso como acción de la
   cabecera (`ic_logout`), que es el modismo de la casa.
4. **La errata de T3 rompió la pantalla del cliente**: al pasar T3 a U1, U1
   quedó con dos servicios sin cerrar y `TaxiViewModel.separar` se quedaba con el
   de fecha más lejana —una solicitud para el 8 de octubre, todavía sin
   conductor— mientras su taxi de ahora mismo caía a "Servicios anteriores" como
   si ya hubiera pasado. Ahora manda el que más avanzado va. El §8.2 del spec
   pedía justamente que U1 siguiera viendo su taxi en camino.

**Verificado en el emulador, Bloque E**: el recorrido entero de §10.2 del spec,
de un tirón y sin reiniciar la app (los datos simulados viven en memoria: un
`force-stop` los devuelve a la semilla, así que los cambios de cuenta se hicieron
con "Cerrar sesión"). Como cliente, el catálogo enseña los diez hoteles
publicados; como superadministrador, están los diez, publicados y cada uno con su
administrador. El alta de **"Hostal Amazonas"** (Iquitos, Punchana) nace **sin
publicar y sin administrador**, y el cliente no lo ve por ninguna puerta: ni en
la búsqueda por ciudad, ni escribiendo "Iquitos" en el selector de destinos
("No encontramos ese destino"), ni ofreciéndose "Punchana" entre los distritos.
Asignado **Rocío Vargas Lira** (U4) desde la ficha, su portada dice "SIN
PUBLICAR" y "Para publicarlo faltan fotografías y habitaciones." — sin error—, y
las otras cuatro pantallas del administrador dicen "Todavía no tienes un hotel
asignado." Con cuatro fotos y la habitación 101 (Doble estándar, 2 adultos ·
22 m² · piso 1 · S/ 180) el botón de publicar pasa de apagado a encendido, la
tarjeta dice "PUBLICADO / Tu hotel se está mostrando a los clientes", y el
cliente ya lo encuentra **por su ciudad y por su distrito**: "1 alojamiento en
Iquitos" y "1 alojamiento en Punchana". Retirado desde la ficha del
superadministrador, el cliente deja de verlo otra vez, **pero la reserva
`EST-2026-0701` que se había hecho sobre él se sigue pintando** en "Próximas" —
`hotel(id)` no filtra, que es justo lo que el spec pedía comprobar—. Desactivar
a un cliente lo deja fuera: al intentar entrar, "Tu cuenta está deshabilitada.
Escríbenos si crees que es un error."; a un superadministrador **no se le ofrece
el interruptor**, y la fila de Ana Ferreyra sale sin acción. Habilitado el
conductor pendiente (D3, Pedro Ccahuana), la bitácora enseña los movimientos de
la sesión con su autor y su hora.

**Corregido después: el superadministrador no tenía perfil.** Los otros dos
roles que administran algo tienen una sección Perfil, y en ella el cerrar
sesión; el superadministrador solo lo tenía en un icono de la cabecera de la
portada, así que desde Usuarios, Conductores u Hoteles no había salida. Ahora
los tres son iguales: la barra es **Inicio · Usuarios · Conductores · Hoteles ·
Perfil**, la sección que cedió el sitio fue la bitácora —que ya se abría desde
la portada y ahora también desde el perfil, y que ganó la flecha de vuelta— y el
icono de la cabecera se retiró. Verificado en el emulador: la barra con sus
cinco rótulos enteros, el perfil con la insignia "Superadministrador", las
cifras de la plataforma (7 de 7 usuarios, 2 de 3 conductores, 10 de 10 hoteles,
1 por aprobar), el botón a la auditoría y su vuelta, y el cierre de sesión
llevando a la pantalla de acceso. Los perfiles del cliente y del administrador
se volvieron a abrir para comprobar que siguen igual.

**Dos ayudantes salieron del paquete de un rol al escribir ese perfil**, porque
el nuevo los necesitaba y copiarlos habría dejado dos versiones del mismo
formato: **`utils/FormatoDeDatos`** (era `ui/admin/AdminFormato`; el documento
de una persona y el nombre de un servicio a partir de su identificador, que
ahora comparten los perfiles del administrador y del superadministrador) y
**`utils/VersionDeLaApp`** (el "Versión 1.0" del pie, que estaba copiado en los
dos perfiles que ya existían). Y `tools/verificar_recursos.py` ganó una
comprobación: **cada item de la barra de un rol tiene que ser un destino de su
grafo**, que es el fallo que no rompe la compilación y que el propio `Roles.java`
avisa de que ya ocurrió una vez.

**La pantalla de Auditoría, leída entera** (RC-042): 17 movimientos, del más
reciente al más antiguo, y **ningún detalle lleva contraseñas, tokens ni datos de
acceso** — nombres, hoteles, códigos de reserva, importes y estados. Lo único que
dice "password" en la vista es un atributo del volcado de accesibilidad, no un
texto de la pantalla.

**Los tres movimientos de §10.2 paso 9.** El spec los llama así sin decir cuáles
son, y la implementación escribe **una entrada por acción**: habilitar a D3
escribe una (`APROBACION`). Lo que la bitácora enseña al terminar el paso 9 son
los tres últimos actos del superadministrador —retirar el hotel, habilitar al
conductor y desactivar la cuenta—, además del resto de la sesión. Se anota la
lectura literal, que es la que se pudo comprobar.

**El recorrido del Bloque E encontró dos cosas**, las dos corregidas antes de dar
el bloque por cerrado:

1. **Un hotel nacido del alta se anunciaba "Desde S/ 0".** El alta del
   superadministrador no pide precio —lo pone el administrador al cargar
   habitaciones—, pero la tarjeta y el detalle leían el campo guardado, que para
   ese hotel vale cero; el hotel tenía una habitación de S/ 180 y el catálogo
   decía S/ 0. `Hotel.getPrecioDesde()` pasa a delegar en
   `calcularPrecioDesde()`, que ya existía, ya era la regla declarada del modelo
   "el precio más bajo entre sus habitaciones" y no lo llamaba nadie. De paso
   quedan de acuerdo las tres cosas que usan ese precio —lo que se enseña, el
   orden por precio y el filtro por rango—, y cuatro hoteles sembrados cuyo
   precio guardado no era el de su habitación más barata pasan a anunciar el
   real: Casa del Mar 480 → 320, San Isidro Business 420 → 340, Posada Cusco
   Centro 260 → 190 y Arequipa Plaza 230 → 160. Lo protegen dos pruebas nuevas
   (`PublicacionHotelTest`): con habitaciones manda la más barata, y sin
   habitaciones se conserva el precio guardado.
2. **El estado "sin hotel" hablaba de la cuenta, no de quien lee.** Diez
   pantallas del administrador decían "Esta cuenta no tiene un hotel asignado.",
   que describe a un tercero; ahora dicen "Todavía no tienes un hotel asignado.",
   que es lo que el recorrido esperaba leer y lo que ya decía el repositorio
   cuando alguien pide un hotel que no le toca.

**Sin verificar en el emulador: el resto del Bloque C.** El recorrido del Bloque
E sí pasó por las pantallas del administrador que su ciclo toca —portada,
reservas, habitaciones, servicios, perfil y datos del hotel, con hotel y sin
él—, pero las otras —clientes, cobros, mensajes, reportes, el chat y el detalle
de reserva del administrador— siguen sin haberse mirado en un dispositivo desde
que se escribieron. Compilan, los recursos resuelven y las pruebas pasan, que no
es lo mismo. §11 pide recorrerlas antes de darlas por buenas.

### Pendiente

- **RF-022 — imágenes de los servicios** (prioridad Media): `Service` solo tiene
  `iconRes`, no un campo de fotografía. Hay que decidir si entra.
- **RF-100 — el administrador consulta el estado del taxi de sus clientes.** Es
  del **Bloque C**, no del D, y quedó fuera de aquel bloque sin hacer: el
  administrador no tiene forma de ver si el traslado de un huésped ya salió. El
  Bloque D le da de dónde sacarlo (`TaxiRepository.serviciosEnCursoDe` y el
  estado del servicio ya son consultables), pero la pantalla no existe.
- **Los bloques A a E están hechos.** Lo que queda de la lista de requisitos que
  el proyecto se propuso cubrir con ellos es lo de arriba: RF-022 y RF-100.
- Los datos simulados sembrados cubren una estancia que cierra hoy en H1 para
  que la portada del administrador tenga algo que mostrar; hay una prueba que lo
  protege.
- **Limpieza, sin fecha**: `UserRepository.conversacion(...)` y
  `UserRepository.enviarMensaje(...)` ya no los llama nadie.

### Decisiones abiertas para el usuario

- **Paleta e identidad** (teal `#0D5C63`, ámbar `#E9A23B`) y el nombre **"Estadía"**:
  definidos de forma unilateral, sin aprobar ni rechazar.
- **Etiqueta cualitativa de la valoración**: `Review.getEtiqueta()` y
  `RatingBadgeView.setRatingConEtiqueta()` existen pero no los usa nadie — no hay
  hueco para la etiqueta en `item_review.xml`. O se conectan (cambia el diseño de
  la lista de reseñas) o se borran. Es una decisión de diseño, no un fallo.
- **Aireado de algunas pantallas**: el usuario lo vio en el seguimiento del taxi
  ("hay algunas cosas que salen muy pegadas") y pidió explícitamente dejarlo para
  después. Sigue pendiente y es deliberado.
- **La ficha del conductor dice "Conductor asignado" también en su propia
  portada.** `DriverCardView` se escribió para el cliente (§37: "quien viene a
  buscarme"), y el Bloque D la reutiliza para que el conductor se vea a sí mismo.
  En la pantalla del cliente la etiqueta es correcta; en la suya, rara. Se
  arregla con una etiqueta opcional en el componente, pero eso toca una pantalla
  del Bloque A que no es de este bloque.
- **T3 sigue apareciendo bajo "Servicios anteriores" estando SOLICITADO.** Es
  consecuencia de la errata corregida: U1 tiene dos servicios sin cerrar y la
  sección es "todo lo que no es el de ahora", pero el título promete otra cosa.
  Renombrar la sección ("Otros servicios") son unas palabras en un texto del
  cliente; se dejó como estaba para no tocar el Bloque A por redacción.
- **La etiqueta "Solicitudes disponibles" de la métrica ocupa dos líneas** y
  deja la fila de tres cifras algo desigual. Es del mismo aireado que ya está
  aplazado.
- **Cuatro pantallas del administrador enseñan el estado de error cuando no hay
  hotel**, en vez de un estado vacío con su explicación: servicios, perfil,
  reservas y habitaciones —las que no llevan `EmptyStateView` en su layout—
  pintan "No pudimos cargar la información" con un "Reintentar" que no arregla
  nada, porque lo que falta no es que falle una carga: es que todavía no hay
  hotel. El texto que importa se lee igual ("Todavía no tienes un hotel
  asignado."), pero el envoltorio miente. Arreglarlo toca `UiState`, que hoy no
  tiene forma de llevar un mensaje en `empty()` y lo usan las cuatro pantallas de
  los cuatro roles; se deja como decisión y no como parche de una pantalla.

---

## 6. Sistema de diseño — fuente de verdad visual

> **Toda** pantalla debe construirse con estos tokens. Si un valor no está aquí,
> se agrega aquí primero — nunca se escribe un valor suelto en un XML.

### 6.1 Paleta

Concepto: **disciplina de herramienta profesional + calidez de hospitality**
(§4.2). Deliberadamente **alejada del azul de Booking**.

**Marca**

| Token | Hex | Uso |
|---|---|---|
| `colorPrimary` | `#0D5C63` | Acción principal, CTA, estado activo, selección |
| `colorPrimaryDark` | `#073D48` | Status bar, pressed |
| `colorPrimaryContainer` | `#DCEAEA` | Fondo de chip seleccionado, badges suaves |
| `colorOnPrimary` | `#FFFFFF` | Texto sobre primario |
| `colorAccent` | `#E9A23B` | **Solo** rating y destacados. Nunca como CTA |

**Neutros**

| Token | Hex | Uso |
|---|---|---|
| `colorBackground` | `#F7F8F8` | Fondo de pantalla |
| `colorSurface` | `#FFFFFF` | Cards, sheets, inputs |
| `colorSurfaceVariant` | `#EEF1F1` | Bloques secundarios, skeletons |
| `colorBorder` | `#E2E6E6` | Bordes de 1dp |
| `colorTextPrimary` | `#141B1C` | Títulos y cuerpo |
| `colorTextSecondary` | `#5A6668` | Metadatos, subtítulos |
| `colorTextDisabled` | `#9AA5A6` | Deshabilitado |

**Semánticos**

| Token | Hex | Uso |
|---|---|---|
| `colorSuccess` | `#2E7D52` | Disponible, confirmada, pagado |
| `colorWarning` | `#B8860B` | Pendiente, por vencer |
| `colorError` | `#C0392B` | Error, cancelada, no disponible |
| `colorInfo` | `#2A6FB0` | Avisos informativos |

**Regla de color:** máximo **un** color de acento por pantalla. El primario
manda; el ámbar solo aparece en ratings. Nunca dos CTAs compitiendo.

### 6.2 Tipografía

Familia: **Roboto** (del sistema) con pesos y tracking controlados. Si más
adelante se quiere una fuente propia, se reemplaza aquí y toda la app la hereda.

| Estilo | Tamaño | Peso | Color | Uso |
|---|---|---|---|---|
| `Display` | 32sp | Bold, tracking −0.5 | textPrimary | Hero, total a pagar |
| `H1` | 24sp | Bold | textPrimary | Título de pantalla |
| `H2` | 20sp | SemiBold | textPrimary | Título de sección |
| `H3` | 17sp | Medium | textPrimary | Nombre de hotel en card |
| `Body` | 15sp | Regular | textPrimary | Texto corriente |
| `BodySecondary` | 14sp | Regular | textSecondary | Descripción, dirección |
| `Caption` | 12sp | Regular | textSecondary | "284 reseñas", timestamps |
| `Label` | 12sp | Medium, tracking +0.4, MAYÚSCULAS | textSecondary | Encabezados de grupo |
| `Button` | 15sp | Medium | según variante | Etiquetas de botón |
| `Price` | 18sp | Bold | textPrimary | Precio en card |
| `PriceLarge` | 28sp | Bold | textPrimary | Total en resumen y pago |
| `NumericEmphasis` | 15sp | Bold | textPrimary | El número del RatingBadge |

La jerarquía debe dejar leer sin esfuerzo: **Hotel → ubicación → descripción →
precio → acción** (§7).

### 6.3 Espaciado

Escala en dp. Sin excepciones, sin valores intermedios.

`4` · `8` · `12` · `16` · `20` · `24` · `32` · `40` · `48`

| Token | Valor | Uso típico |
|---|---|---|
| `spaceXs` | 4 | Separación ícono-texto |
| `spaceSm` | 8 | Entre líneas de una misma card |
| `spaceMd` | 12 | Padding interno de chips |
| `spaceBase` | 16 | **Margen lateral de pantalla**; padding de card |
| `spaceLg` | 20 | Entre bloques de una card |
| `spaceXl` | 24 | Entre secciones |
| `space2xl` | 32 | Antes de un CTA principal |

**Margen lateral de pantalla: siempre 16dp.** Ni 12, ni 20.

### 6.4 Radios y superficies

Una sola familia coherente (§9). Prohibido mezclar cards muy redondeadas con
cuadradas, o botones circulares con inputs rectos.

| Elemento | Radio |
|---|---|
| Chips, badges | 8dp |
| Botones, inputs | 12dp |
| **Cards** | **16dp** |
| Imagen dentro de card | 16dp arriba, 0 abajo |
| Bottom sheets, dialogs | 24dp (solo esquinas superiores en sheets) |
| Avatares | Circular |

**Elevación: mínima.** Las cards se separan con **borde de 1dp** y, como mucho,
sombra de 2dp. §78 prohíbe sombras exageradas. La jerarquía la hace el
espaciado y el peso tipográfico, no la sombra.

### 6.5 Iconografía

Material Symbols, siempre como **Vector Drawable**. Tamaños permitidos: **16**
(inline en texto), **20** (dentro de chips y botones), **24** (acción
independiente, navegación). Nunca decorativos: si un icono no comunica, sobra.

### 6.6 Fotografía

Es información, no adorno (§11). Reglas:

- Las cards de hotel **llevan fotografía grande**, con *aspect ratio* fijo
  (16:10) y `centerCrop`. Nunca deformadas.
- Sin fotografía disponible → placeholder con el color `surfaceVariant` y el
  icono del establecimiento. **Nunca** un hueco gris vacío ni un crash.
- Glide siempre con `placeholder` y `error` definidos.

---

## 7. Reglas de composición

1. **Una acción primaria por pantalla.** Si hay dos, una es secundaria.
2. **Jerarquía explícita**: el usuario debe saber en 2 segundos dónde está, qué
   ve y qué puede hacer (§5.1).
3. **Progressive disclosure**: lo esencial primero; el detalle en bottom sheets,
   expandibles o pantallas propias (§5.2).
4. **No saturar.** Si una pantalla necesita scroll largo, probablemente son dos
   pantallas.
5. **Nada de formularios gigantes** en una sola vista (§78).
6. Los **precios** tienen jerarquía fuerte pero no compiten con el nombre del
   hotel (§66).

---

## 8. Componentes obligatorios (§58)

Cada uno es un **Custom View** en Java con API propia (`hotelCard.setHotel(h)`),
no un XML copiado. Debe soportar sus variantes y estados.

**Navegación:** `AppHeaderView`, bottom navigation
**Hoteles:** `HotelCardView`, `HotelImageGalleryView`, `RatingBadgeView`, `HotelInfoSectionView`
**Habitaciones:** `RoomCardView`, `RoomFeatureView`
**Servicios:** `ServiceChipView`, `ServiceCardView`, `ServiceSectionView`
**Reservas:** `BookingCardView`, `BookingSummaryView`, `BookingStatusBadgeView`, `PriceBreakdownView`
**Taxi:** `TaxiRequestCardView`, `DriverCardView`, `VehicleCardView`, `TaxiStatusTimelineView`, `QrView`
**UI:** `PrimaryButton`, `SecondaryButton`, `SearchFieldView`, `DateSelectorView`, `GuestSelectorView`, `FilterChipView`, `EmptyStateView`, `ErrorStateView`, `LoadingSkeletonView`

**Prueba de reutilización:** `HotelCardView` debe servir sin cambios en Home,
Search, Recomendados y Favoritos. Si hay que duplicarlo, el componente está mal
diseñado.

---

## 9. Estados de UI (§50, §62)

**Toda** pantalla que muestra datos debe resolver los cinco estados. Diseñar
solo el *happy path* es un error.

| Estado | Qué se muestra |
|---|---|
| **Loading** | *Skeleton* que conserva la estructura de la pantalla (§51). Nunca un spinner bloqueando todo |
| **Empty** | Ilustración/icono + mensaje + CTA. Ej: "Aún no tienes reservas" → [Buscar hoteles] |
| **Error** | Mensaje orientado al usuario + [Reintentar]. **Jamás** "HTTP 500" ni "NullPointerException" (§53) |
| **Success** | Contenido, y confirmación explícita en operaciones críticas (RC-030) |
| **Disabled** | Visualmente inerte, no solo con menos opacidad |

**Offline / API de taxis caída** (RC-019 a RC-022): se informa **sin romper** el
resto de la app. Reservas y hoteles deben seguir funcionando.

Los mocks emulan latencia (~400 ms) y saben devolver vacío y error a propósito,
para que estos estados sean **demostrables**, no decorativos.

---

## 10. Errores que hay que evitar (§78)

Pantallas genéricas de CRUD · formularios gigantes · cards sobrecargadas ·
colores aleatorios · iconos inconsistentes · botones con estilo distinto en cada
pantalla · márgenes arbitrarios · diez componentes compitiendo por atención ·
RecyclerViews monótonos · gradientes excesivos · sombras exageradas · border
radius aplicado al azar · animaciones innecesarias · copiar Booking.

**Animar para comunicar, no para presumir** (§55).

---

## 11. Cómo verificar

```bash
python3 tools/verificar_recursos.py     # referencias que el compilador no atrapa
./gradlew :app:assembleDebug            # compila
./gradlew :app:testDebugUnitTest        # 61 pruebas de la capa de dominio
```

**`tools/verificar_recursos.py` existe porque tres fallos reales pasaron la
compilación**: un `@dimen/space_xxs` que no existía, un estilo inventado
(`Widget.App.RadioButton`) y un `navigate(R.id.bookingDetailFragment)` a un
destino que nadie registró. Los dos primeros tumban la compilación; el tercero
no, porque la navegación se resuelve por reflexión y falla en ejecución. El
script comprueba además que un `TextInputLayout` no lleve hint propio *y* otro en
su `TextInputEditText`, que se dibujan superpuestos.

Antes de fiarse de él, comprobar que **falla** cuando debe: meter una referencia
falsa y ver que la reporta. Un verificador que siempre dice "ok" es peor que no
tenerlo.

**"BUILD SUCCESSFUL" no siempre significa "se reconstruyó".** Con la caché de
Gradle en marcha, una tarea puede darse por buena y devolver la salida anterior;
visto aquí: un `assembleDebug` en 2s con el APK todavía en la marca de tiempo
del build anterior. Por eso, al verificar, se mira la **marca de tiempo** del
APK y del XML de resultados, y las pruebas se fuerzan con `--rerun-tasks`. Un
build verde sobre artefactos viejos no verifica nada.

SDK en `/android-data/Sdk` (platforms 34, 35, 36 — ya configurado en
`local.properties`). JDK 21. Emulador `Pixel_4`; adb en
`/android-data/Sdk/platform-tools/adb`.

Al terminar cualquier bloque de trabajo: **compilar, correr las pruebas y
recorrer la pantalla en el emulador**. Un defecto visual —dos textos
superpuestos, un panel encima de otro— no lo ve el compilador. Varios de los
fallos corregidos aquí se encontraron mirando una captura, no un log.

---

## 12. Prioridad si hay que recortar (§79)

1. Home · 2. Search · 3. Hotel Detail · 4. Room Selection · 5. Booking Flow ·
6. Reservations · 7. Taxi · 8. Profile · 9. Hotel Admin · 10. Driver ·
11. Superadmin

**Calidad de las pantallas principales > cantidad de pantallas.**
