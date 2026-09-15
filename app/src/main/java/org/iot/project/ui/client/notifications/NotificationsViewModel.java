package org.iot.project.ui.client.notifications;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.AppNotification;

import java.util.List;

/** Estado del centro de notificaciones (§39, §41). */
public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<AppNotification>>> notificaciones =
            new MutableLiveData<>();

    public LiveData<UiState<List<AppNotification>>> getNotificaciones() {
        return notificaciones;
    }

    public void cargar() {
        UiState<List<AppNotification>> actual = notificaciones.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        recargar();
    }

    public void recargar() {
        notificaciones.setValue(UiState.loading());
        ServiceLocator.usuarios().notificaciones(SessionManager.getUsuarioIdSeguro(),
                new ResultCallback<List<AppNotification>>() {
                    @Override
                    public void onExito(@NonNull List<AppNotification> datos) {
                        notificaciones.setValue(datos.isEmpty()
                                ? UiState.empty() : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        notificaciones.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Marca todo como leído.
     *
     * <p>Se llama después de pintar la lista, no antes: la pantalla ya muestra
     * los puntos de "sin leer" tal como estaban al entrar, así que el usuario
     * alcanza a ver qué había nuevo. La marca se nota en la visita siguiente.
     */
    public void marcarLeidas() {
        ServiceLocator.usuarios().marcarNotificacionesLeidas(SessionManager.getUsuarioIdSeguro());
    }
}
