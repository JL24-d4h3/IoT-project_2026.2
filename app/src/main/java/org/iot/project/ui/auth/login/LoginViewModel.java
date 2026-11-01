package org.iot.project.ui.auth.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.data.api.TaxiApi;
import org.iot.project.models.Cuenta;

/**
 * Acceso a la aplicacion (§74).
 *
 * <p>El repositorio autentica las credenciales y devuelve el perfil autorizado.
 * La contrasena solo se pasa durante la solicitud; nunca forma parte del
 * estado de la pantalla.
 */
public class LoginViewModel extends ViewModel {

    private final MutableLiveData<UiState<Cuenta>> acceso = new MutableLiveData<>();

    public LiveData<UiState<Cuenta>> getAcceso() {
        return acceso;
    }

    public boolean estaCargando() {
        UiState<Cuenta> estado = acceso.getValue();
        return estado != null && estado.isLoading();
    }

    /**
     * Autentica una sola solicitud a la vez sin conservar las credenciales.
     */
    public void entrar(@NonNull String correo, @NonNull String contrasena) {
        if (estaCargando()) {
            return;
        }
        acceso.setValue(UiState.loading());
        ServiceLocator.acceso().iniciarSesion(correo, contrasena, new ResultCallback<Cuenta>() {
            @Override
            public void onExito(@NonNull Cuenta datos) {
                acceso.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                // No es cliente ni administrador: puede ser un taxista, que entra por la API.
                probarComoTaxista(correo, contrasena, mensaje);
            }
        });
    }

    private void probarComoTaxista(@NonNull String correo, @NonNull String contrasena,
                                   @NonNull String mensajeFirebase) {
        TaxiApi.get().loginConductor(correo, contrasena, new TaxiApi.LoginCallback() {
            @Override
            public void onExito(@NonNull Cuenta cuenta) {
                acceso.setValue(UiState.success(cuenta));
            }

            @Override
            public void onRechazado(@NonNull String mensaje) {
                // Credenciales de taxista correctas, pero cuenta en revision o desactivada.
                acceso.setValue(UiState.error(mensaje));
            }

            @Override
            public void onNoReconocido(@NonNull String mensaje) {
                // Para quien no es taxista, el error es el de siempre.
                acceso.setValue(UiState.error(mensajeFirebase));
            }
        });
    }

    /**
     * Deja el acceso en blanco.
     *
     * <p>Se llama al terminar de entrar. Si no, al volver a esta pantalla el
     * ultimo resultado seguiria ahi y se volveria a entrar solo.
     */
    public void limpiarAcceso() {
        acceso.setValue(null);
    }
}
