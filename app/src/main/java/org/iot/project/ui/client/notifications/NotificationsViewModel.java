package org.iot.project.ui.client.notifications;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.UserRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Estado del centro de notificaciones (§39, §41). */
public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<AppNotification>>> notificaciones =
            new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();
    private final Set<String> lecturasSolicitadas = new HashSet<>();
    private UserRepository.Suscripcion suscripcion;
    private String cuentaId;
    private boolean visible;
    private long generacion;
    private final Observer<Cuenta> sesionObserver = cuenta -> {
        if (!Objects.equals(cuentaId, SessionManager.getUsuarioId())
                || SessionManager.getRolActivo() != Role.CLIENTE) {
            detener();
            cuentaId = null;
            notificaciones.setValue(UiState.error(
                    "Inicia sesión como cliente para ver tus notificaciones."));
            if (visible) iniciar();
        }
    };

    public NotificationsViewModel() {
        SessionManager.sesion().observeForever(sesionObserver);
    }

    public LiveData<UiState<List<AppNotification>>> getNotificaciones() {
        return notificaciones;
    }

    public LiveData<String> getAviso() {
        return aviso;
    }

    public void consumirAviso() {
        aviso.setValue(null);
    }

    public void cargar() {
        visible = true;
        iniciar();
    }

    public void recargar() {
        detener();
        if (visible) iniciar();
    }

    public void suspender() {
        visible = false;
        detener();
    }

    private void iniciar() {
        String uid = SessionManager.getUsuarioId();
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            notificaciones.setValue(UiState.error(
                    "Inicia sesión como cliente para ver tus notificaciones."));
            return;
        }
        if (suscripcion != null && uid.equals(cuentaId)) return;
        cuentaId = uid;
        long solicitud = ++generacion;
        notificaciones.setValue(UiState.loading());
        suscripcion = ServiceLocator.usuarios().observarNotificaciones(uid,
                new ResultCallback<List<AppNotification>>() {
                    @Override
                    public void onExito(@NonNull List<AppNotification> datos) {
                        if (!vigente(uid, solicitud)) return;
                        notificaciones.setValue(datos.isEmpty()
                                ? UiState.empty() : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (!vigente(uid, solicitud)) return;
                        notificaciones.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Marca exactamente la lista entregada al adaptador, nunca una consulta nueva. */
    public void marcarLeidas(@NonNull List<AppNotification> entregadas) {
        String uid = cuentaId;
        long solicitud = generacion;
        if (uid == null || !vigente(uid, solicitud)) return;
        UiState<List<AppNotification>> actual = notificaciones.getValue();
        // Un diff del adaptador anterior puede terminar tras una recarga o cambio de cuenta.
        if (actual == null || !actual.isSuccess() || actual.requireData() != entregadas) return;
        List<String> ids = new ArrayList<>();
        for (AppNotification notificacion : entregadas) {
            if (!notificacion.isLeida() && lecturasSolicitadas.add(notificacion.getId())) {
                ids.add(notificacion.getId());
            }
        }
        if (ids.isEmpty()) return;
        ServiceLocator.usuarios().marcarNotificacionesLeidas(uid, ids,
                new ResultCallback<Boolean>() {
                    @Override public void onExito(@NonNull Boolean resultado) { }

                    @Override public void onError(@NonNull String mensaje) {
                        if (vigente(uid, solicitud)) aviso.setValue(mensaje);
                    }
                });
    }

    private boolean vigente(String uid, long solicitud) {
        return visible && solicitud == generacion && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    private void detener() {
        generacion++;
        if (suscripcion != null) {
            suscripcion.cancelar();
            suscripcion = null;
        }
        lecturasSolicitadas.clear();
        aviso.setValue(null);
    }

    @Override protected void onCleared() {
        visible = false;
        detener();
        SessionManager.sesion().removeObserver(sesionObserver);
        super.onCleared();
    }
}
