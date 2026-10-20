package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.data.repository.UserRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Avisos y contador del cliente mientras la aplicación está visible. */
public final class AvisosClienteViewModel extends ViewModel {
    private final MutableLiveData<Integer> sinLeer = new MutableLiveData<>();
    private final Set<String> procesados = new HashSet<>();
    private final Set<String> pendientes = new HashSet<>();
    private UserRepository.Suscripcion avisos;
    private TaxiRepository.Suscripcion taxis;
    private String cuentaId;
    private long generacion;
    private boolean activo;

    public LiveData<Integer> getSinLeer() { return sinLeer; }

    public void iniciar() {
        String uid = SessionManager.getUsuarioId();
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            detener();
            cuentaId = null;
            procesados.clear();
            sinLeer.setValue(null);
            return;
        }
        if (activo && uid.equals(cuentaId)) return;
        detener();
        if (!Objects.equals(uid, cuentaId)) procesados.clear();
        cuentaId = uid;
        activo = true;
        long solicitud = ++generacion;
        sinLeer.setValue(null);
        avisos = ServiceLocator.usuarios().observarNotificaciones(uid,
                new ResultCallback<List<AppNotification>>() {
                    @Override public void onExito(@NonNull List<AppNotification> datos) {
                        if (!vigente(uid, solicitud)) return;
                        int cantidad = 0;
                        for (AppNotification aviso : datos) if (!aviso.isLeida()) cantidad++;
                        sinLeer.setValue(cantidad);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (vigente(uid, solicitud)) sinLeer.setValue(null);
                    }
                });
        taxis = ServiceLocator.taxis().escucharHistorial(uid, new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> datos) {
                if (!vigente(uid, solicitud)) return;
                for (TaxiService taxi : datos) {
                    if (taxi.getEstado() == TaxiStatus.SOLICITADO) continue;
                    String evento = taxi.getId() + ":" + taxi.getEstado().name();
                    if (procesados.contains(evento) || !pendientes.add(evento)) continue;
                    ServiceLocator.taxis().sincronizarAvisoEstado(taxi.getId(), new ResultCallback<Boolean>() {
                        @Override public void onExito(@NonNull Boolean creado) {
                            if (!vigente(uid, solicitud)) return;
                            pendientes.remove(evento);
                            procesados.add(evento);
                        }
                        @Override public void onError(@NonNull String mensaje) {
                            if (vigente(uid, solicitud)) pendientes.remove(evento);
                        }
                    });
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                // Un fallo del transporte no bloquea el centro de avisos ni las reservas.
            }
        });
    }

    public void detener() {
        activo = false;
        generacion++;
        if (avisos != null) avisos.cancelar();
        if (taxis != null) taxis.cerrar();
        avisos = null;
        taxis = null;
        pendientes.clear();
    }

    private boolean vigente(String uid, long solicitud) {
        return activo && solicitud == generacion && uid.equals(cuentaId)
                && uid.equals(SessionManager.getUsuarioId()) && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    @Override protected void onCleared() {
        detener();
        super.onCleared();
    }
}
