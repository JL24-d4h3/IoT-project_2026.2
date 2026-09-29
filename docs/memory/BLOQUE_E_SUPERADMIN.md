# Bloque E — Superadministrador (§47)

> **Especificación de diseño.** Escrita antes de implementar, para que el bloque
> se pueda revisar, discutir y —si hiciera falta— retomar desde aquí.
>
> Fecha: 2026-09-18
> Estado: aprobada, pendiente de implementar

---

## 1. Punto de partida

Los bloques A, B, C y D están construidos y las 85 pruebas pasan. El
superadministrador, en cambio, no existe: `nav_superadmin.xml` apunta a
`PanelRolFragment`, la pantalla provisional que enseña la cuenta en sesión y
promete lo que falta. Su barra inferior tiene un solo elemento, así que ni
siquiera se dibuja.

Al tomar contexto aparecieron tres cosas que cambian el tamaño real del bloque.

### 1.1 La bitácora se escribe y nadie la lee

`MockData.BITACORA` recibe entradas desde tres repositorios —
`MockGestionHotelRepository`, `MockBookingRepository` y `MockTaxiRepository`—
con cinco entradas sembradas y una más por cada operación que se hace en la
aplicación. No hay ninguna puerta para consultarla. RF-120 es exactamente abrir
esa puerta.

### 1.2 El administrador de hotel no administra un hotel: administra "H1"

`SessionManager.HOTEL_ADMINISTRADO` es la constante `"H1"`, y
`getHotelAdministrado()` la devuelve para cualquier administrador. La regla de
identidad existe y funciona (RF-023), pero está cableada al hotel de
demostración.

