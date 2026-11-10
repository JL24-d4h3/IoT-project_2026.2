package org.iot.project.ui.auth.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Coordinación del acceso con proveedores falsos: sin Activity, Firebase ni red. */
@RunWith(AndroidJUnit4.class)
public final class LoginGoogleViewModelAndroidTest {
    private SavedStateHandle guardado;
    private FakeFirebase firebase;
    private FakeTaxista taxista;
    private LoginViewModel viewModel;

    @Before public void preparar() {
        enPrincipal(() -> {
            guardado = new SavedStateHandle();
            firebase = new FakeFirebase();
            taxista = new FakeTaxista();
            viewModel = new LoginViewModel(guardado, firebase, taxista);
        });
    }

    @After public void liberar() {
        enPrincipal(() -> { if (viewModel != null) viewModel.onCleared(); });
    }

    @Test public void googleSoloComienzaPorAccionExplicitaYSinAutenticarAntesDelSelector() {
        enPrincipal(() -> {
            assertNull(viewModel.getAcceso().getValue());
            assertFalse(viewModel.estaCargando());
            assertFalse(viewModel.estaEsperandoGoogle());
            assertTrue(firebase.google.isEmpty());

            assertTrue(viewModel.comenzarGoogle());

            assertTrue(viewModel.estaCargando());
            assertTrue(viewModel.estaEsperandoGoogle());
            assertTrue(firebase.google.isEmpty());
            assertTrue(firebase.correo.isEmpty());
            assertTrue(taxista.solicitudes.isEmpty());
        });
    }

    @Test public void modoTaxistaNoEnviaGoogleNiCredencialesAlProveedorFirebase() {
        enPrincipal(() -> {
            viewModel.seleccionarModoTaxista(true);
            assertFalse(viewModel.comenzarGoogle());
            viewModel.completarGoogle("token-que-no-corresponde-al-taxista");
            assertFalse(viewModel.estaCargando());
            assertTrue(firebase.google.isEmpty());
            assertTrue(firebase.correo.isEmpty());

            viewModel.entrar("taxista@example.test", "password-web-ficticio");
            assertEquals(1, taxista.solicitudes.size());
            assertTrue(firebase.google.isEmpty());
            assertTrue(firebase.correo.isEmpty());
        });
    }

    @Test public void dobleToqueYCompletarDuplicadoNoCreanDosAutenticaciones() {
        enPrincipal(() -> {
            assertTrue(viewModel.comenzarGoogle());
            assertFalse(viewModel.comenzarGoogle());
            viewModel.entrar("cliente@example.test", "password-local-ficticio");
            viewModel.seleccionarModoTaxista(true);
            assertFalse(viewModel.esModoTaxista());
            assertTrue(firebase.correo.isEmpty());
            assertTrue(taxista.solicitudes.isEmpty());

            viewModel.completarGoogle("token-google-ficticio");
            viewModel.completarGoogle("token-duplicado");

            assertEquals(1, firebase.google.size());
            assertEquals("token-google-ficticio", firebase.google.get(0).secreto);
            assertTrue(viewModel.estaCargando());
            assertFalse(viewModel.estaEsperandoGoogle());
        });
    }

    @Test public void cancelarSelectorNoIniciaSesionYPermiteReintentar() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.cancelarGoogle();
            assertNull(viewModel.getAcceso().getValue());
            assertFalse(viewModel.estaCargando());
            assertFalse(viewModel.estaEsperandoGoogle());
            assertTrue(firebase.google.isEmpty());
            viewModel.completarGoogle("respuesta-tardia-del-selector");
            assertTrue(firebase.google.isEmpty());

