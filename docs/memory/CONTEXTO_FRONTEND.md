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
- **61 pruebas unitarias en verde**, 0 fallos:

  | Suite | Pruebas | Cubre |
  |---|---|---|
  | `BookingOverlapTest` | 10 | RF-032, RC-012 — solapamiento, día de rotación, canceladas |
  | `MockDataTest` | 19 | Coherencia de los datos sembrados |
  | `PriceFormatterTest` | 9 | §66 — formato `S/ 1,240` |
  | `HotelServiceTest` | 7 | Reglas 5-8, 18-20 — incluido vs adicional, no duplicar |
  | `TaxiStatusTest` | 7 | RF-106 a RF-111 — flujo exacto, sin saltos ni retrocesos |
  | `VentasPorPeriodoTest` | 5 | RF-055 a RF-058 — agrupación diaria, mensual y anual |
  | `CrucesDeListaTest` | 4 | "Cruce es una foto, no una ventana" en las cuatro listas |

**Verificado en el emulador** (`Pixel_4`, SDK 34), recorriendo la app de verdad,
no solo compilando: Home · lista de reservas · detalle de reserva · chat (envío
de mensajes, sin `ConcurrentModificationException`) · flujo de reserva · pago ·
checkout · valoración · la valoración aparece en el detalle y en la ficha del
hotel. Aritmética comprobada: 4 × 480 + 120 + 45 = S/ 2,085.

**Sin verificar en el emulador: todo el Bloque C.** Compila, el APK se arma, los
recursos resuelven y las pruebas pasan, pero nadie ha mirado las pantallas del
administrador en un dispositivo. §11 pide recorrerlas antes de darlas por buenas.

### Pendiente

- **RF-022 — imágenes de los servicios** (prioridad Media): `Service` solo tiene
  `iconRes`, no un campo de fotografía. Hay que decidir si entra.
- **Bloque D: conductor** (§46). El QR es la única forma de cerrar un servicio
  (RF-110, RT-016), así que no debe existir un botón de "finalizar" manual.
- **Bloque E: superadmin** (§47). `MockData.BITACORA` ya se escribe desde
  `MockGestionHotelRepository`, pero todavía no hay quién la lea. RF-059 pide
  que el superadmin consulte los reportes de reservas por hotel.
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
