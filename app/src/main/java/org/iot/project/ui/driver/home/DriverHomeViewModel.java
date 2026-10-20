package org.iot.project.ui.driver.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;

/** Portada autenticada del taxista, independiente del sistema de traslados. */
public class DriverHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<Cuenta>> contenido = new MutableLiveData<>();

    public LiveData<UiState<Cuenta>> getContenido() {
        return contenido;
    }

    /** Los datos personales proceden del perfil que ya verificó la sesión. */
    public void actualizarCuenta(@Nullable Cuenta cuenta) {
        if (cuenta == null || cuenta.getRol() != Role.CONDUCTOR
                || !cuenta.getIdentidadId().equals(SessionManager.getConductorId())) {
            contenido.setValue(UiState.error(
                    "La sesión del taxista ya no está disponible. Vuelve a iniciar sesión."));
            return;
        }
        contenido.setValue(UiState.success(cuenta));
    }

    public void cargar() {
        actualizarCuenta(SessionManager.getCuenta());
    }

    public void recargar() {
        cargar();
    }

    /**
     * Conserva la firma de la hoja de validación existente. Hasta conectar el
     * sistema de taxistas no se aceptan acciones ni se simulan traslados.
     */
    public void validarCodigo(@NonNull String taxiId, @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        callback.onError("Los servicios de taxi estarán disponibles próximamente.");
    }
}
