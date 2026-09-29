# Bloque D — Conductor (§46)

> **Especificación de diseño.** Escrita antes de implementar, para que el bloque
> se pueda revisar, discutir y —si hiciera falta— retomar desde aquí.
>
> Fecha: 2026-09-18
> Estado: aprobada, pendiente de implementar

---

## 1. Punto de partida

El Bloque C (§42 a §45, administrador de hotel) está construido y las 61 pruebas
pasan. El taxi existe entero **del lado del cliente**: `TaxiFragment` pide un
traslado, sigue al conductor en el mapa y muestra el QR. El conductor, en
cambio, no existe: `nav_driver.xml` apunta a `PanelRolFragment`, la pantalla
provisional que comparten los roles sin construir.

Al tomar contexto aparecieron tres cosas que cambian el tamaño real del bloque.

### 1.1 El taxi no se puede cerrar

`TaxiRepository.avanzar(...)` y `TaxiRepository.confirmarQr(...)` **no los llama
nadie**. `QrSheet` solo dibuja el código: no valida nada. Consecuencia: hoy
ningún servicio de taxi puede llegar a FINALIZADO. El servicio T1, sembrado en
EN_CAMINO, se queda ahí para siempre.

El Bloque D no es, por tanto, "añadir las pantallas del conductor". Es lo que
hace que el flujo del taxi funcione.

### 1.2 `avanzar()` contradice a RF-090

Ese método, al pasar a ASIGNADO, elige él mismo al conductor
(`conductorDisponible()`). Pero RF-090 y RF-107 dicen que **quien acepta es el
taxista**. Con el rol de conductor construido, esa asignación automática no
puede seguir existiendo: es código muerto que además codifica la regla
contraria a la del enunciado.

### 1.3 Dos incoherencias en los datos sembrados

**a) T3 declara un cliente que no es el de su reserva.**

| Servicio | Reserva | Cliente de la reserva | `clienteId` del servicio |
|---|---|---|---|
| T1 | B1 | U1 | U1 ✔ |
| T2 | B3 | U1 | U1 ✔ |
| T3 | B2 | **U1** | **U9** ✘ |

`T3` se construye con `new TaxiService("T3", "TAX-2026-0734", "B2", "U9")`,
pero B2 es de U1 (Lucía Quispe, hotel H4). La errata se confirma por la
geografía: el origen de T3 es `Calle Plateros 145` y esa es, literalmente, la
dirección de H4 (`MockData:272`). T3 es el traslado de B2, y B2 es de U1.

Importa porque `MockTaxiRepository.solicitar(...)` tampoco comprueba que la
reserva pertenezca al cliente que la pide, así que la misma incoherencia se
puede crear en tiempo de ejecución. Y para el conductor sí se nota: RF-089 pide
enseñarle de quién es el pedido.

**b) El conductor de demostración está ocupado y la única solicitud está en otra
ciudad.**

`SessionManager.CONDUCTOR_ACTIVO` es `D1` (Julio Mendoza, Lima), y D1 ya tiene
T1 en curso — así que al entrar abre directamente en la cara ocupada y **nunca
ve la lista de solicitudes**. Y la única solicitud disponible es T3, en Cusco:
aunque estuviera libre, vería un traslado a 569 km (medido entre las
coordenadas sembradas de H1 y H4, no estimado a ojo).

---

## 2. Alcance

**Entra:**

- Paquete `ui/driver/`: portada del conductor con sus dos caras, y la validación
  del código.
- Operaciones del conductor en `TaxiRepository`.
- Eliminación del código muerto (`avanzar`, `confirmarQr`).
- Costura de ubicación para RF-098.
- Arreglo de los datos sembrados descritos en §1.3.
- Pruebas de dominio y verificación en el emulador.

**No entra:**

