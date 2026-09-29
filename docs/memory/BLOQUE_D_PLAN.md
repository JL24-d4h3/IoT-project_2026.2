# Bloque D — Conductor · Plan de implementación

> **Para quien ejecute esto:** sigue las tareas en orden. Cada paso lleva su
> comando y su resultado esperado. Las casillas (`- [ ]`) son para ir marcando.
>
> **Especificación:** [BLOQUE_D_CONDUCTOR.md](BLOQUE_D_CONDUCTOR.md) — el plan
> argumenta desde ahí; conviene leer las dos.

**Objetivo:** construir el rol Conductor (§46) y, con él, cerrar el flujo del
taxi, que hoy no puede terminar porque nada valida el código del cliente.

**Arquitectura:** las reglas puras viven en el dominio (`TaxiService`), el
repositorio queda como adaptador delgado, y la ubicación del conductor sale de
una interfaz (`FuenteUbicacion`) para que el GPS real entre después sin tocar
las pantallas.

**Stack:** Java, Views + ViewBinding, RecyclerView, Material, Navigation
Component. Gradle 9.1.0 / AGP 9.0.1, minSdk 34.

## Restricciones globales

- **Java, nunca Kotlin** (RT-002). **XML, nunca Compose** (§3).
- **RecyclerView** para toda lista (§48).
- Los mocks viven fuera de Activities/Fragments/adaptadores (§49).
- Todos los valores visuales salen de `res/values/` — nunca un dp o color suelto.
- Márgenes laterales de pantalla: siempre **16dp** (`space_base`).
- Toda pantalla con datos resuelve los **cinco estados** de §50.
- Mensajes de error orientados al usuario, nunca técnicos (§53).
- Comandos de verificación, siempre desde `/home/jleon/1TEL05-recuperado`:

```bash
python3 tools/verificar_recursos.py
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --rerun-tasks
```

> **Sobre los commits.** El repo tiene un solo commit y la rama es `main`. Los
> pasos de commit están escritos, pero **no se ejecuta ninguno hasta que el
> usuario lo autorice**; se le pregunta al llegar al primero.

---

## Ficheros

**Nuevos**

| Fichero | Responsabilidad |
|---|---|
| `utils/Distancia.java` | Semiverseno y formato legible. Función pura |
| `models/Ubicacion.java` | Par latitud/longitud |
| `models/OfertaDeTaxi.java` | Un servicio y a cuánto queda su recojo |
| `core/FuenteUbicacion.java` | De dónde salen las coordenadas del conductor |
| `data/mock/FuenteUbicacionSimulada.java` | Implementación simulada de hoy |
| `ui/driver/home/DriverHomeFragment.java` | Portada del conductor, dos caras |
| `ui/driver/home/DriverHomeViewModel.java` | Estado de la portada |
| `ui/driver/home/SolicitudAdapter.java` | Lista de solicitudes disponibles |
| `ui/driver/qr/ValidarCodigoSheet.java` | Validación del código del cliente |
| `res/layout/fragment_driver_home.xml` | Portada |
| `res/layout/item_solicitud_taxi.xml` | Fila de la lista de solicitudes |
| `res/layout/sheet_validar_codigo.xml` | Hoja de validación |

**Modificados**

| Fichero | Qué cambia |
|---|---|
| `models/TaxiService.java` | +`puedeAceptarlo`, +`avanzarPorConductor`, +`validarCodigo` |
| `data/repository/TaxiRepository.java` | +5 operaciones, −`avanzar`, −`confirmarQr` |
| `data/mock/MockTaxiRepository.java` | Implementa lo anterior; usa `FuenteUbicacion` |
| `data/mock/MockData.java` | Datos sembrados (§8 de la especificación) |
| `ui/components/TaxiRequestCardView.java` | +`setOnAceptar`, +distancia |
| `res/layout/view_taxi_request_card.xml` | +fila de distancia, +botón aceptar |
| `res/navigation/nav_driver.xml` | Destinos reales |
| `res/values/strings.xml` | Textos del conductor |
| `ui/common/PanelRolFragment.java` | Se le quita el rol conductor |
| `ui/client/taxi/TaxiFragment.java` | Usa `Distancia.legible` en vez de su copia |
| `docs/memory/CONTEXTO_FRONTEND.md` | §5, estado tras el bloque |

---

## Tarea 1: `Distancia` — semiverseno y formato

**Ficheros:** crear `app/src/main/java/org/iot/project/utils/Distancia.java`,
`app/src/test/java/org/iot/project/utils/DistanciaTest.java`

**Interfaces — produce:**

```java
public static double metrosEntre(double lat1, double lng1, double lat2, double lng2)
public static String legible(double metros)   // "850 m" | "1,9 km"
```

- [ ] **Paso 1: escribir la prueba que falla**

```java
package org.iot.project.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DistanciaTest {

    // Coordenadas reales de los datos sembrados (MockData).
    private static final double H1_LAT = -12.1219, H1_LNG = -77.0297;  // Casa del Mar, Lima
    private static final double H4_LAT = -13.5156, H4_LNG = -71.9785;  // Posada Cusco
    private static final double BASE_D1_LAT = -12.1060, BASE_D1_LNG = -77.0360;

    @Test
    public void laDistanciaDeUnPuntoASiMismoEsCero() {
        assertEquals(0d, Distancia.metrosEntre(H1_LAT, H1_LNG, H1_LAT, H1_LNG), 0.5d);
    }

    /**
     * Lima y Cusco estan a 569 km. Es el numero que justifica el umbral de
     * cercania: si esta prueba cambiara, el umbral habria que revisarlo.
     */
    @Test
    public void limaACuscoRondaLosQuinientosSetentaKilometros() {
        double km = Distancia.metrosEntre(H1_LAT, H1_LNG, H4_LAT, H4_LNG) / 1000d;
        assertTrue("esperado ~569 km, fue " + km, km > 560d && km < 580d);
    }

    @Test
    public void dosPuntosDeLaMismaCiudadQuedanMuyPorDebajoDeCienKilometros() {
        double km = Distancia.metrosEntre(BASE_D1_LAT, BASE_D1_LNG, H1_LAT, H1_LNG) / 1000d;
        assertTrue("esperado ~1,9 km, fue " + km, km < 5d);
    }

    @Test
    public void porDebajoDeUnKilometroSeLeeEnMetrosRedondeadosADiez() {
        assertEquals("850 m", Distancia.legible(847d));
        assertEquals("0 m", Distancia.legible(3d));
    }

    @Test
    public void aPartirDeUnKilometroSeLeeEnKilometrosConUnaDecimal() {
        assertEquals("1,9 km", Distancia.legible(1896d));
        assertEquals("1,0 km", Distancia.legible(1000d));
    }
}
```

- [ ] **Paso 2: comprobar que falla**

```bash
./gradlew :app:testDebugUnitTest --tests org.iot.project.utils.DistanciaTest
```

Esperado: **FAIL** — `cannot find symbol: class Distancia`.

- [ ] **Paso 3: implementar**

```java
package org.iot.project.utils;

import java.util.Locale;

/**
 * Distancias sobre la Tierra (RF-089).
 *
 * <p>Es una funcion pura y sin Android a proposito: la usan el filtro de
 * cercania del conductor, la tarjeta de solicitud y el plano de seguimiento, y
 * una formula mal copiada en tres sitios da tres respuestas distintas para el
 * mismo par de puntos.
 *
 * <p>Se usa el semiverseno y no la distancia euclidea sobre grados: los grados
 * de longitud miden distinto segun la latitud —a la altura de Lima un grado de
 * longitud es un 2,4% mas corto que uno de latitud—, y con distancias de
 * cientos de kilometros ese error deja de ser despreciable.
 */
public final class Distancia {

    /** Radio medio terrestre, en metros. */
    private static final double RADIO_TIERRA_M = 6371000d;

    private Distancia() {
    }

    /** Distancia en metros entre dos coordenadas. */
    public static double metrosEntre(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RADIO_TIERRA_M * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /**
     * Metros o kilometros, segun lo que se lea mejor: "850 m", "1,9 km".
     *
     * <p>Por debajo del kilometro se redondea a decenas: decir "847 m" anuncia
     * una precision que el dato no tiene.
     */
    public static String legible(double metros) {
        if (metros < 1000d) {
            return Math.round(metros / 10d) * 10 + " m";
        }
        return String.format(Locale.getDefault(), "%.1f", metros / 1000d)
                .replace('.', ',') + " km";
    }
}
```

- [ ] **Paso 4: comprobar que pasa**

```bash
./gradlew :app:testDebugUnitTest --tests org.iot.project.utils.DistanciaTest
```

Esperado: **PASS**, 5 pruebas.

- [ ] **Paso 5: commit** (previa autorización del usuario)

```bash
git add app/src/main/java/org/iot/project/utils/Distancia.java \
        app/src/test/java/org/iot/project/utils/DistanciaTest.java
git commit -m "Bloque D: distancia entre coordenadas como funcion pura"
```

---

## Tarea 2: reglas del conductor en `TaxiService`

**Ficheros:** modificar `app/src/main/java/org/iot/project/models/TaxiService.java`,
crear `app/src/test/java/org/iot/project/models/TaxiConductorTest.java`

**Interfaces — produce:**

```java
public boolean puedeAceptarlo(@NonNull Driver conductor)   // RF-077, RF-090, RF-092
public void avanzarPorConductor(@NonNull TaxiStatus siguiente)  // RF-108/109, nunca FINALIZADO
public boolean validarCodigo(@Nullable String introducido) // RF-103
```

- [ ] **Paso 1: escribir la prueba que falla**

