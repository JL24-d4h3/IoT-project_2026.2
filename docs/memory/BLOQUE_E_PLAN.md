# Bloque E — Superadministrador: plan de implementación

> **Para quien ejecuta:** usa `superpowers:executing-plans` tarea por tarea. Los
> pasos van en casillas (`- [ ]`) para poder seguirlos en orden.

**Objetivo:** cerrar §47 —el rol Superadministrador— con sus cinco secciones, y
con ellas RF-005, RF-006, RF-007, RF-008, RF-059 y RF-077/RF-078, incluido el
gobierno de la publicación del hotel que hoy no existe.

**Arquitectura:** el estado de publicación vive en el modelo (`Hotel.publicado`)
y la relación con el administrador también (`Hotel.administradorId`), no en la
sesión ni en una tabla aparte. Las consultas del cliente filtran los borradores
dentro del repositorio simulado; las pantallas nuevas del superadmin cuelgan de
un `SuperadminRepository` propio y publican su estado con `UiState`. La sesión
deja de devolver la constante `"H1"` y lee el hotel asignado.

**Stack:** Android nativo, Java 21, XML Views + ViewBinding, Navigation
Component, LiveData/ViewModel, JUnit 4. Sin backend: repositorios simulados
resueltos por `ServiceLocator`.

**Spec:** `docs/memory/BLOQUE_E_SUPERADMIN.md`

## Restricciones globales

- **Solo front end.** No hay backend ni autenticación real; todo dato sale de
  los repositorios simulados de `data/mock/` (§49).
- **§49: los mocks no entran en Activities, Fragments ni adaptadores.** Ninguna
  pantalla lee `MockData` ni llama a un repositorio: todo pasa por el ViewModel.
- **RC-042 / RT-038: la bitácora nunca guarda contraseñas ni secretos.** Los
  detalles que se escriben aquí son nombres y acciones, nada más.
- **Java siempre, Kotlin nunca (RT-002). XML Views siempre, Compose nunca (§3).**
  ViewBinding en todas las pantallas.
- **Nunca copiar la identidad de Booking** (§3, §4.1, reglas 28-30): ni logo, ni
  nombre, ni colores, ni textos, ni fotografías.
- **Las calificaciones son de 1 a 10** (regla 9). Nunca 5 estrellas.
- **Los commits solo se hacen si el usuario los autoriza.** Ningún paso de
  commit de este plan se ejecuta sin decirlo antes.
- **Colores, tipografía y espaciado del sistema de diseño** de
  `CONTEXTO_FRONTEND.md` §6: `colorPrimary`, `colorPrimaryContainer`,
  `colorAccent` solo en calificaciones, `colorTextDisabled`, `colorDivider`,
  `TextAppearance.App.*`, márgenes de pantalla de 16 dp (`screen_margin`).
- **Ninguna prueba de este plan puede llamar a un repositorio simulado.**
  `MockRepository` construye su `Handler` sobre `Looper.getMainLooper()`, que en
  una prueba de JVM es `null`. Lo que se prueba es el modelo y `MockData`; las
  reglas de los repositorios se comprueban en el emulador (spec §10.1).
- **Vocabulario de recursos — corregido durante la ejecución.** Los bloques XML
  de las tareas 9 a 15 se escribieron con nombres que este proyecto no tiene. Los
  reales son:
  - Texto: `TextAppearance.App.Display|H1|H2|H3|Body|BodySecondary|Caption|Label|Button|NumericEmphasis|Price|PriceLarge`. **No existen** `Subtitle1`, `Subtitle2`, `Body1` ni `Body2`.
  - Medidas: `space_xs|space_sm|space_md|space_base|space_lg|space_xl|space_2xl`, con guion bajo. **No existen** `spaceXs`, `spaceMd`, `radiusMd`.
  - Tarjetas: `style="@style/Widget.App.Card"` (y `.Interactive`), nunca `app:cardCornerRadius` ni `strokeColor` en línea.
  - Botones: `Widget.App.Button.Primary|Secondary|Text|Destructive` y `Widget.App.Button.Primary.Small` para acciones dentro de una tarjeta. Los `Widget.Material3.Button.*` solo valen como `parent` en `styles.xml`, no como `style` en un layout.
  - El estilo se declara con `style="..."`, no con `android:textAppearance="..."`.
  - Nombres del modelo corregidos: `Periodicidad.{DIA,MES,ANIO}` — no existe
    `MENSUAL`—, `Hotel.MIN_FOTOS`, `Hotel.getUbicacionCorta()`,
    `Hotel.aptoParaPublicar()`, `Hotel.getFotos()`, `Hotel.getHabitaciones()`.
  - Componentes que ya existen y las tareas reutilizan en vez de escribir a
    mano: `DataRowView.bind(@StringRes etiqueta, @Nullable CharSequence valor,
    @StringRes textoSiVacio)`, `StatView.setDato(@StringRes etiqueta,
    CharSequence valor)`, `RatingBadgeView.setRating(float)` y `PeriodoAdapter`
    de `ui/admin/reportes` con su `item_periodo_reporte.xml`.
  - Una hoja que comparte estado con su pantalla usa
    `ViewModelGrafo.de(this, R.id.<grafo>, XViewModel.class)`, nunca argumentos
    con arrays ni un escucha que se pierde al recrear la pantalla.
  - Las pantallas se llaman `Superadmin*` (`SuperadminHotelesFragment`,
    `SuperadminHotelViewModel`...), no `HotelesSaFragment` ni `HotelSaViewModel`:
    el sufijo `Sa` se quedó en los adaptadores (`CuentaSaAdapter`,
    `HotelSaAdapter`).

## Estructura de ficheros

**Nuevos — modelo y datos**

| Fichero | Responsabilidad |
|---|---|
| `models/ResumenSuperadmin.java` | Cifras de la plataforma y últimos eventos, en un solo objeto |

**Nuevos — repositorio**

| Fichero | Responsabilidad |
|---|---|
| `data/repository/SuperadminRepository.java` | La interfaz del panel: usuarios, conductores, hoteles y bitácora |
| `data/mock/MockSuperadminRepository.java` | Su implementación simulada y sus reglas |

**Nuevos — pantallas (`ui/superadmin/`)**

| Fichero | Responsabilidad |
|---|---|
| `home/SuperadminHomeFragment.java`, `home/SuperadminHomeViewModel.java` | §47 portada: las cifras de la plataforma y lo que espera atención |
| `usuarios/UsuariosSaFragment.java`, `usuarios/UsuariosSaViewModel.java`, `usuarios/CuentaSaAdapter.java` | RF-005, RF-006: listar cuentas y activarlas o desactivarlas |
| `conductores/ConductoresSaFragment.java`, `conductores/ConductoresSaViewModel.java`, `conductores/ConductorSaAdapter.java` | RF-077, RF-078: aprobar y reactivar conductores |
| `hoteles/HotelesSaFragment.java`, `hoteles/HotelesSaViewModel.java`, `hoteles/HotelSaAdapter.java` | Listar hoteles con su estado y su administrador |
| `hoteles/HotelSaFragment.java`, `hoteles/HotelSaViewModel.java`, `hoteles/AsignarAdministradorSheet.java`, `hoteles/AdministradorElegibleAdapter.java` | Ficha del hotel: datos, administrador, publicación y reporte (RF-059) |
| `hoteles/AltaHotelFragment.java`, `hoteles/AltaHotelViewModel.java` | RF-007: dar de alta un hotel, sin publicar y sin administrador |
| `bitacora/BitacoraFragment.java`, `bitacora/BitacoraViewModel.java`, `bitacora/BitacoraAdapter.java` | RF-118 a RF-120: consultar la bitácora |

**Nuevos — recursos**

`fragment_superadmin_home.xml`, `fragment_superadmin_usuarios.xml`,
`item_cuenta_sa.xml`, `fragment_superadmin_conductores.xml`,
`item_conductor_sa.xml`, `fragment_superadmin_hoteles.xml`, `item_hotel_sa.xml`,
`fragment_superadmin_hotel.xml`, `sheet_asignar_administrador.xml`,
`item_administrador_elegible.xml`, `fragment_superadmin_alta_hotel.xml`,
`fragment_superadmin_bitacora.xml`, `item_bitacora.xml`.

**Nuevos — pruebas**

`test/java/org/iot/project/models/PublicacionHotelTest.java`,
`test/java/org/iot/project/models/ReglasDeRolTest.java`.

**Modificados**

`models/Hotel.java`, `models/User.java`, `core/SessionManager.java`,
`core/ServiceLocator.java`, `core/Roles.java`, `data/mock/MockData.java`,
`data/mock/MockHotelRepository.java`, `data/mock/MockGestionHotelRepository.java`,
`data/repository/HotelRepository.java`,
`data/repository/GestionHotelRepository.java`,
`ui/admin/home/AdminHomeViewModel.java`, `ui/admin/home/AdminHomeFragment.java`,
`ui/admin/hotel/HotelDatosViewModel.java`, `ui/admin/hotel/HotelDatosFragment.java`,
`res/layout/fragment_admin_home.xml`, `res/layout/fragment_admin_hotel_datos.xml`,
`res/navigation/nav_superadmin.xml`, `res/menu/menu_bottom_nav_superadmin.xml`,
`res/values/strings.xml`, `test/java/org/iot/project/data/mock/MockDataTest.java`,
`docs/memory/CONTEXTO_FRONTEND.md`.

**Borrados**

`ui/common/PanelRolFragment.java` y `res/layout/fragment_panel_rol.xml`: existían
solo para decir que el panel del superadmin llegaría en la siguiente entrega.

---

## Tarea 1: El estado de publicación, en el modelo

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/models/Hotel.java`
- Crear: `app/src/test/java/org/iot/project/models/PublicacionHotelTest.java`

**Interfaces:**
- Produce: `Hotel.isPublicado()`, `Hotel.setPublicado(boolean)`,
  `Hotel.getAdministradorId()`, `Hotel.setAdministradorId(String)`,
  `Hotel.aptoParaPublicar()`.

- [ ] **Paso 1: escribir la prueba que falla**

```java
package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * RF-007, RF-013 y RF-014: un hotel no se ofrece al cliente hasta que esta
 * completo y alguien lo publica.
 */
public class PublicacionHotelTest {

    private static Hotel nuevo() {
        return new Hotel("HX", "Hotel de prueba", "Miraflores", "Lima");
    }

    /** Cuatro fotografias, que es el minimo de RF-013. */
    private static void conFotos(Hotel hotel) {
        for (int i = 0; i < Hotel.MIN_FOTOS; i++) {
            hotel.addFoto("https://ejemplo.test/foto" + i + ".jpg");
        }
    }

    private static void conHabitacion(Hotel hotel) {
        hotel.addHabitacion(new Room("HX-R1", "HX", "Doble", 240));
    }

    @Test
    public void unHotelNaceSinPublicar() {
        assertFalse(nuevo().isPublicado());
    }

    @Test
    public void unHotelNaceSinAdministrador() {
        assertNull(nuevo().getAdministradorId());
    }

    @Test
    public void sinFotografiasNoEsPublicable() {
        Hotel hotel = nuevo();
        conHabitacion(hotel);
        assertFalse(hotel.aptoParaPublicar());
    }

    @Test
    public void sinHabitacionesNoEsPublicable() {
        Hotel hotel = nuevo();
        conFotos(hotel);
        assertFalse(hotel.aptoParaPublicar());
    }

    @Test
    public void conFotosYHabitacionesEsPublicable() {
        Hotel hotel = nuevo();
        conFotos(hotel);
        conHabitacion(hotel);
        assertTrue(hotel.aptoParaPublicar());
    }

    /**
     * Retirar no tiene condiciones: un hotel publicado al que despues se le
     * quitan las fotos se puede retirar igual. Lo que no puede es volver a
     * publicarse sin arreglarlo, y eso lo impone {@code aptoParaPublicar}.
     */
    @Test
    public void unHotelIncompletoSePuedeRetirar() {
        Hotel hotel = nuevo();
        hotel.setPublicado(true);
        hotel.setPublicado(false);
        assertFalse(hotel.isPublicado());
    }

    @Test
    public void elAdministradorSePuedeAsignarYCambiar() {
        Hotel hotel = nuevo();
        hotel.setAdministradorId("U2");
        assertEquals("U2", hotel.getAdministradorId());
        hotel.setAdministradorId("U5");
        assertEquals("U5", hotel.getAdministradorId());
    }
}
```

Falta el `import static org.junit.Assert.assertEquals;` — añádelo con el resto.

- [ ] **Paso 2: ejecutar la prueba y ver que falla**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*PublicacionHotelTest*'
```
Esperado: **FALLA al compilar** — `isPublicado()`, `setPublicado(...)`,
`getAdministradorId()`, `setAdministradorId(...)` y `aptoParaPublicar()` no
existen.

- [ ] **Paso 3: implementar**

En `Hotel.java`, añade `import androidx.annotation.Nullable;` junto a los
`import java.util.*`, y los dos campos después de `private double longitud;`:

```java
    /** Si el hotel ya se ofrece en el catalogo del cliente (RF-007). */
    private boolean publicado;

    /** Administrador asignado, o {@code null} si todavia no tiene (RF-008). */
    @Nullable
    private String administradorId;
```

Y los métodos, justo antes de `getUbicacionCorta()`:

```java
    /** Si el hotel se ofrece en el catalogo del cliente (RF-007). */
    public boolean isPublicado() {
        return publicado;
    }

    /** Publica o retira el hotel. Retirar no tiene condiciones. */
    public void setPublicado(boolean publicado) {
        this.publicado = publicado;
    }

    /** Identificador del administrador asignado, o {@code null} si no tiene. */
    @Nullable
    public String getAdministradorId() {
        return administradorId;
    }

    public void setAdministradorId(@Nullable String administradorId) {
        this.administradorId = administradorId;
    }

    /**
     * Si el hotel reune lo minimo para ofrecerse al cliente.
     *
     * <p>Un hotel sin fotografias ni habitaciones no es publicable (RF-013,
     * RF-014): ofrecerlo seria enseñar una ficha que no dice nada. La regla vive
     * aqui y no en el repositorio porque es del hotel, no de quien lo guarda, y
     * asi la comprueban igual el administrador antes de publicar y el
     * superadministrador al mirar la ficha.
     */
    public boolean aptoParaPublicar() {
        return cumpleMinimoFotos() && !habitaciones.isEmpty();
    }
```

- [ ] **Paso 4: ejecutar la prueba y ver que pasa**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*PublicacionHotelTest*'
```
Esperado: **PASA**, 7 pruebas.

- [ ] **Paso 5: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/models/Hotel.java \
        app/src/test/java/org/iot/project/models/PublicacionHotelTest.java
git commit -m "feat(modelo): el hotel nace sin publicar y sabe si es publicable"
```

---

## Tarea 2: La regla de rol que protege al superadministrador

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/models/User.java`
- Crear: `app/src/test/java/org/iot/project/models/ReglasDeRolTest.java`

**Interfaces:**
- Produce: `User.esDesactivable()`.

- [ ] **Paso 1: escribir la prueba que falla**

```java
package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** RF-006: el panel desactiva cuentas, pero no la de un superadministrador. */
public class ReglasDeRolTest {

    @Test
    public void unSuperadministradorNoSeDesactiva() {
        assertFalse(new User("U3", "Ana", "Ferreyra", Role.SUPERADMIN).esDesactivable());
    }

    @Test
    public void unClienteSeDesactiva() {
        assertTrue(new User("U1", "Lucía", "Quispe", Role.CLIENTE).esDesactivable());
    }

    @Test
    public void unAdministradorDeHotelSeDesactiva() {
        assertTrue(new User("U2", "Martín", "Rojas", Role.ADMIN_HOTEL).esDesactivable());
    }
}
```

- [ ] **Paso 2: ejecutar la prueba y ver que falla**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*ReglasDeRolTest*'
```
Esperado: **FALLA al compilar** — `esDesactivable()` no existe.

- [ ] **Paso 3: implementar, y de paso borrar el campo muerto**

En `User.java`, junto a `isActivo()`:

```java
    /**
     * Si esta cuenta se puede desactivar desde el panel (RF-006).
     *
     * <p>Un superadministrador no: desactivarlo dejaria la plataforma sin nadie
     * que pueda volver a activarlo. La regla vive en el modelo y no en el
     * repositorio porque describe al usuario, y asi la puede comprobar una
     * prueba sin levantar el repositorio entero.
     */
    public boolean esDesactivable() {
        return rol != Role.SUPERADMIN;
    }
```

Y **borra** el campo `aprobado`, su `isAprobado()` y su `setAprobado(...)`:
ninguna clase fuera de `User` los usa, y su comentario de clase afirma que
"`aprobado` solo aplica a conductores" —falso, porque un conductor es un
`Driver`, no un `User`—. Actualiza el comentario de clase:

```java
/**
 * Usuario del sistema en cualquiera de los cuatro roles (§38).
 *
 * <p>Un usuario deshabilitado no puede acceder a las funcionalidades
 * protegidas (RF-009). Los conductores no son usuarios: se habilitan aparte
 * (RF-077, RT-014) y su estado vive en {@link Driver}.
 */
```

- [ ] **Paso 4: ejecutar las pruebas y ver que pasan**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*ReglasDeRolTest*' --tests '*MockDataTest*'
```
Esperado: **PASAN**. Si `MockDataTest` fallara aquí, es que algo usaba
`aprobado`: búscalo con
`grep -rn "isAprobado\|setAprobado" app/src` antes de seguir.

- [ ] **Paso 5: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/models/User.java \
        app/src/test/java/org/iot/project/models/ReglasDeRolTest.java
git commit -m "feat(modelo): el superadministrador no es desactivable (RF-006)"
```

---

## Tarea 3: Los datos de la demostración

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/data/mock/MockData.java:136-151`
  (bloque `static` y `crearHoteles`), `MockData.java:453` (`crearUsuarios`)
- Modificar: `app/src/test/java/org/iot/project/data/mock/MockDataTest.java`

**Interfaces:**
- Consume: `Hotel.setPublicado`, `Hotel.setAdministradorId` (tarea 1).
- Produce: `MockData.HOTELES` pasa a ser **mutable**; existen los usuarios
  `U4` (administrador sin hotel), `U5` y `U6` (administradores de hotel).

- [ ] **Paso 1: escribir las pruebas que fallan**

Añade al final de `MockDataTest.java`, antes de la última llave:

```java
    /**
     * RF-007: los diez hoteles de la demostracion ya pasaron por el proceso de
     * publicacion. Si nacieran sin publicar —que es como nacen— el catalogo del
     * cliente quedaria vacio.
     */
    @Test
    public void losHotelesDeEjemploEstanPublicados() {
        assertFalse("No hay hoteles de ejemplo", MockData.HOTELES.isEmpty());
        for (Hotel hotel : MockData.HOTELES) {
            assertTrue(hotel.getId() + " no esta publicado", hotel.isPublicado());
        }
    }

    /** RF-008: un hotel publicado sin administrador es un hotel que nadie atiende. */
    @Test
    public void losHotelesDeEjemploTienenAdministrador() {
        for (Hotel hotel : MockData.HOTELES) {
            String administradorId = hotel.getAdministradorId();
            assertNotNull(hotel.getId() + " no tiene administrador", administradorId);
            User administrador = MockData.usuario(administradorId);
            assertNotNull(hotel.getId() + " apunta a un administrador que no existe",
                    administrador);
            assertEquals(hotel.getId() + " lo administra alguien que no es administrador",
                    Role.ADMIN_HOTEL, administrador.getRol());
        }
    }

    /**
     * El estado que RF-008 tiene que poder resolver: existe un administrador
     * esperando hotel, que es lo que el superadministrador va a asignar.
     */
    @Test
    public void hayUnAdministradorSinHotel() {
        int sinHotel = 0;
        for (User usuario : MockData.USUARIOS) {
            if (usuario.getRol() != Role.ADMIN_HOTEL) {
                continue;
            }
            boolean tieneHotel = false;
            for (Hotel hotel : MockData.HOTELES) {
                if (usuario.getId().equals(hotel.getAdministradorId())) {
                    tieneHotel = true;
                    break;
                }
            }
            if (!tieneHotel) {
                sinHotel++;
            }
        }
        assertEquals("La demostracion necesita exactamente un administrador sin hotel",
                1, sinHotel);
    }

    /** El hotel que se da de alta (RF-007) tiene donde guardarse. */
    @Test
    public void laListaDeHotelesEsMutable() {
        Hotel borrador = new Hotel("HTEST", "Hotel de prueba", "Cusco", "Cusco");
        try {
            MockData.HOTELES.add(borrador);
            assertSame(borrador, MockData.hotel("HTEST"));
        } finally {
            MockData.HOTELES.remove(borrador);
        }
    }