- **RF-100** ("el administrador del hotel debe poder consultar el estado del
  servicio de taxi asociado a sus clientes"). Es un hueco del **Bloque C**: no
  hay nada de taxi en `ui/admin/`. Queda fuera de D a propósito, y anotado como
  pendiente.

---

## 3. La portada del conductor

§46 pide una sola pantalla con dos caras, y lo dice literal:

> *Cuando exista un servicio activo: ocultar la lista de solicitudes y
> concentrar toda la pantalla en el servicio actual.*

### 3.1 Cara libre

El conductor no tiene servicio activo.

- **Métricas**: solicitudes disponibles, en curso y completados. Los textos ya
  existen en `strings.xml` (`driver_home_disponibles`, `driver_home_activos`,
  `driver_home_completados`), sembrados por la sesión anterior y todavía sin
  usar. La forma de esta pantalla ya estaba pensada.
- **Su vehículo y su calificación** (`driver_home_vehiculo`, `driver_home_rating`,
  `driver_home_calificaciones`).
- **Lista de solicitudes disponibles**, cada una en un `TaxiRequestCardView` con
  acción **Aceptar**.

### 3.2 Cara ocupada

La lista de solicitudes desaparece por completo. Queda el servicio actual:
cliente, punto de recojo, destino, pasajeros, y la línea de estados
(`TaxiStatusView`). Y **una sola acción primaria**, que cambia con el estado:

| Estado | Acción | Requisito |
|---|---|---|
| ASIGNADO | "Voy en camino" → EN_CAMINO | RF-108 |
| EN_CAMINO | "Inicié el traslado" → EN_TRASLADO | RF-109 |
| EN_TRASLADO | "Validar código" → FINALIZADO | RF-110 |

Al finalizar, la pantalla vuelve sola a la cara libre. Es §7.1 —una acción
primaria por pantalla— aplicado al pie de la letra: en ningún momento hay dos
botones compitiendo.

---

## 4. Dónde viven las reglas

Se sigue el criterio que el proyecto ya aplicó con `PeriodoDeVentas.agrupar(...)`:
**la regla pura vive en el dominio, para poder probarla sin Android.** El
repositorio queda como adaptador delgado.

```java
// TaxiService — reglas del conductor, puras
boolean puedeAceptarlo(Driver conductor)       // RF-077, RF-090, RF-092
void avanzarPorConductor(TaxiStatus siguiente) // RF-108, RF-109 — y nunca a FINALIZADO
boolean validarCodigo(String codigo)           // RF-103
```

- **`puedeAceptarlo`** exige que el servicio esté en SOLICITADO, que el
  conductor esté habilitado (RF-077) y que no tenga ya un viaje en curso.
- **`avanzarPorConductor`** acepta solo transiciones válidas y **rechaza
  FINALIZADO explícitamente**. RF-110 dice que FINALIZADO *solo* se alcanza
  validando el código; sin este rechazo, `TaxiStatus.canTransitionTo` lo
  permitiría desde EN_TRASLADO y la regla quedaría solo en la pantalla.
- **`validarCodigo`** compara contra el código del propio servicio (RF-103).

### 4.1 Cuándo se puede validar el código

`TaxiService.debeMostrarQr()` devuelve cierto en EN_CAMINO y en EN_TRASLADO, y
`TaxiStatusTest.elQrSoloSeMuestraCuandoElConductorEstaEnCaminoOEnTraslado` lo
fija a propósito. La validación respeta esa decisión ya tomada: se admite
mientras el QR sea visible, y si el servicio está en EN_CAMINO **se pasa por
EN_TRASLADO antes de finalizar**, para no saltarse ningún estado (RF-111). Es el
comportamiento que ya tenía `confirmarQr`, que se conserva.

---

## 5. `TaxiRepository`: qué gana y qué pierde

Gana las seis operaciones del conductor:

```java
void perfilDe(String conductorId, cb)                             // RF-096
void disponibles(String conductorId, cb)     // List<OfertaDeTaxi> — RF-088, RF-089
void serviciosEnCursoDe(String conductorId, cb)                   // RF-097
void aceptar(String taxiId, String conductorId, cb)               // RF-090, 091, 092, 107
void avanzarComoConductor(String taxiId, String conductorId, cb)  // RF-108, RF-109
void validarCodigo(String taxiId, String conductorId, String codigo, cb)
                                                                  // RF-102, 103, 104, 110
```

Pierde `avanzar(...)`, por lo dicho en §1.2, y `confirmarQr(...)`, que
`validarCodigo` sustituye. Ninguno de los dos tiene llamadores: su eliminación
no rompe nada.

Tres detalles que la implementación fijó y conviene no deshacer:

- **Toda operación del conductor pide el conductor.** `validarCodigo` también:
  sin eso, cualquiera podría cerrar el traslado de otro con solo conocer su
  identificador.
- **El servicio en curso se pide como lista** (`serviciosEnCursoDe`), aunque haya
  como mucho uno. "No tengo viaje" es el caso normal del conductor libre, no un
  error; con un dato único, `MockRepository.entregarDato` lo convertiría en el
  estado de error de §50 y la portada elegiría la cara equivocada.
- **`disponibles` filtra en vez de validar**, igual que `servicioActivo` e
  `historial` con el cliente. `entregarLista` —la única puerta que respeta
  `MockConfig.Modo.VACIO`, y por tanto la única que deja demostrar el estado
  vacío— no captura excepciones, así que dentro no puede ir nada que lance.
- **`disponibles` devuelve `OfertaDeTaxi`, no `TaxiService`.** La tarjeta tiene
  que decir a cuántos kilómetros queda el recojo, y esa distancia solo se puede
  calcular desde la posición del conductor, que vive en `FuenteUbicacion`, dentro
  del repositorio. Si devolviera servicios a secas, el adaptador tendría que
  pedir la base por su cuenta —leyendo `MockData` desde una vista, que es lo que
  prohíbe §49— o haría falta una operación más solo para eso. La distancia viaja
  con la oferta y se calcula en un único sitio.

`perfilDe` existe porque **no hay ningún repositorio que devuelva un `Driver`** y
el conductor no es un `User` (`SessionManager.getUsuarioId()` devuelve nulo para
ese rol). Sin ella, la portada tendría que leer `MockData` desde el ViewModel,
que es lo que prohíbe §49. El repositorio del taxi *es* el repositorio del
conductor.

`disponibles(...)` **filtra por cercanía**: un conductor ve las solicitudes que
puede atender, no las de otra ciudad. Es lo que hace que la distancia de la
tarjeta signifique algo y evita que a D1 le aparezca un traslado a 569 km.

El umbral es una constante con nombre y su valor se justifica en el propio
código: tiene que dejar fuera Cusco desde Lima (**569 km**, el número real entre
las coordenadas sembradas) y dejar dentro cualquier punto de la misma ciudad
(unos pocos km). **100 km** cae holgadamente entre los dos extremos. La distancia
se calcula con la fórmula del semiverseno, en una función pura y probable.

---

## 6. La costura de la ubicación (RF-098)

RF-098 pide registrar automáticamente la ubicación del conductor durante un
servicio activo. Se implementa **simulada**, pero sin soldarla a la pantalla:
si más adelante el curso pide GPS real, debe entrar sin reescribir nada.

Un solo concepto —*dónde está el conductor*— detrás de una interfaz mínima:

```
core/FuenteUbicacion
├── FuenteUbicacionSimulada   hoy: acerca al conductor al punto de recojo
└── FuenteUbicacionGps        mañana: FusedLocationProvider
```

Una interfaz, una implementación, un punto de inyección. Con esto la
**distancia** de la tarjeta pasa a ser un dato real: cuánto le falta al
conductor para llegar al punto de recojo.

---

## 7. Componentes

`TaxiRequestCardView` hoy pinta origen, destino, horario, precio y la acción
"Valorar". §46 pide además **Distancia** y **Acción aceptar**, y no los tiene.

- Gana `setOnAceptar(...)` y la distancia, **ambos opcionales**: la tarjeta se
  sigue usando igual en el historial del cliente, donde no hay nada que aceptar.

---

## 8. Datos sembrados

Cuatro cambios, cada uno con su razón:

1. **`T3.clienteId`: `"U9"` → `"U1"`.** La errata de §1.3a.
2. **T1 pasa de D1 a D2.** El cliente U1 sigue viendo su taxi en camino, ahora
   con D2 al volante: la demostración del cliente no pierde nada. D1 queda libre
   y puede recorrer el flujo entero.
3. **Un servicio disponible nuevo en Lima**, ligado a la reserva B6 (Diego Salas
   Pinto, Casa del Mar, checkout en cuatro días): un huésped que pide traslado al
   aeropuerto. Es la solicitud que D1 podrá aceptar.
4. **T3 se queda como está** —salvo el cliente— y representa el caso real de una
   solicitud en otra ciudad, que D1 no ve gracias al filtro de cercanía.

Además, **el conductor necesita una posición base** para que la distancia sea
creíble mientras está libre. No es un dato suelto: lo suministra
`FuenteUbicacion` (§6), que es la única pieza que sabe dónde está el conductor.
Fuente única, para que el día que entre el GPS no queden dos verdades sobre la
misma posición.

---

## 9. Verificación

Al terminar, y según §11 de `CONTEXTO_FRONTEND.md`:

```bash
python3 tools/verificar_recursos.py
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --rerun-tasks
```

Mirando **la marca de tiempo** del APK y del XML de resultados, no solo el
"BUILD SUCCESSFUL".

**Pruebas nuevas** (de dominio, sin Android, como las 61 actuales): aceptación
rechazada por conductor no habilitado (RF-077), por servicio ya asignado
(RF-092) y por conductor ocupado; `avanzarPorConductor` rechazando FINALIZADO y
los saltos de estado (RF-110, RF-111); y la validación del código correcto e
incorrecto (RF-103).

**En el emulador**, recorriendo el flujo entero con D1: entrar como conductor →
ver la solicitud de Lima → aceptar → "voy en camino" → "inicié el traslado" →
validar el código → el servicio queda cerrado y la pantalla vuelve a la cara
libre. Y en paralelo, que el cliente U1 siga viendo lo suyo.

---

## 10. Decisiones abiertas

- **RF-100** queda fuera (§2). Si se quiere, es una pantalla más del Bloque C.
- **RF-022** (imágenes de los servicios) sigue sin decidir, y no lo toca este
  bloque.
- La costura de ubicación (§6) está pensada para que el GPS real entre después.
  El día que se implemente, lo único que cambia es qué implementación se
  construye en el punto de inyección.