Consecuencia para este bloque: **RF-008 ("asignar un administrador a cada hotel
registrado") no se puede cumplir de verdad sin cambiar eso.** Si el superadmin
asigna un administrador a un hotel nuevo y esa asignación no cambia lo que el
administrador ve al entrar, la pantalla está diciendo algo que no es cierto.

### 1.3 Un hotel no tiene estado: existe o no existe

Un hotel sembrado está publicado por el solo hecho de estar en `MockData`. No
hay forma de tener un hotel a medio armar sin que el cliente lo vea. RF-013
(minimo de cuatro fotografias) y RF-014 (habitaciones) son reglas del
enunciado, pero hoy nada impide que un hotel sin fotografias ni habitaciones
aparezca en el buscador.

El flujo que se pidió para el alta —el superadmin crea el hotel, le asigna un
administrador, el administrador completa la ficha y recién entonces se
publica— necesita ese estado intermedio.

---

## 2. Alcance

**Entra:**

- Paquete `ui/superadmin/`: inicio, usuarios, conductores, hoteles, ficha del
  hotel, alta de hotel y bitácora.
- Repositorio nuevo `SuperadminRepository` con su implementación simulada.
- Estado de publicación del hotel (`Hotel.publicado`), la relación
  hotel-administrador (`Hotel.administradorId`) y la regla de rol que protege
  al superadministrador (`User.esDesactivable`).
- La operación de publicar y retirar, compartida con el administrador.
- El filtro del catálogo del cliente: los hoteles sin publicar no se ofrecen.
- El bloque de publicación en la pantalla de hotel del administrador
  (Bloque C), y el estado "todavía no tengo hotel" en sus pantallas.
- Datos sembrados: los diez hoteles publicados y con administrador, más un
  administrador sin hotel.
- Pruebas de dominio y verificación en el emulador.

**No entra:**

- RF-100 (el administrador de hotel consulta el taxi de sus clientes). Es del
  Bloque C y sigue pendiente, como ya está registrado en
  `CONTEXTO_FRONTEND.md`.
- Alta de cuentas de administrador desde la aplicación. La cuenta existe antes:
  la crea el sistema de gestión de la plataforma, igual que a los conductores
  (RT-006, RT-023). El superadmin **asigna**, que es lo que pide RF-008.
- Que el superadmin pueda saltarse la regla de publicación. Puede retirar
  cualquier hotel y volver a publicarlo; no puede publicar uno que no cumpla el
  mínimo.

---

## 3. El estado del hotel

### 3.1 El campo

`Hotel` gana `publicado`, que **nace en falso**, y `administradorId`, que nace
en nulo.

Que nazca en falso no es un detalle: es el mismo criterio que
`Driver.habilitado`, que también nace en falso porque habilitar es una
decisión, no un dato de alta. Un hotel recién creado no está publicado porque
todavía no hay nada que publicar — ni fotografías, ni habitaciones, ni precio.

Los diez hoteles sembrados se marcan publicados **explícitamente**, uno por
uno, igual que D1 y D2 se marcan habilitados. La alternativa —que el valor por
defecto sea "publicado"— haría que el día que alguien cree un hotel por
equivocación aparezca en el catálogo, que es justo lo que no puede pasar.

### 3.2 La regla

Publicar no es un interruptor suelto: es una afirmación de que el hotel se puede
ofrecer. La regla vive en el modelo y la aplica el repositorio.

```java
/** Un hotel sin fotografias ni habitaciones no es publicable (RF-013, RF-014). */
public boolean aptoParaPublicar() {
    return cumpleMinimoFotos() && !habitaciones.isEmpty();
}
```

Se exigen las dos cosas por motivos distintos. Las cuatro fotografías son
RF-013 y son una regla escrita. La habitación no está escrita en ningún
requisito, pero un hotel sin habitaciones no tiene nada que reservar: publicarlo
llenaría el buscador de fichas que no llevan a ninguna parte, y el cliente se
enteraría al abrir la ficha y ver la lista de habitaciones vacía.

**Retirar no tiene condiciones.** Un hotel publicado se puede retirar siempre,
aunque haya quedado por debajo del mínimo: es la operación que existe
precisamente para cuando algo va mal.

### 3.3 El filtro del catálogo

El cliente no debe ver los hoteles sin publicar. El filtro va **en el
repositorio, no en las pantallas**, porque son cinco consultas distintas las que
tienen que coincidir:

| Consulta | Qué hace con los borradores |
|---|---|
| `buscar` | No los devuelve |
| `recomendados` | No los devuelve |
| `enCiudad` | No los devuelve |
| `ciudades` | No ofrece una ciudad que solo tenga borradores |
| `distritosDisponibles` | Lo mismo, por distrito |
| `precioMaximo` | No cuenta sus precios: el techo del filtro no puede salir de un hotel invisible |
| `obtener` / `hotel` | **Sí los devuelve** |

Esa última fila es deliberada y es la más importante de la tabla. `hotel(id)` es
la consulta sincrónica que usan las listas que solo guardan identificadores
—reservas, servicios de taxi— para pintarse. Si un hotel se retira y esa
consulta dejara de devolverlo, **una reserva ya hecha se rompería**: el cliente
entraría a "Mis reservas" y vería una fila sin nombre de hotel. Retirar un hotel
lo saca del catálogo, no borra lo que ya pasó. Es el mismo criterio que una
cancelación de reserva: la reserva cancelada libera la habitación, pero sigue
existiendo.

Como el cliente solo puede llegar a la ficha de un hotel tocando una tarjeta, y
ninguna tarjeta lleva a un borrador, no hay puerta de entrada a un hotel sin
publicar. `obtener` sigue siendo la consulta genérica que necesitan el
administrador (su propio hotel, que puede estar en borrador) y el superadmin.

---

## 4. Quién administra qué hotel

### 4.1 Dónde vive la relación

En el hotel: `Hotel.administradorId`. RF-008 dice "asignar un administrador **a
cada hotel** registrado", así que la relación pertenece al hotel, y es lo que la
ficha del hotel necesita enseñar de todas formas.

### 4.2 Cómo la lee la sesión

`SessionManager.getHotelAdministrado()` deja de devolver una constante y pasa a
leer el dato:

```java
@Nullable
public static String getHotelAdministrado() {
    if (getRolActivo() != Role.ADMIN_HOTEL) {
        return null;
    }
    Hotel hotel = ServiceLocator.hoteles().hotelDeAdministrador(getUsuarioIdSeguro());
    return hotel != null ? hotel.getId() : null;
}
```

Tres decisiones dentro de esas seis líneas:

**Se lee de un repositorio, no de `MockData`.** `SessionManager` es una pieza de
`core`, y el proyecto tiene una costura —`ServiceLocator`— precisamente para que
el día que exista un backend cambie una clase y no todas. Importar `MockData`
aquí rompería esa costura en el sitio donde más caro sale.

**La consulta es sincrónica.** Las pantallas del Bloque C llaman a
`getHotelAdministrado()` y usan el resultado en el acto, sin `await`. Convertir
esa llamada en asincrónica obligaría a rehacer las cinco pantallas del
administrador para ganar nada: el dato ya está en memoria. `HotelRepository` ya
tiene una consulta sincrónica por el mismo motivo —`hotel(id)`—, con el
razonamiento escrito en su documentación.

**`puedeGestionar` no cambia.** Su excepción deliberada para el superadmin
(RF-059, RF-077) sigue siendo correcta, y ahora además deja de estar apoyada en
una constante: el administrador gestiona el hotel que tiene asignado.

### 4.3 El administrador sin hotel

Un administrador recién creado no tiene hotel. Es un estado real —existe entre
que la plataforma le crea la cuenta y el superadmin lo asigna— y las pantallas
del Bloque C no lo conocen porque hoy `getHotelAdministrado()` devuelve la
constante `"H1"` y nunca devuelve `null`. En cuanto la sesión lea la relación de
verdad, sí puede llegar un `null`.

Se resuelve en dos sitios, y solo en dos:

1. **La portada del administrador** (`AdminHomeViewModel`), que es donde el
   usuario aterriza al entrar. Enseña su estado vacío con un mensaje propio:
   "Todavía no tienes un hotel asignado." y la explicación de que un
   superadministrador tiene que asignárselo. Sin él, el administrador nuevo
   vería un error al entrar y no sabría que su cuenta está bien y que lo que
   falta es un paso ajeno.
2. **`exigirHotel`, en el repositorio**, con un mensaje propio cuando el
   identificador es `null`: "Todavía no tienes un hotel asignado." Es una sola
   línea y cubre el camino de error de las cuatro pantallas restantes
   —reservas, habitaciones, servicios y perfil—, que hoy mostrarían "Ese hotel
   no es el tuyo", falso para ese usuario.

No se toca ninguna de esas cuatro pantallas: su estado de error ya pinta el
mensaje que devuelve el repositorio, y basta con que el mensaje diga la verdad.
Repartir un guard por las cinco sería repetir la misma condición en cinco
ViewModel para el mismo dato.

---

## 5. Las pantallas del superadmin

### 5.1 Navegación

Cinco secciones: **Inicio · Usuarios · Conductores · Hoteles · Perfil**.

§47 pide seis cosas —usuarios, conductores, hoteles, aprobaciones, reportes,
logs— y una barra inferior aguanta cinco (§49). Las seis caben así:

- **Aprobaciones no es una sección**: es la lista de conductores pendientes, y
  vive en Conductores como filtro. Inicio la enseña como tarjeta, con el número
  de pendientes, y lleva a esa lista ya filtrada.
- **Reportes no es una sección**: RF-059 dice literalmente "reportes de reservas
  **por hotel**", así que el reporte de un hotel se abre desde su ficha, junto a
  sus datos y su administrador. Es el mismo reporte que ve el administrador
  —`GestionHotelRepository.ventas`—, que ya autoriza al superadmin por la
  excepción deliberada de `puedeGestionar`, así que no hace falta escribir una
  consulta nueva.
- **La bitácora no está en la barra**: se abre desde Inicio —que ya enseña los
  últimos movimientos y tiene el botón a la lista entera, que es lo que §5.2
  llama "la puerta a la única sección que no cabe"— y desde el perfil.

El orden de la barra va de lo que más se usa a lo que menos: los usuarios y los
conductores se tocan todas las semanas, los hoteles cuando entra uno nuevo, y el
perfil cuando hay que salir.

**El perfil entró después, y por una razón de paridad.** En la primera versión
de este reparto la quinta sección era la Bitácora y el superadministrador no
tenía perfil: cerraba sesión desde un icono en la cabecera de la portada, que
era la última pantalla del rol. Eso dejaba sin salida a quien estuviera en
Usuarios, Conductores u Hoteles —tenía que volver a Inicio—, y lo apartaba de
los otros dos roles, donde el perfil es una sección y el cerrar sesión vive en
ella. Ahora es igual en los tres. La sección que cedió su sitio fue la bitácora,
que ya tenía puerta doble desde la portada; el icono de la cabecera se retiró
—no hace falta tenerlo dos veces— y la pantalla de la bitácora ganó la flecha
de vuelta que tienen todas las que no son sección.

### 5.2 Inicio

La portada del rol, con la estética de herramienta que pide §47: métricas
arriba, pendientes después.

- Cuatro cifras: usuarios activos, conductores habilitados, hoteles publicados y
  hoteles en borrador.
- Tarjeta de pendientes: cuántos conductores esperan aprobación y cuántos
  hoteles están sin publicar. Si no hay ninguno, la tarjeta no se enseña: un
  cero que no se puede accionar es ruido.
- Fila "Bitácora de eventos", con el número de eventos registrados, que lleva a
  la bitácora. Es la puerta a la única sección que no cabe en la barra.

Los datos salen de una sola consulta, `resumen()`, por el mismo motivo que la
portada del administrador pide `resumen(hotelId)`: tres consultas darían tres
fotografías distintas del mismo momento.

### 5.3 Usuarios (RF-005, RF-006)

Lista densa de clientes y administradores de hotel con su rol, su correo y su
estado. Filtros por rol y por estado, en la fila de chips.

Cada fila lleva un interruptor para activar o desactivar la cuenta, con la
misma confirmación que el resto de la aplicación: desactivar deja a alguien
fuera y no puede depender de un toque accidental. Al desactivar se escribe en la
bitácora.

**Los superadministradores se ven pero no se tocan.** RF-005 pide consultar los
usuarios registrados y RF-006 nombra expresamente administradores de hotel,
taxistas y clientes. Dejar que un superadmin desactive a otro —o a sí mismo— es
una puerta que ningún requisito pide y que en una demostración se puede pulsar
por curiosidad y dejar la aplicación sin forma de entrar.

Los conductores **no aparecen aquí**, porque no son usuarios: son otro modelo y
tienen su propia sección. Es la misma separación que ya hace
`SessionManager.getUsuarioId()` contra `getConductorId()`.

El efecto de desactivar es el que ya existe: `MockAccesoRepository` rechaza el
acceso de una cuenta inactiva (RF-009). Una sesión ya abierta no se cierra sola
—no hay backend que empuje ese cambio—, así que la demostración de RF-009 es
desactivar y volver a entrar.

### 5.4 Conductores (RF-077, RF-078)

Lista de conductores con su documento, su vehículo, su valoración y su estado de
habilitación. Filtro "pendientes" para que la cola de aprobación sea una
pantalla y no una búsqueda.

La acción es habilitar o deshabilitar, con confirmación al deshabilitar. Es un
solo interruptor para las dos cosas porque es un solo dato: RF-077 pide aprobar
antes de prestar servicios y RF-006 pide poder desactivar un taxista. Aprobar y
reactivar son la misma operación sobre el mismo campo, y separarlos en dos
botones distintos solo daría dos formas de dejar el campo en el mismo sitio.

La consecuencia ya está escrita en el dominio: `Driver.isHabilitado()` decide si
el conductor puede entrar (`MockAccesoRepository`) y si puede recibir servicios
(`TaxiService.puedeAceptarlo`).

### 5.5 Hoteles (RF-007, RF-008)

Lista de todos los hoteles, publicados y en borrador, con un distintivo que lo
diga y con el nombre de su administrador —o "Sin administrador", que es la
señal de que falta un paso. Filtro por estado.

El botón de alta lleva al formulario. Cada fila abre la ficha del hotel.

### 5.6 Ficha del hotel (RF-008, RF-059)

Una pantalla con cuatro bloques:

1. **El hotel**: nombre, ubicación, descripción y fotografías, en modo lectura.
2. **Su administrador**, con la acción de asignar o cambiar. Abre una hoja con
   los administradores registrados; los que ya tienen hotel aparecen con el
   suyo, para no asignar a alguien que ya está en otro sitio.
3. **Publicación**: el estado y el interruptor de retirar o volver a publicar.
   Si el hotel no cumple el mínimo, el botón de publicar está inerte y la
   pantalla dice qué falta —cuántas fotografías, si no hay habitaciones—, que es
   la misma información que necesita el administrador.
4. **Reporte de reservas** (RF-059): el mismo reporte del administrador —
   periodos, ocupación e ingresos— para este hotel.

### 5.7 Alta de hotel (RF-007)

Una pantalla con dos bloques, siguiendo la composición de
`fragment_admin_hotel_datos.xml`: los datos en el primero y el resumen de lo que
va a pasar en el segundo.

Los datos son nombre, ciudad, distrito, dirección, coordenadas, descripción y
monto mínimo de taxi. **No se piden fotografías ni habitaciones**: el hotel nace
en borrador y completarlo es trabajo del administrador, que es exactamente el
flujo pedido. Pedirlas aquí sería inventar un formulario que el enunciado no
pide y que además contradice el reparto de requisitos —RF-012, RF-013 y RF-014
son del administrador de hotel—.

El hotel se crea **sin administrador**. Asignarlo es el paso siguiente y tiene
su sitio: la ficha del hotel. Meter las dos cosas en el mismo formulario
obligaría a decidir qué hacer si el alta se guarda y la asignación falla.

### 5.8 Bitácora (RF-120)

Los eventos registrados, del más reciente al más antiguo, con su marca de
tiempo, el usuario que los produjo, el tipo y el detalle. Filtro por tipo de
evento y búsqueda por usuario o detalle.

La lista llega entera del repositorio y **los filtros se aplican en el
ViewModel**, no en el repositorio. No es una excepción a la regla de que los
mocks viven fuera de las pantallas —el ViewModel no lee `MockData`, sigue
pidiendo por el repositorio—: es que un filtro por texto que viaje al
repositorio vuelve con los 400 ms de latencia simulada en cada tecla. La
bitácora es una lista en memoria y cabe entera.

RC-042 y RT-038 se respetan por construcción: el detalle se redacta en el
repositorio que lo escribe, y este bloque solo lo lee.

### 5.9 Perfil

El mismo molde de los perfiles del cliente (§40) y del administrador de hotel:
quién eres, tus datos y la sesión. Es de consulta, como los otros dos, porque no
hay a dónde mandar los cambios desde aquí.

Lo que lo hace el perfil de un superadministrador y no una copia no es el rol en
una etiqueta —eso también lo tiene el del administrador— sino la segunda
tarjeta. Donde el administrador enseña **el hotel que administra**, este enseña
**la plataforma que administra**: usuarios activos, conductores habilitados y
hoteles publicados, cada cifra leída como "parte de totales", más los
conductores que esperan aprobación, que es lo único que reclama una decisión
suya. Y debajo, la puerta a la auditoría.

Las cifras salen de `resumen()`, la misma consulta de la portada, y no de un
puñado de listas contadas en la pantalla: qué es un usuario activo o un hotel
publicado lo decide el dominio, y contarlo aquí sería tenerlo escrito dos veces.
Se piden por separado de la cuenta, y un fallo al leerlas deja la tarjeta con su
título y un aviso, en vez de vaciarla: un cero es una afirmación, y no sabemos
si la plataforma está vacía o si la consulta falló.

---

## 6. El lado del administrador (Bloque C)

Publicar vive donde vive el hotel. La operación es una sola
—`GestionHotelRepository.cambiarPublicacion(hotelId, publicado)`— y tiene dos
puertas: la del administrador, sobre su hotel, y la del superadmin, sobre
cualquiera. Las dos pasan por la misma comprobación de `puedeGestionar`, que ya
autoriza al superadmin a propósito.

Podría haberse puesto en el repositorio del superadmin y dejar al administrador
sin publicar, pero entonces el flujo pedido —el administrador completa y
publica— no se podría hacer, y la regla del mínimo estaría en un sitio y su
excepción en otro.

En la pantalla de hotel del administrador aparece un bloque de estado:

- **En borrador**: un aviso de que el hotel no es visible para los clientes, con
  la lista de lo que falta, y el botón **Publicar**.
- **Publicado**: el estado y la acción de retirarlo.

El aviso es lo que convierte el borrador en algo distinto de un error: el
administrador que entra y ve su hotel sin fotografías tiene que entender que eso
es el principio del trabajo y no un fallo de la aplicación.

---

## 7. Repositorios

### 7.1 `HotelRepository`: una consulta nueva

```java
/** El hotel que ese administrador tiene asignado, o null si no tiene ninguno. */
@Nullable
Hotel hotelDeAdministrador(@NonNull String usuarioId);
```

Sincrónica, como `hotel(id)` y por el mismo motivo.

### 7.2 `GestionHotelRepository`: una operación nueva

```java
void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                        @NonNull ResultCallback<Hotel> callback);
```

Comprueba `puedeGestionar`, exige `aptoParaPublicar()` cuando se publica, y
anota el cambio en la bitácora.

### 7.3 `SuperadminRepository`: el repositorio nuevo

```java
public interface SuperadminRepository {

    void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback);

    void usuarios(@NonNull ResultCallback<List<User>> callback);

    void cambiarActivo(@NonNull String usuarioId, boolean activo,
                       @NonNull ResultCallback<User> callback);

    void conductores(@NonNull ResultCallback<List<Driver>> callback);

    void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                            @NonNull ResultCallback<Driver> callback);

    void hoteles(@NonNull ResultCallback<List<Hotel>> callback);

    void administradores(@NonNull ResultCallback<List<User>> callback);

    void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback);

    void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                              @NonNull ResultCallback<Hotel> callback);

    void bitacora(@NonNull ResultCallback<List<LogEntry>> callback);
}
```

Las reglas que aplica, todas en la implementación y ninguna en quien llama —un
formulario se puede saltar, un repositorio no—:

- **`cambiarActivo`** rechaza desactivar a un superadministrador (RF-006) y
  escribe ACTIVACION o DESACTIVACION en la bitácora. La condición no se escribe
  dentro del repositorio sino en el modelo, en `User.esDesactivable()`, que es
  lo único de esta regla que una prueba de JVM puede alcanzar (§10.1).
- **`habilitarConductor`** escribe APROBACION en la bitácora.
- **`registrarHotel`** exige nombre, ciudad, distrito y coordenadas, y crea el
  hotel **sin publicar y sin administrador**. Escribe ACCION_ADMINISTRATIVA.
- **`asignarAdministrador`** exige que el usuario exista, tenga rol de
  administrador de hotel y no esté desactivado. Avisa en el mensaje si ese
  administrador ya tiene otro hotel, porque es un dato que el superadmin tiene
  que ver antes de confirmar.
- **`bitacora`** devuelve los eventos del más reciente al más antiguo.

`MockData.HOTELES` pasa de `unmodifiableList` a `ArrayList` mutable, como ya lo
son `USUARIOS`, `RESERVAS` y `TAXIS`: registrar un hotel (RF-007) es dar de alta
uno nuevo, y la lista donde viven tiene que poder crecer.

---

## 8. Componentes

El bloque no estrena biblioteca: §47 pide una estética de herramienta
profesional dentro del mismo sistema de diseño, y eso es exactamente lo que el
proyecto ya tiene.

| Necesidad | Pieza |
|---|---|
| Cifras de Inicio | `StatView` |
| Filtros | `ChipGroup` + `FilterChipView` |
| Filas de ajuste y puertas | `SettingRowView` |
| Listas | `RecyclerView` + `ListAdapter` con `DiffUtil` |
| Estados | `LoadingSkeletonView`, `EmptyStateView`, `ErrorStateView` |
| Cabecera | `AppHeaderView` |
| Distintivo de estado | `bg_badge_*` existentes |
| Confirmaciones | `AlertDialog`, como en el resto |
| Hojas | `BottomSheetDialogFragment`, como `QrSheet` y `ValidarCodigoSheet` |

`FilterChipView` está atado hoy a `Service` (`bind(Service)`), así que los
filtros de rol y de estado usan `Chip` directamente dentro de un `ChipGroup`,
como ya hace `fragment_admin_servicios.xml`. No se toca el componente: cambiarle
la API para que acepte texto suelto afectaría a las tres pantallas que ya lo
usan, y el proyecto ya tiene precedente de las dos formas.

Los distintivos de estado (Publicado / Borrador, Activo / Desactivado,
Habilitado / Pendiente) usan los `bg_badge_*` que ya existen: `success` para lo
que está bien, `warning` para lo que espera, `error` para lo que está fuera.

---

## 9. Datos sembrados

1. **Los diez hoteles quedan publicados**, marcados uno por uno. Sin esto el
   catálogo del cliente quedaría vacío de golpe: es el cambio con más riesgo del
   bloque, y lo protege una prueba.

2. **Los diez hoteles quedan con administrador.** Hoy solo H1 tiene uno, porque
   la relación no existía. Se siembran **dos administradores más** —U5 y U6— y
   los diez hoteles se reparten entre los tres, empezando por H1 con U2 para no
   mover nada de lo que ya funciona en el Bloque C. El reparto es de siembra y
   no significa nada más que eso, pero hace que la lista de hoteles enseñe tres
   nombres distintos en vez del mismo diez veces, y que "Sin administrador" sea
   un estado que solo tiene el hotel que se cree en la demostración del bloque.
   De paso, la lista de usuarios (RF-005) pasa de cinco filas a siete.

3. **Un administrador sin hotel.** El dueño de un hotel nuevo se comunicó con la
   plataforma y eligió a alguien; la cuenta existe y todavía no tiene hotel
   asignado. Sin él, RF-008 no se puede demostrar: no habría a quién asignar.

4. **D3 sigue pendiente de aprobación.** Es la cola de RF-077 y ya estaba así
   desde el Bloque D. Se le suma un cuarto conductor si hace falta que la lista
   de conductores no tenga una sola fila.

La demostración completa del bloque:

1. Entrar como superadmin → Inicio enseña las cifras y un conductor pendiente.
2. Conductores → aprobar a D3, y ver que el pendiente baja a cero.
3. Usuarios → desactivar una cuenta, y comprobar que esa cuenta ya no entra.
4. Hoteles → dar de alta un hotel: nace en borrador y sin administrador.
5. Su ficha → asignarle el administrador nuevo.
6. Entrar como ese administrador → su hotel está ahí, en borrador, con lo que
   falta por hacer.
7. Completarlo: cuatro fotografías y una habitación.
8. Publicar → entrar como cliente y encontrarlo en el buscador.
9. Volver como superadmin → retirarlo, y comprobar que desaparece del catálogo
   sin romper las reservas que ya existían.

---

## 10. Verificación

Además de compilar y de las pruebas, este bloque toca tres pantallas del
administrador y el catálogo del cliente, así que la comprobación en el emulador
no es opcional:

- `python3 tools/verificar_recursos.py`
- `./gradlew :app:assembleDebug` y `:app:testDebugUnitTest --rerun-tasks`,
  mirando la **marca de tiempo** del APK y del XML de resultados.
- Recorrido en el emulador de la demostración entera de §9, más el catálogo del
  cliente antes y después de publicar.
- **El Bloque C sigue sin verificar desde su propio bloque.** Este bloque lo
  toca, así que el recorrido del administrador —sus cinco pantallas, ahora con
  el bloque de publicación— se hace aquí y queda registrado.

### 10.1 Qué se puede probar y qué no

Las pruebas del proyecto son de JVM pura: el módulo solo declara `junit`, sin
Robolectric y sin `returnDefaultValues`. Eso deja fuera a los repositorios de
mock: `MockRepository` construye su `Handler` sobre `Looper.getMainLooper()`, y
en una prueba de JVM ese looper es `null`. Ninguna prueba del proyecto llama
hoy a un repositorio, y esta no va a ser la primera.

Así que las reglas se prueban **donde son puras**, y el resto se comprueba en
el emulador:

| Prueba | Qué protege |
|---|---|
| `PublicacionHotelTest` | Un hotel nace sin publicar y sin administrador; `aptoParaPublicar` exige fotos y habitaciones; retirar no tiene condiciones |
| `ReglasDeRolTest` | `User.esDesactivable` deja fuera al superadministrador (RF-006) |
| `MockDataTest` (ampliada) | Los diez hoteles publicados y con administrador; el administrador sin hotel; el conductor pendiente |

Las reglas que viven en los repositorios —que el catálogo esconda borradores,
que activar y desactivar registre en la bitácora, que el alta exija nombre,
ciudad, distrito y coordenadas, que la asignación exija un administrador
activo— se comprueban en el recorrido del emulador de §10.2, que es donde el
proyecto comprueba hoy las reglas del Bloque C.

### 10.2 El recorrido del emulador, paso a paso

1. Entrar como cliente: el catálogo enseña los diez hoteles publicados.
2. Entrar como superadmin → Hoteles: están los diez, todos publicados.
3. Alta de hotel con nombre, ciudad, distrito y coordenadas. El hotel nace
   **sin publicar** y **sin administrador**.
4. Volver al cliente: el hotel nuevo **no** aparece en la búsqueda, ni en
   recomendados, ni en el filtro por ciudad, ni en el selector de distritos.
5. Como superadmin, asignarle un administrador desde la ficha del hotel.
6. Entrar como ese administrador: la portada dice que el hotel no está
   publicado y qué le falta. Rellenar fotos y habitaciones y publicar.
7. Volver al cliente: el hotel nuevo ya aparece, y por la ciudad y el distrito
   que se le pusieron.
8. Como superadmin, retirarlo desde la ficha: el cliente deja de verlo, y una
   reserva que ya existiera sobre él se sigue pintando —`hotel(id)` no filtra.
9. Como superadmin: desactivar un cliente y comprobar que no puede volver a
   entrar; intentar desactivar a un superadministrador y comprobar que no se
   puede. Habilitar al conductor pendiente y ver la bitácora —que se abre desde
   la portada, no desde la barra— con los movimientos que dejaron esos actos.
10. Como superadmin: abrir **Perfil** desde la barra, ver la cuenta con su
    insignia de rol y las cifras de la plataforma, entrar a la auditoría desde
    su botón y volver con la flecha, y **cerrar la sesión desde ahí**: la
    aplicación vuelve a la pantalla de acceso.

---

## 11. Decisiones abiertas

- **La aprobación de hoteles.** `nav_superadmin.xml` decía en su comentario que
  el superadmin aprueba hoteles, y ningún requisito pide eso: RF-007 pide
  registrarlos. El comentario se corrige en este bloque. Queda abierto si el
  enunciado espera una cola de aprobación de hoteles además de la de
  conductores; hoy no está escrita en ningún RF.
- **El efecto de desactivar una sesión abierta.** Desactivar una cuenta impide
  que vuelva a entrar, pero no cierra la sesión que ya tiene abierta, porque no
  hay backend que empuje ese cambio. La demostración de RF-009 es desactivar y
  volver a entrar.
- **Un administrador puede tener más de un hotel.** El modelo lo permite —la
  relación vive en el hotel— pero la pantalla del administrador asume uno solo
  desde el Bloque C. Hoy no hay ningún requisito que pida lo contrario, y el
  mensaje de la asignación avisa cuando alguien ya tiene hotel.