```java
package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TaxiConductorTest {

    private static TaxiService servicioSolicitado() {
        return new TaxiService("T9", "TAX-2026-9999", "B1", "U1");
    }

    private static Driver conductorHabilitado() {
        Driver d = new Driver("D9", "Ana", "Prueba");
        d.setHabilitado(true);
        return d;
    }

    private static Driver conductorNoHabilitado() {
        Driver d = new Driver("D8", "Beto", "Prueba");
        d.setHabilitado(false);
        return d;
    }

    // ---------------------------------------------------------- Aceptar

    @Test
    public void unConductorHabilitadoPuedeAceptarUnServicioSolicitado() {
        assertTrue(servicioSolicitado().puedeAceptarlo(conductorHabilitado()));
    }

    /** RF-077, RT-014: sin la aprobacion del Superadmin no se presta servicio. */
    @Test
    public void unConductorNoHabilitadoNoPuedeAceptar() {
        assertFalse(servicioSolicitado().puedeAceptarlo(conductorNoHabilitado()));
    }

    /** RF-092: un servicio ya asignado deja de estar disponible. */
    @Test
    public void unServicioYaAsignadoNoSePuedeAceptarOtraVez() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        assertFalse(servicio.puedeAceptarlo(conductorHabilitado()));
    }

    @Test
    public void unServicioYaIniciadoNoSePuedeAceptar() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarA(TaxiStatus.EN_CAMINO);
        assertFalse(servicio.puedeAceptarlo(conductorHabilitado()));
    }

    // ------------------------------------------------- Avanzar como conductor

    @Test
    public void elConductorAvanzaDeAsignadoAEnCaminoYDeAhiAEnTraslado() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarPorConductor(TaxiStatus.EN_CAMINO);
        servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
        assertTrue(servicio.getEstado() == TaxiStatus.EN_TRASLADO);
    }

    /**
     * RF-110: FINALIZADO solo se alcanza validando el codigo. Sin este rechazo,
     * canTransitionTo lo permitiria desde EN_TRASLADO y la regla quedaria solo
     * en la pantalla, que es donde no se puede probar.
     */
    @Test
    public void elConductorNoPuedeFinalizarAMano() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarPorConductor(TaxiStatus.EN_CAMINO);
        servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
        try {
            servicio.avanzarPorConductor(TaxiStatus.FINALIZADO);
            fail("deberia haber rechazado la finalizacion manual");
        } catch (IllegalStateException esperado) {
            assertTrue(servicio.getEstado() == TaxiStatus.EN_TRASLADO);
        }
    }

    /** RF-111: los saltos siguen prohibidos para el conductor. */
    @Test
    public void elConductorNoPuedeSaltarseEstados() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        try {
            servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
            fail("deberia haber rechazado el salto");
        } catch (IllegalStateException esperado) {
            assertTrue(servicio.getEstado() == TaxiStatus.ASIGNADO);
        }
    }

    // ------------------------------------------------- Validar el codigo

    @Test
    public void elCodigoCorrectoValida() {
        assertTrue(servicioSolicitado().validarCodigo("TAX-2026-9999"));
    }

    @Test
    public void elCodigoSeAceptaConEspaciosYEnMinusculas() {
        assertTrue(servicioSolicitado().validarCodigo("  tax-2026-9999  "));
    }

    @Test
    public void unCodigoDeOtroServicioNoValida() {
        assertFalse(servicioSolicitado().validarCodigo("TAX-2026-0733"));
    }

    @Test
    public void unCodigoVacioNoValida() {
        assertFalse(servicioSolicitado().validarCodigo(null));
        assertFalse(servicioSolicitado().validarCodigo(""));
        assertFalse(servicioSolicitado().validarCodigo("   "));
    }
}
```

- [ ] **Paso 2: comprobar que falla**

```bash
./gradlew :app:testDebugUnitTest --tests org.iot.project.models.TaxiConductorTest
```

Esperado: **FAIL** — `cannot find symbol: method puedeAceptarlo(Driver)`.

- [ ] **Paso 3: implementar**

En `TaxiService.java`, tras `asignarA(...)`:

```java
    /**
     * RF-077, RF-090, RF-092: si este conductor puede quedarse con el servicio.
     *
     * <p>Solo se acepta lo que esta en SOLICITADO y sin conductor: en cuanto
     * alguien lo acepta deja de estar disponible para los demas, y esa es la
     * unica forma de que dos conductores no se repartan el mismo pasajero.
     */
    public boolean puedeAceptarlo(@Nullable Driver conductor) {
        return estado == TaxiStatus.SOLICITADO
                && conductor != null
                && conductor.isHabilitado();
    }

    /**
     * RF-108, RF-109: avanzar el servicio desde el lado del conductor.
     *
     * <p>Se diferencia de {@link #avanzarA} en una sola cosa, y es la que
     * importa: <b>nunca llega a FINALIZADO</b>. RF-110 reserva ese estado a la
     * validacion del codigo del cliente, asi que un conductor no puede cerrar
     * un traslado pulsando un boton.
     *
     * @throws IllegalStateException si el destino es FINALIZADO, o si el salto
     *                               no es el del flujo (RF-111).
     */
    public void avanzarPorConductor(@NonNull TaxiStatus siguiente) {
        if (siguiente == TaxiStatus.FINALIZADO) {
            throw new IllegalStateException(
                    "Un servicio no se cierra a mano: se cierra validando el código del cliente.");
        }
        avanzarA(siguiente);
    }

    /**
     * RF-103: el codigo introducido tiene que ser el de este servicio.
     *
     * <p>Se compara sin distinguir mayusculas y sin espacios sobrantes porque
     * el codigo se teclea a mano copiandolo de la pantalla del cliente, y
     * rechazar por una mayuscula seria rechazar al conductor correcto.
     */
    public boolean validarCodigo(@Nullable String introducido) {
        return introducido != null && codigo.equalsIgnoreCase(introducido.trim());
    }
```

Y añadir los `import` que falten (`androidx.annotation.NonNull` ya está,
`androidx.annotation.Nullable` puede que no).

- [ ] **Paso 4: comprobar que pasa**

```bash
./gradlew :app:testDebugUnitTest --tests org.iot.project.models.TaxiConductorTest
```

Esperado: **PASS**, 11 pruebas.

- [ ] **Paso 5: comprobar que no se ha roto nada**

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks
```

Esperado: **PASS**, 61 + 5 + 11 = 77 pruebas.

- [ ] **Paso 6: commit** (previa autorización)

---

## Tarea 3: ubicación — modelo, interfaz y fuente simulada

**Ficheros:** crear `models/Ubicacion.java`, `core/FuenteUbicacion.java`,
`data/mock/FuenteUbicacionSimulada.java`,
`app/src/test/java/org/iot/project/data/mock/FuenteUbicacionSimuladaTest.java`

**Interfaces — produce:**

```java
// models/Ubicacion.java
public Ubicacion(double latitud, double longitud)
public double getLatitud(); public double getLongitud()

// core/FuenteUbicacion.java
Ubicacion posicion(@NonNull Driver conductor, @Nullable TaxiService enCurso)

// data/mock/FuenteUbicacionSimulada.java
public FuenteUbicacionSimulada()
```

- [ ] **Paso 1: `Ubicacion`**

```java
package org.iot.project.models;

/**
 * Un punto sobre la Tierra.
 *
 * <p>Existe para que las coordenadas del conductor viajen juntas. Sueltas son
 * dos doubles que se pueden intercambiar sin que el compilador diga nada, y
 * latitud y longitud intercambiadas no dan un error: dan un punto en el golfo
 * de Guinea.
 */
public final class Ubicacion {

    private final double latitud;
    private final double longitud;

    public Ubicacion(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }
}
```

- [ ] **Paso 2: escribir la prueba que falla**

```java
package org.iot.project.data.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Ubicacion;
import org.junit.Test;

public class FuenteUbicacionSimuladaTest {

    private final FuenteUbicacionSimulada fuente = new FuenteUbicacionSimulada();

    private static Driver conductor(String id) {
        return MockData.conductor(id);
    }

    /** Sin viaje en curso, el conductor esta en su base. */
    @Test
    public void unConductorLibreEstaEnSuBase() {
        Driver d1 = conductor("D1");
        Ubicacion base = fuente.posicion(d1, null);
        assertTrue("la base de D1 deberia estar en Lima",
                base.getLatitud() < -12d && base.getLatitud() > -13d);
    }

    /**
     * Durante un servicio, el conductor se mueve hacia el punto de recojo: un
     * conductor que se aleja no es un conductor que viene a recogerte.
     */
    @Test
    public void duranteUnServicioSeAcercaAlPuntoDeRecojo() {
        Driver d1 = conductor("D1");
        TaxiService servicio = new TaxiService("T99", "TAX-2026-9998", "B1", "U1");
        servicio.withRecojo(-12.1219, -77.0297).asignarA(d1);
        servicio.avanzarA(TaxiStatus.EN_CAMINO);

        Ubicacion primera = fuente.posicion(d1, servicio);
        servicio.actualizarUbicacion(primera.getLatitud(), primera.getLongitud(),
                java.time.LocalDateTime.now());
        Ubicacion segunda = fuente.posicion(d1, servicio);

        double antes = org.iot.project.utils.Distancia.metrosEntre(
                primera.getLatitud(), primera.getLongitud(), -12.1219, -77.0297);
        double despues = org.iot.project.utils.Distancia.metrosEntre(
                segunda.getLatitud(), segunda.getLongitud(), -12.1219, -77.0297);
        assertTrue("deberia acercarse: " + antes + " -> " + despues, despues < antes);
    }

    @Test
    public void nuncaDevuelveCeroCero() {
        Ubicacion base = fuente.posicion(conductor("D3"), null);
        assertTrue(base.getLatitud() != 0d || base.getLongitud() != 0d);
    }

    @Test
    public void todosLosConductoresSembradosTienenBase() {
        for (Driver d : MockData.CONDUCTORES) {
            assertTrue("sin base: " + d.getId(), fuente.posicion(d, null) != null);
        }
    }
}
```

- [ ] **Paso 3: comprobar que falla**

```bash
./gradlew :app:testDebugUnitTest --tests "*FuenteUbicacionSimuladaTest"
```

Esperado: **FAIL** — no compila, `FuenteUbicacionSimulada` no existe.

- [ ] **Paso 4: la interfaz**

```java
package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.Ubicacion;

