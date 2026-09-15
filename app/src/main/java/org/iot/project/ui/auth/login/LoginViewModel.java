package org.iot.project.ui.auth.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Cuenta;

import java.util.List;

/**
 * Acceso a la aplicacion (§74).
 *
 * <p>No comprueba la contrasena, y no es un olvido: sin backend no hay nada con
 * lo que compararla, y guardar secretos en el cliente es justo lo que RC-042 y
 * RT-038 prohiben. Lo que si resuelve de verdad es lo que viene despues: con el
 * correo se identifica la cuenta y su rol, y ese rol decide la interfaz que se
 * carga (RF-004).
 */
public class LoginViewModel extends ViewModel {

    private final MutableLiveData<UiState<Cuenta>> acceso = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Cuenta>>> ejemplos = new MutableLiveData<>();

    public LiveData<UiState<Cuenta>> getAcceso() {
        return acceso;
    }

    public LiveData<UiState<List<Cuenta>>> getEjemplos() {
        return ejemplos;
    }

    /**
     * Intenta entrar con un correo.
     *
     * <p>No recibe la contrasena a proposito. La pantalla comprueba que se haya
     * escrito algo —un campo obligatorio vacio hay que avisarlo— y la descarta
     * ahi mismo: lo que no tiene que ocurrir es que un secreto que nadie va a
     * usar quede guardado en el estado de esta pantalla.
     */
    public void entrar(@NonNull String correo) {
        acceso.setValue(UiState.loading());
        ServiceLocator.acceso().cuentaPorEmail(correo, new ResultCallback<Cuenta>() {
            @Override
            public void onExito(@NonNull Cuenta datos) {
                acceso.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                acceso.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Carga las cuentas de ejemplo con las que se puede entrar sin escribir el correo. */
    public void cargarEjemplos() {
        ejemplos.setValue(UiState.loading());
        ServiceLocator.acceso().cuentasDeDemostracion(new ResultCallback<List<Cuenta>>() {
            @Override
            public void onExito(@NonNull List<Cuenta> datos) {
                ejemplos.setValue(datos.isEmpty() ? UiState.<List<Cuenta>>empty()
                        : UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                ejemplos.setValue(UiState.error(mensaje));
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
