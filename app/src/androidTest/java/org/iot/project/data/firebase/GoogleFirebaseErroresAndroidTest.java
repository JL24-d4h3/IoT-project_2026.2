package org.iot.project.data.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuthException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Las excepciones SDK necesitan TextUtils de Android; no inicializa Auth ni usa red. */
@RunWith(AndroidJUnit4.class)
public final class GoogleFirebaseErroresAndroidTest {
    @Test public void erroresGoogleNoSugierenContrasenaErroneaNiIncluyenDetalleSdk() {
        String interno = "detalle-interno-que-no-se-muestra";
        String deshabilitado = FirebaseErrores.mensajeGoogle(new FirebaseAuthException(
                "ERROR_OPERATION_NOT_ALLOWED", interno));
        assertTrue(deshabilitado.contains("Google"));
        assertFalse(deshabilitado.contains(interno));
        String invalido = FirebaseErrores.mensajeGoogle(new FirebaseAuthException("ERROR_INVALID_CREDENTIAL", interno));
        assertTrue(invalido.contains("Google"));
        assertFalse(invalido.contains(interno));
        assertFalse(invalido.contains("contraseña"));
        String falloLocal = FirebaseErrores.mensajeGoogle(new IllegalArgumentException(interno));
        assertTrue(falloLocal.contains("Google"));
        assertFalse(falloLocal.contains(interno));
    }

    @Test public void colisionDeProveedorExigeMetodoHabitualSinFusionarCuentas() {
        for (String codigo : Arrays.asList("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
                "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_EMAIL_ALREADY_IN_USE")) {
            String mensaje = FirebaseErrores.mensajeGoogle(new FirebaseAuthException(codigo, "detalle-privado"));
            assertTrue(mensaje.contains("método habitual"));
            assertFalse(mensaje.contains("detalle-privado"));
        }
    }
}