/**
 * De donde salen las coordenadas del conductor (RF-098).
 *
 * <p>Hoy la unica implementacion es simulada: el conductor se acerca al punto
 * de recojo a saltos, como ya hacia el repositorio. La interfaz existe para que
 * el dia que se pida ubicacion real se escriba <em>otra</em> implementacion y no
 * haya que tocar ninguna pantalla.
 *
 * <p>Devuelve el dato de forma sincrona porque la ultima posicion conocida es
 * un dato que ya se tiene: una implementacion con GPS pediria actualizaciones y
 * guardaria la ultima, pero quien pregunta solo quiere saber donde esta.
 */
public interface FuenteUbicacion {

    /**
     * Donde esta el conductor ahora mismo.
     *
     * @param conductor el conductor, que siempre existe
     * @param enCurso   el servicio que tiene en curso, o {@code null} si esta libre
     */
    @NonNull
    Ubicacion posicion(@NonNull Driver conductor, @Nullable TaxiService enCurso);
}
```

- [ ] **Paso 5: la implementación simulada y la base sembrada**

En `MockData.java`, junto a `CONDUCTORES` (línea 75), hay que **añadir tres
imports**, que hoy no están: `java.util.HashMap`, `java.util.Map` y
`org.iot.project.models.Ubicacion`.

```java
    /**
     * Base de cada conductor: donde esta cuando no tiene viaje.
     *
     * <p>Sin esto no hay forma de decir a que distancia le queda una solicitud.
     * Son puntos reales de la ciudad de cada uno, no el origen de coordenadas:
     * un conductor parado en (0,0) pondria todas las solicitudes a 6.000 km.
     */
    public static final Map<String, Ubicacion> BASE_CONDUCTOR;

    public static Ubicacion baseDe(String driverId) {
        return BASE_CONDUCTOR.get(driverId);
    }
```

y en el bloque estático, junto a `CONDUCTORES = ...` (línea 129):

```java
        BASE_CONDUCTOR = Collections.unmodifiableMap(crearBases());
```

```java
    private static Map<String, Ubicacion> crearBases() {
        Map<String, Ubicacion> bases = new HashMap<>();
        // D1 y D2 trabajan en Lima; D3, en Cusco.
        bases.put("D1", new Ubicacion(-12.1060, -77.0360));  // Miraflores
        bases.put("D2", new Ubicacion(-12.1320, -77.0210));  // Barranco
        bases.put("D3", new Ubicacion(-13.5200, -71.9700));  // Cusco
        return bases;
    }
```

`data/mock/FuenteUbicacionSimulada.java`:

```java
package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.FuenteUbicacion;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.Ubicacion;

import java.time.LocalDateTime;

/**
 * Ubicacion simulada del conductor (RF-098).
 *
 * <p>Sin viaje en curso devuelve su base. Con un viaje en curso lo acerca al
 * punto de recojo, que es lo que hace creible el seguimiento del cliente
 * (RF-099): un conductor que se aleja del punto de recojo no es un conductor
 * que viene a recogerte.
 */
public class FuenteUbicacionSimulada implements FuenteUbicacion {

    /**
     * Donde aparece el conductor la primera vez, medido desde el punto de
     * recojo. Algo menos de 1,2 km: lo bastante lejos para que el seguimiento
     * tenga recorrido que ensenar, y lo bastante cerca para entrar entero en el
     * plano sin salirse por el borde.
     */
    private static final double DESPLAZE_INICIAL_LAT = 0.009;
    private static final double DESPLAZE_INICIAL_LNG = 0.006;

    /** Cuanto de la distancia restante se recorta en cada reporte. */
    private static final double FRACCION_ACERCAMIENTO = 0.4;

    @NonNull
    @Override
    public Ubicacion posicion(@NonNull Driver conductor, @Nullable TaxiService enCurso) {
        if (enCurso == null || !enCurso.hasRecojo()) {
            Ubicacion base = MockData.baseDe(conductor.getId());
            return base != null ? base : new Ubicacion(0d, 0d);
        }

        double recojoLat = enCurso.getLatRecojo();
        double recojoLng = enCurso.getLngRecojo();

        if (enCurso.getUltimaActualizacionUbicacion() == null) {
            return new Ubicacion(recojoLat + DESPLAZE_INICIAL_LAT,
                    recojoLng + DESPLAZE_INICIAL_LNG);
        }

        double lat = enCurso.getLatConductor();
        double lng = enCurso.getLngConductor();
        return new Ubicacion(lat + (recojoLat - lat) * FRACCION_ACERCAMIENTO,
                lng + (recojoLng - lng) * FRACCION_ACERCAMIENTO);
    }
}
```

> El `import java.time.LocalDateTime` solo hace falta si se usa; si no, se quita.

- [ ] **Paso 6: comprobar que pasa**

```bash
./gradlew :app:testDebugUnitTest --tests "*FuenteUbicacionSimuladaTest"
```

Esperado: **PASS**, 4 pruebas.

- [ ] **Paso 7: commit** (previa autorización)

---

## Tarea 4: `TaxiRepository` y `MockTaxiRepository`

**Ficheros:** modificar `data/repository/TaxiRepository.java`,
`data/mock/MockTaxiRepository.java`

**Interfaces — consume:** `FuenteUbicacion` (Tarea 3), `Distancia` (Tarea 1), las
reglas de `TaxiService` (Tarea 2).

**Interfaces — produce:** las seis operaciones del conductor —`perfilDe`,
`disponibles`, `serviciosEnCursoDe`, `aceptar`, `avanzarComoConductor`,
`validarCodigo`—; desaparecen `avanzar` y `confirmarQr`.

- [ ] **Paso 1: `OfertaDeTaxi`**

Fichero nuevo, `app/src/main/java/org/iot/project/models/OfertaDeTaxi.java`:

```java
package org.iot.project.models;

import androidx.annotation.NonNull;

/**
 * Una solicitud de traslado vista por un conductor: el servicio y lo que le
 * falta para llegar al punto de recojo (RF-088, RF-089).
 *
 * <p>La distancia no es una propiedad del servicio —el mismo traslado esta a
 * distinta distancia de cada conductor—, asi que no puede vivir en
 * {@link TaxiService}. Va aqui, que es el par que la pantalla necesita.
 *
 * <p>La calcula el repositorio y no la pantalla porque depende de donde esta el
 * conductor, y quien sabe eso es {@code FuenteUbicacion}.
 */
public final class OfertaDeTaxi {

    private final TaxiService servicio;
    private final double distanciaM;

    public OfertaDeTaxi(@NonNull TaxiService servicio, double distanciaM) {
        this.servicio = servicio;
        this.distanciaM = distanciaM;
    }

    @NonNull
    public TaxiService getServicio() {
        return servicio;
    }

    /** Metros del conductor al punto de recojo. */
    public double getDistanciaM() {
        return distanciaM;
    }
}
```

- [ ] **Paso 2: la interfaz**

Sustituir el cuerpo de `TaxiRepository` (conservando `servicioActivo`, `historial`,
`solicitar`, `obtener`, `calificar`):

```java
    /** RF-096: el perfil del conductor: su vehiculo, su nota y sus servicios. */
    void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback);

    /** RF-088, RF-089: solicitudes que este conductor puede atender, por cercania. */
    void disponibles(@NonNull String conductorId,
                     @NonNull ResultCallback<List<OfertaDeTaxi>> callback);

    /** RF-097: los servicios en curso de este conductor (ninguno, o uno). */
    void serviciosEnCursoDe(@NonNull String conductorId,
                            @NonNull ResultCallback<List<TaxiService>> callback);

    /** RF-090, RF-091, RF-092, RF-107: aceptar un pedido. */
    void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                 @NonNull ResultCallback<TaxiService> callback);

    /** RF-108, RF-109: avanzar el servicio. Nunca a FINALIZADO (RF-110). */
    void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                              @NonNull ResultCallback<TaxiService> callback);

    /** RF-102, RF-103, RF-104, RF-110: cerrar el servicio validando el codigo. */
    void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                       @NonNull String codigo, @NonNull ResultCallback<TaxiService> callback);
```

> **Se eliminan `avanzar(...)` y `confirmarQr(...)`.** No los llama nadie y el
> primero contradice a RF-090 al elegir él mismo al conductor.
>
> **Tres decisiones que conviene no deshacer.**
>
> Las dos consultas devuelven **listas**, aunque un conductor tenga como mucho un
> viaje en curso. `MockRepository` tiene tres puertas y no son intercambiables:
> `entregarLista` es la única que respeta `MockConfig.Modo.VACIO`, que es como se
> demuestra el estado vacío de §50. Y `entregarLista` **no captura excepciones**
> —las captura `ejecutar`—, así que dentro de su proveedor no puede ir nada que
> lance.
>
> Por eso `disponibles` **filtra en vez de validar**, igual que `servicioActivo`
> e `historial` hacen con el cliente: un identificador desconocido da lista vacía,
> no excepción.
>
> Y por eso el servicio en curso se pide como lista: "no tengo viaje" es el caso
> **normal** del conductor libre, no un error. Con `entregarDato` recibiría el
> estado de error y vería la pantalla equivocada.
>
> `validarCodigo` pide el conductor igual que `aceptar`: sin eso, cualquier
> conductor podría cerrar el traslado de otro con solo conocer su identificador.
>
> **Por qué `perfilDe` vive aquí y no en `UserRepository`.** El conductor no es un
> `User` (lo dice `SessionManager`: `getUsuarioId()` devuelve nulo para ese rol),
> y hoy no hay ningún repositorio que devuelva un `Driver`. Sin esta operación, la
> portada tendría que leer `MockData` directamente desde el ViewModel, que es justo
> lo que prohíbe §49. El repositorio del taxi *es* el repositorio del conductor.
>
> **Por qué `disponibles` devuelve `OfertaDeTaxi` y no `TaxiService`.** La tarjeta
> tiene que decir a cuántos kilómetros queda el recojo (RF-089), y la distancia
> solo se puede calcular desde la posición del conductor, que vive en
> `FuenteUbicacion` — dentro del repositorio. Si devolviera servicios a secas, el
> adaptador tendría que pedir la base por su cuenta, y para eso o lee `MockData`
> (§49) o aparece una séptima operación solo para eso. La distancia viaja con la
> oferta y el cálculo queda en un único sitio.

- [ ] **Paso 3: `MockTaxiRepository`**

- Constructor con inyección:

```java
    /** Umbral de cercania (RF-088). Ver {@link #estaCerca}. */
    private static final double RADIO_ATENCION_M = 100_000d;

    private final FuenteUbicacion ubicacion;

    public MockTaxiRepository() {
        this(new FuenteUbicacionSimulada());
    }

    public MockTaxiRepository(FuenteUbicacion ubicacion) {
        this.ubicacion = ubicacion;
    }
