package org.iot.project.ui.auth.recuperacion;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;

/**
 * Recuperacion de contrasena (§74).
 *
 * <p>El resultado de exito es el correo al que se habria enviado el enlace, no
 * una confirmacion de que la cuenta existe: el repositorio contesta lo mismo
 * para cualquier correo, para que este formulario no sirva para averiguar quien
 * esta registrado.
 */
public class RecuperacionViewModel extends ViewModel {

    private final MutableLiveData<UiState<String>> envio = new MutableLiveData<>();

    public LiveData<UiState<String>> getEnvio() {
        return envio;
    }

    public void recuperar(@NonNull String correo) {
        envio.setValue(UiState.loading());
        ServiceLocator.acceso().recuperarContrasena(correo, new ResultCallback<String>() {
            @Override
            public void onExito(@NonNull String datos) {
                envio.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                envio.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Deja el envio en blanco, para que volver a la pantalla no lo repita. */
    public void limpiarEnvio() {
        envio.setValue(null);
    }
}
