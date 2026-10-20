package org.iot.project.ui.client.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Role;
import org.iot.project.models.User;

/**
 * Estado del perfil del cliente (§40).
 *
 * <p>El contador de avisos procede del observador compartido de la Activity;
 * cargar el perfil no lanza otra consulta de notificaciones.
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<UiState<User>> perfil = new MutableLiveData<>();
    private long generacion;

    public LiveData<UiState<User>> getPerfil() {
        return perfil;
    }

    public void cargar() {
        perfil.setValue(UiState.loading());
        cargarPerfil();
    }

    private void cargarPerfil() {
        String uid = SessionManager.getUsuarioId();
        long solicitud = ++generacion;
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            perfil.setValue(UiState.error("Inicia sesión como cliente para consultar tu perfil."));
            return;
        }
        ServiceLocator.usuarios().usuario(uid,
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User datos) {
                        if (!vigente(uid, solicitud)) return;
                        perfil.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (!vigente(uid, solicitud)) return;
                        perfil.setValue(UiState.error(mensaje));
                    }
                });
    }

    private boolean vigente(String uid, long solicitud) {
        return solicitud == generacion && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    @Override protected void onCleared() {
        generacion++;
        super.onCleared();
    }

    public void reintentar() {
        cargar();
    }
}