```

- `perfilDe`:

```java
    @Override
    public void perfilDe(@NonNull String conductorId,
                         @NonNull ResultCallback<Driver> callback) {
        entregarDato(callback, () -> MockData.conductor(conductorId),
                "No encontramos esta cuenta de conductor.");
    }
```

- `disponibles`:

```java
    @Override
    public void disponibles(@NonNull String conductorId,
                            @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        entregarLista(callback, () -> {
            Driver conductor = MockData.conductor(conductorId);
            Ubicacion base = conductor == null ? null : ubicacion.posicion(conductor, null);
            // Sin conductor conocido no hay a quien ofrecerle nada, y sin base no
            // se puede decir a que distancia le queda: en ambos casos, vacio. Los
            // datos sembrados dan siempre las dos cosas, y
            // MockDataTest.hayUnaSolicitudDisponibleEnLaCiudadDelConductorDeDemostracion
            // lo fija.
            if (base == null) {
                return Collections.emptyList();
            }

            List<OfertaDeTaxi> resultado = new ArrayList<>();
            for (TaxiService servicio : MockData.TAXIS) {
                if (servicio.puedeAceptarlo(conductor) && estaCerca(servicio, base)) {
                    resultado.add(new OfertaDeTaxi(servicio, distanciaAlRecojo(servicio, base)));
                }
            }
            // Lo mas cercano primero: es lo que el conductor quiere ver arriba.
            resultado.sort(Comparator.comparingDouble(OfertaDeTaxi::getDistanciaM));
            return resultado;
        });
    }
```

- `aceptar`:

```java
    @Override
    public void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                        @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            Driver conductor = exigirConductor(conductorId);
            TaxiService servicio = exigirServicio(taxiId);

            if (!servicio.puedeAceptarlo(conductor)) {
                throw new IllegalStateException(
                        "Este servicio ya no está disponible. Actualiza la lista.");
            }
            if (estaOcupado(conductor.getId())) {
                throw new IllegalStateException(
                        "Ya tienes un servicio en curso. Termínalo antes de aceptar otro.");
            }

            servicio.asignarA(conductor);
            reportarUbicacion(servicio);

            registrar("CAMBIO_ESTADO_TAXI", "Aceptó el servicio " + servicio.getCodigo()
                    + " hacia " + servicio.getDestino() + ".");
            return servicio;
        });
    }
```

- `serviciosEnCursoDe`:

```java
    @Override
    public void serviciosEnCursoDe(@NonNull String conductorId,
                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        entregarLista(callback, () -> {
            for (TaxiService servicio : MockData.TAXIS) {
                Driver asignado = servicio.getDriver();
                if (asignado != null && asignado.getId().equals(conductorId)
                        && servicio.getEstado().isActive()) {
                    return Collections.singletonList(servicio);
                }
            }
            // Vacio es la respuesta correcta, no un fallo: significa que el
            // conductor esta libre y la portada muestra su cara libre.
            return Collections.emptyList();
        });
    }
```

- `avanzarComoConductor`:

```java
    @Override
    public void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                                     @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicioDe(taxiId, conductorId);
            TaxiStatus actual = servicio.getEstado();

            // avanzarPorConductor rechaza FINALIZADO (RF-110) y los saltos (RF-111).
            servicio.avanzarPorConductor(actual.next());
            reportarUbicacion(servicio);

            registrar("CAMBIO_ESTADO_TAXI", "El servicio " + servicio.getCodigo()
                    + " pasó de " + actual.getDisplayName() + " a "
                    + servicio.getEstado().getDisplayName() + ".");
            return servicio;
        });
    }
```

- `validarCodigo`:

```java
    @Override
    public void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                              @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicioDe(taxiId, conductorId);

            // RF-103: se valida antes de tocar el estado. Un codigo que no es el
            // de este servicio no deja el traslado a medias.
            if (!servicio.validarCodigo(codigo)) {
                throw new IllegalArgumentException(
                        "Ese código no corresponde a este servicio. "
                                + "Pídeselo otra vez al cliente.");
            }
            if (!servicio.debeMostrarQr()) {
                throw new IllegalStateException(
                        "El servicio todavía no tiene un código que validar.");
            }

            // RF-110 y RF-111: se llega a FINALIZADO sin saltarse EN_TRASLADO.
            if (servicio.getEstado() == TaxiStatus.EN_CAMINO) {
                servicio.avanzarA(TaxiStatus.EN_TRASLADO);
            }
            servicio.avanzarA(TaxiStatus.FINALIZADO);

            registrar("CAMBIO_ESTADO_TAXI", "Validó el código del servicio "
                    + servicio.getCodigo() + "; el servicio finalizó.");
            return servicio;
        });
    }
```

- Auxiliares nuevos:

```java
    /**
     * Si el conductor puede llegar a este recojo (RF-088).
     *
     * <p>El umbral esta entre los dos extremos que separan "mi ciudad" de "otra
     * ciudad": en los datos sembrados, Lima y Cusco estan a 569 km, y dos puntos
     * de la misma ciudad, a menos de 2. Cien kilometros cae holgadamente entre
     * los dos, y deja margen para un traslado interprovincial corto sin que se
     * cuele una solicitud al otro extremo del pais.
     */
    private boolean estaCerca(TaxiService servicio, Ubicacion base) {
        return servicio.hasRecojo()
                && distanciaAlRecojo(servicio, base) <= RADIO_ATENCION_M;
    }

    private static double distanciaAlRecojo(TaxiService servicio, Ubicacion base) {
        return Distancia.metrosEntre(base.getLatitud(), base.getLongitud(),
                servicio.getLatRecojo(), servicio.getLngRecojo());
    }

    /** RF-093: el conductor existe en el sistema de gestion de taxistas. */
    private Driver exigirConductor(String conductorId) {
        Driver conductor = MockData.conductor(conductorId);
        if (conductor == null) {
            throw new IllegalArgumentException("No encontramos esta cuenta de conductor.");
        }
        return conductor;
    }

    /**
     * El servicio, exigiendo ademas que sea de este conductor.
     *
     * <p>Sin esta comprobacion, un conductor podria avanzar o cerrar el traslado
     * de otro con solo conocer su identificador.
     */
    private TaxiService exigirServicioDe(String taxiId, @NonNull String conductorId) {
        TaxiService servicio = exigirServicio(taxiId);
        Driver asignado = servicio.getDriver();
        if (asignado == null || !asignado.getId().equals(conductorId)) {
            throw new IllegalStateException("Este servicio no está asignado a ti.");
        }
        return servicio;
    }
```

- Y `reportarUbicacion` pasa a delegar en la fuente:

```java
    private void reportarUbicacion(TaxiService servicio) {
        Driver conductor = servicio.getDriver();
        if (conductor == null || !servicio.hasRecojo()) {
            return;
        }
        Ubicacion siguiente = ubicacion.posicion(conductor, servicio);
        servicio.actualizarUbicacion(siguiente.getLatitud(), siguiente.getLongitud(),
                LocalDateTime.now());
    }
```

> **Lo que hay que quitar de `MockTaxiRepository`**, porque se queda sin usar:
>
> | Se va | Por qué |
> |---|---|
> | `avanzar(...)` | RF-090: elegía él mismo al conductor. Le sustituye `aceptar` |
> | `confirmarQr(...)` | Le sustituye `validarCodigo`, que además comprueba quién cierra |
> | `conductorDisponible()` | Solo lo usaba `avanzar` |
> | `DESPLAZE_INICIAL_LAT/LNG`, `FRACCION_ACERCAMIENTO` | Se mudan a `FuenteUbicacionSimulada` |
>
> `exigirConductor` y `estaOcupado` **se quedan**: los usa `aceptar`. Y hay que
> añadir los `import` de `Distancia` y `Ubicacion`.
>
> Conviene comprobar que no queda ningún llamador antes de dar el paso por bueno:
> `grep -rn "conductorDisponible\|confirmarQr(" app/src/main/java`.

- [ ] **Paso 4: compilar**

```bash
./gradlew :app:assembleDebug
```

Esperado: **BUILD SUCCESSFUL**. Si falla por `avanzar`/`confirmarQr`, es que
quedaba un llamador: buscarlo con
`grep -rn "avanzar(\|confirmarQr(\|conductorDisponible" app/src/main/java`.

- [ ] **Paso 5: las 77 pruebas siguen pasando**

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks
```

Esperado: **PASS**, 77 pruebas.

- [ ] **Paso 6: commit** (previa autorización)

---

## Tarea 5: datos sembrados

**Ficheros:** modificar `data/mock/MockData.java`,
`app/src/test/java/org/iot/project/data/mock/MockDataTest.java`

- [ ] **Paso 1: escribir la prueba que falla**

Añadir a `MockDataTest`. El fichero ya importa `Booking`, `assertNotNull` y
`assertEquals`; hay que **añadir cinco imports**: `Driver`, `TaxiService`,
`Ubicacion`, `org.iot.project.core.SessionManager` y
`org.iot.project.utils.Distancia`.