```

Añade los `import static` que falten (`assertFalse`, `assertNotNull`,
`assertSame`, `assertTrue`, `assertEquals`) y los imports de `Hotel`, `User` y
`Role` si no están ya.

- [ ] **Paso 2: ejecutar y ver que falla**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*MockDataTest*'
```
Esperado: **FALLA**. `losHotelesDeEjemploEstanPublicados` falla porque ningún
hotel está publicado; `hayUnAdministradorSinHotel` falla porque hay cero;
`laListaDeHotelesEsMutable` lanza `UnsupportedOperationException`.

- [ ] **Paso 3: hacer mutable la lista**

En el bloque `static` de `MockData`, sustituye la línea 138:

```java
        // Mutable: dar de alta un hotel (RF-007) es añadir uno nuevo, y la lista
        // donde viven tiene que poder crecer.
        HOTELES = new ArrayList<>(crearHoteles());
```

- [ ] **Paso 4: publicar los diez y repartir los administradores**

Al final de `crearHoteles()`, justo antes de `return hoteles;`:

```java
        // RF-007: un hotel nace sin publicar, y los diez de la demostracion ya
        // pasaron por el proceso. Se marcan uno por uno —y no con un bucle— para
        // que el dia que se añada un hotel al catalogo de ejemplo haya que
        // responder a proposito la pregunta "¿ya esta publicado?".
        h1.setPublicado(true);
        h2.setPublicado(true);
        h3.setPublicado(true);
        h4.setPublicado(true);
        h5.setPublicado(true);
        h6.setPublicado(true);
        h7.setPublicado(true);
        h8.setPublicado(true);
        h9.setPublicado(true);
        h10.setPublicado(true);

        // RF-008: cada hotel tiene quien lo lleve. Se reparten entre los tres
        // administradores empezando por U2, que es el que ya administraba H1:
        // asi nada de lo construido en el Bloque C cambia de dueño.
        String[] administradores = {"U2", "U5", "U6"};
        for (int i = 0; i < hoteles.size(); i++) {
            hoteles.get(i).setAdministradorId(administradores[i % administradores.length]);
        }

        return hoteles;
```

**Comprueba antes** que `h1`…`h10` están declarados en el cuerpo del método y no
dentro de un bloque: si alguno viviera dentro de un `{ }`, muévelo fuera o
sustituye estas líneas por un recorrido con `hoteles.get(i)`.

- [ ] **Paso 5: sembrar los tres administradores**

En `crearUsuarios()`, después del segundo cliente (`huesped`) y antes del
`return`:

```java
        // Dos administradores mas (RF-008): con diez hoteles y un solo
        // administrador, la lista de usuarios de RF-005 tendria una sola fila de
        // ese rol y asignar seria siempre la misma operacion.
        User segundoAdmin = new User("U5", "Iván", "Paredes Nieto", Role.ADMIN_HOTEL);
        segundoAdmin.setEmail("i.paredes@estadia.pe");
        segundoAdmin.setTelefono("+51 934 776 210");
        segundoAdmin.withDocumento("DNI", "43218907");
        segundoAdmin.withFoto("https://loremflickr.com/200/200/portrait,man?lock=9005");

        User tercerAdmin = new User("U6", "Carmen", "Zevallos Ríos", Role.ADMIN_HOTEL);
        tercerAdmin.setEmail("c.zevallos@estadia.pe");
        tercerAdmin.setTelefono("+51 967 330 118");
        tercerAdmin.withDocumento("DNI", "44102376");
        tercerAdmin.withFoto("https://loremflickr.com/200/200/portrait,woman?lock=9006");

        // Y uno sin hotel: es el estado que RF-008 existe para resolver, y sin
        // el no habria forma de demostrar la asignacion desde cero.
        User adminSinHotel = new User("U4", "Rocío", "Vargas Lira", Role.ADMIN_HOTEL);
        adminSinHotel.setEmail("r.vargas@estadia.pe");
        adminSinHotel.setTelefono("+51 921 554 883");
        adminSinHotel.withDocumento("DNI", "41887255");
        adminSinHotel.withFoto("https://loremflickr.com/200/200/portrait,woman?lock=9007");

        return Arrays.asList(cliente, admin, superadmin, huesped,
                segundoAdmin, tercerAdmin, adminSinHotel);
```

- [ ] **Paso 6: ejecutar las pruebas y ver que pasan**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
```
Esperado: **PASAN todas**, incluidas las que ya existían. Las cuentas de
demostración no cambian: U2 sigue siendo el primer administrador activo y H1
sigue siendo suyo.

- [ ] **Paso 7: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/data/mock/MockData.java \
        app/src/test/java/org/iot/project/data/mock/MockDataTest.java
git commit -m "feat(datos): los diez hoteles publicados, con administrador, y uno libre"
```

---

## Tarea 4: El catálogo del cliente esconde los borradores

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/data/mock/MockHotelRepository.java`
- Modificar: `app/src/test/java/org/iot/project/data/mock/MockDataTest.java`

**Interfaces:**
- Consume: `Hotel.isPublicado()` (tarea 1).
- Produce: `MockData.hotelesPublicados()` — la lista que usan las seis consultas
  del cliente.

- [ ] **Paso 1: escribir la prueba que falla**

En `MockDataTest.java`:

```java
    /**
     * RF-007 y el filtro del catalogo: un hotel sin publicar no se ofrece, y
     * las seis consultas del cliente miran la misma lista.
     */
    @Test
    public void elCatalogoDelClienteSoloTieneHotelesPublicados() {
        Hotel borrador = new Hotel("HBORRADOR", "Todavía sin publicar", "Cusco", "Cusco");
        MockData.HOTELES.add(borrador);
        try {
            List<Hotel> publicados = MockData.hotelesPublicados();
            assertFalse("El borrador no deberia ofrecerse", publicados.contains(borrador));
            assertEquals(MockData.HOTELES.size() - 1, publicados.size());
            // Y sigue existiendo para quien lo pida por identificador: una
            // reserva ya hecha sobre un hotel retirado tiene que poder pintarse.
            assertSame(borrador, MockData.hotel("HBORRADOR"));
        } finally {
            MockData.HOTELES.remove(borrador);
        }
    }
```

- [ ] **Paso 2: ejecutar y ver que falla**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --tests '*MockDataTest*'
```
Esperado: **FALLA al compilar** — `hotelesPublicados()` no existe.

- [ ] **Paso 3: implementar en MockData**

Junto a `ciudades()` y `distritos()`:

```java
    /**
     * Los hoteles que el cliente puede ver (RF-007).
     *
     * <p>Es la lista que consultan todas las busquedas del catalogo. Existe aqui
     * y no repetida en cada consulta porque el dia que la regla de publicacion
     * cambie —o se añada otra condicion— tiene que cambiar en un solo sitio.
     *
     * <p>No la usa {@link #hotel(String)}: las listas que solo guardan el
     * identificador —reservas, taxis, bitacora— necesitan el nombre para
     * pintarse aunque el hotel se haya retirado.
     */
    public static List<Hotel> hotelesPublicados() {
        List<Hotel> publicados = new ArrayList<>();
        for (Hotel hotel : HOTELES) {
            if (hotel.isPublicado()) {
                publicados.add(hotel);
            }
        }
        return publicados;
    }
```

Y haz que `ciudades()`, `distritos()` y `distritosDe(String)` recorran
`hotelesPublicados()` en vez de `HOTELES`: un distrito que solo tiene hoteles
sin publicar no debe aparecer en el selector de filtros.

- [ ] **Paso 4: que las consultas del cliente la usen**

En `MockHotelRepository`, cambia el recorrido de `MockData.HOTELES` por
`MockData.hotelesPublicados()` en **`buscar`**, **`recomendados`**,
**`enCiudad`** y **`precioMaximo`**. Y sustituye las dos consultas sincronas que
leen el catálogo entero:

```java
    @Override
    public List<String> ciudadesDisponibles() {
        return MockData.ciudades();
    }

    @Override
    @NonNull
    public List<String> distritosDisponibles() {
        return MockData.distritos();
    }
```
se quedan **igual**: `MockData.ciudades()` y `MockData.distritos()` ya filtran
por publicación tras el paso 3. No las toques dos veces.

`obtener(...)`, `hotel(...)`, `resenas(...)` y `habitacionesDisponibles(...)` no
se tocan: `hotel(id)` tiene que seguir devolviendo un hotel retirado, y las
otras dos cuelgan de un identificador que ya se tiene.

- [ ] **Paso 5: ejecutar y ver que pasa**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
```
Esperado: **PASAN todas**.

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/data/mock/MockData.java \
        app/src/main/java/org/iot/project/data/mock/MockHotelRepository.java \
        app/src/test/java/org/iot/project/data/mock/MockDataTest.java
git commit -m "feat(catalogo): los hoteles sin publicar no se ofrecen al cliente"
```

---

## Tarea 5: El hotel de un administrador

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/data/repository/HotelRepository.java`
- Modificar: `app/src/main/java/org/iot/project/data/mock/MockHotelRepository.java`

**Interfaces:**
- Produce: `HotelRepository.hotelDeAdministrador(String usuarioId)` — la consume
  la tarea 6.

- [ ] **Paso 1: declarar la consulta**

En `HotelRepository`, junto a `hotel(String)` y con el mismo razonamiento:

```java
    /**
     * Hotel que administra un usuario, o {@code null} si no administra ninguno.
     *
     * <p>Es sincrona por el mismo motivo que {@link #hotel(String)}: la llaman
     * las pantallas del administrador en cada arranque, y una espera simulada de
     * 400 ms solo serviria para dejar la portada en esqueleto por un dato que ya
     * esta en memoria.
     */
    @Nullable
    Hotel hotelDeAdministrador(@NonNull String usuarioId);
```

Si el fichero no importa `androidx.annotation.Nullable`, añádelo.

- [ ] **Paso 2: implementarla**

En `MockHotelRepository`, junto a `hotel(String)`:

```java
    @Override
    @Nullable
    public Hotel hotelDeAdministrador(@NonNull String usuarioId) {
        for (Hotel hotel : MockData.HOTELES) {
            if (usuarioId.equals(hotel.getAdministradorId())) {
                return hotel;
            }
        }
        return null;
    }
```

Recorre `MockData.HOTELES` y no `hotelesPublicados()`: un administrador cuyo
hotel está sin publicar sigue siendo su administrador, y es justo quien tiene
que publicarlo.

- [ ] **Paso 3: compilar**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
```
Esperado: **BUILD SUCCESSFUL**.

- [ ] **Paso 4: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/data/repository/HotelRepository.java \
        app/src/main/java/org/iot/project/data/mock/MockHotelRepository.java
git commit -m "feat(hoteles): consulta del hotel que administra un usuario (RF-008)"
```

---

## Tarea 6: La sesión lee el hotel asignado

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/core/SessionManager.java:44,125-146`
- Modificar: `app/src/main/java/org/iot/project/data/mock/MockGestionHotelRepository.java:593`
- Modificar: `app/src/main/java/org/iot/project/ui/admin/home/AdminHomeViewModel.java`
- Modificar: `app/src/main/java/org/iot/project/ui/admin/home/AdminHomeFragment.java`
- Modificar: `app/src/main/res/layout/fragment_admin_home.xml`
- Modificar: `app/src/main/res/values/strings.xml`
- Modificar: `app/src/test/java/org/iot/project/data/mock/MockDataTest.java`

**Interfaces:**
- Consume: `HotelRepository.hotelDeAdministrador` (tarea 5).
- Produce: `SessionManager.getHotelAdministrado()` devuelve el hotel asignado y
  `null` si no hay ninguno. **Desaparece la constante
  `SessionManager.HOTEL_ADMINISTRADO`.**

- [ ] **Paso 1: borrar la constante y leer el hotel de verdad**

En `SessionManager`, elimina el bloque

```java
    /** Hotel que administra el usuario con rol de administrador. */
    public static final String HOTEL_ADMINISTRADO = "H1";
```

y sustituye `getHotelAdministrado()` por:

```java
    /**
     * Hotel que administra la sesion activa, o {@code null} si su rol no
     * administra ninguno.
     *
     * <p>Solo hay un hotel administrado por sesion porque asi es el modelo: un
     * administrador pertenece a un hotel, no a una lista (RF-008).
     *
     * <p>La relacion se lee del repositorio y no de {@code MockData} para que el
     * dia que exista un backend solo cambie el repositorio (§49). Y se lee de
     * forma sincrona —igual que {@code hoteles().hotel(id)}— porque las cinco
     * pantallas del administrador la consultan al arrancar y ninguna esta
     * escrita para esperar una respuesta.
     *
     * <p>Puede devolver {@code null} aunque el rol sea el de administrador: una
     * cuenta recien creada todavia no tiene hotel asignado, y ese estado es real
     * hasta que un superadministrador se lo asigne (RF-008).
     */
    @Nullable
    public static String getHotelAdministrado() {
        if (getRolActivo() != Role.ADMIN_HOTEL) {
            return null;
        }
        Hotel hotel = ServiceLocator.hoteles()
                .hotelDeAdministrador(getUsuarioIdSeguro());
        return hotel != null ? hotel.getId() : null;
    }
```

Añade los imports `org.iot.project.models.Hotel`.

Y en `puedeGestionar`, cambia la última línea:

```java
        return rol == Role.ADMIN_HOTEL && hotelId.equals(getHotelAdministrado());
```

- [ ] **Paso 2: que el administrador sin hotel reciba un mensaje que sea verdad**

En `MockGestionHotelRepository.exigirHotel`, al principio:

```java
    private Hotel exigirHotel(String hotelId) {
        if (hotelId == null) {
            // Un administrador recien creado todavia no tiene hotel: RF-008 lo
            // resuelve asignandole uno, y hasta entonces esta es la verdad.
            throw new IllegalStateException(
                    "Todavía no tienes un hotel asignado. Un superadministrador tiene que asignarte uno.");
        }
        if (!SessionManager.puedeGestionar(hotelId)) {
            throw new IllegalStateException("Ese hotel no es el tuyo.");
        }
        ...
```

- [ ] **Paso 3: corregir las pruebas que citaban la constante**

En `MockDataTest.java`, las líneas 249, 282 y 323 nombran
`SessionManager.HOTEL_ADMINISTRADO` dentro de un mensaje de aserción. Cambia el
texto por `"H1"` (o por una frase sin el nombre: `"el hotel del administrador de
demostración"`). No cambies la aserción, solo el mensaje.

- [ ] **Paso 4: la portada del administrador, cuando no hay hotel**

En `AdminHomeViewModel.pedir()`, sustituye

```java
            contenido.setValue(UiState.<Contenido>error(
                    "Esta pantalla es para el administrador de un hotel."));
```
por

```java
            // Un administrador sin hotel no es un error: su cuenta esta bien y lo
            // que falta es un paso ajeno (RF-008). Se publica como vacio para que
            // la portada lo explique en lugar de ofrecer un reintento que no
            // arregla nada.
            contenido.setValue(UiState.empty());
```

En `fragment_admin_home.xml`, dentro del `FrameLayout` que contiene
`admin_esqueleto` y `admin_error` (alrededor de la línea 273), añade un tercer
hijo **después** de `admin_error`:

```xml
        <org.iot.project.ui.components.EmptyStateView
            android:id="@+id/admin_vacio"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:visibility="gone"
            tools:visibility="visible" />
```

En `AdminHomeFragment`, añade el caso y el parámetro:

```java
            case EMPTY:
                mostrar(false, false, false);
                binding.adminVacio.setVisibility(View.VISIBLE);
                binding.adminVacio.conIcono(R.drawable.ic_info)
                        .conTitulo(R.string.admin_sin_hotel_titulo)
                        .conMensaje(R.string.admin_sin_hotel_mensaje);
                break;
```

y dentro de `mostrar(...)`:

```java
        binding.adminVacio.setVisibility(View.GONE);
```

En `strings.xml`:

```xml
    <string name="admin_sin_hotel_titulo">Todavía no tienes un hotel</string>
    <string name="admin_sin_hotel_mensaje">Un superadministrador tiene que asignarte uno. Cuando lo haga, aquí verás el resumen de tu hotel.</string>
```

- [ ] **Paso 5: compilar y ejecutar las pruebas**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
```
Esperado: **BUILD SUCCESSFUL** y **PASAN todas**. El compilador delatará
cualquier sitio que siguiera usando `SessionManager.HOTEL_ADMINISTRADO`; si
aparece alguno fuera de las pruebas, es un lector del hotel que hay que dejar
como está (lee `getHotelAdministrado()`) o corregir.

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/core/SessionManager.java \
           app/src/main/java/org/iot/project/data/mock/MockGestionHotelRepository.java \
           app/src/main/java/org/iot/project/ui/admin/home \
           app/src/main/res/layout/fragment_admin_home.xml \
           app/src/main/res/values/strings.xml \
           app/src/test/java/org/iot/project/data/mock/MockDataTest.java
git commit -m "feat(sesion): el hotel administrado sale del hotel, no de una constante (RF-008)"
```

---

## Tarea 7: Publicar y retirar, una sola operación

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/data/repository/GestionHotelRepository.java`
- Modificar: `app/src/main/java/org/iot/project/data/mock/MockGestionHotelRepository.java`

**Interfaces:**
- Produce: `GestionHotelRepository.cambiarPublicacion(String hotelId, boolean publicado, ResultCallback<Hotel>)`.
  La consumen la tarea 15 (administrador) y la 12 (ficha del superadmin).

- [ ] **Paso 1: declarar la operación**

En `GestionHotelRepository`, junto a `actualizarDatos(...)`:

```java
    /**
     * Publica el hotel o lo retira del catalogo (RF-007).
     *
     * <p>Publicar exige que el hotel este completo —fotografias y habitaciones—;
     * retirar no tiene condiciones. La regla la aplica el repositorio y no quien
     * llama: un formulario se puede saltar, un repositorio no.
     */
    void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                            @NonNull ResultCallback<Hotel> callback);
