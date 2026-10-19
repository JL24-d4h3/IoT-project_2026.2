package org.iot.project.test;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;

import org.hamcrest.Matcher;
import org.iot.project.MainActivity;
import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Flujo real de UI + Auth + Firestore, exclusivamente contra emuladores. */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class CrearHotelE2ETest {
    private ActivityScenario<MainActivity> escenario;
    private EmulatorRest.CuentaPrueba superadmin;
    private EmulatorRest.CuentaPrueba administrador;
    private boolean autenticado;
    private boolean terminado;

    @Before
    public void prepararProyectoDemo() throws Exception {
        FirebaseEmulatorApplication.exigirProyectoDemo();
        Tasks.await(FirebaseFirestore.getInstance().enableNetwork(), 15, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(SessionManager::cerrarSesion);
        // Es destructivo exclusivamente para el proyecto demo local fijo.
        EmulatorRest.limpiarFirestoreDemo();
        superadmin = EmulatorRest.crearCuenta("sa", "SUPERADMIN");
        administrador = EmulatorRest.crearCuenta("admin", "ADMIN_HOTEL");
        escenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void cerrarPantalla() throws Exception {
        if (!terminado && autenticado && escenario != null) captura("e2e-fallo");
        Tasks.await(FirebaseFirestore.getInstance().enableNetwork(), 15, TimeUnit.SECONDS);
        if (escenario != null) escenario.close();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(SessionManager::cerrarSesion);
    }

    @Test
    public void caso01CrearHotelVisibleConListaVaciaYConError() throws Exception {
        entrar(superadmin);
        esperar(withId(R.id.sa_inicio_crear_hotel));
        onView(withId(R.id.sa_inicio_crear_hotel)).check(matches(isEnabled()));
        captura("e2e-inicio-crear-hotel");
        onView(withId(R.id.superadminHotelesFragment)).perform(click());
        esperar(withId(R.id.sa_hoteles_vacio));
        onView(withId(R.id.sa_hoteles_crear)).check(matches(allOf(isDisplayed(), isEnabled())));
        captura("e2e-hoteles-vacio-crear-hotel");

        Tasks.await(FirebaseFirestore.getInstance().disableNetwork(), 15, TimeUnit.SECONDS);
        onView(withId(R.id.superadminHomeFragment)).perform(click());
        esperar(withId(R.id.sa_inicio_crear_hotel));
        onView(withId(R.id.superadminHotelesFragment)).perform(click());
        esperar(withId(R.id.sa_error));
        onView(withId(R.id.sa_hoteles_crear)).check(matches(allOf(isDisplayed(), isEnabled())));
        captura("e2e-hoteles-error-crear-hotel");
        onView(withId(R.id.sa_hoteles_crear)).perform(click());
        esperar(withId(R.id.sa_alta_nombre));
        Tasks.await(FirebaseFirestore.getInstance().enableNetwork(), 15, TimeUnit.SECONDS);
        terminado = true;
    }

    @Test
    public void caso02CrearAsignarYVerHotelPersistidoEnPerfilAdmin() throws Exception {
        String nombreHotel = "Hotel E2E " + UUID.randomUUID().toString().substring(0, 8);
        entrar(superadmin);
        esperar(withId(R.id.sa_inicio_crear_hotel));
        onView(withId(R.id.sa_inicio_crear_hotel)).perform(click());
        esperar(withId(R.id.sa_alta_nombre));
        escribir(R.id.sa_alta_nombre, nombreHotel);
        escribir(R.id.sa_alta_ciudad, "Lima");
        escribir(R.id.sa_alta_distrito, "Miraflores");
        escribir(R.id.sa_alta_direccion, "Av. Emulador 123");
        escribir(R.id.sa_alta_latitud, "-12.12");
        escribir(R.id.sa_alta_longitud, "-77.03");
        onView(withId(R.id.sa_alta_guardar)).perform(scrollTo(), click());
        esperar(withId(R.id.sa_hoteles_crear));
        esperar(withText(nombreHotel));
        onView(allOf(withText(nombreHotel), isDisplayed())).perform(click());
        esperar(allOf(withId(R.id.sa_ficha_nombre), withText(nombreHotel)));
        onView(withId(R.id.sa_ficha_asignar)).perform(scrollTo(), click());
        esperar(allOf(withId(R.id.sa_candidato_correo), withText(administrador.email)));
        onView(allOf(withId(R.id.sa_candidato_correo), withText(administrador.email))).perform(click());
        esperar(allOf(withId(R.id.sa_ficha_admin), withText(administrador.nombreCompleto)));
        captura("e2e-detalle-hotel-asignado");

        JSONObject perfilAdmin = EmulatorRest.documento("usuarios/" + administrador.uid);
        String hotelId = EmulatorRest.textoCampo(perfilAdmin, "hotelId");
        JSONObject hotel = EmulatorRest.documento("hoteles/" + hotelId);
        assertEquals(nombreHotel, EmulatorRest.textoCampo(hotel, "nombre"));
        assertEquals(administrador.uid, EmulatorRest.textoCampo(hotel, "administradorId"));
        assertEquals(superadmin.uid, EmulatorRest.textoCampo(hotel, "creadoPor"));
        assertFalse(hotel.getJSONObject("publicado").getBoolean("booleanValue"));
        String eventoId = EmulatorRest.textoCampo(hotel, "ultimoEventoId");
        JSONObject evento = EmulatorRest.documento("auditoria/" + eventoId);
        assertEquals("ASIGNAR_ADMINISTRADOR", EmulatorRest.textoCampo(evento, "accion"));
        assertEquals(superadmin.uid, EmulatorRest.textoCampo(evento, "actorUid"));

        pressBack();
        esperar(withId(R.id.sa_hoteles_crear));
        onView(withId(R.id.superadminPerfilFragment)).perform(click());
        esperar(withId(R.id.sa_perfil_contenido));
        esperarSinAviso();
        onView(withId(R.id.sa_perfil_cerrar_sesion)).perform(scrollTo(), click());
        esperar(withId(android.R.id.button1));
        onView(withId(android.R.id.button1)).perform(click());
        autenticado = false;
        entrar(administrador);
        // El tablero aun no migra reportes/reservas; debe explicar esa fase
        // pendiente sin intentar resolver el UUID real en los datos simulados.
        esperar(withId(R.id.admin_error));
        onView(withId(R.id.adminPerfilFragment)).perform(click());
        esperar(allOf(withId(R.id.admin_perfil_hotel_nombre), withText(nombreHotel)));
        captura("e2e-admin-perfil-hotel");

        // Relanza la Activity: no sustituye una prueba separada de muerte de proceso.
        escenario.close();
        escenario = ActivityScenario.launch(MainActivity.class);
        esperar(withId(R.id.admin_error));
        onView(withId(R.id.adminPerfilFragment)).perform(click());
        esperar(allOf(withId(R.id.admin_perfil_hotel_nombre), withText(nombreHotel)));
        captura("e2e-admin-perfil-reabierto");
        terminado = true;
    }

    private void entrar(EmulatorRest.CuentaPrueba cuenta) {
        autenticado = false;
        esperar(withId(R.id.login_correo));
        escribir(R.id.login_correo, cuenta.email);
        escribir(R.id.login_contrasena, EmulatorRest.PASSWORD);
        onView(withId(R.id.login_entrar)).perform(scrollTo(), click());
        esperar(withId(cuenta == superadmin
                ? R.id.sa_inicio_crear_hotel : R.id.adminPerfilFragment));
        autenticado = true;
    }

    private static void escribir(int id, String texto) {
        onView(withId(id)).perform(scrollTo(), replaceText(texto));
        closeSoftKeyboard();
    }

    private static void esperar(Matcher<View> vista) {
        long limite = SystemClock.uptimeMillis() + 25000;
        Throwable ultimo = null;
        do {
            try {
                onView(vista).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | AssertionError error) {
                ultimo = error;
                SystemClock.sleep(120);
            }
        } while (SystemClock.uptimeMillis() < limite);
        throw new AssertionError("La pantalla esperada no aparecio: " + vista, ultimo);
    }

    private static void captura(String nombre) throws Exception {
        Bitmap imagen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (imagen == null) throw new AssertionError("No se pudo capturar " + nombre);
        File carpeta = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        if (carpeta == null) throw new AssertionError("No hay carpeta para las capturas E2E.");
        try (FileOutputStream salida = new FileOutputStream(new File(carpeta, nombre + ".png"))) {
            if (!imagen.compress(Bitmap.CompressFormat.PNG, 100, salida)) {
                throw new AssertionError("No se pudo guardar " + nombre);
            }
        } finally {
            imagen.recycle();
        }
        // UTP desinstala el APK al terminar y borra externalFiles. La copia
        // publica conserva solo estas capturas sin credenciales en el AVD.
        if (!nombre.matches("e2e-[a-z0-9-]+")) throw new IllegalArgumentException("Nombre de captura invalido.");
        ejecutarShellEmulador("mkdir -p /sdcard/Download/iot-e2e");
        ejecutarShellEmulador("cp /sdcard/Android/data/org.iot.project/files/" + nombre
                + ".png /sdcard/Download/iot-e2e/" + nombre + ".png");
    }

    private static void esperarSinAviso() {
        long limite = SystemClock.uptimeMillis() + 10000;
        do {
            try {
                onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist());
                return;
            } catch (AssertionError visible) {
                SystemClock.sleep(120);
            }
        } while (SystemClock.uptimeMillis() < limite);
        throw new AssertionError("El aviso temporal no desaparecio antes de usar el contenido inferior.");
    }

    private static void ejecutarShellEmulador(String comando) throws Exception {
        ParcelFileDescriptor salida = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(comando);
        try (InputStream entrada = new ParcelFileDescriptor.AutoCloseInputStream(salida)) {
            byte[] buffer = new byte[1024];
            while (entrada.read(buffer) != -1) { /* Esperar a que finalice la copia local. */ }
        }
    }
}