```java
    /**
     * El cliente de un servicio de taxi tiene que ser el de su reserva.
     *
     * <p>Estuvo mal: T3 declaraba a U9 y su reserva (B2) era de U1. Se ve en
     * cuanto el conductor tiene que enseñar de quien es el pedido (RF-089).
     */
    @Test
    public void cadaServicioDeTaxiPerteneceAlClienteDeSuReserva() {
        for (TaxiService servicio : MockData.TAXIS) {
            Booking reserva = MockData.reserva(servicio.getBookingId());
            assertNotNull("servicio sin reserva: " + servicio.getId(), reserva);
            assertEquals("el cliente de " + servicio.getId() + " no es el de su reserva",
                    reserva.getClienteId(), servicio.getClienteId());
        }
    }

    /** El conductor de demostracion tiene que empezar libre, o no vera la lista. */
    @Test
    public void elConductorDeDemostracionNoTieneServicioEnCurso() {
        String demo = SessionManager.CONDUCTOR_ACTIVO;
        for (TaxiService servicio : MockData.TAXIS) {
            Driver asignado = servicio.getDriver();
            if (asignado != null && asignado.getId().equals(demo)) {
                assertTrue("D1 no deberia tener servicio activo al entrar",
                        !servicio.getEstado().isActive());
            }
        }
    }

    /** RF-088: sin al menos una solicitud cerca, la portada del conductor nace vacia. */
    @Test
    public void hayUnaSolicitudDisponibleEnLaCiudadDelConductorDeDemostracion() {
        Driver demo = MockData.conductor(SessionManager.CONDUCTOR_ACTIVO);
        Ubicacion base = MockData.baseDe(demo.getId());
        assertNotNull("el conductor de demostracion no tiene base", base);

        boolean hay = false;
        for (TaxiService servicio : MockData.TAXIS) {
            if (servicio.puedeAceptarlo(demo) && servicio.hasRecojo()
                    && Distancia.metrosEntre(base.getLatitud(), base.getLongitud(),
                            servicio.getLatRecojo(), servicio.getLngRecojo()) <= 100_000d) {
                hay = true;
                break;
            }
        }
        assertTrue("no hay ninguna solicitud que el conductor de demostracion pueda aceptar", hay);
    }
```

- [ ] **Paso 2: comprobar que fallan**

```bash
./gradlew :app:testDebugUnitTest --tests org.iot.project.data.mock.MockDataTest
```

Esperado: **FAIL** en las tres. La primera por la errata de T3, la segunda porque
D1 tiene T1, la tercera porque la única solicitud libre está en Cusco.

- [ ] **Paso 3: arreglar los datos**

En `crearTaxis()`, el método entero queda así (solo cambian la línea de `t1`,
la de `t3` y el bloque nuevo de `t4`):

```java
    private static List<TaxiService> crearTaxis() {
        // En curso: EN_CAMINO, con conductor asignado. El QR ya es visible (RF-109)
        TaxiService t1 = new TaxiService("T1", "TAX-2026-0733", "B1", "U1");
        t1.withRuta("Av. Malecón Cisneros 1240, Miraflores", "Aeropuerto Jorge Chávez")
                .withRecojo(-12.1219, -77.0297)
                .withProgramacion(HOY, LocalTime.of(9, 30), false)
                .withPasajeros(2)
                .withPrecio(0);
        // D2 y no D1: D1 es el conductor de demostracion y tiene que entrar libre
        // para poder ver la lista de solicitudes antes de aceptar nada. El cliente
        // U1 sigue viendo su taxi en camino, ahora con D2 al volante.
        t1.asignarA(CONDUCTORES.get(1));
        t1.avanzarA(TaxiStatus.EN_CAMINO);
        // A algo menos de 1,2 km del hotel de la reserva (H1), que es el punto
        // de recojo: el plano de seguimiento (RF-099) se dibuja desde ahi.
        t1.actualizarUbicacion(-12.1129, -77.0357, LocalDateTime.of(HOY, LocalTime.of(9, 12)));

        // Cerrado y calificado
        TaxiService t2 = new TaxiService("T2", "TAX-2026-0518", "B3", "U1");
        t2.withRuta("Jr. Unión 185, Barranco", "Aeropuerto Jorge Chávez")
                .withRecojo(-12.1466, -77.0206)
                .withProgramacion(HOY.minusDays(36), LocalTime.of(6, 0), false)
                .withPasajeros(2)
                .withPrecio(0);
        t2.asignarA(CONDUCTORES.get(1));
        t2.avanzarA(TaxiStatus.EN_CAMINO);
        t2.avanzarA(TaxiStatus.EN_TRASLADO);
        t2.avanzarA(TaxiStatus.FINALIZADO);
        t2.valorar(9f);

        // Recien solicitado, sin conductor todavia (RF-106). El cliente es el de
        // su reserva, B2, y B2 es de U1: la reserva manda. Es el caso de una
        // solicitud en otra ciudad, que el conductor de Lima no ve gracias al
        // filtro de cercania.
        TaxiService t3 = new TaxiService("T3", "TAX-2026-0734", "B2", "U1");
        t3.withRuta("Calle Plateros 145, Cusco", "Aeropuerto Velasco Astete")
                .withRecojo(-13.5156, -71.9785)
                .withProgramacion(HOY.plusDays(20), LocalTime.of(15, 0), true)
                .withPasajeros(2)
                .withPrecio(TaxiService.TARIFA_AEROPUERTO);

        // Solicitado en Lima, ligado a B6 (Diego Salas Pinto, Casa del Mar, que
        // sale en cuatro dias). Es la solicitud que el conductor de demostracion
        // puede aceptar: sin ella, la unica viva seria la de Cusco y su portada
        // naceria vacia.
        TaxiService t4 = new TaxiService("T4", "TAX-2026-0735", "B6", "U9");
        t4.withRuta("Av. Malecón Cisneros 1240, Miraflores", "Aeropuerto Jorge Chávez")
                .withRecojo(-12.1219, -77.0297)
                .withProgramacion(HOY, LocalTime.of(14, 30), false)
                .withPasajeros(3)
                .withPrecio(TaxiService.TARIFA_AEROPUERTO);

        return Arrays.asList(t1, t2, t3, t4);
    }
```

> **El código `TAX-2026-0735` es el que se teclea en el emulador** (Tarea 11).
> Conviene no cambiarlo sin cambiar también la prueba de recorrido.

Y `crearBases()` + `BASE_CONDUCTOR` de la Tarea 3.

- [ ] **Paso 4: comprobar que pasan**

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks
```

Esperado: **PASS**, 80 pruebas.

- [ ] **Paso 5: commit** (previa autorización)

---

## Tarea 6: `TaxiRequestCardView` — aceptar y distancia

**Ficheros:** modificar `ui/components/TaxiRequestCardView.java`,
`res/layout/view_taxi_request_card.xml`

**Interfaces — produce:**

```java
public void bind(@NonNull TaxiService servicio)              // sin cambios: historial del cliente
public void bind(@NonNull TaxiService servicio, double distanciaM)  // solicitud del conductor
public void setOnAceptar(@Nullable OnClickListener oyente)
```

- [ ] **Paso 1: el layout**

Ojo: la raíz de `view_taxi_request_card.xml` es un **`<merge>`**, no un
`LinearLayout`. Los dos bloques nuevos van dentro del `LinearLayout` interior,
**justo antes** del bloque `<com.google.android.material.button.MaterialButton
android:id="@+id/request_rate" ...>` (línea 166), para que queden después del
precio y antes de las acciones:

```xml
    <TextView
        android:id="@+id/request_distance"
        style="@style/TextAppearance.App.Caption"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/space_xs"
        tools:text="A 1,9 km de ti" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/request_accept"
        style="@style/Widget.App.Button.Primary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/space_base"
        android:text="@string/driver_aceptar"
        android:visibility="gone"
        tools:visibility="visible" />
```

El estilo `Widget.App.Button.Primary` **ya existe** (`values/styles.xml:15`), y
`TextAppearance.App.Caption` también; si alguno no resolviera,
`tools/verificar_recursos.py` lo diría en el Paso 3.

- [ ] **Paso 2: la clase**

```java
    private TextView distancia;
    private MaterialButton aceptar;
```

en `recogerVistas()`:

```java
        distancia = findViewById(R.id.request_distance);
        aceptar = findViewById(R.id.request_accept);
```

`bind(servicio)` delega:

```java
    /** La tarjeta del historial del cliente: sin distancia y sin nada que aceptar. */
    public void bind(@NonNull TaxiService servicio) {
        pintar(servicio, -1d);
    }

    /**
     * La tarjeta de una solicitud disponible (RF-089).
     *
     * @param distanciaM metros del conductor al punto de recojo, o negativo si
     *                   no se sabe
     */
    public void bind(@NonNull TaxiService servicio, double distanciaM) {
        pintar(servicio, distanciaM);
    }

    private void pintar(@NonNull TaxiService servicio, double distanciaM) {
        // ... el cuerpo actual de bind(), y al final:
        boolean hayDistancia = distanciaM >= 0d;
        distancia.setVisibility(hayDistancia ? VISIBLE : GONE);
        if (hayDistancia) {
            distancia.setText(getContext().getString(R.string.driver_a_distancia,
                    Distancia.legible(distanciaM)));
        }
    }
```

y:

```java
    /**
     * Que hacer al pulsar "Aceptar" (RF-090).
     *
     * <p>La visibilidad del boton la decide este metodo y no {@code pintar}: el
     * RecyclerView reutiliza las vistas, asi que quien sabe si la fila se puede
     * aceptar es quien la usa, no lo que se esta pintando. Con el oyente nulo el
     * boton desaparece, que es lo que quiere el historial del cliente.
     */
    public void setOnAceptar(@Nullable OnClickListener oyente) {
        aceptar.setOnClickListener(oyente);
        aceptar.setVisibility(oyente != null ? View.VISIBLE : View.GONE);
    }
```

> Por eso el adaptador de la Tarea 7 llama **siempre** a `setOnAceptar`, aunque
> el oyente sea el mismo: es lo que deja el boton en el estado correcto en cada
> fila reciclada.

- [ ] **Paso 3: el cliente sigue igual**

```bash
./gradlew :app:assembleDebug && python3 tools/verificar_recursos.py
```

Esperado: **BUILD SUCCESSFUL** y *"Sin problemas: todas las referencias resuelven"*.

- [ ] **Paso 4: commit** (previa autorización)

---

## Tarea 7: portada del conductor — cara libre

**Ficheros:** crear `res/layout/fragment_driver_home.xml`,
`ui/driver/home/DriverHomeViewModel.java`, `ui/driver/home/SolicitudAdapter.java`,
`res/layout/item_solicitud_taxi.xml`, `ui/driver/home/DriverHomeFragment.java`;
modificar `res/values/strings.xml`

**Interfaces — consume:** `TaxiRepository.perfilDe`, `.disponibles`,
`.serviciosEnCursoDe` (Tarea 4).

**Interfaces — produce:**

```java
// DriverHomeViewModel
LiveData<UiState<Estado>> getContenido()
LiveData<String> getAviso(); void consumirAviso()
void cargar(); void recargar(); void avanzar(); void aceptar(String taxiId)
void validarCodigo(String taxiId, String codigo, ResultCallback<TaxiService> cb)