```

- [ ] **Paso 2: implementarla**

En `MockGestionHotelRepository`, junto a `actualizarDatos(...)`:

```java
    @Override
    public void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                                   @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            if (publicado && !hotel.aptoParaPublicar()) {
                throw new IllegalStateException(loQueFalta(hotel));
            }
            if (hotel.isPublicado() == publicado) {
                // Nada que cambiar: no se registra un movimiento que no ocurrio.
                return hotel;
            }
            hotel.setPublicado(publicado);
            registrar(publicado
                    ? "Se publicó " + hotel.getNombre() + "."
                    : "Se retiró " + hotel.getNombre() + " del catálogo.");
            return hotel;
        });
    }

    /**
     * Que le falta al hotel para poder publicarse.
     *
     * <p>Se enumera lo que falta y no se dice un "no se puede" a secas: el
     * administrador tiene delante la pantalla donde arreglarlo.
     */
    private static String loQueFalta(Hotel hotel) {
        boolean faltanFotos = !hotel.cumpleMinimoFotos();
        boolean faltanHabitaciones = hotel.getHabitaciones().isEmpty();
        if (faltanFotos && faltanHabitaciones) {
            return "Para publicar el hotel faltan fotografías y habitaciones.";
        }
        if (faltanFotos) {
            return "Para publicar el hotel faltan fotografías.";
        }
        return "Para publicar el hotel falta al menos una habitación.";
    }
```

`registrar(String)` ya existe en esta clase (línea ~632) y escribe
`ACCION_ADMINISTRATIVA` con el nombre del usuario en sesión.

- [ ] **Paso 3: compilar**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
```
Esperado: **BUILD SUCCESSFUL**.

- [ ] **Paso 4: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/data/repository/GestionHotelRepository.java \
        app/src/main/java/org/iot/project/data/mock/MockGestionHotelRepository.java
git commit -m "feat(hoteles): publicar y retirar el hotel, en una sola operacion"
```

---

## Tarea 8: El repositorio del superadministrador

**Ficheros:**
- Crear: `app/src/main/java/org/iot/project/models/ResumenSuperadmin.java`
- Crear: `app/src/main/java/org/iot/project/data/repository/SuperadminRepository.java`
- Crear: `app/src/main/java/org/iot/project/data/mock/MockSuperadminRepository.java`
- Modificar: `app/src/main/java/org/iot/project/core/ServiceLocator.java`

**Interfaces:**
- Consume: `User.esDesactivable()` (tarea 2), `MockData.hotelesPublicados()` (tarea 4).
- Produce: `ServiceLocator.superadmin()`; el contrato entero de
  `SuperadminRepository` que consumen las tareas 9 a 13.

- [ ] **Paso 1: el resumen**

```java
package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Lo que el superadministrador ve de un vistazo (§47).
 *
 * <p>Las cifras vienen contadas y no como listas para que la portada no tenga
 * que cruzarlas: contar en la pantalla es repartir la regla —que es un usuario
 * activo, que es un conductor habilitado— entre el repositorio y el fragment.
 */
public final class ResumenSuperadmin {

    private final int usuariosActivos;
    private final int usuariosTotales;
    private final int conductoresHabilitados;
    private final int conductoresTotales;
    private final int hotelesPublicados;
    private final int hotelesTotales;
    private final int hotelesSinAdministrador;
    private final List<LogEntry> ultimos;

    public ResumenSuperadmin(int usuariosActivos, int usuariosTotales,
                             int conductoresHabilitados, int conductoresTotales,
                             int hotelesPublicados, int hotelesTotales,
                             int hotelesSinAdministrador, @NonNull List<LogEntry> ultimos) {
        this.usuariosActivos = usuariosActivos;
        this.usuariosTotales = usuariosTotales;
        this.conductoresHabilitados = conductoresHabilitados;
        this.conductoresTotales = conductoresTotales;
        this.hotelesPublicados = hotelesPublicados;
        this.hotelesTotales = hotelesTotales;
        this.hotelesSinAdministrador = hotelesSinAdministrador;
        this.ultimos = Collections.unmodifiableList(ultimos);
    }

    public int getUsuariosActivos() {
        return usuariosActivos;
    }

    public int getUsuariosTotales() {
        return usuariosTotales;
    }

    public int getConductoresHabilitados() {
        return conductoresHabilitados;
    }

    public int getConductoresTotales() {
        return conductoresTotales;
    }

    public int getHotelesPublicados() {
        return hotelesPublicados;
    }

    public int getHotelesTotales() {
        return hotelesTotales;
    }

    public int getHotelesSinAdministrador() {
        return hotelesSinAdministrador;
    }

    /** Cuentas desactivadas: ninguno de ellos puede volver a entrar (RF-009). */
    public int getUsuariosInactivos() {
        return usuariosTotales - usuariosActivos;
    }

    /** Conductores que esperan aprobacion (RF-077). */
    public int getConductoresPendientes() {
        return conductoresTotales - conductoresHabilitados;
    }

    /** Hoteles que todavia no se ofrecen al cliente. */
    public int getHotelesSinPublicar() {
        return hotelesTotales - hotelesPublicados;
    }

    public boolean hayConductoresPendientes() {
        return getConductoresPendientes() > 0;
    }

    /** Si hay algo que el superadministrador tenga que resolver. */
    public boolean hayPendientes() {
        return hayConductoresPendientes() || hotelesSinAdministrador > 0;
    }

    /** Los ultimos movimientos de la bitacora, del mas reciente al mas antiguo. */
    @NonNull
    public List<LogEntry> getUltimos() {
        return ultimos;
    }
}
```

- [ ] **Paso 2: la interfaz**

```java
package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.User;

import java.util.List;

/**
 * Panel del superadministrador (§47): RF-005, RF-006, RF-007, RF-008, RF-059,
 * RF-077, RF-078 y RF-118 a RF-120.
 *
 * <p>Es un repositorio propio y no un puñado de consultas repartidas entre los
 * que ya existen porque las reglas que aplica son suyas y de nadie mas: quien
 * puede desactivarse, que hace falta para dar de alta un hotel, a quien se le
 * puede asignar uno. Repartidas por ahi, la proxima pantalla que las necesite
 * las escribiria otra vez.
 */
public interface SuperadminRepository {

    /** Cifras de la plataforma y ultimos movimientos, en una sola llamada. */
    void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback);

    /** RF-005: cuentas de cliente, administrador y superadministrador. */
    void usuarios(@NonNull ResultCallback<List<User>> callback);

    /** RF-006: activar o desactivar una cuenta. */
    void cambiarActivo(@NonNull String usuarioId, boolean activo,
                       @NonNull ResultCallback<User> callback);

    /** RF-077: conductores, habilitados y pendientes. */
    void conductores(@NonNull ResultCallback<List<Driver>> callback);

    /** RF-078: aprobar a un conductor o retirarle la habilitacion. */
    void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                            @NonNull ResultCallback<Driver> callback);

    /** Los hoteles de la plataforma, con su estado de publicacion. */
    void hoteles(@NonNull ResultCallback<List<Hotel>> callback);

    /** RF-008: administradores de hotel, para poder asignar uno. */
    void administradores(@NonNull ResultCallback<List<User>> callback);

    /** RF-007: dar de alta un hotel. Nace sin publicar y sin administrador. */
    void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback);

    /** RF-008: asignar un administrador a un hotel. */
    void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                              @NonNull ResultCallback<Hotel> callback);

    /** RF-118 a RF-120: la bitacora, del movimiento mas reciente al mas antiguo. */
    void bitacora(@NonNull ResultCallback<List<LogEntry>> callback);
}
```

- [ ] **Paso 3: la implementación**

```java
package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.SuperadminRepository;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Implementacion simulada del panel del superadministrador (§47). */
public class MockSuperadminRepository extends MockRepository implements SuperadminRepository {

    /** Cuantos movimientos enseña la portada; el resto quedan en la bitacora. */
    private static final int ULTIMOS_EN_PORTADA = 3;

    @Override
    public void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback) {
        entregarDato(callback, () -> {
            int usuariosActivos = 0;
            for (User usuario : MockData.USUARIOS) {
                if (usuario.isActivo()) {
                    usuariosActivos++;
                }
            }

            int conductoresHabilitados = 0;
            for (Driver conductor : MockData.CONDUCTORES) {
                if (conductor.isHabilitado()) {
                    conductoresHabilitados++;
                }
            }

            int hotelesPublicados = 0;
            int sinAdministrador = 0;
            for (Hotel hotel : MockData.HOTELES) {
                if (hotel.isPublicado()) {
                    hotelesPublicados++;
                }
                if (hotel.getAdministradorId() == null) {
                    sinAdministrador++;
                }
            }

            return new ResumenSuperadmin(usuariosActivos, MockData.USUARIOS.size(),
                    conductoresHabilitados, MockData.CONDUCTORES.size(),
                    hotelesPublicados, MockData.HOTELES.size(), sinAdministrador,
                    ultimos(ULTIMOS_EN_PORTADA));
        }, "No pudimos cargar el resumen de la plataforma.");
    }

    @Override
    public void usuarios(@NonNull ResultCallback<List<User>> callback) {
        entregarLista(callback, () -> {
            List<User> ordenados = new ArrayList<>(MockData.USUARIOS);
            // Por rol y luego por nombre: es como se busca una cuenta, y deja
            // los superadministradores —que no se pueden tocar— al final.
            ordenados.sort(Comparator
                    .comparingInt((User u) -> u.getRol().ordinal())
                    .thenComparing(User::getNombreCompleto));
            return ordenados;
        });
    }

    @Override
    public void cambiarActivo(@NonNull String usuarioId, boolean activo,
                              @NonNull ResultCallback<User> callback) {
        ejecutar(callback, () -> {
            User usuario = exigirUsuario(usuarioId);
            if (!activo && !usuario.esDesactivable()) {
                throw new IllegalStateException(
                        "Un superadministrador no se puede desactivar: la plataforma se quedaría sin quien la administre.");
            }
            if (usuario.isActivo() == activo) {
                return usuario;
            }
            usuario.setActivo(activo);
            registrar(activo ? LogEntry.Evento.ACTIVACION : LogEntry.Evento.DESACTIVACION,
                    usuario.getNombreCompleto() + (activo
                            ? " fue activado."
                            : " fue desactivado y ya no puede entrar."));
            return usuario;
        });
    }

    @Override
    public void conductores(@NonNull ResultCallback<List<Driver>> callback) {
        entregarLista(callback, () -> {
            List<Driver> ordenados = new ArrayList<>(MockData.CONDUCTORES);
            // Los pendientes primero (RF-077): son los unicos sobre los que hay
            // algo que decidir, y es lo que el superadministrador viene a hacer.
            ordenados.sort(Comparator
                    .comparing(Driver::isHabilitado)
                    .thenComparing(Driver::getNombreCompleto));
            return ordenados;
        });
    }

    @Override
    public void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                                   @NonNull ResultCallback<Driver> callback) {
        ejecutar(callback, () -> {
            Driver conductor = MockData.conductor(conductorId);
            if (conductor == null) {
                throw new IllegalArgumentException("No encontramos ese conductor.");
            }
            if (conductor.isHabilitado() == habilitado) {
                return conductor;
            }
            conductor.setHabilitado(habilitado);
            registrar(LogEntry.Evento.APROBACION, conductor.getNombreCompleto()
                    + (habilitado ? " fue habilitado para prestar servicios."
                                  : " dejó de estar habilitado."));
            return conductor;
        });
    }

    @Override
    public void hoteles(@NonNull ResultCallback<List<Hotel>> callback) {
        entregarLista(callback, () -> {
            List<Hotel> ordenados = new ArrayList<>(MockData.HOTELES);
            // Sin publicar primero: son los que esperan una accion del
            // superadministrador; los demas ya funcionan solos.
            ordenados.sort(Comparator
                    .comparing(Hotel::isPublicado)
                    .thenComparing(Hotel::getNombre));
            return ordenados;
        });
    }

    @Override
    public void administradores(@NonNull ResultCallback<List<User>> callback) {
        entregarLista(callback, () -> {
            List<User> administradores = new ArrayList<>();
            for (User usuario : MockData.USUARIOS) {
                if (usuario.getRol() == Role.ADMIN_HOTEL && usuario.isActivo()) {
                    administradores.add(usuario);
                }
            }
            administradores.sort(Comparator.comparing(User::getNombreCompleto));
            return administradores;
        });
    }

    @Override
    public void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            String nombre = limpiar(borrador.getNombre());
            if (nombre.isEmpty()) {
                throw new IllegalArgumentException("Ponle un nombre al hotel.");
            }
            if (limpiar(borrador.getCiudad()).isEmpty()) {
                throw new IllegalArgumentException("Indica en qué ciudad está el hotel.");
            }
            if (limpiar(borrador.getDistrito()).isEmpty()) {
                throw new IllegalArgumentException("Indica el distrito del hotel.");
            }
            // Un hotel sin coordenadas no se puede enseñar en el plano de
            // seguimiento del taxi (RF-084), asi que no se admite a medias.
            if (borrador.getLatitud() == 0d && borrador.getLongitud() == 0d) {
                throw new IllegalArgumentException("Indica las coordenadas del hotel.");
            }

            Hotel hotel = new Hotel(MockData.idNuevo("H"), nombre,
                    limpiar(borrador.getDistrito()), limpiar(borrador.getCiudad()));
            hotel.setDireccion(borrador.getDireccion());
            hotel.setUbicacion(borrador.getLatitud(), borrador.getLongitud());
            // Nace sin publicar y sin administrador (RF-007, RF-008): los dos
            // pasos que faltan son justamente los que vienen despues.
            MockData.HOTELES.add(hotel);

            registrar(LogEntry.Evento.ACCION_ADMINISTRATIVA,
                    "Se registró el hotel " + hotel.getNombre() + " en " + hotel.getCiudad() + ".");
            return hotel;
        });
    }

    @Override
    public void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                                     @NonNull ResultCallback<Hotel> callback) {
        ejecutar(callback, () -> {
            Hotel hotel = exigirHotel(hotelId);
            User administrador = exigirUsuario(usuarioId);
            if (administrador.getRol() != Role.ADMIN_HOTEL) {
                throw new IllegalArgumentException(
                        administrador.getNombreCompleto() + " no es administrador de hotel.");
            }
            if (!administrador.isActivo()) {
                throw new IllegalStateException(
                        administrador.getNombreCompleto() + " tiene la cuenta desactivada.");
            }

            hotel.setAdministradorId(administrador.getId());
            registroDeAsignacion(hotel, administrador);
            return hotel;
        });
    }

    /**
     * Escribe el movimiento, y avisa si ese administrador ya llevaba otro hotel.
     *
     * <p>El aviso va en el detalle y no en un error porque no lo es: el modelo
     * admite que alguien lleve dos, y quien decide si eso es un problema es el
     * superadministrador. Lo que no puede es no enterarse.
     */
    private void registroDeAsignacion(Hotel hotel, User administrador) {
        int otros = 0;
        for (Hotel otro : MockData.HOTELES) {
            if (otro != hotel && administrador.getId().equals(otro.getAdministradorId())) {
                otros++;
            }
        }
        String detalle = administrador.getNombreCompleto() + " quedó a cargo de "
                + hotel.getNombre() + ".";
        if (otros > 0) {
            detalle += " Ya administraba " + otros + " hotel"
                    + (otros == 1 ? "." : "es.");
        }
        registrar(LogEntry.Evento.ACCION_ADMINISTRATIVA, detalle);
    }

    @Override
    public void bitacora(@NonNull ResultCallback<List<LogEntry>> callback) {
        entregarLista(callback, MockSuperadminRepository::ordenadosDeLaBitacora);
    }

    // ------------------------------------------------------------------
    //  Apoyo
    // ------------------------------------------------------------------

    private static List<LogEntry> ultimos(int cuantos) {
        List<LogEntry> eventos = ordenadosDeLaBitacora();
        return eventos.size() <= cuantos ? eventos : eventos.subList(0, cuantos);
    }

    /** Una lista nueva con los mas recientes primero. */
    private static List<LogEntry> ordenadosDeLaBitacora() {
        List<LogEntry> eventos = new ArrayList<>(MockData.BITACORA);
        eventos.sort(Comparator.comparing(LogEntry::getTimestamp).reversed());
        return eventos;
    }

    private static User exigirUsuario(String usuarioId) {
        User usuario = MockData.usuario(usuarioId);
        if (usuario == null) {
            throw new IllegalArgumentException("No encontramos esa cuenta.");
        }
        return usuario;
    }

    private static Hotel exigirHotel(String hotelId) {
        Hotel hotel = MockData.hotel(hotelId);
        if (hotel == null) {
            throw new IllegalArgumentException("No encontramos ese hotel.");
        }
        return hotel;
    }

    /** Escribe un movimiento en la bitacora a nombre de quien lo hizo. */
    private static void registrar(LogEntry.Evento evento, String detalle) {
        User autor = MockData.usuario(SessionManager.getUsuarioIdSeguro());
        MockData.BITACORA.add(new LogEntry(LocalDateTime.now(),
                autor != null ? autor.getNombreCompleto() : "Superadministrador",
                evento, detalle));
    }

    private static String limpiar(@Nullable String texto) {
        return texto == null ? "" : texto.trim();
    }
}
```

**Nota:** `ultimos(int)` y `ordenadosDeLaBitacora()` son la misma cosa; quédate
con una sola —`ultimos(cuantos)`— y usa `ultimos(Integer.MAX_VALUE)` en
`bitacora(...)`. No dejes las dos.

- [ ] **Paso 4: exponerlo**

En `ServiceLocator`, añade el campo, el import y el acceso:

```java
    private static final SuperadminRepository SUPERADMIN = new MockSuperadminRepository();
```

```java
    /** Panel del superadministrador (§47). */
    public static SuperadminRepository superadmin() {
        return SUPERADMIN;
    }
```

- [ ] **Paso 5: compilar**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
```
Esperado: **BUILD SUCCESSFUL**. Si `Driver` no tiene `getNombreCompleto()`,
usa el que tenga para componer el nombre y ajusta estas líneas.

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add app/src/main/java/org/iot/project/models/ResumenSuperadmin.java \
        app/src/main/java/org/iot/project/data/repository/SuperadminRepository.java \
        app/src/main/java/org/iot/project/data/mock/MockSuperadminRepository.java \
        app/src/main/java/org/iot/project/core/ServiceLocator.java
git commit -m "feat(superadmin): repositorio del panel y sus reglas (RF-005 a RF-008, RF-077)"
```

---

## Tarea 9: La navegación del superadministrador y su portada

**Ficheros:**
- Reescribir: `app/src/main/res/navigation/nav_superadmin.xml`
- Reescribir: `app/src/main/res/menu/menu_bottom_nav_superadmin.xml`
- Modificar: `app/src/main/java/org/iot/project/core/Roles.java:35-37`
- Borrar: `app/src/main/java/org/iot/project/ui/common/PanelRolFragment.java`
- Borrar: `app/src/main/res/layout/fragment_panel_rol.xml`
- Crear: `app/src/main/java/org/iot/project/ui/superadmin/home/SuperadminHomeViewModel.java`
- Crear: `app/src/main/java/org/iot/project/ui/superadmin/home/SuperadminHomeFragment.java`
- Crear: `app/src/main/java/org/iot/project/ui/superadmin/bitacora/BitacoraAdapter.java`
- Crear: `app/src/main/res/layout/fragment_superadmin_home.xml`
- Crear: `app/src/main/res/layout/item_bitacora.xml`
- Modificar: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consume: `ServiceLocator.superadmin()`, `SuperadminRepository.resumen(...)`, `ResumenSuperadmin`.
- Produce: `BitacoraAdapter` y `item_bitacora.xml`, que reutiliza la tarea 13;
  el destino `R.id.superadminHomeFragment`, que es el inicio del rol.

- [ ] **Paso 1: el grafo y la barra**

`nav_superadmin.xml` entero:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Grafo del rol Superadministrador (§47).

    Cinco secciones en la barra inferior: Inicio, Usuarios, Conductores, Hoteles
    y Auditoría. §47 pide seis cosas y una barra aguanta cinco, así que las dos
    que sobran viven dentro de las que hay: aprobar conductores (RF-077) es un
    filtro de Conductores, y los reportes de reservas (RF-059) son un bloque de
    la ficha de cada hotel, porque el requisito los pide "por hotel".

    El superadministrador no aprueba hoteles: RF-007 pide registrarlos y RF-008
    asignarles un administrador. Quien publica es el administrador.
-->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/nav_superadmin"
    app:startDestination="@id/superadminHomeFragment">

    <!-- ==================== Barra inferior ==================== -->

    <fragment
        android:id="@+id/superadminHomeFragment"
        android:name="org.iot.project.ui.superadmin.home.SuperadminHomeFragment"
        android:label="@string/nav_inicio"
        tools:layout="@layout/fragment_superadmin_home" />

</navigation>
```