            assertTrue(viewModel.comenzarGoogle());
            viewModel.completarGoogle("token-reintento");
            assertEquals(1, firebase.google.size());
        });
    }

    @Test public void destruirVistaDespuesDeEntregarTokenNoCancelaLaAutenticacionFirebase() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.completarGoogle("token-google-ficticio");
            UiState<Cuenta> esperandoFirebase = viewModel.getAcceso().getValue();
            viewModel.cancelarGoogle();
            assertSame(esperandoFirebase, viewModel.getAcceso().getValue());
            assertTrue(viewModel.estaCargando());

            Cuenta cuenta = cuenta(Role.CLIENTE);
            firebase.google.get(0).exito(cuenta);
            assertSame(cuenta, viewModel.getAcceso().getValue().requireData());
        });
    }

    @Test public void googleConservaRolYHotelConfirmadosPorElRepositorio() {
        enPrincipal(() -> {
            for (Role rol : new Role[] {Role.CLIENTE, Role.ADMIN_HOTEL, Role.SUPERADMIN}) {
                assertTrue(viewModel.comenzarGoogle());
                viewModel.completarGoogle("token-rol-" + rol.name());
                Cuenta autorizada = cuenta(rol);
                firebase.google.get(firebase.google.size() - 1).exito(autorizada);

                assertNotNull(viewModel.getAcceso().getValue());
                assertTrue(viewModel.getAcceso().getValue().isSuccess());
                assertSame(autorizada, viewModel.getAcceso().getValue().requireData());
                assertEquals(rol, viewModel.getAcceso().getValue().requireData().getRol());
                assertEquals(rol == Role.ADMIN_HOTEL ? "hotel-asignado" : null,
                        viewModel.getAcceso().getValue().requireData().getHotelId());
                viewModel.limpiarAcceso();
            }
        });
    }

    @Test public void falloDelSelectorMuestraMensajeYDesbloqueaOtroIntento() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.fallarGoogle("No se pudo abrir el selector de Google.");
            assertTrue(viewModel.getAcceso().getValue().isError());
            assertEquals("No se pudo abrir el selector de Google.",
                    viewModel.getAcceso().getValue().getMessage());
            assertFalse(viewModel.estaEsperandoGoogle());
            assertFalse(viewModel.estaCargando());
            assertTrue(firebase.google.isEmpty());

            assertTrue(viewModel.comenzarGoogle());
            viewModel.completarGoogle("token-reintento");
            assertEquals(1, firebase.google.size());
        });
    }

    @Test public void falloDeFirebasePermiteVolverAEntrarConCorreoSinCambiarProveedor() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.completarGoogle("token-google-ficticio");
            firebase.google.get(0).error("Tu cuenta está deshabilitada.");
            assertTrue(viewModel.getAcceso().getValue().isError());
            assertEquals("Tu cuenta está deshabilitada.", viewModel.getAcceso().getValue().getMessage());
            assertFalse(viewModel.estaCargando());

            viewModel.entrar("cliente@example.test", "password-local-ficticio");
            assertEquals(1, firebase.correo.size());
            assertTrue(taxista.solicitudes.isEmpty());
            Cuenta cuenta = cuenta(Role.CLIENTE);
            firebase.correo.get(0).exito(cuenta);
            assertSame(cuenta, viewModel.getAcceso().getValue().requireData());
        });
    }

    @Test public void limpiarEstadoDescartaCallbacksDeUnIntentoAnterior() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.completarGoogle("token-antiguo");
            Solicitud antigua = firebase.google.get(0);
            viewModel.limpiarAcceso();
            assertTrue(viewModel.comenzarGoogle());
            viewModel.completarGoogle("token-actual");
            UiState<Cuenta> actual = viewModel.getAcceso().getValue();

            antigua.exito(cuenta(Role.SUPERADMIN));
            antigua.error("Error del intento anterior");
            assertSame(actual, viewModel.getAcceso().getValue());
            Cuenta autorizada = cuenta(Role.CLIENTE);
            firebase.google.get(1).exito(autorizada);
            assertSame(autorizada, viewModel.getAcceso().getValue().requireData());
        });
    }

    @Test public void tokensYContrasenasNuncaSeGuardanEnSavedState() {
        enPrincipal(() -> {
            String token = "token-google-ficticio-solo-memoria";
            String password = "password-ficticio-solo-memoria";
            viewModel.comenzarGoogle();
            viewModel.completarGoogle(token);
            firebase.google.get(0).error("Error de prueba");
            viewModel.entrar("cliente@example.test", password);

            Map<String, Object> copia = new HashMap<>();
            for (String clave : guardado.keys()) {
                Object valor = guardado.get(clave);
                assertFalse(token.equals(valor));
                assertFalse(password.equals(valor));
                assertFalse(clave.toLowerCase(java.util.Locale.ROOT).contains("token"));
                assertFalse(clave.toLowerCase(java.util.Locale.ROOT).contains("password"));
                copia.put(clave, valor);
            }
            viewModel.onCleared();
            viewModel = new LoginViewModel(new SavedStateHandle(copia), firebase, taxista);
            assertFalse(viewModel.estaEsperandoGoogle());
            assertFalse(viewModel.estaCargando());
            assertNull(viewModel.getAcceso().getValue());
        });
    }

    @Test public void limpiarViewModelInvalidaRespuestasPendientesYCancelaProveedorTaxista() {
        enPrincipal(() -> {
            viewModel.comenzarGoogle();
            viewModel.completarGoogle("token-google-ficticio");
            UiState<Cuenta> antes = viewModel.getAcceso().getValue();
            viewModel.onCleared();
            firebase.google.get(0).exito(cuenta(Role.SUPERADMIN));
            firebase.google.get(0).error("Respuesta tardía");
            assertSame(antes, viewModel.getAcceso().getValue());
            assertTrue(taxista.cancelaciones > 0);
        });
    }

    private static Cuenta cuenta(Role rol) {
        return new Cuenta(rol, "uid-firebase", "Usuario de prueba", "usuario@example.test",
                rol == Role.ADMIN_HOTEL ? "hotel-asignado" : null);
    }

    private static void enPrincipal(Runnable accion) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(accion);
    }

    private static final class Solicitud {
        final String secreto;
        final ResultCallback<Cuenta> callback;
        Solicitud(String secreto, ResultCallback<Cuenta> callback) {
            this.secreto = secreto;
            this.callback = callback;
        }
        void exito(Cuenta cuenta) { callback.onExito(cuenta); }
        void error(String mensaje) { callback.onError(mensaje); }
    }

    private static final class FakeFirebase implements AccesoRepository {
        final List<Solicitud> google = new ArrayList<>();
        final List<Solicitud> correo = new ArrayList<>();
        @Override public void iniciarSesionGoogle(String idToken, ResultCallback<Cuenta> callback) {
            google.add(new Solicitud(idToken, callback));
        }
        @Override public void iniciarSesion(String email, String password, ResultCallback<Cuenta> callback) {
            correo.add(new Solicitud(password, callback));
        }
        @Override public void registrar(User usuario, String password, ResultCallback<Cuenta> callback) { throw ajena(); }
        @Override public void recuperarContrasena(String email, ResultCallback<String> callback) { throw ajena(); }
        @Override public void restaurarSesion(ResultCallback<Cuenta> callback) { throw ajena(); }
        @Override public void cerrarSesion() { }
        private AssertionError ajena() { return new AssertionError("La prueba no debe invocar otra operación de Firebase."); }
    }

    private static final class FakeTaxista implements LoginViewModel.ProveedorTaxista {
        final List<Solicitud> solicitudes = new ArrayList<>();
        int cancelaciones;
        @Override public void iniciarSesion(String email, String password, ResultCallback<Cuenta> callback) {
            solicitudes.add(new Solicitud(password, callback));
        }
        @Override public void cancelar() { cancelaciones++; }
    }
}
