package org.iot.project.ui.auth.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.databinding.FragmentLoginBinding;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Solo vistas: no abre el selector de Google ni inicia autenticación. */
@RunWith(AndroidJUnit4.class)
public final class GoogleLoginLayoutAndroidTest {
    @Test public void accesoIncluyeGoogleYConservaElFormularioYLaSeleccionDeTaxista() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context contexto = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_Project);
            FragmentLoginBinding vista = FragmentLoginBinding.inflate(LayoutInflater.from(contexto));
            assertEquals(View.VISIBLE, vista.loginGoogleAcciones.getVisibility());
            assertEquals(contexto.getString(R.string.acceso_google_continuar), vista.loginGoogle.getText().toString());
            assertNotNull(vista.loginGoogle.getIcon());
            assertTrue(vista.loginGoogle.isEnabled());
            assertEquals(R.id.login_modo_normal, vista.loginTipoCuenta.getCheckedRadioButtonId());
            assertEquals(View.GONE, vista.loginCuentaWeb.getVisibility());
            assertNotNull(vista.loginCorreo);
            assertNotNull(vista.loginEntrar);
            assertNotNull(vista.loginCrear);
            assertNotNull(vista.loginOlvide);
            assertFalse(vista.loginContrasena.isSaveEnabled());
        });
    }

}
