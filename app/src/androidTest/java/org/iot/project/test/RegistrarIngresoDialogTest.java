package org.iot.project.test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.iot.project.MainActivity;
import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regresión del InflateException al mostrar la confirmación de ingreso.
 * Usa el tema real de MainActivity y los mismos recursos y botones que
 * ReservaAdminFragment; no requiere reservas, cuentas ni servidores Firebase.
 * El runner inicializa exclusivamente el proyecto demo local.
 */
@RunWith(AndroidJUnit4.class)
public final class RegistrarIngresoDialogTest {
    private ActivityScenario<MainActivity> escenario;
    private AlertDialog dialogo;
    private final AtomicInteger confirmaciones = new AtomicInteger();

    @Before
    public void abrirActividadSinSesion() {
        FirebaseEmulatorApplication.exigirProyectoDemo();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(SessionManager::cerrarSesion);
        escenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void cerrarActividad() {
        if (escenario != null) {
            escenario.onActivity(activity -> {
                if (dialogo != null && dialogo.isShowing()) dialogo.dismiss();
            });
            escenario.close();
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(SessionManager::cerrarSesion);
    }

    @Test
    public void muestraTituloMensajeYBotonesYCancelarNoRegistraIngreso() {
        mostrarConfirmacion();

        onView(withText(R.string.admin_firestore_ingreso_titulo)).check(matches(isDisplayed()));
        onView(withText(R.string.admin_firestore_ingreso_mensaje)).check(matches(isDisplayed()));
        onView(allOf(withId(android.R.id.button1), withText(R.string.admin_firestore_ingreso)))
                .check(matches(isDisplayed()));
        onView(allOf(withId(android.R.id.button2), withText(R.string.accion_volver)))
                .check(matches(isDisplayed()))
                .perform(click());

        onView(withText(R.string.admin_firestore_ingreso_titulo)).check(doesNotExist());
        assertEquals(0, confirmaciones.get());
        escenario.onActivity(activity -> assertFalse(dialogo.isShowing()));
    }

    @Test
    public void confirmarEjecutaUnaVezYCierraElDialogo() {
        mostrarConfirmacion();

        onView(allOf(withId(android.R.id.button1), withText(R.string.admin_firestore_ingreso)))
                .perform(click());

        onView(withText(R.string.admin_firestore_ingreso_titulo)).check(doesNotExist());
        assertEquals(1, confirmaciones.get());
        escenario.onActivity(activity -> assertFalse(dialogo.isShowing()));
    }

    private void mostrarConfirmacion() {
        escenario.onActivity(activity -> {
            dialogo = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.admin_firestore_ingreso_titulo)
                    .setMessage(R.string.admin_firestore_ingreso_mensaje)
                    .setNegativeButton(R.string.accion_volver, null)
                    .setPositiveButton(R.string.admin_firestore_ingreso,
                            (dialog, which) -> confirmaciones.incrementAndGet())
                    .create();
            dialogo.show();
            assertTrue(dialogo.isShowing());
        });
    }
}