`menu_bottom_nav_superadmin.xml` entero:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Barra inferior del Superadministrador (§47). Las cinco secciones.

    Aprobaciones no tiene entrada propia —es el filtro de pendientes dentro de
    Conductores— ni Reportes tampoco: el reporte de reservas es de cada hotel y
    se abre desde su ficha.
-->
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@+id/superadminHomeFragment"
        android:icon="@drawable/ic_home"
        android:title="@string/nav_inicio" />

</menu>
```

En `Roles.java`, el inicio del rol:

```java
    private static final Roles SUPERADMIN = new Roles(
            R.navigation.nav_superadmin, R.menu.menu_bottom_nav_superadmin,
            R.id.superadminHomeFragment);
```

- [ ] **Paso 2: borrar el panel provisional**

```bash
git rm app/src/main/java/org/iot/project/ui/common/PanelRolFragment.java \
       app/src/main/res/layout/fragment_panel_rol.xml
```

Y en `strings.xml`, borra `titulo_panel_superadmin` y `superadmin_siguiente`:
solo los usaba esa pantalla.

- [ ] **Paso 3: el ViewModel**

```java
package org.iot.project.ui.superadmin.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.ResumenSuperadmin;

import androidx.annotation.NonNull;

/**
 * Portada del superadministrador (§47).
 *
 * <p>Una sola consulta: el repositorio entrega las cifras ya contadas y los
 * ultimos movimientos de la bitacora juntos. Contarlas aqui obligaria a la
 * pantalla a saber que es un usuario activo o un hotel sin administrador, y esa
 * definicion es del dominio.
 */