public static final class Estado {
    Driver conductor;                 // su vehiculo, su nota, sus servicios
    TaxiService activo;               // null si esta libre -> cara libre
    List<OfertaDeTaxi> disponibles;   // ya ordenadas por cercania, con su distancia
}
```

- [ ] **Paso 1: los textos**

En `strings.xml`:

```xml
    <string name="driver_aceptar">Aceptar</string>
    <string name="driver_a_distancia">A %1$s de ti</string>
    <string name="driver_sin_servicio_titulo">No hay solicitudes ahora mismo</string>
    <string name="driver_sin_servicio">Cuando un cliente pida un traslado al aeropuerto cerca de ti, aparecerá aquí.</string>
    <string name="driver_servicio_activo">Servicio en curso</string>
    <string name="driver_voy_en_camino">Voy en camino</string>
    <string name="driver_inicie_traslado">Inicié el traslado</string>
    <string name="driver_validar_codigo">Validar código del cliente</string>
    <string name="driver_recogida">Punto de recojo</string>
    <string name="driver_destino">Destino</string>
    <string name="driver_pasajeros">Pasajeros</string>
    <string name="driver_codigo_titulo">Código del cliente</string>
    <string name="driver_codigo_instruccion">Pídele al cliente el código que aparece bajo su QR.</string>
    <string name="driver_codigo_campo">Código</string>
    <string name="driver_codigo_confirmar">Cerrar servicio</string>
    <string name="driver_servicio_cerrado">Servicio cerrado. Ya puedes aceptar otro.</string>
```

Y **borrar** `driver_home_siguiente` (línea 518), que anunciaba como futuras unas
funciones que ya existen. Su uso está en `PanelRolFragment` (Tarea 10).

> Los `driver_home_*` que ya existían **no se tocan**: son los que dan los títulos
> de la portada. `driver_servicio_cerrado` lo usa el aviso de la Tarea 9.
>
> No hay texto de "servicio aceptado" a propósito: al aceptar, la pantalla entera
> cambia de cara, y eso ya es la confirmación. Un aviso encima sería repetir lo
> que se está viendo.

- [ ] **Paso 2: el layout**

`fragment_driver_home.xml`, siguiendo la estructura de `fragment_admin_clientes.xml`:

```xml
<LinearLayout ... android:orientation="vertical"
    android:background="@color/colorBackground">

    <org.iot.project.ui.components.AppHeaderView
        android:id="@+id/driver_header" ... />

    <FrameLayout android:layout_width="match_parent" android:layout_height="0dp"
        android:layout_weight="1">

        <!-- Cara LIBRE -->
        <ScrollView android:id="@+id/driver_libre" ...>
            <LinearLayout android:orientation="vertical"
                android:paddingHorizontal="@dimen/space_base">

                <!-- metricas: disponibles / en curso / completados -->
                <LinearLayout android:id="@+id/driver_metricas"
                    android:orientation="horizontal" ... >
                    <org.iot.project.ui.components.StatView android:id="@+id/driver_metrica_disponibles" ... />
                    <org.iot.project.ui.components.StatView android:id="@+id/driver_metrica_activos" ... />
                    <org.iot.project.ui.components.StatView android:id="@+id/driver_metrica_completados" ... />
                </LinearLayout>

                <!-- vehiculo y calificacion -->
                <org.iot.project.ui.components.VehicleCardView
                    android:id="@+id/driver_vehiculo" ... />
                <org.iot.project.ui.components.DriverCardView
                    android:id="@+id/driver_conductor" ... />

                <TextView style="@style/TextAppearance.App.Label"
                    android:text="@string/driver_home_disponibles" ... />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/driver_solicitudes" ... />

                <org.iot.project.ui.components.EmptyStateView
                    android:id="@+id/driver_vacio" android:visibility="gone" ... />
            </LinearLayout>
        </ScrollView>

        <!-- Cara OCUPADA -->
        <ScrollView android:id="@+id/driver_ocupado" android:visibility="gone" ...>
            ...
        </ScrollView>

        <org.iot.project.ui.components.LoadingSkeletonView
            android:id="@+id/driver_esqueleto" android:visibility="gone" ... />
        <org.iot.project.ui.components.ErrorStateView
            android:id="@+id/driver_error" android:visibility="gone" ... />
    </FrameLayout>
</LinearLayout>
```

`item_solicitud_taxi.xml` es una raíz simple:

```xml
<org.iot.project.ui.components.TaxiRequestCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/space_sm" />
```

- [ ] **Paso 3: `SolicitudAdapter`**

Al estilo de `ClienteAdapter`: `RecyclerView.Adapter` con
`submitList(List<OfertaDeTaxi>)` y un oyente para "Aceptar". El adaptador **no
calcula nada**: la distancia ya viene en la oferta.

```java
    public interface OnAceptarListener {
        void onAceptar(@NonNull TaxiService servicio);
    }

    @Override
    public void onBindViewHolder(@NonNull Titular titular, int posicion) {
        OfertaDeTaxi oferta = ofertas.get(posicion);
        TaxiService servicio = oferta.getServicio();

        titular.tarjeta.bind(servicio, oferta.getDistanciaM());
        // Siempre, no solo cuando cambia: RecyclerView reutiliza las vistas y el
        // boton tiene que quedar en el estado correcto en cada fila.
        titular.tarjeta.setOnAceptar(v -> {
            if (oyente != null) {
                oyente.onAceptar(servicio);
            }
        });
    }
```

- [ ] **Paso 4: `DriverHomeViewModel`**

```java
package org.iot.project.ui.driver.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.TaxiService;

import java.util.Collections;
import java.util.List;

/**
 * Estado de la portada del conductor (§46).
 *
 * <p>La pantalla necesita tres datos que llegan por separado —el perfil, si hay
 * viaje en curso y que solicitudes hay— y no puede pintar ninguna de sus dos
 * caras hasta tenerlos todos: con dos, elegiria cara a medias. De ahi el
 * contador.
 *
 * <p>Los fallos de una accion (aceptar, avanzar) no tumban la pantalla: van por
 * {@link #getAviso()} para que se lean en un aviso y lo que habia siga ahi. Un
 * error de carga, en cambio, si es de pantalla completa, porque no hay nada que
 * mostrar.
 */
public class DriverHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<Estado>> contenido = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    private boolean cargado;

    /** Cuantas de las tres consultas faltan por llegar. */
    private int pendientes;

    private Driver conductor;
    private TaxiService activo;
    private List<OfertaDeTaxi> disponibles = Collections.emptyList();

    public LiveData<UiState<Estado>> getContenido() {
        return contenido;
    }

    /** Un mensaje de una sola vez, para el aviso emergente. */
    public LiveData<String> getAviso() {
        return aviso;
    }

    /** Marca el aviso como ya mostrado, para que no reaparezca al girar. */
    public void consumirAviso() {
        aviso.setValue(null);
    }

    void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        pedir();
    }

    /**
     * Volver a pedir los datos desde cero.
     *
     * <p>Se usa despues de aceptar o de avanzar: lo que hay en pantalla deja de
     * ser cierto en cuanto el servicio cambia de estado.
     */
    public void recargar() {
        pedir();
    }

    private void pedir() {
        String conductorId = SessionManager.getConductorId();
        TaxiRepository taxis = ServiceLocator.taxis();

        contenido.setValue(UiState.loading());
        pendientes = 3;

        taxis.perfilDe(conductorId, new ResultCallback<Driver>() {
            @Override
            public void onExito(@NonNull Driver dato) {
                conductor = dato;
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                fallar(mensaje);
            }
        });

        taxis.serviciosEnCursoDe(conductorId, new ResultCallback<List<TaxiService>>() {
            @Override
            public void onExito(@NonNull List<TaxiService> datos) {
                activo = datos.isEmpty() ? null : datos.get(0);
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                fallar(mensaje);
            }
        });

        taxis.disponibles(conductorId, new ResultCallback<List<OfertaDeTaxi>>() {
            @Override
            public void onExito(@NonNull List<OfertaDeTaxi> datos) {
                disponibles = datos;
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                fallar(mensaje);
            }
        });
    }

    /** Una de las tres ha llegado. Cuando estan las tres, se publica. */
    private void recibido() {
        pendientes--;
        if (pendientes == 0) {
            contenido.setValue(UiState.success(new Estado(conductor, activo, disponibles)));
        }
    }

    private void fallar(@NonNull String mensaje) {
        // Se pone a cero para que las otras dos respuestas, que ya vienen en
        // camino, no resuciten la pantalla despues del error.
        pendientes = 0;
        contenido.setValue(UiState.error(mensaje));
    }

    /**
     * RF-108, RF-109: pasar al siguiente estado del servicio en curso.
     *
     * <p>El destino no lo elige la pantalla: se dice "avanza" y el dominio
     * decide cual toca y rechaza lo que no proceda (RF-110, RF-111).
     */
    public void avanzar() {
        if (activo == null) {
            return;
        }
        ServiceLocator.taxis().avanzarComoConductor(activo.getId(),
                SessionManager.getConductorId(), new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    /** RF-090, RF-091, RF-107: quedarse con una solicitud de la lista. */
    public void aceptar(@NonNull String taxiId) {
        ServiceLocator.taxis().aceptar(taxiId, SessionManager.getConductorId(),
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    /**
     * RF-102, RF-103, RF-104, RF-110: cerrar el servicio con el codigo del
     * cliente.
     *
     * <p>Lo llama la hoja de validacion, que pide <em>este</em> ViewModel al
     * fragmento padre: es el mismo modismo de {@code ValorarTaxiSheet}. El
     * resultado se le devuelve a la hoja —que es quien decide si se cierra o
     * enseña el error— y ademas se recarga la portada, porque el servicio acaba
     * de cerrarse y lo que hay en pantalla ya no es cierto.
     */
    public void validarCodigo(@NonNull String taxiId, @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        ServiceLocator.taxis().validarCodigo(taxiId, SessionManager.getConductorId(), codigo,
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        callback.onExito(dato);
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        callback.onError(mensaje);
                    }
                });
    }

    /** Lo que la portada necesita para pintarse. */
    public static final class Estado {

        public final Driver conductor;
        /** El servicio en curso, o {@code null} si el conductor esta libre. */
        public final TaxiService activo;
        public final List<OfertaDeTaxi> disponibles;

        Estado(Driver conductor, TaxiService activo, List<OfertaDeTaxi> disponibles) {
            this.conductor = conductor;
            this.activo = activo;
            this.disponibles = disponibles;
        }
    }
}
```

> La métrica de **completados** no es un campo aparte: es
> `estado.conductor.getNumServicios()`, el total de la carrera del conductor
> (842 en D1). Es el mismo número que ya usa la tarjeta del conductor para
> "N calificaciones", así que no hay dos verdades sobre lo mismo.

- [ ] **Paso 5: `DriverHomeFragment`**

Mismo patrón que `ClientesFragment`: ViewBinding, `InsetUtils.applyTopPadding`
sobre la cabecera, y un `switch` sobre el estado de `UiState` que reparte
esqueleto / contenido / vacío / error.

```java
        // Aceptar pasa por el ViewModel y no por el repositorio: la pantalla no
        // habla con los mocks (§49).
        adaptador = new SolicitudAdapter(servicio -> viewModel.aceptar(servicio.getId()));
        binding.driverSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.driverSolicitudes.setAdapter(adaptador);

        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);

        // El aviso es de una sola vez: se muestra y se olvida, para que no
        // vuelva a salir al girar la pantalla.
        viewModel.getAviso().observe(getViewLifecycleOwner(), mensaje -> {
            if (mensaje != null) {
                Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
                viewModel.consumirAviso();
            }
        });
