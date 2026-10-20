package org.iot.project.ui.client.payment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Card;
import org.iot.project.models.Payment;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Confirmación de pago simulado guardada en la reserva remota. */
public class PaymentViewModel extends ViewModel {

    public static class Datos {
        public final Booking reserva;
        public final List<Card> tarjetas;
        Datos(@NonNull Booking reserva, @NonNull List<Card> tarjetas) {
            this.reserva = reserva;
            this.tarjetas = tarjetas;
        }
    }

    private final SavedStateHandle estado;
    private final MutableLiveData<UiState<Datos>> datos = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> pago = new MutableLiveData<>();
    private String bookingId;
    private String cuentaId;
    private Booking reserva;
    private long generacion;
    private boolean cargando;
    private boolean pagando;

    public PaymentViewModel(@NonNull SavedStateHandle estado) {
        this.estado = estado;
    }

    public LiveData<UiState<Datos>> getDatos() { return datos; }
    public LiveData<UiState<Booking>> getPago() { return pago; }
    public boolean estaPagando() { return pagando; }
    @Nullable public String getMetodoElegido() { return estado.get("pagoMetodo"); }
    public void setMetodoElegido(@NonNull String metodo) {
        if (!pagando) estado.set("pagoMetodo", metodo);
    }

    public void cargar(@NonNull String id) {
        String uid = SessionManager.getUsuarioId();
        if (id.equals(bookingId) && Objects.equals(cuentaId, uid)
                && (cargando || pagando || (datos.getValue() != null
                && datos.getValue().isSuccess()))) return;
        if (!id.equals(estado.get("pagoReserva")) || !Objects.equals(uid, estado.get("pagoUid"))) {
            estado.set("pagoReserva", id);
            estado.set("pagoUid", uid);
            estado.set("pagoId", UUID.randomUUID().toString());
            estado.remove("pagoMetodo");
        }
        if (estado.get("pagoId") == null) estado.set("pagoId", UUID.randomUUID().toString());
        bookingId = id;
        pago.setValue(null);
        recargar();
    }

    public void recargar() {
        String uid = SessionManager.getUsuarioId();
        if (bookingId == null) return;
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            generacion++;
            cargando = false;
            pagando = false;
            datos.setValue(UiState.error("Inicia sesión como cliente para confirmar tu reserva."));
            return;
        }
        if ((cargando || pagando) && uid.equals(cuentaId)) return;
        if (uid.equals(cuentaId) && pago.getValue() != null && pago.getValue().isSuccess()) return;
        cuentaId = uid;
        long solicitud = ++generacion;
        cargando = true;
        pagando = false;
        reserva = null;
        datos.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(bookingId, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking encontrada) {
                if (!vigente(uid, solicitud)) return;
                if (!uid.equals(encontrada.getClienteId())) {
                    cargando = false;
                    datos.setValue(UiState.error("Esta reserva pertenece a otra cuenta."));
                    return;
                }
                reserva = encontrada;
                if (encontrada.getEstado() == BookingStatus.CONFIRMADA) {
                    cargando = false;
                    pago.setValue(UiState.success(encontrada));
                    return;
                }
                if (encontrada.getEstado() != BookingStatus.PENDIENTE) {
                    cargando = false;
                    datos.setValue(UiState.error("Esta reserva ya está "
                            + encontrada.getEstado().getDisplayName().toLowerCase() + "."));
                    return;
                }
                pedirTarjetas(encontrada, uid, solicitud);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                datos.setValue(UiState.error(mensaje));
            }
        });
    }

    private void pedirTarjetas(Booking encontrada, String uid, long solicitud) {
        ServiceLocator.usuarios().usuario(uid, new ResultCallback<User>() {
            @Override public void onExito(@NonNull User usuario) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                datos.setValue(UiState.success(new Datos(encontrada, usuario.getTarjetas())));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                datos.setValue(UiState.success(new Datos(encontrada, Collections.emptyList())));
            }
        });
    }

    public boolean puedePagar() {
        return !cargando && !pagando && reserva != null
                && reserva.getEstado() == BookingStatus.PENDIENTE
                && Objects.equals(cuentaId, SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE
                && datos.getValue() != null && datos.getValue().isSuccess();
    }

    public void pagar(@NonNull String metodo) {
        if (!puedePagar() || metodo.trim().isEmpty()) return;
        String id = estado.get("pagoId");
        if (id == null) return;
        String uid = cuentaId;
        long solicitud = generacion;
        estado.set("pagoMetodo", metodo);
        Payment registro = new Payment(id, bookingId, reserva.getTotal(),
                metodo.trim(), LocalDateTime.now());
        pagando = true;
        pago.setValue(UiState.loading());
        ServiceLocator.reservas().pagar(bookingId, registro, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking actualizada) {
                if (!vigente(uid, solicitud)) return;
                pagando = false;
                reserva = actualizada;
                pago.setValue(UiState.success(actualizada));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                pagando = false;
                pago.setValue(UiState.error(mensaje));
            }
        });
    }

    public double getTotal() { return reserva != null ? reserva.getTotal() : 0; }

    public void limpiarPago() {
        UiState<Booking> actual = pago.getValue();
        if (actual != null && actual.isError()) pago.setValue(null);
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
