package org.iot.project.ui.client.bookingdetail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.Role;

import java.util.Objects;

/** Detalle remoto y cancelación, vinculados a la cuenta que hizo la consulta. */
public class BookingDetailViewModel extends ViewModel {
    private final MutableLiveData<UiState<Booking>> reserva = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> operacion = new MutableLiveData<>();
    private String bookingId;
    private String cuentaId;
    private long generacion;
    private boolean cargando;
    private boolean escribiendo;

    public LiveData<UiState<Booking>> getReserva() { return reserva; }
    public LiveData<UiState<Booking>> getOperacion() { return operacion; }
    public String getBookingId() { return bookingId; }
    public boolean estaOcupado() { return cargando || escribiendo; }

    public void cargar(@NonNull String id) {
        if (id.equals(bookingId) && Objects.equals(cuentaId, SessionManager.getUsuarioId())
                && (estaOcupado() || (reserva.getValue() != null
                && reserva.getValue().isSuccess()))) return;
        bookingId = id;
        operacion.setValue(null);
        recargar();
    }

    public void recargar() {
        String uid = SessionManager.getUsuarioId();
        if (bookingId == null) return;
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            generacion++;
            cargando = false;
            escribiendo = false;
            reserva.setValue(UiState.error("Inicia sesión como cliente para ver esta reserva."));
            return;
        }
        if (estaOcupado() && uid.equals(cuentaId)) return;
        cuentaId = uid;
        long solicitud = ++generacion;
        cargando = true;
        escribiendo = false;
        reserva.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(bookingId, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                reserva.setValue(uid.equals(datos.getClienteId())
                        ? UiState.success(datos)
                        : UiState.error("Esta reserva pertenece a otra cuenta."));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                reserva.setValue(UiState.error(mensaje));
            }
        });
    }

    public void cancelar() {
        UiState<Booking> actual = reserva.getValue();
        String uid = SessionManager.getUsuarioId();
        if (estaOcupado() || actual == null || !actual.isSuccess()
                || uid == null || !uid.equals(cuentaId)
                || !uid.equals(actual.requireData().getClienteId())
                || SessionManager.getRolActivo() != Role.CLIENTE
                || !actual.requireData().getEstado().allowsCancellation()) return;
        escribiendo = true;
        long solicitud = generacion;
        operacion.setValue(UiState.loading());
        ServiceLocator.reservas().cancelar(bookingId, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (!vigente(uid, solicitud)) return;
                escribiendo = false;
                reserva.setValue(UiState.success(datos));
                operacion.setValue(UiState.success(datos));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                escribiendo = false;
                operacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void limpiarOperacion() {
        UiState<Booking> actual = operacion.getValue();
        if (actual == null || !actual.isLoading()) operacion.setValue(null);
    }

    private boolean vigente(String uid, long solicitud) {
        return solicitud == generacion && Objects.equals(cuentaId, uid)
                && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    @Override protected void onCleared() {
        generacion++;
        super.onCleared();
    }
}
