package org.iot.project.ui.auth.registro;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;

/**
 * Alta de un cliente desde la aplicacion (RF-001).
 *
 * <p>El cliente queda habilitado al terminar el registro (RF-002): no hay paso
 * de aprobacion, y por eso el alta devuelve la cuenta ya utilizable.
 *
 * <p>No recibe contrasena. Esta pantalla la pide, comprueba que tenga el largo
 * minimo y que las dos coincidan, y la descarta ahi mismo: guardarla obligaria
 * a inventar un almacen de secretos en una entrega que es solo front end
 * (RC-042, RT-038).
 */
public class RegistroViewModel extends ViewModel {

    private final MutableLiveData<UiState<Cuenta>> alta = new MutableLiveData<>();

    public LiveData<UiState<Cuenta>> getAlta() {
        return alta;
    }

    public void registrar(@NonNull String nombres, @NonNull String apellidos,
                          @NonNull String correo, @NonNull String telefono) {
        User borrador = new User("", nombres.trim(), apellidos.trim(), Role.CLIENTE);
        borrador.setEmail(correo.trim());
        borrador.setTelefono(telefono.trim());

        alta.setValue(UiState.loading());
        ServiceLocator.acceso().registrar(borrador, new ResultCallback<Cuenta>() {
            @Override
            public void onExito(@NonNull Cuenta datos) {
                alta.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                alta.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Deja el alta en blanco, para que volver a la pantalla no la repita. */
    public void limpiarAlta() {
        alta.setValue(null);
    }
}