```

y el reparto de caras, que es §46 literal:

```java
    private void pintar(@NonNull UiState<DriverHomeViewModel.Estado> estado) {
        boolean cargando = estado.isLoading();
        boolean error = estado.isError();
        binding.driverEsqueleto.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.driverError.setVisibility(error ? View.VISIBLE : View.GONE);
        if (error) {
            // El mensaje lo redacta el repositorio; el reintento es de la
            // pantalla. Es el modismo de StateView que ya usan las del Bloque C.
            binding.driverError.conReintento(estado.getMessage(), v -> viewModel.recargar());
        }
        if (!estado.isSuccess()) {
            // Mientras no haya datos no se muestra ninguna de las dos caras: la
            // eleccion depende del servicio en curso, y aun no se sabe cual es.
            binding.driverLibre.setVisibility(View.GONE);
            binding.driverOcupado.setVisibility(View.GONE);
            return;
        }

        DriverHomeViewModel.Estado datos = estado.requireData();
        boolean ocupado = datos.activo != null;
        binding.driverLibre.setVisibility(ocupado ? View.GONE : View.VISIBLE);
        binding.driverOcupado.setVisibility(ocupado ? View.VISIBLE : View.GONE);

        if (ocupado) {
            pintarServicio(datos.activo);
        } else {
            pintarLibre(datos);
        }
    }
```

```java
    private void pintarLibre(@NonNull DriverHomeViewModel.Estado datos) {
        binding.driverMetricaDisponibles.setDato(
                R.string.driver_home_disponibles, String.valueOf(datos.disponibles.size()));
        binding.driverMetricaActivos.setDato(R.string.driver_home_activos, "0");
        binding.driverMetricaCompletados.setDato(R.string.driver_home_completados,
                String.valueOf(datos.conductor.getNumServicios()));

        binding.driverVehiculo.bind(datos.conductor.getVehiculo());
        binding.driverConductor.bind(datos.conductor);

        boolean hay = !datos.disponibles.isEmpty();
        binding.driverSolicitudes.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.driverVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
        if (!hay) {
            binding.driverVacio.conIcono(R.drawable.ic_taxi)
                    .conTitulo(R.string.driver_sin_servicio_titulo)
                    .conMensaje(R.string.driver_sin_servicio);
        }
        adaptador.submitList(datos.disponibles);
    }
```

`StateView.conIcono(...).conTitulo(...).conMensaje(...)` es el modismo que ya
usan `ClientesFragment` y `HabitacionesFragment`; `driver_vacio` se declara en el
XML **sin atributos**, como las demás.

- [ ] **Paso 6: compilar y ver los recursos**

```bash
./gradlew :app:assembleDebug && python3 tools/verificar_recursos.py
```

- [ ] **Paso 7: commit** (previa autorización)

---

## Tarea 8: portada del conductor — cara ocupada

**Ficheros:** modificar `res/layout/fragment_driver_home.xml`,
`ui/driver/home/DriverHomeFragment.java`, `ui/driver/home/DriverHomeViewModel.java`

- [ ] **Paso 1: el bloque en el layout**

Dentro de `driver_ocupado`: título `driver_servicio_activo`, `TaxiStatusView`,
las filas de recojo/destino/pasajeros (con `DataRowView`), y **un solo**
`MaterialButton` primario `driver_accion`.

- [ ] **Paso 2: la acción según el estado**

```java
    /**
     * §46 y §7.1: una sola accion primaria, y cambia con el estado.
     *
     * <p>No hay boton de finalizar: RF-110 lo reserva al codigo del cliente, y
     * {@code avanzarComoConductor} lo rechazaria. La accion de EN_TRASLADO abre
     * la hoja del codigo, que es la unica puerta a FINALIZADO.
     */
    private void pintarAccion(@NonNull TaxiService servicio) {
        switch (servicio.getEstado()) {
            case ASIGNADO:
                binding.driverAccion.setText(R.string.driver_voy_en_camino);
                binding.driverAccion.setOnClickListener(v -> viewModel.avanzar());
                break;
            case EN_CAMINO:
                binding.driverAccion.setText(R.string.driver_inicie_traslado);
                binding.driverAccion.setOnClickListener(v -> viewModel.avanzar());
                break;
            case EN_TRASLADO:
                binding.driverAccion.setText(R.string.driver_validar_codigo);
                binding.driverAccion.setOnClickListener(v -> abrirValidacion(servicio.getId()));
                break;
            default:
                binding.driverAccion.setVisibility(View.GONE);
                break;
        }
    }

    private void abrirValidacion(@NonNull String taxiId) {
        ValidarCodigoSheet.newInstance(taxiId)
                .show(getChildFragmentManager(), ValidarCodigoSheet.TAG);
    }
```

`pintarServicio(...)` rellena además el `TaxiStatusView` con `bind(servicio)` y
las tres filas de datos:

```java
        binding.driverEstado.bind(servicio);
        binding.driverFilaRecogida.bind(R.string.driver_recogida, servicio.getOrigen());
        binding.driverFilaDestino.bind(R.string.driver_destino, servicio.getDestino());
        binding.driverFilaPasajeros.bind(R.string.driver_pasajeros,
                String.valueOf(servicio.getNumPasajeros()));
        pintarAccion(servicio);
```

- [ ] **Paso 3: compilar**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Paso 4: commit** (previa autorización)

---

## Tarea 9: validación del código

**Ficheros:** crear `res/layout/sheet_validar_codigo.xml`,
`ui/driver/qr/ValidarCodigoSheet.java`

- [ ] **Paso 1: la hoja**

`BottomSheetDialogFragment` al estilo de `ValorarTaxiSheet`: recibe `taxiId` por
argumentos, muestra un `TextInputLayout` + `TextInputEditText` para el código, el
texto de instrucción, y un botón primario.

El layout debe llevar **`app:errorEnabled="true"`** en el `TextInputLayout` y un
`TextView` aparte `codigo_error`, que es como lo resuelven las hojas del cliente.

> **Aviso del verificador:** el hint va **solo** en el `TextInputLayout`, nunca
> además en el `TextInputEditText` — se dibujarían superpuestos, y
> `tools/verificar_recursos.py` lo comprueba.

- [ ] **Paso 2: la clase**

Igual que `ValorarTaxiSheet`, la hoja **no** habla con el repositorio: le pide el
ViewModel a la pantalla que la abrió. Así los mocks siguen fuera de las vistas
(§49) y el refresco de la portada ocurre en un solo sitio.

```java
    public static final String TAG = "validar_codigo";

    private static final String ARG_TAXI = "taxiId";

    private SheetValidarCodigoBinding binding;
    private DriverHomeViewModel viewModel;

    public static ValidarCodigoSheet newInstance(@NonNull String taxiId) {
        ValidarCodigoSheet hoja = new ValidarCodigoSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_TAXI, taxiId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        viewModel = new ViewModelProvider(requireParentFragment())
                .get(DriverHomeViewModel.class);

        binding.validarCerrar.setOnClickListener(v -> dismiss());
        binding.validarConfirmar.setOnClickListener(v -> confirmar());
    }
```

```java
    private void confirmar() {
        String taxiId = getArguments() != null ? getArguments().getString(ARG_TAXI) : null;
        if (taxiId == null) {
            dismiss();
            return;
        }

        CharSequence texto = binding.validarCampo.getText();
        String codigo = texto == null ? "" : texto.toString().trim();
        if (codigo.isEmpty()) {
            binding.validarCampo.setError(getString(R.string.driver_codigo_campo));
            return;
        }

        binding.validarConfirmar.setEnabled(false);
        binding.validarError.setVisibility(View.GONE);

        viewModel.validarCodigo(taxiId, codigo, new ResultCallback<TaxiService>() {
            @Override
            public void onExito(@NonNull TaxiService dato) {
                Toast.makeText(requireContext(), R.string.driver_servicio_cerrado,
                        Toast.LENGTH_LONG).show();
                dismiss();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                // RF-103: un codigo que no es el de este servicio no cambia
                // nada. La hoja se queda abierta y el conductor puede volver a
                // intentarlo sin salir y entrar.
                binding.validarConfirmar.setEnabled(true);
                binding.validarError.setText(mensaje);
                binding.validarError.setVisibility(View.VISIBLE);
            }
        });
    }
