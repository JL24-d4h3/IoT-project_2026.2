package org.iot.project.ui.client.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.AppNotification;
import org.iot.project.models.User;

import java.util.List;

/**
 * Estado del perfil del cliente (§40).
 *
 * <p>Los datos personales y el resumen de notificaciones se piden por separado
 * porque son dos cosas distintas: si el contador de notificaciones falla, el
 * perfil sigue siendo el perfil. Encadenarlos dejaria al cliente sin ver su
 * propio nombre por un fallo que no tiene nada que ver.
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<UiState<User>> perfil = new MutableLiveData<>();
    private final MutableLiveData<Integer> sinLeer = new MutableLiveData<>();

    public LiveData<UiState<User>> getPerfil() {
        return perfil;
    }

    /** Notificaciones no leidas, o {@code null} mientras no se sabe. */
    public LiveData<Integer> getSinLeer() {
        return sinLeer;
    }

    public void cargar() {
        perfil.setValue(UiState.loading());
        cargarPerfil();
        cargarNotificaciones();
    }

    private void cargarPerfil() {
        ServiceLocator.usuarios().usuario(SessionManager.getIdentidadId(),
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User datos) {
                        perfil.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        perfil.setValue(UiState.error(mensaje));
                    }
                });
    }

    private void cargarNotificaciones() {
        ServiceLocator.usuarios().notificaciones(SessionManager.getIdentidadId(),
                new ResultCallback<List<AppNotification>>() {
                    @Override
                    public void onExito(@NonNull List<AppNotification> datos) {
                        int cuenta = 0;
                        for (AppNotification aviso : datos) {
                            if (!aviso.isLeida()) {
                                cuenta++;
                            }
                        }
                        sinLeer.setValue(cuenta);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        // Sin dato no se enseña un cero: un cero afirmaria que
                        // no hay nada pendiente, y no lo sabemos.
                        sinLeer.setValue(null);
                    }
                });
    }

    public void reintentar() {
        cargar();
    }
}