public class SuperadminHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<ResumenSuperadmin>> contenido = new MutableLiveData<>();

    private boolean cargando;

    public LiveData<UiState<ResumenSuperadmin>> getContenido() {
        return contenido;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        // Al rotar la pantalla el ViewModel sobrevive con las cifras ya
        // cargadas: volver al esqueleto dejaria la pantalla en blanco un
        // instante sin motivo.
        UiState<ResumenSuperadmin> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    /**
     * Recarga sin esqueleto, al volver a la portada.
     *
     * <p>Las cifras cambian desde otras pantallas del propio panel —desactivar
     * una cuenta, habilitar un conductor— y volver a Inicio tiene que enseñarlas
     * al dia sin borrar el tablero mientras llegan.
     */
    public void refrescarEnSilencio() {
        UiState<ResumenSuperadmin> actual = contenido.getValue();
        if (cargando || actual == null || !actual.isSuccess()) {
            return;
        }
        cargando = true;
        pedir();
    }

    private void pedir() {
        ServiceLocator.superadmin().resumen(new ResultCallback<ResumenSuperadmin>() {
            @Override
            public void onExito(@NonNull ResumenSuperadmin datos) {
                cargando = false;
                contenido.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }
}
```

- [ ] **Paso 4: el adaptador de la bitácora**

Lo usa la portada para los tres últimos movimientos, y la pantalla de Auditoría
lo reutiliza entero (tarea 13).

```java
package org.iot.project.ui.superadmin.bitacora;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemBitacoraBinding;
import org.iot.project.models.LogEntry;

import java.time.format.DateTimeFormatter;

/**
 * Filas de la bitácora (RF-118 a RF-120).
 *
 * <p>Cada movimiento enseña cuando ocurrió, quién lo hizo y qué pasó. El
 * "cuándo" va en formato corto —día y hora— porque la lista se lee de arriba
 * abajo buscando lo reciente, no consultando un acta.
 */
public class BitacoraAdapter extends ListAdapter<LogEntry, BitacoraAdapter.Fila> {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM · HH:mm");

    public BitacoraAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemBitacoraBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.binding.bind(getItem(posicion));
    }

    static class Fila extends RecyclerView.ViewHolder {

        final ItemBitacoraBinding binding;

        Fila(@NonNull ItemBitacoraBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<LogEntry> DIFF =
            new DiffUtil.ItemCallback<LogEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                    // Un movimiento no tiene identificador: lo que lo distingue
                    // es su marca temporal, que RC-040 obliga a que exista.
                    return a.getTimestamp().equals(b.getTimestamp())
                            && a.getEvento() == b.getEvento()
                            && a.getDetalle().equals(b.getDetalle());
                }

                @Override
                public boolean areContentsTheSame(@NonNull LogEntry a, @NonNull LogEntry b) {
                    return areItemsTheSame(a, b);
                }
            };

    /** Formato corto de la marca temporal. */
    @NonNull
    public static String cuando(@NonNull LogEntry evento) {
        return evento.getTimestamp().format(FORMATO);
    }
}
```

- [ ] **Paso 5: la fila de la bitácora**

`item_bitacora.xml`, entero:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Un movimiento de la bitácora (RF-118 a RF-120).

    Tres líneas de alto fijo: la hora y el evento arriba, quien lo hizo y el
    detalle debajo. El texto nunca lleva contraseñas ni secretos (RC-042).
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="@dimen/screen_margin"
    android:paddingVertical="@dimen/spaceMd">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/bitacora_evento"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textAppearance="@style/TextAppearance.App.Subtitle2"
            android:textColor="@color/colorOnSurface"
            tools:text="Alta de hotel" />

        <TextView
            android:id="@+id/bitacora_cuando"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/spaceSm"
            android:textAppearance="@style/TextAppearance.App.Caption"
            android:textColor="@color/colorTextDisabled"
            tools:text="18/09 · 10:04" />
    </LinearLayout>

    <TextView
        android:id="@+id/bitacora_detalle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/spaceXs"
        android:textAppearance="@style/TextAppearance.App.Body2"
        android:textColor="@color/colorOnSurfaceVariant"
        tools:text="Ana Ferreyra Campos registró el hotel Casa del Mar en Lima." />

    <TextView
        android:id="@+id/bitacora_autor"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/spaceXs"
        android:textAppearance="@style/TextAppearance.App.Caption"
        android:textColor="@color/colorTextDisabled"
        tools:text="Ana Ferreyra Campos" />

</LinearLayout>
```

**Antes de escribirlo**, comprueba en `res/values/type.xml` y `res/values/colors.xml`
que existen `TextAppearance.App.Subtitle2`, `TextAppearance.App.Caption`,
`TextAppearance.App.Body2`, `colorOnSurface`, `colorOnSurfaceVariant` y los
dimens `spaceXs`/`spaceSm`/`spaceMd`/`screen_margin`: son los nombres que usa el
resto de la aplicación, y si alguno se llamara de otra forma habría que usar el
suyo.

- [ ] **Paso 6: la portada**

`fragment_superadmin_home.xml`: parte de `fragment_admin_reportes.xml` —misma
cabecera, mismo `NestedScrollView`, mismo esqueleto de carga— y deja el
contenedor interior así:

```xml
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <org.iot.project.ui.components.StatView
                            android:id="@+id/sa_stat_usuarios"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1" />

                        <org.iot.project.ui.components.StatView
                            android:id="@+id/sa_stat_conductores"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceSm"
                        android:orientation="horizontal">

                        <org.iot.project.ui.components.StatView
                            android:id="@+id/sa_stat_hoteles"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1" />

                        <org.iot.project.ui.components.StatView
                            android:id="@+id/sa_stat_pendientes"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1" />
                    </LinearLayout>

                    <com.google.android.material.card.MaterialCardView
                        android:id="@+id/sa_pendientes_tarjeta"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceMd"
                        app:cardCornerRadius="@dimen/radiusMd"
                        app:cardElevation="0dp"
                        app:strokeColor="@color/colorDivider"
                        app:strokeWidth="1dp">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:padding="@dimen/spaceMd">

                            <TextView
                                android:id="@+id/sa_pendientes_titulo"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:textAppearance="@style/TextAppearance.App.Subtitle1"
                                android:textColor="@color/colorOnSurface"
                                tools:text="1 conductor espera aprobación" />

                            <TextView
                                android:id="@+id/sa_pendientes_mensaje"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="@dimen/spaceXs"
                                android:textAppearance="@style/TextAppearance.App.Body2"
                                android:textColor="@color/colorOnSurfaceVariant"
                                tools:text="Habilítalo para que pueda recibir solicitudes de traslado." />

                            <com.google.android.material.button.MaterialButton
                                android:id="@+id/sa_pendientes_accion"
                                style="@style/Widget.Material3.Button.TonalButton"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_gravity="end"
                                android:layout_marginTop="@dimen/spaceSm"
                                android:text="@string/sa_ir_a_conductores" />
                        </LinearLayout>
                    </com.google.android.material.card.MaterialCardView>

                    <TextView
                        android:id="@+id/sa_ultimos_titulo"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceLg"
                        android:text="@string/sa_ultimos_movimientos"
                        android:textAppearance="@style/TextAppearance.App.Subtitle1"
                        android:textColor="@color/colorOnSurface" />

                    <androidx.recyclerview.widget.RecyclerView
                        android:id="@+id/sa_ultimos"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceSm"
                        android:nestedScrollingEnabled="false" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/sa_ver_auditoria"
                        style="@style/Widget.Material3.Button.TextButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_gravity="end"
                        android:text="@string/sa_ver_toda_la_auditoria" />
                </LinearLayout>
```

Para que las filas de `item_bitacora` no queden pegadas al borde de la tarjeta,
`item_bitacora.xml` lleva `paddingHorizontal="@dimen/screen_margin"` y el
contenedor de la portada ya tiene ese mismo margen: dentro de la lista se verá
doble. **Quita el `paddingHorizontal` de `item_bitacora.xml`** y deja solo el
vertical; la pantalla de Auditoría (tarea 13) es la que decidirá su propio
margen.

Los identificadores del esqueleto, el error y el contenido:
`sa_contenido`, `sa_esqueleto`, `sa_error`, y la cabecera `sa_header`.

- [ ] **Paso 7: el Fragment**

```java
package org.iot.project.ui.superadmin.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminHomeBinding;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.ui.superadmin.bitacora.BitacoraAdapter;
import org.iot.project.utils.InsetUtils;

/**
 * Portada del superadministrador (§47).
 *
 * <p>Responde a dos preguntas: cómo está la plataforma y qué espera una decisión
 * mía. Las cuatro cifras van arriba, el aviso de pendientes justo debajo —es lo
 * único accionable de la pantalla— y los últimos movimientos al final.
 *
 * <p>El aviso de pendientes no se esconde cuando no hay ninguno: se queda y dice
 * que no hay. Un bloque que desaparece deja al superadministrador sin saber si
 * no hay pendientes o si el tablero no cargó.
 */
public class SuperadminHomeFragment extends Fragment {

    /** Cuántos movimientos enseña la portada; el resto quedan en Auditoría. */
    private static final int ULTIMOS_EN_PORTADA = 3;

    private FragmentSuperadminHomeBinding binding;
    private SuperadminHomeViewModel viewModel;
    private BitacoraAdapter bitacora;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminHomeBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.sa_titulo_inicio);

        // Esta portada es la última pantalla del rol y no tiene perfil, así que
        // la cabecera es el único sitio donde el superadministrador puede cerrar
        // sesión.
        binding.saHeader.mostrarAccion(R.drawable.ic_logout, R.string.cd_cerrar_sesion,
                v -> confirmarCierre());

        bitacora = new BitacoraAdapter();
        binding.saUltimos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saUltimos.setAdapter(bitacora);

        binding.saPendientesAccion.setOnClickListener(v -> irA(R.id.superadminConductoresFragment));
        binding.saVerAuditoria.setOnClickListener(v -> irA(R.id.superadminBitacoraFragment));

        viewModel = new ViewModelProvider(this).get(SuperadminHomeViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Volver de Usuarios o de Conductores, donde se acaba de desactivar una
        // cuenta o habilitar un conductor: las cifras de arriba tienen que
        // reflejarlo sin que el tablero parpadee.
        viewModel.refrescarEnSilencio();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ResumenSuperadmin> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                llenar(estado.requireData());
                break;
            case EMPTY:
                // El repositorio no publica este estado: el resumen siempre
                // tiene cifras. Si llegara, es un fallo.
                pintarError(getString(R.string.estado_error_descripcion));
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void llenar(@NonNull ResumenSuperadmin resumen) {
        binding.saStatUsuarios.setDato(R.string.sa_stat_usuarios,
                getString(R.string.sa_de_total, resumen.getUsuariosActivos(),
                        resumen.getUsuariosTotales()));
        binding.saStatConductores.setDato(R.string.sa_stat_conductores,
                getString(R.string.sa_de_total, resumen.getConductoresHabilitados(),
                        resumen.getConductoresTotales()));
        binding.saStatHoteles.setDato(R.string.sa_stat_hoteles,
                getString(R.string.sa_de_total, resumen.getHotelesPublicados(),
                        resumen.getHotelesTotales()));
        binding.saStatPendientes.setDato(R.string.sa_stat_pendientes,
                String.valueOf(resumen.getConductoresPendientes()));

        pintarPendientes(resumen);
        pintarUltimos(resumen);
    }

    /**
     * Los últimos movimientos.
     *
     * <p>Cuántos son un adelanto y cuántos la lista entera lo decide el
     * repositorio, que es quien sabe que la portada solo enseña unos pocos.
     * Recortar aquí sería tener la misma regla escrita en dos sitios.
     */
    private void pintarUltimos(@NonNull ResumenSuperadmin resumen) {
        boolean hay = !resumen.getUltimos().isEmpty();
        binding.saUltimos.setVisibility(hay ? View.VISIBLE : View.GONE);
        ultimos.submitList(hay ? resumen.getUltimos() : Collections.emptyList());
    }

    /**
     * El aviso de lo que espera una decisión: una fila por cada cosa pendiente.
     *
     * <p>Son dos filas y no una porque son dos destinos distintos: mandar a
     * Conductores a quien tiene un hotel sin administrador sería mandarlo a un
     * sitio donde no está lo que le falta. Una sola fila obligaría además a
     * elegir cuál de las dos enseñar cuando las dos esperan, y el
     * superadministrador se enteraría de una sola.
     */
    private void pintarPendientes(@NonNull ResumenSuperadmin resumen) {
        int conductores = resumen.getConductoresPendientes();
        int hoteles = resumen.getHotelesSinAdministrador();
        boolean hayConductores = conductores > 0;
        boolean hayHoteles = hoteles > 0;

        binding.saPendientesTitulo.setText(hayConductores || hayHoteles
                ? R.string.sa_pendientes_titulo
                : R.string.sa_sin_pendientes_titulo);

        binding.saPendientesConductores.setVisibility(hayConductores ? View.VISIBLE : View.GONE);
        binding.saPendientesConductoresTexto.setText(getResources()
                .getQuantityString(R.plurals.sa_conductores_pendientes, conductores, conductores));

        binding.saPendientesHoteles.setVisibility(hayHoteles ? View.VISIBLE : View.GONE);
        binding.saPendientesHotelesTexto.setText(getResources()
                .getQuantityString(R.plurals.sa_hoteles_sin_administrador, hoteles, hoteles));

        binding.saPendientesVacio.setVisibility(
                hayConductores || hayHoteles ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private void pintarError(@Nullable String mensaje) {
        mostrar(false, false, true);
        binding.saError.conReintento(mensaje, v -> viewModel.reintentar());
    }

    private void confirmarCierre() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.sesion_cerrar_titulo)
                .setMessage(R.string.sesion_cerrar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.sesion_cerrar,
                        (dialogo, cual) -> SessionManager.cerrarSesion())
                .show();
    }

    private void irA(@IdRes int destino) {
        Navigation.findNavController(requireView()).navigate(destino);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saUltimos.setAdapter(null);
        binding = null;
    }
}
```

Sustituye los nombres completos de `java.util.List` y `LogEntry` por sus
imports. Y **quita el `@IdRes`** y su import si prefieres pasar los dos destinos
como constantes; lo importante es que `navigate(...)` reciba un identificador
declarado en el grafo.

- [ ] **Paso 8: las cadenas**

```xml
    <string name="sa_titulo_inicio">Panel de la plataforma</string>
    <string name="sa_stat_usuarios">Usuarios activos</string>
    <string name="sa_stat_conductores">Conductores habilitados</string>
    <string name="sa_stat_hoteles">Hoteles publicados</string>
    <string name="sa_stat_pendientes">Conductores por aprobar</string>
    <string name="sa_de_total">%1$d de %2$d</string>
    <string name="sa_ultimos_movimientos">Últimos movimientos</string>
    <string name="sa_ver_toda_la_auditoria">Ver toda la auditoría</string>
    <string name="sa_ir_a_conductores">Ver conductores</string>
    <string name="sa_ir_a_hoteles">Ver hoteles</string>
    <string name="sa_pendientes_titulo">Lo que espera tu decisión</string>
    <string name="sa_sin_pendientes_titulo">No hay nada pendiente</string>
    <string name="sa_sin_pendientes_mensaje">No hay conductores esperando aprobación ni hoteles sin administrador.</string>

    <plurals name="sa_conductores_pendientes">
        <item quantity="one">%1$d conductor espera aprobación</item>
        <item quantity="other">%1$d conductores esperan aprobación</item>
    </plurals>

    <plurals name="sa_hoteles_sin_administrador">
        <item quantity="one">%1$d hotel todavía no tiene administrador</item>
        <item quantity="other">%1$d hoteles todavía no tienen administrador</item>
    </plurals>
```

Sin punto final en los dos plurales: son rótulos de fila dentro de la tarjeta,
no frases sueltas.

- [ ] **Paso 9: compilar y probar**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
Esperado: **BUILD SUCCESSFUL**, pruebas **en verde** y el verificador **sin
problemas**.

- [ ] **Paso 10: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin \
           app/src/main/java/org/iot/project/ui/common \
           app/src/main/java/org/iot/project/core/Roles.java \
           app/src/main/res/layout/fragment_superadmin_home.xml \
           app/src/main/res/layout/item_bitacora.xml \
           app/src/main/res/layout/fragment_panel_rol.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/menu/menu_bottom_nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): navegacion del panel y su portada (§47)"
```

---

## Tarea 10: Usuarios (RF-005, RF-006)

**Ficheros:**
- Crear: `ui/superadmin/usuarios/UsuariosSaViewModel.java`, `UsuariosSaFragment.java`, `CuentaSaAdapter.java`
- Crear: `res/layout/fragment_superadmin_usuarios.xml`, `res/layout/item_cuenta_sa.xml`
- Modificar: `res/navigation/nav_superadmin.xml`, `res/menu/menu_bottom_nav_superadmin.xml`, `res/values/strings.xml`

**Interfaces:**
- Consume: `SuperadminRepository.usuarios()`, `.cambiarActivo(...)`.
- Produce: el destino `R.id.superadminUsuariosFragment`.

- [ ] **Paso 1: el destino y la pestaña**

En `nav_superadmin.xml`, dentro de «Barra inferior»:

```xml
    <fragment
        android:id="@+id/superadminUsuariosFragment"
        android:name="org.iot.project.ui.superadmin.usuarios.UsuariosSaFragment"
        android:label="@string/nav_usuarios"
        tools:layout="@layout/fragment_superadmin_usuarios" />
```

En `menu_bottom_nav_superadmin.xml`, después de Inicio:

```xml
    <item
        android:id="@+id/superadminUsuariosFragment"
        android:icon="@drawable/ic_person_group"
        android:title="@string/nav_usuarios" />
```

- [ ] **Paso 2: el ViewModel**

```java
package org.iot.project.ui.superadmin.usuarios;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cuentas de la plataforma (RF-005) y su activacion (RF-006).
 *
 * <p><b>El filtro vive aqui y no en el repositorio.</b> Filtrar por rol es
 * mirar una lista que ya esta en memoria; hacerlo pasar por el repositorio
 * costaria los 400 ms de latencia simulada en cada toque de filtro, y el
 * usuario veria el esqueleto cada vez que cambia de pestaña.
 *
 * <p>Los superadministradores se enseñan y no se tocan: saber quien puede
 * administrar la plataforma es parte de RF-005, y esconderlos haria creer que no
 * existen.
 */
public class UsuariosSaViewModel extends ViewModel {

    /**
     * Filtros de la lista. {@code null} en {@link #filtro} significa "todas".
     */
    @Nullable
    private Role filtro;

    private final MutableLiveData<UiState<List<User>>> contenido = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    /** La lista completa, tal como llego: el filtro se aplica sobre ella. */
    private List<User> todas = Collections.emptyList();

    private boolean cargando;

    public LiveData<UiState<List<User>>> getContenido() {
        return contenido;
    }

    /** Mensaje de una sola vez tras activar o desactivar. */
    public LiveData<String> getAviso() {
        return aviso;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<User>> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    private void pedir() {
        ServiceLocator.superadmin().usuarios(new ResultCallback<List<User>>() {
            @Override
            public void onExito(@NonNull List<User> datos) {
                cargando = false;
                todas = datos;
                publicar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    public void filtrarPor(@Nullable Role rol) {
        filtro = rol;
        publicar();
    }

    private void publicar() {
        List<User> visibles = new ArrayList<>();
        for (User usuario : todas) {
            if (filtro == null || usuario.getRol() == filtro) {
                visibles.add(usuario);
            }
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<User>>empty()
                : UiState.success(visibles));
    }

    /**
     * Activa o desactiva una cuenta.
     *
     * <p>Se recarga la lista entera en vez de cambiar la fila en memoria: el
     * repositorio es quien decide si el cambio era posible —un
     * superadministrador no se desactiva—, y adelantarlo en la pantalla seria
     * tener la regla escrita dos veces.
     */
    public void cambiarActivo(@NonNull User usuario, boolean activo) {
        ServiceLocator.superadmin().cambiarActivo(usuario.getId(), activo,
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User dato) {
                        aviso.setValue(activo
                                ? getNombre(dato) + " ya puede entrar."
                                : getNombre(dato) + " ya no puede entrar.");
                        cargando = true;
                        pedir();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    public void consumirAviso() {
        aviso.setValue(null);
    }

    @NonNull
    private static String getNombre(@NonNull User usuario) {
        return usuario.getNombreCompleto();
    }
}
```

Los dos mensajes de `cambiarActivo` se escriben en el ViewModel y no como
cadenas de recurso porque llevan el nombre dentro; si prefieres recursos, usa
`getString(R.string.sa_cuenta_activada, nombre)` desde el Fragment pasando el
resultado por `aviso`. Lo que **no** puede es quedarse el mensaje en el
repositorio: allí no hay `Context`.

- [ ] **Paso 3: la fila**

`item_cuenta_sa.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Una cuenta en la lista del superadministrador (RF-005, RF-006).

    El botón dice la acción, no el estado: "Desactivar" sobre una cuenta activa.
    Se usa un botón y no un interruptor porque desactivar deja a alguien fuera
    de la aplicación (RF-009) y tiene que preguntarse antes; un interruptor que
    se enciende y hay que revertir cuando el usuario cancela miente un instante.
-->
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/spaceSm"
    app:cardCornerRadius="@dimen/radiusMd"
    app:cardElevation="0dp"
    app:strokeColor="@color/colorDivider"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="@dimen/spaceMd">

        <TextView
            android:id="@+id/sa_cuenta_iniciales"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="@drawable/bg_badge_primary"
            android:gravity="center"
            android:textAppearance="@style/TextAppearance.App.Subtitle1"
            android:textColor="@color/colorPrimary"
            tools:text="LQ" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/spaceMd"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/sa_cuenta_nombre"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="@style/TextAppearance.App.Subtitle1"
                android:textColor="@color/colorOnSurface"
                tools:text="Lucía Quispe Ramos" />

            <TextView
                android:id="@+id/sa_cuenta_email"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spaceXs"
                android:textAppearance="@style/TextAppearance.App.Body2"
                android:textColor="@color/colorOnSurfaceVariant"
                tools:text="lucia.quispe@correo.pe" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spaceSm"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/sa_cuenta_rol"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_badge_primary"
                    android:paddingHorizontal="@dimen/spaceSm"
                    android:paddingVertical="@dimen/spaceXs"
                    android:textAppearance="@style/TextAppearance.App.Caption"
                    android:textColor="@color/colorPrimary"
                    tools:text="Cliente" />

                <TextView
                    android:id="@+id/sa_cuenta_estado"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/spaceSm"
                    android:background="@drawable/bg_badge_success"
                    android:paddingHorizontal="@dimen/spaceSm"
                    android:paddingVertical="@dimen/spaceXs"
                    android:textAppearance="@style/TextAppearance.App.Caption"
                    tools:text="Activa" />
            </LinearLayout>
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/sa_cuenta_accion"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_vertical"
            tools:text="Desactivar" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Paso 4: el adaptador**

```java
package org.iot.project.ui.superadmin.usuarios;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemCuentaSaBinding;
import org.iot.project.models.User;

/**
 * Filas de la lista de cuentas (RF-005).
 *
 * <p>El boton de cada fila se ata aqui y no en el Fragment: es una accion sobre
 * la fila, y quien sabe de que cuenta se trata es la fila.
 */
public class CuentaSaAdapter extends ListAdapter<User, CuentaSaAdapter.Fila> {

    /** Lo que la pantalla hace cuando se pulsa el boton de una cuenta. */
    public interface AlAccionar {
        void accionar(@NonNull User usuario, boolean activo);
    }

    private final AlAccionar alAccionar;

    public CuentaSaAdapter(@NonNull AlAccionar alAccionar) {
        super(DIFF);
        this.alAccionar = alAccionar;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemCuentaSaBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.bind(getItem(posicion));
    }

    class Fila extends RecyclerView.ViewHolder {

        private final ItemCuentaSaBinding binding;

        Fila(@NonNull ItemCuentaSaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull User usuario) {
            binding.saCuentaIniciales.setText(usuario.getIniciales());
            binding.saCuentaNombre.setText(usuario.getNombreCompleto());
            binding.saCuentaEmail.setText(usuario.getEmail());
            binding.saCuentaRol.setText(etiquetaDeRol(usuario));

            boolean activa = usuario.isActivo();
            binding.saCuentaEstado.setText(activa
                    ? R.string.sa_cuenta_activa : R.string.sa_cuenta_desactivada);
            binding.saCuentaEstado.setBackgroundResource(activa
                    ? R.drawable.bg_badge_success : R.drawable.bg_badge_error);

            // Un superadministrador no se desactiva (RF-006): no se le ofrece el
            // boton, en vez de ofrecerlo y rechazarlo despues.
            boolean desactivable = usuario.esDesactivable();
            binding.saCuentaAccion.setVisibility(desactivable ? View.VISIBLE : View.GONE);
            if (!desactivable) {
                return;
            }
            binding.saCuentaAccion.setText(activa
                    ? R.string.sa_desactivar : R.string.sa_activar);
            binding.saCuentaAccion.setOnClickListener(v -> alAccionar.accionar(usuario, !activa));
        }

        @NonNull
        private CharSequence etiquetaDeRol(@NonNull User usuario) {
            switch (usuario.getRol()) {
                case ADMIN_HOTEL:
                    return itemView.getContext().getString(R.string.sa_rol_administrador);
                case SUPERADMIN:
                    return itemView.getContext().getString(R.string.sa_rol_superadmin);
                case CONDUCTOR:
                    // No deberia llegar: los conductores no son usuarios y la
                    // lista no los pide. Si llegara, se dice lo que es.
                    return itemView.getContext().getString(R.string.nav_conductores);
                case CLIENTE:
                default:
                    return itemView.getContext().getString(R.string.sa_rol_cliente);
            }
        }
    }

    private static final DiffUtil.ItemCallback<User> DIFF =
            new DiffUtil.ItemCallback<User>() {
                @Override
                public boolean areItemsTheSame(@NonNull User a, @NonNull User b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull User a, @NonNull User b) {
                    return a.isActivo() == b.isActivo()
                            && a.getNombreCompleto().equals(b.getNombreCompleto())
                            && mismoTexto(a.getEmail(), b.getEmail());
                }
            };

    private static boolean mismoTexto(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
```

- [ ] **Paso 5: la pantalla**

`fragment_superadmin_usuarios.xml`: mismo esqueleto que
`fragment_superadmin_home.xml` —cabecera `sa_header`, `sa_contenido` con un
`NestedScrollView`, `sa_esqueleto`, `sa_error`— y dentro, como único hijo del
contenedor con margen:

```xml
                    <com.google.android.material.chip.ChipGroup
                        android:id="@+id/sa_usuarios_filtros"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:singleSelection="true"
                        app:selectionRequired="true">

                        <com.google.android.material.chip.Chip
                            android:id="@+id/sa_filtro_todas"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:checked="true"
                            android:text="@string/sa_filtro_todas" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/sa_filtro_clientes"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/sa_rol_cliente" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/sa_filtro_administradores"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/sa_rol_administrador" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/sa_filtro_superadmins"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/sa_rol_superadmin" />
                    </com.google.android.material.chip.ChipGroup>

                    <androidx.recyclerview.widget.RecyclerView
                        android:id="@+id/sa_usuarios_lista"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceMd"
                        android:nestedScrollingEnabled="false" />

                    <org.iot.project.ui.components.EmptyStateView
                        android:id="@+id/sa_usuarios_vacio"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/spaceXl"
                        android:visibility="gone" />
```

`CuentaSaAdapter` necesita además un `layout_marginTop` para la primera fila: lo
resuelve el `layout_marginTop` del `RecyclerView`.

```java
package org.iot.project.ui.superadmin.usuarios;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminUsuariosBinding;
import org.iot.project.models.Role;
import org.iot.project.models.User;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Cuentas de la plataforma (RF-005, RF-006).
 *
 * <p>Los superadministradores se ven pero no se tocan: desactivarlos dejaria la
 * plataforma sin nadie que pueda volver a activar a los demas.
 */
public class UsuariosSaFragment extends Fragment {

    private FragmentSuperadminUsuariosBinding binding;
    private UsuariosSaViewModel viewModel;
    private CuentaSaAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminUsuariosBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_usuarios);

        adaptador = new CuentaSaAdapter(this::confirmar);
        binding.saUsuariosLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saUsuariosLista.setAdapter(adaptador);

        binding.saUsuariosFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrarPor(rolDe(marcados.isEmpty() ? 0 : marcados.get(0))));

        viewModel = new ViewModelProvider(this).get(UsuariosSaViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getAviso().observe(getViewLifecycleOwner(), mensaje -> {
            if (mensaje != null) {
                Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
                viewModel.consumirAviso();
            }
        });
        viewModel.cargar();
    }

    @Nullable
    private Role rolDe(int chipId) {
        if (chipId == R.id.sa_filtro_clientes) {
            return Role.CLIENTE;
        }
        if (chipId == R.id.sa_filtro_administradores) {
            return Role.ADMIN_HOTEL;
        }
        if (chipId == R.id.sa_filtro_superadmins) {
            return Role.SUPERADMIN;
        }
        return null;
    }

    /**
     * Desactivar deja a alguien fuera de la aplicación (RF-009), así que se
     * pregunta antes. Activar no se pregunta: devuelve el acceso y no quita nada.
     */
    private void confirmar(@NonNull User usuario, boolean activo) {
        if (activo) {
            viewModel.cambiarActivo(usuario, true);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sa_desactivar_titulo, usuario.getNombreCompleto()))
                .setMessage(R.string.sa_desactivar_mensaje)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.sa_desactivar,
                        (dialogo, cual) -> viewModel.cambiarActivo(usuario, false))
                .show();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<User>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saUsuariosVacio.setVisibility(View.GONE);
                binding.saUsuariosLista.setVisibility(View.VISIBLE);
                adaptador.submitList(estado.requireData());
                break;
            case EMPTY:
                mostrar(true, false, false);
                binding.saUsuariosLista.setVisibility(View.GONE);
                binding.saUsuariosVacio.setVisibility(View.VISIBLE);
                binding.saUsuariosVacio.conIcono(R.drawable.ic_person_group)
                        .conTitulo(R.string.sa_usuarios_vacio_titulo)
                        .conMensaje(R.string.sa_usuarios_vacio_mensaje);
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saUsuariosLista.setAdapter(null);
        binding = null;
    }
}
```

**Nota:** `setOnCheckedStateChangeListener` es de Material 1.7 en adelante; este
proyecto usa Material 1.12. Si el método no existiera con el `ChipGroup` del
proyecto, usa `setOnCheckedChangeListener` —el de `RadioGroup`— o el patrón que
ya emplea `fragment_admin_servicios.xml` para sus chips.

- [ ] **Paso 6: las cadenas**

```xml
    <string name="sa_rol_cliente">Cliente</string>
    <string name="sa_rol_administrador">Administrador</string>
    <string name="sa_rol_superadmin">Superadministrador</string>
    <string name="sa_cuenta_activa">Activa</string>
    <string name="sa_cuenta_desactivada">Desactivada</string>
    <string name="sa_activar">Activar</string>
    <string name="sa_desactivar">Desactivar</string>
    <string name="sa_filtro_todas">Todas</string>
    <string name="sa_desactivar_titulo">¿Desactivar a %1$s?</string>
    <string name="sa_desactivar_mensaje">No podrá volver a entrar con su cuenta. Sus reservas y su historial no se borran, y puedes activarla de nuevo cuando quieras.</string>
    <string name="sa_usuarios_vacio_titulo">No hay cuentas con ese rol</string>
    <string name="sa_usuarios_vacio_mensaje">Cambia el filtro para ver las demás.</string>
```

- [ ] **Paso 7: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
Después, en el emulador: entrar como superadmin → Usuarios. Se ven siete cuentas;
el filtro deja ver solo las de cada rol; el superadministrador no ofrece botón;
desactivar U9 pide confirmación y lo deja en "Desactivada".

- [ ] **Paso 8: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin/usuarios \
           app/src/main/res/layout/fragment_superadmin_usuarios.xml \
           app/src/main/res/layout/item_cuenta_sa.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/menu/menu_bottom_nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): lista de cuentas y su activacion (RF-005, RF-006)"
```

---

## Tarea 11: Conductores (RF-077, RF-078)

**Ficheros:**
- Crear: `ui/superadmin/conductores/ConductoresSaViewModel.java`, `ConductoresSaFragment.java`, `ConductorSaAdapter.java`
- Crear: `res/layout/fragment_superadmin_conductores.xml`, `res/layout/item_conductor_sa.xml`
- Modificar: `res/navigation/nav_superadmin.xml`, `res/menu/menu_bottom_nav_superadmin.xml`, `res/values/strings.xml`

**Interfaces:**
- Consume: `SuperadminRepository.conductores()`, `.habilitarConductor(...)`.
- Produce: el destino `R.id.superadminConductoresFragment`, al que ya apunta el
  botón de pendientes de la portada (tarea 9).

- [ ] **Paso 1: el destino y la pestaña**

```xml
    <fragment
        android:id="@+id/superadminConductoresFragment"
        android:name="org.iot.project.ui.superadmin.conductores.ConductoresSaFragment"
        android:label="@string/nav_conductores"
        tools:layout="@layout/fragment_superadmin_conductores" />
```

```xml
    <item
        android:id="@+id/superadminConductoresFragment"
        android:icon="@drawable/ic_taxi"
        android:title="@string/nav_conductores" />
```

- [ ] **Paso 2: el ViewModel**

```java
package org.iot.project.ui.superadmin.conductores;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Driver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conductores de la plataforma (RF-077) y su habilitacion (RF-078).
 *
 * <p>El filtro de pendientes vive aqui por el mismo motivo que el de usuarios:
 * es mirar una lista que ya esta en memoria, y hacerlo pasar por el repositorio
 * costaria la latencia simulada en cada toque.
 */
public class ConductoresSaViewModel extends ViewModel {

    /** Si el filtro enseña solo los que esperan aprobacion. */
    private boolean soloPendientes;

    private final MutableLiveData<UiState<List<Driver>>> contenido = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    private List<Driver> todos = Collections.emptyList();

    private boolean cargando;

    public LiveData<UiState<List<Driver>>> getContenido() {
        return contenido;
    }

    public LiveData<String> getAviso() {
        return aviso;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<Driver>> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    private void pedir() {
        ServiceLocator.superadmin().conductores(new ResultCallback<List<Driver>>() {
            @Override
            public void onExito(@NonNull List<Driver> datos) {
                cargando = false;
                todos = datos;
                publicar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    /** El filtro de la lista: todos, o solo los que esperan aprobacion. */
    public void filtrar(boolean soloPendientes) {
        this.soloPendientes = soloPendientes;
        publicar();
    }

    private void publicar() {
        List<Driver> visibles = new ArrayList<>();
        for (Driver conductor : todos) {
            if (!soloPendientes || !conductor.isHabilitado()) {
                visibles.add(conductor);
            }
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<Driver>>empty()
                : UiState.success(visibles));
    }

    public void habilitar(@NonNull Driver conductor, boolean habilitado) {
        ServiceLocator.superadmin().habilitarConductor(conductor.getId(), habilitado,
                new ResultCallback<Driver>() {
                    @Override
                    public void onExito(@NonNull Driver dato) {
                        aviso.setValue(habilitado
                                ? dato.getNombreCompleto() + " ya puede recibir solicitudes."
                                : dato.getNombreCompleto() + " dejó de estar habilitado.");
                        cargando = true;
                        pedir();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    public void consumirAviso() {
        aviso.setValue(null);
    }
}
```

Si `Driver` no tiene `getNombreCompleto()`, usa el método que tenga para componer
el nombre.

- [ ] **Paso 3: la fila**

`item_conductor_sa.xml`: la misma tarjeta que `item_cuenta_sa.xml` —avata,
nombre, contacto, dos insignias, botón— con estos identificadores y datos:
`sa_conductor_foto` (fotografía con `ic_person` de reserva), `sa_conductor_nombre`,
`sa_conductor_contacto` (correo · teléfono), `sa_conductor_insignia_estado`
("Habilitado" / "Pendiente" / "Sin habilitar"), `sa_conductor_vehiculo`
("Toyota Corolla · ABC-123" o el texto de que no tiene), y
`sa_conductor_accion`.

Es una copia estructural de `item_cuenta_sa.xml` con esos identificadores: cópialo
y renombra, no lo escribas de cero, para que las dos listas se lean igual.

- [ ] **Paso 4: el adaptador**

Copia `CuentaSaAdapter` y adapta: `ListAdapter<Driver, ...>`, `ItemConductorSaBinding`,
identidad por `getId().equals(...)`, y el botón:

```java
            boolean habilitado = conductor.isHabilitado();
            binding.saConductorInsigniaEstado.setText(habilitado
                    ? R.string.sa_conductor_habilitado : R.string.sa_conductor_pendiente);
            binding.saConductorInsigniaEstado.setBackgroundResource(habilitado
                    ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);

            // Un conductor pendiente solo tiene una accion posible, y un
            // habilitado tambien: el mismo boton hace las dos (RF-078).
            binding.saConductorAccion.setText(habilitado
                    ? R.string.sa_retirar_habilitacion : R.string.sa_habilitar);
            binding.saConductorAccion.setOnClickListener(v -> alAccionar.accionar(conductor, !habilitado));
```

El texto de la insignia cuando el conductor **nunca** estuvo habilitado y cuando
se le retiró es el mismo —"Pendiente"— porque desde la plataforma son el mismo
estado: no puede recibir solicitudes. Si se quiere distinguir, hace falta un
campo nuevo en `Driver` y ningún requisito lo pide.

- [ ] **Paso 5: la pantalla**

`fragment_superadmin_conductores.xml`: mismo esqueleto, con un `ChipGroup` de
dos chips —`sa_conductores_todos` (marcado) y `sa_conductores_pendientes`—, un
`RecyclerView` `sa_conductores_lista` y un `EmptyStateView` `sa_conductores_vacio`
con `ic_taxi`. El `ChipGroup` usa `app:singleSelection="true"`.

`ConductoresSaFragment` es `UsuariosSaFragment` con los nombres cambiados, el
filtro booleano en vez del rol, y sin diálogo de confirmación al habilitar ni al
retirar: ninguna de las dos acciones deja a nadie sin acceso a la aplicación
—retirar la habilitación solo impide recibir servicios nuevos (RF-078), no
cerrar sesión—.

- [ ] **Paso 6: las cadenas**

```xml
    <string name="sa_conductor_habilitado">Habilitado</string>
    <string name="sa_conductor_pendiente">Pendiente</string>
    <string name="sa_habilitar">Habilitar</string>
    <string name="sa_retirar_habilitacion">Retirar</string>
    <string name="sa_conductor_sin_vehiculo">Sin vehículo registrado</string>
    <string name="sa_conductores_todos">Todos</string>
    <string name="sa_conductores_solo_pendientes">Por aprobar</string>
    <string name="sa_conductores_vacio_titulo">No hay conductores que aprobar</string>
    <string name="sa_conductores_vacio_mensaje">Todos los conductores registrados están habilitados.</string>
    <string name="sa_retirar_titulo">¿Retirar la habilitación a %1$s?</string>
    <string name="sa_retirar_mensaje">Dejará de recibir solicitudes de traslado. Su historial y sus servicios ya cerrados no cambian.</string>
```

- [ ] **Paso 7: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
En el emulador: Conductores enseña a D1 y D2 habilitados y a D3 pendiente;
habilitar a D3 lo deja habilitado y el aviso lo dice; el filtro "Por aprobar"
queda vacío y enseña su estado vacío. Y D3 puede entrar como conductor.

- [ ] **Paso 8: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin/conductores \
           app/src/main/res/layout/fragment_superadmin_conductores.xml \
           app/src/main/res/layout/item_conductor_sa.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/menu/menu_bottom_nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): aprobacion de conductores (RF-077, RF-078)"
```

---

## Tarea 12: Hoteles: la lista, la ficha y la asignación

**Correcciones aplicadas al ejecutarla** (el código de los pasos 2 a 5 quedó
desactualizado; lo que hay en el repositorio es esto):

- `Periodicidad.MENSUAL` no existe: el reporte de la ficha se pide con
  `Periodicidad.MES`.
- La hoja de asignación **no recibe los candidatos por argumentos**. La ficha
  usa `ViewModelGrafo.de(this, R.id.nav_superadmin, SuperadminHotelViewModel.class)`
  —el idioma de `ServicioSheet`, `CargoSheet` y `HotelSheet`— y la hoja lee de
  ahí los candidatos y llama a `asignar(...)`. Sin `newInstance`, sin arrays
  paralelos y sin `AlElegir`: el escucha que se ata antes de `show(...)` se
  pierde al recrear la pantalla, y el ViewModel del grafo no.
- Por eso mismo `SuperadminHotelViewModel.abrir(hotelId)` tira lo cargado
  cuando el hotel es otro: el ViewModel sobrevive a la pantalla.
- El nombre del administrador se resuelve en `Administradores` (fichero nuevo,
  dos funciones estáticas), que comparten la lista y la ficha.
- Tres estados de administrador y no dos: sin asignar, asignado, y asignado a
  una cuenta desactivada después (RF-006). Decirle "sin administrador" a un
  hotel que sí lo tiene sería un dato falso, y la desactivación de un
  administrador es una acción que este mismo bloque permite.
- La lista distingue el vacío con filtro del vacío sin filtro, como la de
  conductores.
- El bloque de publicación lleva las dos direcciones —publicar y retirar—,
  porque es lo que pide la spec §5.6 ("el interruptor de retirar o volver a
  publicar"). Publicar está inerte y explicado cuando el hotel no cumple el
  mínimo, y retirar se pregunta antes.

**Lo que encontró el recorrido por el emulador.** La comprobación de esta tarea
—retirar H1 y que el cliente deje de verlo— no se puede hacer sin cambiar de
cuenta, y al intentarlo faltaba el cierre de sesión que la tarea 9 pide en la
portada: `SuperadminHomeFragment` no tenía la acción en la cabecera. Sin ella el
rol se quedaba encerrado y el recorrido de la spec §10.2 era irrealizable.
Añadida (`mostrarAccion` + `confirmarCierre`), y con ella el recorrido entero
pasa: Lima enseña 4 alojamientos con Casa del Mar, retirarlo lo deja en 3 y sin
él, y volver a publicarlo lo devuelve a 4 y a su sitio.

**La fila que no se repintaba (tareas 10 y 11).** El mismo recorrido destapó un
defecto en las dos listas que se accionan desde la propia fila. Al desactivar
una cuenta, el aviso decía que esa persona ya no podía entrar y la fila seguía
en ACTIVA con el botón "Desactivar"; al habilitar un conductor, la insignia
seguía en PENDIENTE. La causa es el comparador de `ListAdapter`: los modelos son
mutables y `MockSuperadminRepository` entrega sus propias instancias, así que la
lista anterior y la nueva apuntan al mismo objeto y `areContentsTheSame`
comparaba al objeto consigo mismo. Salir de la pantalla y volver lo disimulaba
—el ViewModel conserva la lista, pero la vista se recrea y ata las filas de
nuevo—, que es la peor forma de que un fallo se esconda.

Corregido con el patrón que el propio plan ya usa en
`AdministradorElegibleAdapter.Candidato`: la lista guarda filas resueltas
(`CuentaSaAdapter.Cuenta`, `ConductorSaAdapter.Conductor`), copiadas al publicar
la lista, con los valores que se pintan dentro. La lista vieja conserva lo que
enseñaba, el cambio se ve en el sitio, y el botón pasa a llevar identificador y
nombre en vez del modelo entero. Comprobado en el emulador: la fila de Carmen
Zevallos pasa a DESACTIVADA con el botón en "Activar" sin salir de la pantalla,
y la de Pedro Ccahuana pasa a HABILITADO y baja al segundo puesto —la lista
llega con los pendientes primero— en cuanto se le aprueba.

**La misma trampa sigue en las otras listas del panel.** `HotelSaAdapter`
compara campos de un `Hotel` que también es mutable y compartido, pero hoy no se
puede alcanzar: sus filas no cambian nada, y lo que cambia el hotel —la
publicación— se hace en la ficha, que al volver recrea la lista entera. Queda
anotado aquí para que la próxima fila con botón no herede el fallo.

**Ficheros:**
- Crear: `ui/superadmin/hoteles/HotelesSaViewModel.java`, `HotelesSaFragment.java`, `HotelSaAdapter.java`
- Crear: `ui/superadmin/hoteles/HotelSaViewModel.java`, `HotelSaFragment.java`, `AsignarAdministradorSheet.java`, `AdministradorElegibleAdapter.java`
- Crear: `res/layout/fragment_superadmin_hoteles.xml`, `item_hotel_sa.xml`, `fragment_superadmin_hotel.xml`, `sheet_asignar_administrador.xml`, `item_administrador_elegible.xml`
- Modificar: `res/navigation/nav_superadmin.xml`, `res/menu/menu_bottom_nav_superadmin.xml`, `res/values/strings.xml`

**Interfaces:**
- Consume: `SuperadminRepository.hoteles()`, `.administradores()`,
  `.asignarAdministrador(...)`, `GestionHotelRepository.cambiarPublicacion(...)`,
  `GestionHotelRepository.ventas(...)`.
- Produce: el destino `R.id.superadminHotelesFragment` y el destino con argumento
  `R.id.superadminHotelFragment`.

- [ ] **Paso 1: los destinos y la pestaña**

```xml
    <fragment
        android:id="@+id/superadminHotelesFragment"
        android:name="org.iot.project.ui.superadmin.hoteles.HotelesSaFragment"
        android:label="@string/nav_hoteles"
        tools:layout="@layout/fragment_superadmin_hoteles" />

    <!-- ==================== Fuera de la barra ==================== -->

    <fragment
        android:id="@+id/superadminHotelFragment"
        android:name="org.iot.project.ui.superadmin.hoteles.HotelSaFragment"
        android:label="@string/sa_titulo_ficha_hotel"
        tools:layout="@layout/fragment_superadmin_hotel">

        <argument
            android:name="hotelId"
            app:argType="string" />
    </fragment>
```

```xml
    <item
        android:id="@+id/superadminHotelesFragment"
        android:icon="@drawable/ic_service_business"
        android:title="@string/nav_hoteles" />
```

- [ ] **Paso 2: la lista de hoteles**

`HotelesSaViewModel`: como `UsuariosSaViewModel` pero con
`ServiceLocator.superadmin().hoteles(...)` y un filtro booleano `soloSinPublicar`
—un hotel publicado funciona solo; los que esperan algo son los demás—. Expone
`UiState<List<Hotel>>` y `aviso`.

`HotelSaAdapter`: fila con `Hotel.getNombre()`, `Hotel.getUbicacionCorta()`, la
insignia de publicación ("Publicado" / "Sin publicar"), el administrador
(`MockData` no: el nombre se saca del propio `Hotel.getAdministradorId()`... **no**:
el identificador no es un nombre). El repositorio devuelve `Hotel`, y el nombre
del administrador no viaja dentro. Así que el ViewModel cruza: pide `hoteles()` y
`administradores()` y publica una lista de un tipo propio:

```java
    /** Un hotel con el nombre de quien lo administra, ya cruzado. */
    public static final class FilaHotel {

        @NonNull
        public final Hotel hotel;

        /** Nombre del administrador asignado, o {@code null} si no tiene. */
        @Nullable
        public final String administrador;

        FilaHotel(@NonNull Hotel hotel, @Nullable String administrador) {
            this.hotel = hotel;
            this.administrador = administrador;
        }

        public boolean tieneAdministrador() {
            return administrador != null;
        }
    }
```

`HotelSaAdapter` pasa a ser `ListAdapter<FilaHotel, ...>` e identifica por
`hotel.getId()`.

- [ ] **Paso 3: el ViewModel de la ficha**

```java
package org.iot.project.ui.superadmin.hoteles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.Periodicidad;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Ficha de un hotel para el superadministrador (§47): sus datos, su
 * administrador, su publicacion y sus ventas (RF-059).
 *
 * <p>Son dos consultas de dos repositorios distintos. El reporte sale de
 * {@code gestion()}, que ya autoriza al superadministrador por la excepcion
 * deliberada de {@code puedeGestionar}: el reporte de reservas de un hotel es el
 * mismo que ve su administrador y no hay razon para escribirlo dos veces.
 */
public class HotelSaViewModel extends ViewModel {

    /** Piezas que hay que reunir antes de poder pintar la ficha. */
    private static final int PIEZAS = 3;

    /** Todo lo que la ficha enseña. */
    public static final class Contenido {

        @NonNull
        public final Hotel hotel;

        /** Administrador asignado, o {@code null}. */
        @Nullable
        public final User administrador;

        /** Cuentas que se le pueden asignar, ya sin la que tiene. */
        @NonNull
        public final List<User> candidatos;

        @NonNull
        public final List<PeriodoDeVentas> ventas;

        Contenido(@NonNull Hotel hotel, @Nullable User administrador,
                  @NonNull List<User> candidatos, @NonNull List<PeriodoDeVentas> ventas) {
            this.hotel = hotel;
            this.administrador = administrador;
            this.candidatos = candidatos;
            this.ventas = ventas;
        }

        public boolean tieneAdministrador() {
            return administrador != null;
        }

        /** Si el hotel reune lo minimo para ofrecerse al cliente. */
        public boolean esPublicable() {
            return hotel.aptoParaPublicar();
        }
    }

    private final MutableLiveData<UiState<Contenido>> contenido = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    @Nullable
    private String hotelId;

    private Hotel hotel;
    private User administrador;
    private List<User> candidatos;
    private List<PeriodoDeVentas> ventas;

    private int tanda;
    private int recibidas;
    private boolean fallo;
    private boolean cargando;

    public LiveData<UiState<Contenido>> getContenido() {
        return contenido;
    }

    public LiveData<String> getAviso() {
        return aviso;
    }

    public void abrir(@NonNull String hotelId) {
        this.hotelId = hotelId;
        cargando = false;
        cargar();
    }

    public void cargar() {
        if (cargando || hotelId == null) {
            return;
        }
        UiState<Contenido> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    private void pedir() {
        int mia = ++tanda;
        recibidas = 0;
        fallo = false;
        hotel = null;
        administrador = null;
        candidatos = null;
        ventas = null;

        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel dato) {
                if (mia != tanda) {
                    return;
                }
                hotel = dato;
                pieza();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == tanda) {
                    fallar(mensaje);
                }
            }
        });

        ServiceLocator.superadmin().administradores(new ResultCallback<List<User>>() {
            @Override
            public void onExito(@NonNull List<User> datos) {
                if (mia != tanda) {
                    return;
                }
                candidatos = datos;
                pieza();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == tanda) {
                    fallar(mensaje);
                }
            }
        });

        // RF-059: el reporte del hotel, con el mismo periodo que ve su
        // administrador. Un solo periodo —el mes— porque la ficha enseña un
        // resumen y el reporte completo ya existe en la pantalla del hotel.
        ServiceLocator.gestion().ventas(hotelId, Periodicidad.MENSUAL,
                new ResultCallback<List<PeriodoDeVentas>>() {
                    @Override
                    public void onExito(@NonNull List<PeriodoDeVentas> datos) {
                        if (mia != tanda) {
                            return;
                        }
                        ventas = datos;
                        pieza();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (mia == tanda) {
                            fallar(mensaje);
                        }
                    }
                });
    }

    private void pieza() {
        if (fallo) {
            return;
        }
        recibidas++;
        if (recibidas < PIEZAS) {
            return;
        }
        Hotel listo = hotel;
        List<User> cuentas = candidatos;
        List<PeriodoDeVentas> reporte = ventas;
        if (listo == null || cuentas == null || reporte == null) {
            fallar("No pudimos cargar la ficha del hotel.");
            return;
        }

        cargando = false;
        contenido.setValue(UiState.success(new Contenido(listo,
                administradorDe(listo, cuentas), candidatosPara(listo, cuentas), reporte)));
    }

    @Nullable
    private static User administradorDe(@NonNull Hotel hotel, @NonNull List<User> cuentas) {
        String id = hotel.getAdministradorId();
        if (id == null) {
            return null;
        }
        for (User cuenta : cuentas) {
            if (cuenta.getId().equals(id)) {
                return cuenta;
            }
        }
        // El hotel apunta a alguien que ya no esta activo: se dice con un nulo
        // en vez de inventar un nombre.
        return null;
    }

    @NonNull
    private static List<User> candidatosPara(@NonNull Hotel hotel, @NonNull List<User> cuentas) {
        List<User> libres = new ArrayList<>();
        for (User cuenta : cuentas) {
            if (!cuenta.getId().equals(hotel.getAdministradorId())) {
                libres.add(cuenta);
            }
        }
        return libres;
    }

    private void fallar(@NonNull String mensaje) {
        if (fallo) {
            return;
        }
        fallo = true;
        cargando = false;
        contenido.setValue(UiState.<Contenido>error(mensaje));
    }

    // ------------------------------------------------------------------
    //  Acciones
    // ------------------------------------------------------------------

    /**
     * RF-008: asignar un administrador.
     *
     * <p>Recarga la ficha entera: el nombre que se enseña y la lista de
     * candidatos cambian los dos, y adelantar uno de los dos en memoria dejaria
     * la pantalla diciendo dos cosas distintas del mismo hotel.
     */
    public void asignar(@NonNull String usuarioId) {
        if (hotelId == null) {
            return;
        }
        ServiceLocator.superadmin().asignarAdministrador(hotelId, usuarioId,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel dato) {
                        aviso.setValue(dato.getNombre() + " ya tiene administrador.");
                        cargando = true;
                        pedir();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    /** Publicar o retirar el hotel, con la regla del repositorio (RF-007). */
    public void cambiarPublicacion(boolean publicado) {
        if (hotelId == null) {
            return;
        }
        ServiceLocator.gestion().cambiarPublicacion(hotelId, publicado,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel dato) {
                        aviso.setValue(publicado
                                ? dato.getNombre() + " ya se ofrece en el catálogo."
                                : dato.getNombre() + " dejó de ofrecerse en el catálogo.");
                        cargando = true;
                        pedir();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    public void consumirAviso() {
        aviso.setValue(null);
    }
}
```

**Antes de escribirlo**, comprueba los nombres reales de `Periodicidad` y
`PeriodoDeVentas` en `models/` y la firma exacta de
`GestionHotelRepository.ventas(...)`: están en la interfaz que ya existe, y la
ficha reutiliza esa consulta tal cual.

- [ ] **Paso 4: la hoja de asignación**

```java
package org.iot.project.ui.superadmin.hoteles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.databinding.SheetAsignarAdministradorBinding;
import org.iot.project.models.User;

import java.util.Collections;
import java.util.List;

/**
 * Elegir quien administra el hotel (RF-008).
 *
 * <p>No tiene ViewModel propio: enseña la lista de candidatos que la ficha ya
 * trae cargada y devuelve la eleccion. Pedirla otra vez al repositorio seria
 * enseñar dos veces lo mismo y poder enseñar dos listas distintas.
 */
public class AsignarAdministradorSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AsignarAdministradorSheet";

    /** Quien recibe la eleccion. */
    public interface AlElegir {
        void elegir(@NonNull String usuarioId);
    }

    private static final String ARG_CANDIDATOS = "candidatos";

    private SheetAsignarAdministradorBinding binding;

    /** La ficha, que es quien escucha. */
    @Nullable
    private AlElegir alElegir;

    public void setAlElegir(@Nullable AlElegir alElegir) {
        this.alElegir = alElegir;
    }

    /** Los identificadores de los candidatos, que es lo unico que el grafo admite. */
    @NonNull
    public static AsignarAdministradorSheet newInstance(@NonNull List<User> candidatos) {
        String[] ids = new String[candidatos.size()];
        String[] nombres = new String[candidatos.size()];
        for (int i = 0; i < candidatos.size(); i++) {
            ids[i] = candidatos.get(i).getId();
            nombres[i] = candidatos.get(i).getNombreCompleto();
        }
        Bundle argumentos = new Bundle();
        argumentos.putStringArray(ARG_CANDIDATOS, ids);
        argumentos.putStringArray("nombres", nombres);

        AsignarAdministradorSheet hoja = new AsignarAdministradorSheet();
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetAsignarAdministradorBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        String[] ids = arguments == null ? new String[0]
                : arguments.getStringArray(ARG_CANDIDATOS);
        String[] nombres = arguments == null ? new String[0]
                : arguments.getStringArray("nombres");
        if (ids == null || nombres == null || ids.length != nombres.length) {
            dismiss();
            return;
        }

        AdministradorElegibleAdapter adaptador =
                new AdministradorElegibleAdapter(this::elegir);
        binding.saAsignarLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saAsignarLista.setAdapter(adaptador);
        adaptador.submitList(emparejar(ids, nombres));

        boolean hay = ids.length > 0;
        binding.saAsignarLista.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.saAsignarVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
        if (!hay) {
            binding.saAsignarVacio.conIcono(R.drawable.ic_person_group)
                    .conTitulo(R.string.sa_asignar_vacio_titulo)
                    .conMensaje(R.string.sa_asignar_vacio_mensaje);
        }
    }

    private void elegir(@NonNull String usuarioId) {
        if (alElegir != null) {
            alElegir.elegir(usuarioId);
        }
        dismiss();
    }

    /** Une los dos arreglos del argumento en la lista que el adaptador espera. */
    @NonNull
    private static List<AdministradorElegibleAdapter.Candidato> emparejar(
            @NonNull String[] ids, @NonNull String[] nombres) {
        List<AdministradorElegibleAdapter.Candidato> candidatos = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            candidatos.add(new AdministradorElegibleAdapter.Candidato(ids[i], nombres[i]));
        }
        return candidatos;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saAsignarLista.setAdapter(null);
        binding = null;
    }
}
```

El `AlElegir` se ata **después** de crear la hoja y **antes** de `show(...)`, y
se suelta en `onDestroyView`: si la hoja sobrevive a la rotación de la pantalla,
el escucha viejo apuntaría a un Fragment muerto.

- [ ] **Paso 5: el adaptador de candidatos**

```java
package org.iot.project.ui.superadmin.hoteles;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemAdministradorElegibleBinding;

/**
 * Cuentas que se pueden asignar a un hotel (RF-008).
 *
 * <p>El candidato se identifica por su par identificador-nombre y no por un
 * {@code User}: la hoja recibe su lista por argumentos, y un {@code User} no es
 * serializable sin arrastrar medio modelo.
 */
public class AdministradorElegibleAdapter
        extends ListAdapter<AdministradorElegibleAdapter.Candidato,
        AdministradorElegibleAdapter.Fila> {

    /** Identificador y nombre de una cuenta asignable. */
    public static final class Candidato {

        @NonNull
        public final String id;

        @NonNull
        public final String nombre;

        public Candidato(@NonNull String id, @NonNull String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    /** Lo que la hoja hace cuando se elige un candidato. */
    public interface AlElegir {
        void elegir(@NonNull String usuarioId);
    }

    private final AlElegir alElegir;

    public AdministradorElegibleAdapter(@NonNull AlElegir alElegir) {
        super(DIFF);
        this.alElegir = alElegir;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemAdministradorElegibleBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        Candidato candidato = getItem(posicion);
        fila.binding.saCandidatoNombre.setText(candidato.nombre);
        fila.binding.getRoot().setOnClickListener(v -> alElegir.elegir(candidato.id));
    }

    static class Fila extends RecyclerView.ViewHolder {

        final ItemAdministradorElegibleBinding binding;

        Fila(@NonNull ItemAdministradorElegibleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<Candidato> DIFF =
            new DiffUtil.ItemCallback<Candidato>() {
                @Override
                public boolean areItemsTheSame(@NonNull Candidato a, @NonNull Candidato b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Candidato a, @NonNull Candidato b) {
                    return a.nombre.equals(b.nombre);
                }
            };
}
```

`item_administrador_elegible.xml`: fila de una línea —identificador
`sa_candidato_nombre`, `TextAppearance.App.Body1`, 16 dp de padding vertical y
`?attr/selectableItemBackground` de fondo— más el `ic_chevron_right` a la
derecha. `sheet_asignar_administrador.xml`: el mismo patrón que
`sheet_filtros.xml` —título, `RecyclerView` `sa_asignar_lista` y un
`EmptyStateView` `sa_asignar_vacio`—.

- [ ] **Paso 6: la ficha, en cuatro bloques**

El orden de los bloques no es casual: primero lo que el hotel **es**, después
quién lo lleva, después si se ofrece, y al final cómo va. Lo que se decide
—asignar, publicar— va antes que lo que solo se consulta.

`fragment_superadmin_hotel.xml`: cabecera `sa_header` con `mostrarVolver(...)`,
`NestedScrollView` con `sa_contenido`, `sa_esqueleto`, `sa_error`. Dentro, cuatro
tarjetas:

1. **Datos del hotel** — `sa_ficha_nombre`, `sa_ficha_ubicacion`
   (`getUbicacionCorta()`), `sa_ficha_direccion`, `sa_ficha_descripcion`,
   `RatingBadgeView` `sa_ficha_rating`, y las filas `sa_ficha_fotos`
   ("4 de 4 fotografías" o "2 de 4") y `sa_ficha_habitaciones` ("3 habitaciones").
2. **Administrador** — `sa_ficha_admin_nombre` o, si no tiene,
   `sa_ficha_admin_vacio` con el texto "Todavía no tiene administrador"; y el
   botón `sa_ficha_asignar` ("Asignar administrador" / "Cambiar administrador").
3. **Publicación** — `sa_ficha_publicacion_estado` (insignia "Publicado" /
   "Sin publicar"), `sa_ficha_publicacion_motivo` con lo que falta cuando no es
   publicable, y `sa_ficha_publicacion_accion` ("Publicar" / "Retirar").
   Publicar solo se ofrece si `Contenido.esPublicable()`; si no lo es, el botón
   se enseña **deshabilitado** y la frase de al lado dice qué falta: un botón que
   desaparece deja al superadministrador sin saber por qué no puede.
4. **Reporte de reservas (RF-059)** — `sa_ficha_ventas`, un `RecyclerView` con
   `item_periodo_reporte.xml` (el que ya usa la pantalla del administrador) y
   `sa_ficha_ventas_vacio`.

`HotelSaFragment` sigue el patrón de `AdminHomeFragment`: `pintar(UiState)` con
los cuatro estados, `conReintento` en el error, `Snackbar` para el aviso, y la
hoja de asignación:

```java
    private void abrirAsignacion(@NonNull List<User> candidatos) {
        AsignarAdministradorSheet hoja = AsignarAdministradorSheet.newInstance(candidatos);
        hoja.setAlElegir(usuarioId -> viewModel.asignar(usuarioId));
        hoja.show(getChildFragmentManager(), AsignarAdministradorSheet.TAG);
    }
```

Y el argumento del grafo se lee en `onViewCreated`:

```java
        String hotelId = getArguments() == null ? null
                : getArguments().getString(ARG_HOTEL_ID);
        if (hotelId == null) {
            // Sin hotel no hay ficha: se vuelve en vez de dejar una pantalla en
            // blanco. No deberia ocurrir, porque el unico que navega aqui es la
            // lista y siempre pasa un identificador.
            Navigation.findNavController(requireView()).popBackStack();
            return;
        }
        viewModel.abrir(hotelId);
```

- [ ] **Paso 7: las cadenas**

```xml
    <string name="sa_titulo_ficha_hotel">Ficha del hotel</string>
    <string name="sa_hoteles_todos">Todos</string>
    <string name="sa_hoteles_solo_sin_publicar">Sin publicar</string>
    <string name="sa_hotel_publicado">Publicado</string>
    <string name="sa_hotel_sin_publicar">Sin publicar</string>
    <string name="sa_hotel_sin_administrador">Sin administrador</string>
    <string name="sa_hotel_admin">Administrador</string>
    <string name="sa_hotel_admin_vacio">Todavía no tiene administrador</string>
    <string name="sa_asignar_administrador">Asignar administrador</string>
    <string name="sa_cambiar_administrador">Cambiar administrador</string>
    <string name="sa_asignar_titulo">¿Quién administra este hotel?</string>
    <string name="sa_asignar_vacio_titulo">No hay administradores libres</string>
    <string name="sa_asignar_vacio_mensaje">Todas las cuentas de administrador están activas y ya llevan un hotel.</string>
    <string name="sa_publicacion">Publicación</string>
    <string name="sa_publicar">Publicar</string>
    <string name="sa_retirar">Retirar del catálogo</string>
    <string name="sa_falta_publicar">Para publicarlo faltan fotografías y habitaciones.</string>
    <string name="sa_falta_fotos">Para publicarlo faltan fotografías (lleva %1$d de %2$d).</string>
    <string name="sa_falta_habitaciones">Para publicarlo hace falta al menos una habitación.</string>
    <string name="sa_ficha_fotos">Fotografías</string>
    <string name="sa_ficha_habitaciones">Habitaciones</string>
    <string name="sa_ventas_titulo">Reservas de los últimos meses</string>
    <string name="sa_ventas_vacio">Este hotel todavía no tiene reservas que reportar.</string>
    <string name="sa_hoteles_vacio_titulo">No hay hoteles que mostrar</string>
    <string name="sa_hoteles_vacio_mensaje">Cambia el filtro para ver los demás.</string>
```

- [ ] **Paso 8: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
En el emulador: Hoteles enseña los diez con su administrador; abrir H1 enseña sus
datos, Martín Rojas a cargo, "Publicado" y su reporte de reservas. Retirar H1 lo
deja sin publicar y el cliente deja de verlo. Volver a publicarlo lo devuelve.

- [ ] **Paso 9: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin/hoteles \
           app/src/main/res/layout/fragment_superadmin_hoteles.xml \
           app/src/main/res/layout/item_hotel_sa.xml \
           app/src/main/res/layout/fragment_superadmin_hotel.xml \
           app/src/main/res/layout/sheet_asignar_administrador.xml \
           app/src/main/res/layout/item_administrador_elegible.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/menu/menu_bottom_nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): hoteles, ficha, asignacion y reporte (RF-008, RF-059)"
```

---

## Tarea 13: Auditoría (RF-118 a RF-120)

**Ficheros:**
- Crear: `ui/superadmin/bitacora/BitacoraViewModel.java`, `BitacoraFragment.java`
- Crear: `res/layout/fragment_superadmin_bitacora.xml`
- Modificar: `res/navigation/nav_superadmin.xml`, `res/menu/menu_bottom_nav_superadmin.xml`, `res/values/strings.xml`

**Interfaces:**
- Consume: `SuperadminRepository.bitacora()`, `BitacoraAdapter`, `item_bitacora.xml`
  (los dos de la tarea 9).
- Produce: el destino `R.id.superadminBitacoraFragment`, al que ya apunta el
  botón "Ver toda la auditoría" de la portada.

- [ ] **Paso 1: el destino y la pestaña**

```xml
    <fragment
        android:id="@+id/superadminBitacoraFragment"
        android:name="org.iot.project.ui.superadmin.bitacora.BitacoraFragment"
        android:label="@string/nav_auditoria"
        tools:layout="@layout/fragment_superadmin_bitacora" />
```

```xml
    <item
        android:id="@+id/superadminBitacoraFragment"
        android:icon="@drawable/ic_time"
        android:title="@string/nav_auditoria" />
```

- [ ] **Paso 2: el ViewModel**

```java
package org.iot.project.ui.superadmin.bitacora;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.LogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bitacora de la plataforma (RF-118 a RF-120).
 *
 * <p><b>El filtro vive aqui y no en el repositorio.</b> Es un filtro de texto:
 * si cada tecla pasara por el repositorio, cada tecla costaria los 400 ms de
 * latencia simulada y la lista iria por detras de lo que se escribe.
 *
 * <p>Se filtra sobre el detalle, el autor y el tipo de evento a la vez: quien
 * busca "Ana" busca lo que hizo, y quien busca "hotel" busca las acciones sobre
 * hoteles.
 */
public class BitacoraViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<LogEntry>>> contenido = new MutableLiveData<>();

    private List<LogEntry> todos = Collections.emptyList();

    @Nullable
    private String busqueda;

    /** Si el filtro enseña solo los movimientos que cambiaron algo. */
    private boolean soloAdministrativos;

    private boolean cargando;

    public LiveData<UiState<List<LogEntry>>> getContenido() {
        return contenido;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<LogEntry>> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    private void pedir() {
        ServiceLocator.superadmin().bitacora(new ResultCallback<List<LogEntry>>() {
            @Override
            public void onExito(@NonNull List<LogEntry> datos) {
                cargando = false;
                todos = datos;
                publicar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    public void buscar(@Nullable String texto) {
        busqueda = texto == null || texto.trim().isEmpty() ? null : texto.trim().toLowerCase();
        publicar();
    }

    public void filtrarAdministrativos(boolean soloAdministrativos) {
        this.soloAdministrativos = soloAdministrativos;
        publicar();
    }

    private void publicar() {
        List<LogEntry> visibles = new ArrayList<>();
        for (LogEntry evento : todos) {
            if (soloAdministrativos && !esAdministrativo(evento)) {
                continue;
            }
            if (busqueda != null && !coincide(evento, busqueda)) {
                continue;
            }
            visibles.add(evento);
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<LogEntry>>empty()
                : UiState.success(visibles));
    }

    /**
     * Si el movimiento cambio algo del sistema.
     *
     * <p>Son las acciones que alguien con privilegios tomo sobre la plataforma,
     * que es lo que se viene a auditar; los inicios de sesion y las reservas se
     * consultan, pero no se vigilan.
     */
    private static boolean esAdministrativo(@NonNull LogEntry evento) {
        switch (evento.getEvento()) {
            case ACTIVACION:
            case DESACTIVACION:
            case APROBACION:
            case ACCION_ADMINISTRATIVA:
                return true;
            default:
                return false;
        }
    }

    private static boolean coincide(@NonNull LogEntry evento, @NonNull String buscado) {
        return evento.getDetalle().toLowerCase().contains(buscado)
                || evento.getUsuario().toLowerCase().contains(buscado)
                || evento.getEvento().getDisplayName().toLowerCase().contains(buscado);
    }
}
```

- [ ] **Paso 3: la pantalla**

`fragment_superadmin_bitacora.xml`: mismo esqueleto, con un
`SearchFieldView` `sa_bitacora_busqueda` (el componente que ya usa el buscador
del cliente), un `Chip` `sa_bitacora_solo_administrativos` ("Solo cambios") y el
`RecyclerView` `sa_bitacora_lista`, más `EmptyStateView` `sa_bitacora_vacio` con
`ic_time`.

`BitacoraFragment` sigue el patrón de las demás: `pintar(UiState)` con los cuatro
estados, `EmptyStateView` en el vacío diciendo que ningún movimiento coincide con
lo buscado, y el `SearchFieldView` atado a `viewModel.buscar(...)`.

**Sin `TextWatcher` con latencia:** el filtro es inmediato porque no sale del
ViewModel. Si `SearchFieldView` ya expone un escucha de texto, úsalo tal cual.

- [ ] **Paso 4: las cadenas**

```xml
    <string name="sa_bitacora_buscar">Buscar por movimiento o por persona</string>
    <string name="sa_bitacora_solo_cambios">Solo cambios</string>
    <string name="sa_bitacora_vacio_titulo">Ningún movimiento coincide</string>
    <string name="sa_bitacora_vacio_mensaje">Prueba con otro texto o quita el filtro.</string>
```

- [ ] **Paso 5: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
En el emulador: Auditoría enseña los movimientos ya sembrados más todo lo que se
hizo en las tareas anteriores —hoteles publicados y retirados, cuentas
desactivadas, el conductor habilitado—, del más reciente al más antiguo.
**Comprueba que ningún detalle lleva una contraseña ni un dato de acceso
(RC-042).**

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin/bitacora \
           app/src/main/res/layout/fragment_superadmin_bitacora.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/menu/menu_bottom_nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): auditoria de la plataforma (RF-118 a RF-120)"
```

---

## Tarea 14: Dar de alta un hotel (RF-007)

**Ficheros:**
- Crear: `ui/superadmin/hoteles/AltaHotelViewModel.java`, `AltaHotelFragment.java`
- Crear: `res/layout/fragment_superadmin_alta_hotel.xml`
- Modificar: `res/navigation/nav_superadmin.xml`, `res/values/strings.xml`
- Modificar: `ui/superadmin/hoteles/HotelesSaFragment.java` (el botón de alta)

**Interfaces:**
- Consume: `SuperadminRepository.registrarHotel(...)`.
- Produce: el destino `R.id.superadminAltaHotelFragment`.

- [ ] **Paso 1: el destino y el botón**

En `nav_superadmin.xml`, fuera de la barra:

```xml
    <fragment
        android:id="@+id/superadminAltaHotelFragment"
        android:name="org.iot.project.ui.superadmin.hoteles.AltaHotelFragment"
        android:label="@string/sa_titulo_alta_hotel"
        tools:layout="@layout/fragment_superadmin_alta_hotel" />
```

En `fragment_superadmin_hoteles.xml`, en la cabecera:

```java
        binding.saHeader.mostrarAccion(R.drawable.ic_add, R.string.sa_titulo_alta_hotel,
                v -> Navigation.findNavController(requireView())
                        .navigate(R.id.superadminAltaHotelFragment));
```

- [ ] **Paso 2: el ViewModel**

```java
package org.iot.project.ui.superadmin.hoteles;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;

/**
 * Alta de un hotel (RF-007).
 *
 * <p>El hotel nace **sin publicar y sin administrador**: son los dos pasos que
 * vienen despues —el dueño elige administrador, el superadministrador lo asigna,
 * el administrador rellena el hotel y lo publica— y darlo por hecho aqui los
 * saltaria.
 *
 * <p>Se piden solo los datos basicos. Las fotografias, las habitaciones y los
 * servicios necesitan a alguien que conozca el hotel, y ese es su administrador.
 */
public class AltaHotelViewModel extends ViewModel {

    private final MutableLiveData<UiState<Hotel>> alta = new MutableLiveData<>();

    public LiveData<UiState<Hotel>> getAlta() {
        return alta;
    }

    /** Arma el borrador y lo registra. La validacion la hace el repositorio. */
    public void registrar(@NonNull String nombre, @NonNull String ciudad,
                          @NonNull String distrito, @NonNull String direccion,
                          double latitud, double longitud) {
        Hotel borrador = new Hotel("", nombre, distrito, ciudad);
        borrador.setDireccion(direccion);
        borrador.setUbicacion(latitud, longitud);

        alta.setValue(UiState.loading());
        ServiceLocator.superadmin().registrarHotel(borrador, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel dato) {
                alta.setValue(UiState.success(dato));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                alta.setValue(UiState.error(mensaje));
            }
        });
    }

    /** El formulario se rearma tras un alta con exito o tras un error. */
    public void limpiar() {
        alta.setValue(null);
    }
}
```

- [ ] **Paso 3: la pantalla**

`fragment_superadmin_alta_hotel.xml`: cabecera `sa_header` con
`mostrarVolver(...)`, `NestedScrollView` con dos bloques y un botón al final:

- **Bloque 1, el hotel**: `sa_alta_nombre`, `sa_alta_ciudad`, `sa_alta_distrito`,
  `sa_alta_direccion` —cuatro `TextInputLayout` con su `TextInputEditText`—.
  **Sin `android:hint` en el `TextInputEditText`**: el del `TextInputLayout` ya
  se dibuja, y los dos juntos se superponen (es lo que comprueba el verificador).
- **Bloque 2, dónde está**: `sa_alta_latitud` y `sa_alta_longitud`, dos campos
  numéricos. Son los que el plano de seguimiento del taxi necesita (RF-084).
- `sa_alta_guardar`, el botón primario.

`AltaHotelFragment` valida lo que puede sin salir del formulario —que los cuatro
campos de texto no estén vacíos y que las dos coordenadas sean números— y deja
el resto al repositorio, que es quien tiene la regla. Al recibir el éxito:

```java
                // El aviso se da al volver, no aquí: la lista de hoteles es
                // quien tiene que enseñar el recién creado, y llegar a ella con
                // el Snackbar ya puesto es lo que hace evidente cuál es.
                Navigation.findNavController(requireView()).popBackStack();
                Snackbar.make(requireActivity().findViewById(android.R.id.content),
                        getString(R.string.sa_alta_hecha, hotel.getNombre()),
                        Snackbar.LENGTH_LONG).show();
```

- [ ] **Paso 4: las cadenas**

```xml
    <string name="sa_titulo_alta_hotel">Registrar un hotel</string>
    <string name="sa_alta_datos">Datos del hotel</string>
    <string name="sa_alta_ubicacion">Dónde está</string>
    <string name="sa_alta_nombre">Nombre del hotel</string>
    <string name="sa_alta_ciudad">Ciudad</string>
    <string name="sa_alta_distrito">Distrito</string>
    <string name="sa_alta_direccion">Dirección</string>
    <string name="sa_alta_latitud">Latitud</string>
    <string name="sa_alta_longitud">Longitud</string>
    <string name="sa_alta_guardar">Registrar el hotel</string>
    <string name="sa_alta_hecha">%1$s quedó registrado. Asígnale un administrador para que pueda publicarlo.</string>
    <string name="sa_alta_aviso">El hotel nace sin publicar: no aparecerá en el buscador hasta que su administrador lo complete y lo publique.</string>
```

Añade `sa_alta_aviso` como un `TextView` bajo el botón: es la parte del proceso
que el superadministrador tiene que entender para no creer que el alta falló.

- [ ] **Paso 5: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
En el emulador: registrar un hotel en Cusco; aparece en la lista como "Sin
publicar" y "Sin administrador"; el cliente **no** lo encuentra buscando "Cusco"
ni filtrando por ese distrito.

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/superadmin/hoteles \
           app/src/main/res/layout/fragment_superadmin_alta_hotel.xml \
           app/src/main/res/navigation/nav_superadmin.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(superadmin): alta de hotel sin publicar y sin administrador (RF-007)"
```

---

## Tarea 15: Publicar y retirar, en la pantalla del administrador

**Ficheros:**
- Modificar: `app/src/main/java/org/iot/project/ui/admin/hotel/HotelDatosViewModel.java`
- Modificar: `app/src/main/java/org/iot/project/ui/admin/hotel/HotelDatosFragment.java`
- Modificar: `app/src/main/res/layout/fragment_admin_hotel_datos.xml`
- Modificar: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consume: `GestionHotelRepository.cambiarPublicacion(...)` (tarea 7),
  `Hotel.isPublicado()`, `Hotel.aptoParaPublicar()`.

- [ ] **Paso 1: el ViewModel**

En `HotelDatosViewModel`, un `LiveData` más y la acción:

```java
    private final MutableLiveData<UiState<Hotel>> publicacion = new MutableLiveData<>();

    /** Resultado de publicar o retirar, para el aviso de una sola vez. */
    public LiveData<UiState<Hotel>> getPublicacion() {
        return publicacion;
    }

    /**
     * Publica el hotel o lo retira del catalogo (RF-007).
     *
     * <p>La regla —que hace falta para publicar— no se comprueba aqui: la aplica
     * el repositorio, que es el unico que no se puede saltar. Esta pantalla se
     * limita a enseñar lo que falta y a ofrecer el boton cuando se puede.
     */
    public void cambiarPublicacion(boolean publicado) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            publicacion.setValue(UiState.<Hotel>error(
                    "Todavía no tienes un hotel asignado."));
            return;
        }
        ServiceLocator.gestion().cambiarPublicacion(hotelId, publicado,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel dato) {
                        publicacion.setValue(UiState.success(dato));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        publicacion.setValue(UiState.error(mensaje));
                    }
                });
    }

    public void limpiarPublicacion() {
        publicacion.setValue(null);
    }
```

El `Hotel` que llega ya trae el estado nuevo **y** es el mismo objeto que el
ViewModel tiene cargado en `getHotel()`: el repositorio simulado devuelve el
objeto de `MockData`, así que basta con volver a publicar el que ya hay.

- [ ] **Paso 2: el bloque, en el layout**

En `fragment_admin_hotel_datos.xml`, como **primer** hijo del contenedor con
margen —antes de la tarjeta de datos—, porque es el estado de toda la pantalla y
tiene que verse sin desplazarse:

```xml
                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/admin_publicacion_tarjeta"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardCornerRadius="@dimen/radiusMd"
                    app:cardElevation="0dp"
                    app:strokeColor="@color/colorDivider"
                    app:strokeWidth="1dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="@dimen/spaceMd">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal">

                            <TextView
                                android:id="@+id/admin_publicacion_estado"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:background="@drawable/bg_badge_success"
                                android:paddingHorizontal="@dimen/spaceSm"
                                android:paddingVertical="@dimen/spaceXs"
                                android:textAppearance="@style/TextAppearance.App.Caption"
                                tools:text="Publicado" />

                            <TextView
                                android:id="@+id/admin_publicacion_titulo"
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_marginStart="@dimen/spaceSm"
                                android:layout_weight="1"
                                android:textAppearance="@style/TextAppearance.App.Subtitle1"
                                android:textColor="@color/colorOnSurface"
                                tools:text="Tu hotel se está mostrando a los clientes" />
                        </LinearLayout>

                        <TextView
                            android:id="@+id/admin_publicacion_motivo"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="@dimen/spaceSm"
                            android:textAppearance="@style/TextAppearance.App.Body2"
                            android:textColor="@color/colorOnSurfaceVariant"
                            tools:text="Para publicarlo faltan fotografías (lleva 2 de 4)." />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/admin_publicacion_accion"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_gravity="end"
                            android:layout_marginTop="@dimen/spaceSm"
                            tools:text="Publicar" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
```

- [ ] **Paso 3: el bloque, en el Fragment**

```java
    /**
     * El estado de publicacion del hotel (RF-007).
     *
     * <p>El boton se enseña deshabilitado cuando el hotel no esta completo, en
     * vez de esconderlo: un boton que no aparece deja al administrador sin saber
     * por que no puede publicar, y la frase de al lado es justo la respuesta.
     */
    private void pintarPublicacion(@NonNull Hotel hotel) {
        boolean publicado = hotel.isPublicado();
        boolean publicable = hotel.aptoParaPublicar();

        binding.adminPublicacionEstado.setText(publicado
                ? R.string.sa_hotel_publicado : R.string.sa_hotel_sin_publicar);
        binding.adminPublicacionEstado.setBackgroundResource(publicado
                ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);
        binding.adminPublicacionTitulo.setText(publicado
                ? R.string.admin_publicacion_visible
                : R.string.admin_publicacion_oculto);

        if (publicado) {
            binding.adminPublicacionMotivo.setText(R.string.admin_publicacion_retirar_ayuda);
            binding.adminPublicacionAccion.setEnabled(true);
            binding.adminPublicacionAccion.setText(R.string.sa_retirar);
            binding.adminPublicacionAccion.setOnClickListener(
                    v -> viewModel.cambiarPublicacion(false));
            return;
        }

        binding.adminPublicacionMotivo.setText(motivoParaPublicar(hotel));
        binding.adminPublicacionAccion.setEnabled(publicable);
        binding.adminPublicacionAccion.setText(R.string.sa_publicar);
        binding.adminPublicacionAccion.setOnClickListener(
                v -> viewModel.cambiarPublicacion(true));
    }

    /** Que le falta al hotel para poder publicarse, en una frase. */
    @NonNull
    private CharSequence motivoParaPublicar(@NonNull Hotel hotel) {
        boolean faltanFotos = !hotel.cumpleMinimoFotos();
        boolean faltanHabitaciones = hotel.getHabitaciones().isEmpty();
        if (faltanFotos && faltanHabitaciones) {
            return getString(R.string.admin_falta_fotos_y_habitaciones);
        }
        if (faltanFotos) {
            return getString(R.string.sa_falta_fotos,
                    hotel.getFotos().size(), Hotel.MIN_FOTOS);
        }
        if (faltanHabitaciones) {
            return getString(R.string.sa_falta_habitaciones);
        }
        return getString(R.string.admin_publicacion_listo);
    }
```

Ata también el observador del aviso, junto a los que ya tiene la pantalla:

```java
        viewModel.getPublicacion().observe(getViewLifecycleOwner(), estado -> {
            if (estado == null) {
                return;
            }
            if (estado.isSuccess()) {
                Snackbar.make(binding.getRoot(),
                        getString(R.string.admin_publicacion_hecha,
                                estado.requireData().getNombre()),
                        Snackbar.LENGTH_LONG).show();
            } else if (estado.getStatus() == UiState.Status.ERROR) {
                Snackbar.make(binding.getRoot(), estado.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
            viewModel.limpiarPublicacion();
        });
```

Y llama a `pintarPublicacion(hotel)` dentro del pintado del hotel que ya existe.

- [ ] **Paso 4: las cadenas**

```xml
    <string name="admin_publicacion_visible">Tu hotel se está mostrando a los clientes</string>
    <string name="admin_publicacion_oculto">Tu hotel todavía no se muestra a los clientes</string>
    <string name="admin_publicacion_listo">Está todo listo para publicarlo.</string>
    <string name="admin_publicacion_retirar_ayuda">Si lo retiras, dejará de aparecer en el buscador. Las reservas que ya existen no se pierden.</string>
    <string name="admin_falta_fotos_y_habitaciones">Para publicarlo faltan fotografías y habitaciones.</string>
    <string name="admin_publicacion_hecha">%1$s ya se ofrece en el catálogo.</string>
```

- [ ] **Paso 5: compilar, probar y recorrer**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
python3 tools/verificar_recursos.py
```
En el emulador, como administrador de un hotel recién dado de alta: la tarjeta
dice "Sin publicar", la frase dice qué falta y el botón está deshabilitado.
Añadir cuatro fotografías y una habitación, y publicar. **Este es el primer
recorrido del Bloque C desde que se construyó: aprovecha para pasar por sus cinco
pantallas** (inicio, reservas, habitaciones, servicios y perfil).

- [ ] **Paso 6: commit (previa autorización)**

```bash
git add -A app/src/main/java/org/iot/project/ui/admin/hotel \
           app/src/main/res/layout/fragment_admin_hotel_datos.xml \
           app/src/main/res/values/strings.xml
git commit -m "feat(admin): publicar y retirar el hotel desde su pantalla (RF-007)"
```

---

## Tarea 16: Verificación completa

**Ficheros:** ninguno. Esta tarea no escribe código: comprueba el bloque entero.

- [ ] **Paso 1: el verificador de recursos**

```bash
python3 /home/jleon/1TEL05-recuperado/tools/verificar_recursos.py
```
Esperado: `Sin problemas: todas las referencias resuelven.`

- [ ] **Paso 2: compilar y confirmar por marca de tiempo**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:assembleDebug
ls -l --time-style=full-iso /home/jleon/1TEL05-recuperado/app/build/outputs/apk/debug/*.apk
```
Gradle puede devolver un "BUILD SUCCESSFUL" en caché: **el APK tiene que llevar
la marca de tiempo de ahora**.

- [ ] **Paso 3: las pruebas, con el mismo cuidado**

```bash
./gradlew -p /home/jleon/1TEL05-recuperado :app:testDebugUnitTest --rerun-tasks
ls -l --time-style=full-iso /home/jleon/1TEL05-recuperado/app/build/test-results/testDebugUnitTest/*.xml
```
Cuenta los XML y comprueba que no hay `<failure>` ni `<error>` en ninguno.

- [ ] **Paso 4: el recorrido de la demostración, entero**

Con el emulador encendido (`/android-data/Sdk/platform-tools/adb devices`),
instalar y recorrer los nueve pasos del spec §10.2, en este orden:

1. Como cliente, el catálogo enseña los diez hoteles publicados.
2. Como superadmin, Hoteles: los diez, publicados, cada uno con su administrador.
3. Alta de un hotel en una ciudad nueva. Nace sin publicar y sin administrador.
4. Como cliente: ese hotel no aparece en la búsqueda, ni en recomendados, ni en
   el filtro por ciudad, ni en el selector de distritos.
5. Como superadmin, asignarle a U4 desde la ficha del hotel.
6. Como U4: la portada no da error; dice que el hotel no está publicado y qué le
   falta. Rellenar fotos y una habitación, y publicar.
7. Como cliente: el hotel ya aparece, y por su ciudad y su distrito.
8. Como superadmin, retirarlo: el cliente deja de verlo. **Comprobar además que
   una reserva que ya existiera sobre ese hotel se sigue pintando**, porque
   `hotel(id)` no filtra.
9. Como superadmin: desactivar un cliente y comprobar que no puede volver a
   entrar; intentar desactivar a un superadministrador y ver que no se ofrece;
   habilitar a D3 y ver los tres movimientos en Auditoría.

Para mover la interfaz desde la terminal:
`/android-data/Sdk/platform-tools/adb shell uiautomator dump /sdcard/ui.xml`
seguido de `adb shell cat /sdcard/ui.xml`.

- [ ] **Paso 5: el administrador sin hotel**

Entrar con U4 **antes** del paso 5 del recorrido y comprobar que su portada dice
"Todavía no tienes un hotel" y no un error; y que Reservas, Habitaciones,
Servicios y Perfil dicen "Todavía no tienes un hotel asignado." en vez de "Ese
hotel no es el tuyo."

- [ ] **Paso 6: RC-042**

Leer la pantalla de Auditoría entera y confirmar que ningún detalle lleva
contraseñas, tokens ni datos de acceso.

- [ ] **Paso 7: registrar el resultado**

Anota en el hilo de trabajo qué pasos salieron y cuáles no. **Si algo falla, se
arregla antes de dar el bloque por cerrado**; no se anota como pendiente.

---

## Tarea 17: Dejar el contexto al día

**Ficheros:**
- Modificar: `docs/memory/CONTEXTO_FRONTEND.md`

- [ ] **Paso 1: actualizar el estado de los bloques**

En `CONTEXTO_FRONTEND.md` §5, donde figura el estado de los bloques A a D, añade
el Bloque E con lo mismo que se anotó para D: qué requisitos cierra (RF-005,
RF-006, RF-007, RF-008, RF-059, RF-077, RF-078, RF-118 a RF-120), qué pantallas
nuevas existen, y qué queda pendiente de verdad —RF-100 (el administrador
consulta el taxi de sus clientes)—.

- [ ] **Paso 2: anotar lo que este bloque cambió en lo anterior**

Tres cosas que quien siga tiene que saber:

- `SessionManager.HOTEL_ADMINISTRADO` ya no existe: el hotel administrado se lee
  del repositorio y puede ser `null`.
- `Hotel` tiene estado de publicación y administrador; el catálogo del cliente
  esconde los que no están publicados, salvo `hotel(id)`.
- `MockData.HOTELES` es mutable.

- [ ] **Paso 3: commit (previa autorización)**

```bash
git add docs/memory/CONTEXTO_FRONTEND.md
git commit -m "docs: el estado del front tras el Bloque E"
```

---

## Autoevaluación del plan

**Cobertura del spec**

| Sección del spec | Tarea |
|---|---|
| §3 el estado del hotel | 1, 3, 4, 7 |
| §4 el administrador de un hotel | 5, 6 |
| §5.1 navegación | 9 (y una pestaña por tarea: 10, 11, 12, 13) |
| §5.2 Inicio | 9 |
| §5.3 Usuarios | 10 |
| §5.4 Conductores | 11 |
| §5.5 Hoteles y §5.6 ficha | 12 |
| §5.7 alta de hotel | 14 |
| §5.8 bitácora | 13 |
| §6 el lado del administrador | 15 |
| §7 repositorios | 5, 7, 8 |
| §8 componentes | 9 a 15 (ninguno nuevo) |
| §9 datos sembrados | 3 |
| §10 verificación | 16 |
| §11 decisiones abiertas | el comentario del grafo se corrige en 9; las otras dos quedan documentadas |

**Lo que este plan no hace, a propósito**

- No añade componentes nuevos a la biblioteca: todo se arma con `StatView`,
  `Chip`, `SettingRowView`, `RecyclerView`, `EmptyStateView`, `ErrorStateView`,
  `AppHeaderView` y los `bg_badge_*` que ya existen.
- No prueba repositorios en JVM: no se puede, y está explicado en las
  restricciones globales.
- No toca RF-100, que sigue pendiente y es del Bloque C.


---

## Resultado

**Tareas 1 a 15:** construidas; el detalle de lo que existe y de lo que cambió en
lo anterior está en `CONTEXTO_FRONTEND.md` §5.

**Tarea 16, paso 1 a 3:** los cuatro comandos de §11 en verde —recursos, APK,
pruebas (102, 0 fallos) y los XML con su fecha comprobada.

**Tarea 16, paso 4 (el recorrido de §10.2):** los nueve pasos salieron, de un
tirón y sin reiniciar la app, más el paso 5 (el administrador sin hotel) y el
paso 6 (RC-042: la bitácora leída entera, sin contraseñas, tokens ni datos de
acceso en ningún detalle). El único paso que no salió como el spec lo cuenta es
el 9: **"los tres movimientos" no están definidos en ninguna parte** y la
implementación escribe una entrada por acción —lo que se ve al terminar el paso
es la bitácora con los tres últimos actos del superadministrador—. Se anota lo
que se comprobó, no lo que el spec sugiere.

**Tarea 16, paso 7 (lo que falló):** dos cosas, las dos arregladas antes de dar
el bloque por cerrado, no anotadas como pendientes: el "Desde S/ 0" de un hotel
nacido del alta —`Hotel.getPrecioDesde()` leía el campo guardado, que el alta no
pone, y ahora delega en `calcularPrecioDesde()`— y el "Esta cuenta no tiene un
hotel asignado." de diez pantallas del administrador, que pasa a hablarle a quien
lee. El detalle y las pruebas que lo protegen, en `CONTEXTO_FRONTEND.md` §5.

**Tarea 17 (el contexto):** hecha, salvo el paso 3 —el commit—, que espera
autorización del usuario.

**Después del cierre, una corrección pedida por el usuario:** el
superadministrador no tenía perfil y su único cerrar sesión estaba en la
cabecera de la portada, así que desde las otras cuatro secciones no había
salida. Ahora tiene el mismo perfil que el cliente y el administrador —con la
plataforma como tarjeta, en vez del hotel— y la barra pasó a ser *Inicio ·
Usuarios · Conductores · Hoteles · Perfil*: la bitácora dejó la barra —se abre
desde la portada y desde el perfil, y ganó la flecha de vuelta— y el icono de la
cabecera se retiró. El reparto de §5.1 de `BLOQUE_E_SUPERADMIN.md` está
actualizado y su §10.2 ganó el paso 10 con lo verificado en el emulador.
