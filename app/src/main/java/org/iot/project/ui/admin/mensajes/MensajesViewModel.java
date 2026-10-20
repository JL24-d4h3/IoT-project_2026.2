package org.iot.project.ui.admin.mensajes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bandeja de estadías activas, con resumen y sin leer procedentes de Firestore. */
public class MensajesViewModel extends ViewModel {
    private final MutableLiveData<UiState<List<ConversacionDeHotel>>> conversaciones =
            new MutableLiveData<>();
    private final Observer<Cuenta> sesion = cuenta -> revisarSesion();
    @Nullable private ChatRepository.Suscripcion suscripcion;
    @Nullable private String uid;
    @Nullable private String hotelId;
    private long generacion;
    private boolean visible;

    public MensajesViewModel() { SessionManager.sesion().observeForever(sesion); }
    public LiveData<UiState<List<ConversacionDeHotel>>> getConversaciones() {
        return conversaciones;
    }
    @Nullable public String getHotelId() { return SessionManager.getHotelAdministrado(); }

    public void iniciarSeguimiento() {
        visible = true;
        revisarSesion();
        conectar();
    }

    public void detenerSeguimiento() {
        visible = false;
        cerrarEscucha();
    }

    public void recargar() {
        cerrarEscucha();
        conversaciones.setValue(UiState.loading());
        conectar();
    }

    private void revisarSesion() {
        String cuenta = SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                ? SessionManager.getUsuarioId() : null;
        String hotel = SessionManager.getHotelAdministrado();
        if (Objects.equals(uid, cuenta) && Objects.equals(hotelId, hotel)) return;
        cerrarEscucha();
        uid = cuenta;
        hotelId = hotel;
        conversaciones.setValue(UiState.loading());
        conectar();
    }

    private void conectar() {
        if (!visible || suscripcion != null) return;
        if (uid == null || hotelId == null) {
            conversaciones.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final long version = ++generacion;
        conversaciones.setValue(UiState.loading());
        suscripcion = ServiceLocator.chats().escucharConversacionesDeHotel(hotel,
                new ResultCallback<List<ConversacionDeHotel>>() {
                    @Override public void onExito(@NonNull List<ConversacionDeHotel> datos) {
                        if (!vigente(cuenta, hotel, version)) return;
                        List<ConversacionDeHotel> activas = new ArrayList<>();
                        for (ConversacionDeHotel dato : datos) {
                            if (hotel.equals(dato.getConversacion().getHotelId())
                                    && dato.getEstadia().getReserva().permiteChat()) activas.add(dato);
                        }
                        conversaciones.setValue(activas.isEmpty()
                                ? UiState.empty() : UiState.success(activas));
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (vigente(cuenta, hotel, version)) {
                            conversaciones.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    private boolean vigente(String cuenta, String hotel, long version) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return visible && version == generacion && actual != null
                && cuenta.equals(actual.getUid()) && cuenta.equals(SessionManager.getUsuarioId())
                && hotel.equals(SessionManager.getHotelAdministrado())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
    }

    private void cerrarEscucha() {
        generacion++;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    @Override protected void onCleared() {
        cerrarEscucha();
        SessionManager.sesion().removeObserver(sesion);
    }
}
