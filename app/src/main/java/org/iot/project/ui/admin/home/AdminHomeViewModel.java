package org.iot.project.ui.admin.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.ResumenHotel;
import org.iot.project.models.Role;

import java.util.Objects;
import java.util.List;

/** Resumen real del hotel asignado; la mensajería no condiciona las métricas del panel. */
public class AdminHomeViewModel extends ViewModel {
    public static final class Contenido {
        @NonNull public final ResumenHotel resumen;
        Contenido(@NonNull ResumenHotel resumen) { this.resumen = resumen; }
    }

    private final MutableLiveData<UiState<Contenido>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<ConversacionDeHotel>>> mensajes = new MutableLiveData<>();
    private GestionHotelRepository.Suscripcion suscripcion;
    private ChatRepository.Suscripcion chat;
    private String cuentaId;
    private String hotelId;
    private long generacion;
    private long generacionChat;
    private boolean visible;

    public LiveData<UiState<Contenido>> getContenido() { return contenido; }
    public LiveData<UiState<List<ConversacionDeHotel>>> getMensajes() { return mensajes; }
    @Nullable public String getHotelId() { return SessionManager.getHotelAdministrado(); }

    public void cargar() {
        visible = true;
        iniciar();
    }

    public void refrescarEnSilencio() {
        if (visible) iniciar();
    }

    public void reintentar() {
        suspender();
        visible = true;
        iniciar();
    }

    private void iniciar() {
        String uid = SessionManager.getUsuarioId();
        String asignado = getHotelId();
        if (uid == null || SessionManager.getRolActivo() != Role.ADMIN_HOTEL) {
            suspender();
            contenido.setValue(UiState.error("Inicia sesión como administrador de hotel para consultar tu panel."));
            return;
        }
        if (asignado == null || asignado.isEmpty()) {
            suspender();
            contenido.setValue(UiState.empty());
            return;
        }
        if (suscripcion != null && uid.equals(cuentaId) && asignado.equals(hotelId)) {
            iniciarMensajes(uid, asignado, generacion);
            return;
        }
        if (suscripcion != null) suscripcion.cerrar();
        cerrarChat();
        cuentaId = uid;
        hotelId = asignado;
        long solicitud = ++generacion;
        contenido.setValue(UiState.loading());
        suscripcion = ServiceLocator.gestion().escucharResumen(asignado, new ResultCallback<ResumenHotel>() {
            @Override public void onExito(@NonNull ResumenHotel resumen) {
                if (vigente(uid, asignado, solicitud)) contenido.setValue(UiState.success(new Contenido(resumen)));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(uid, asignado, solicitud)) contenido.setValue(UiState.error(mensaje));
            }
        });
        iniciarMensajes(uid, asignado, solicitud);
    }

    private void iniciarMensajes(String uid, String hotel, long solicitud) {
        if (!visible || chat != null) return;
        long tanda = ++generacionChat;
        mensajes.setValue(UiState.loading());
        chat = ServiceLocator.chats().escucharConversacionesDeHotel(hotel,
                new ResultCallback<List<ConversacionDeHotel>>() {
                    @Override public void onExito(@NonNull List<ConversacionDeHotel> conversaciones) {
                        if (!vigente(uid, hotel, solicitud) || tanda != generacionChat) return;
                        mensajes.setValue(conversaciones.isEmpty()
                                ? UiState.empty() : UiState.success(conversaciones));
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (vigente(uid, hotel, solicitud) && tanda == generacionChat) {
                            mensajes.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    public void reintentarMensajes() {
        if (!visible || cuentaId == null || hotelId == null) return;
        cerrarChat();
        iniciarMensajes(cuentaId, hotelId, generacion);
    }

    private void cerrarChat() {
        generacionChat++;
        if (chat != null) chat.cerrar();
        chat = null;
    }

    public void suspender() {
        visible = false;
        generacion++;
        cerrarChat();
        if (suscripcion != null) suscripcion.cerrar();
        suscripcion = null;
    }

    private boolean vigente(String uid, String asignado, long solicitud) {
        return visible && solicitud == generacion && Objects.equals(cuentaId, uid)
                && Objects.equals(hotelId, asignado) && uid.equals(SessionManager.getUsuarioId())
                && asignado.equals(SessionManager.getHotelAdministrado())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
    }

    @Override protected void onCleared() {
        suspender();
        super.onCleared();
    }
}