```

> El aviso de cierre es un `Toast` y no el `Snackbar` del `aviso` del ViewModel
> porque la hoja se cierra en el mismo instante: el `Snackbar` vive en la vista
> de la portada y aparecería antes de que el ojo llegue a la lista.

- [ ] **Paso 3: compilar y verificar recursos**

```bash
./gradlew :app:assembleDebug && python3 tools/verificar_recursos.py
```

- [ ] **Paso 4: commit** (previa autorización)

---

## Tarea 10: navegación

**Ficheros:** modificar `res/navigation/nav_driver.xml`,
`res/menu/menu_bottom_nav_driver.xml`, `ui/common/PanelRolFragment.java`

- [ ] **Paso 1: el grafo**

```xml
    <fragment
        android:id="@+id/driverHomeFragment"
        android:name="org.iot.project.ui.driver.home.DriverHomeFragment"
        android:label="@string/titulo_inicio_conductor"
        tools:layout="@layout/fragment_driver_home" />
```

El comentario de cabecera del grafo hay que reescribirlo: decía que la pantalla
real llegaría después.

- [ ] **Paso 2: `PanelRolFragment` deja de ser del conductor**

Se le quitan los dos casos `CONDUCTOR`, uno en cada `switch`, y se ajustan los
comentarios que hablan de tres roles:

```java
    /**
     * Los tres roles que siguen sin pantalla propia aparecen uno por uno, sin
     * {@code default}: si se agrega un rol nuevo, el caso que falta salta a la
     * vista al leer el metodo en lugar de quedar escondido detras de un valor
     * por defecto.
     */
    @StringRes
    private static int tituloDe(@NonNull Role rol) {
        switch (rol) {
            case ADMIN_HOTEL:
                return R.string.titulo_panel_admin;
            case SUPERADMIN:
                return R.string.titulo_panel_superadmin;
            case CLIENTE:
                return R.string.app_name;
        }
        // El conductor ya tiene su portada (Bloque D): su grafo no apunta aqui.
        return R.string.app_name;
    }
```

Lo mismo en `siguienteDe`, quitando su caso `CONDUCTOR`. Y en la cabecera de la
clase, donde dice que las tres comparten pantalla "de administracion de hotel,
conductor y superadmin", pasa a decir que hoy solo la usa el superadmin: el
administrador tiene las suyas desde el Bloque C y el conductor desde este.

- [ ] **Paso 3: verificar que no queda ninguna referencia rota**

```bash
python3 tools/verificar_recursos.py && ./gradlew :app:assembleDebug
```

- [ ] **Paso 4: commit** (previa autorización)

---

## Tarea 11: verificación de cierre

- [ ] **Paso 1: la batería completa**

```bash
python3 tools/verificar_recursos.py
./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks
```

Esperado: recursos sin problemas, **BUILD SUCCESSFUL**, **80 pruebas, 0 fallos**.

- [ ] **Paso 2: mirar las marcas de tiempo, no el cartel**

```bash
ls -l --time-style=+%F_%T app/build/outputs/apk/debug/app-debug.apk \
      app/build/test-results/testDebugUnitTest/*.xml | head -3
```

Esperado: fecha y hora de ahora mismo. Un "BUILD SUCCESSFUL" sobre artefactos
viejos no verifica nada (§11).

- [ ] **Paso 3: fallar a propósito**

Meter una referencia falsa (`@dimen/space_que_no_existe`) en un layout del
bloque, comprobar que el verificador **la reporta**, y quitarla. Un verificador
que siempre dice "ok" es peor que no tenerlo.

- [ ] **Paso 4: recorrer el flujo en el emulador**

Emulador `Pixel_4` (SDK 34), `adb` en `/android-data/Sdk/platform-tools/adb`.

1. Entrar como **conductor** (D1).
2. La portada abre en la **cara libre**: métricas, su vehículo y la solicitud de
   Lima con su distancia.
3. **Aceptar** → la pantalla cambia entera a la cara ocupada, con una sola acción.
4. "Voy en camino" → "Inicié el traslado".
5. "Validar código del cliente" → probar **un código falso** (debe rechazarlo sin
   cambiar nada) → el código real `TAX-2026-0735` → el servicio se cierra.
6. La portada vuelve sola a la cara libre, y la lista queda vacía con su estado
   vacío.
7. Entrar como **cliente** (U1): su taxi sigue en camino con D2, y el seguimiento
   del mapa funciona.

**Lo que no ve el compilador:** dos textos superpuestos, un panel encima de otro,
un botón que no cabe. Se mira una captura, no un log.

- [ ] **Paso 5: commit** (previa autorización)

---

## Tarea 12: actualizar la memoria del proyecto

**Ficheros:** modificar `docs/memory/CONTEXTO_FRONTEND.md`

- [ ] **Paso 1: §5**

- Mover el Bloque D de *Pendiente* a *Hecho y verificado*, con el mismo nivel de
  detalle que el Bloque C.
- Dejar **RF-100** anotado como pendiente y explicar por qué: es del Bloque C.
- Actualizar el recuento de pruebas (61 → 80).
- Quitar de la lista de "decisiones abiertas" lo que este bloque haya cerrado.
- Anotar que **el Bloque C sigue sin verificar en el emulador**, que no lo toca
  este bloque.

- [ ] **Paso 2: commit** (previa autorización)

---

## Autorrevisión

**Cobertura de la especificación**

| Sección | Tarea |
|---|---|
| §1.1 el taxi no se puede cerrar | 4, 9 |
| §1.2 `avanzar()` contradice a RF-090 | 4 |
| §1.3a errata de T3 | 5 |
| §1.3b D1 ocupado y solicitud en Cusco | 5 |
| §3 portada, dos caras | 7, 8 |
| §4 reglas en el dominio | 2 |
| §4.1 cuándo se puede validar | 4 |
| §5 repositorio: gana y pierde | 4 |
| §5 filtro de cercanía | 1, 3, 4 |
| §6 costura de ubicación | 3 |
| §7 `TaxiRequestCardView` | 6 |
| §8 datos sembrados | 3, 5 |
| §9 verificación | 11 |
| §2 RF-100 fuera de alcance | 12 (queda anotado) |

**Consistencia de tipos** — comprobada contra el código real, no de memoria:

| Se usa | En las tareas | Definido en |
|---|---|---|
| `Distancia.metrosEntre(double,double,double,double)` | 1, 3, 4, 5, 7 | 1 |
| `Distancia.legible(double)` | 1, 6 | 1 |
| `TaxiService.puedeAceptarlo(Driver)` | 2, 4, 5 | 2 |
| `TaxiService.avanzarPorConductor(TaxiStatus)` | 2, 4 | 2 |
| `TaxiService.validarCodigo(String)` | 2, 4 | 2 |
| `Ubicacion.getLatitud()/getLongitud()` | 3, 4, 5 | 3 |
| `FuenteUbicacion.posicion(Driver, TaxiService)` | 3, 4 | 3 |
| `TaxiRepository.perfilDe(String, cb)` | 4, 7 | 4 |
| `TaxiRepository.disponibles(String, cb)` → `List<OfertaDeTaxi>` | 4, 7 | 4 |
| `TaxiRepository.serviciosEnCursoDe(String, cb)` | 4, 7 | 4 |
| `TaxiRepository.aceptar(String, String, cb)` | 4, 7 | 4 |
| `TaxiRepository.avanzarComoConductor(String, String, cb)` | 4, 7 | 4 |
| `TaxiRepository.validarCodigo(String, String, String, cb)` | 4, 7, 9 | 4 |
| `OfertaDeTaxi.getServicio()/getDistanciaM()` | 4, 7 | 4 |
| `TaxiRequestCardView.bind(TaxiService, double)` | 6, 7 | 6 |
| `TaxiRequestCardView.setOnAceptar(OnClickListener)` | 6, 7 | 6 |
| `DriverHomeViewModel.Estado` | 7, 8 | 7 |

**Lo que la autorrevisión corrigió**, y por qué importa:

- `servicioActivoDe` devolvía un dato único con `entregarDato`, y un conductor
  libre habría recibido el **estado de error**. Pasa a `serviciosEnCursoDe`,
  lista.
- `disponibles` usaba `ejecutar`, que **no respeta `MockConfig.Modo.VACIO`**: el
  estado vacío de §50 no se habría podido demostrar. Pasa a `entregarLista`.
- `validarCodigo` no pedía el conductor, así que cualquiera podía cerrar el viaje
  de otro sabiendo el identificador.
- Faltaba de dónde sacar el perfil del conductor: no hay ningún repositorio que
  devuelva un `Driver`, y leerlo de `MockData` desde el ViewModel va contra §49.
  Nace `perfilDe`.
- `ErrorStateView.setMensaje(...)` y `EmptyStateView` configurado por código no
  existen: la API real es `conReintento(mensaje, listener)` y
  `conIcono(...).conTitulo(...).conMensaje(...)`.
- **No había de dónde sacar la distancia de la tarjeta.** El adaptador la
  calcularía desde la base del conductor, y la base vive en `MockData`: leerla
  desde la vista va contra §49, y añadir una séptima operación solo para eso es
  peor. La distancia viaja dentro de `OfertaDeTaxi`.
- **La hoja de validación no hablaba con nadie.** Las hojas del proyecto piden el
  ViewModel al fragmento padre (`ValorarTaxiSheet`); sin eso, cerrar el servicio
  no refrescaba la portada y el conductor se quedaba mirando un servicio ya
  cerrado.

**Números que se comprobaron midiendo, no recordando:** Lima–Cusco son **569 km**
entre las coordenadas sembradas (no 1.100), y la base de D1 está a **1,9 km** de
H1. El umbral de cercanía, 100 km, cae entre esos dos extremos.

**Sin marcadores pendientes.** Cada paso lleva su código o su XML. Las firmas de
los ficheros que ya existen —`MockRepository`, `StateView`, `Driver`,
`TaxiStatus`— se leyeron del repositorio antes de escribirlas aquí.
