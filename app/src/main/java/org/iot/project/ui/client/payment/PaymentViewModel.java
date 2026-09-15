package org.iot.project.ui.client.payment;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.Card;
import org.iot.project.models.Payment;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Estado del pago de una reserva (RF-045).
 *
 * <p>Los medios de pago salen del perfil del usuario, no de la reserva: son
 * suyos y valen para cualquier reserva. El cobro es simulado y no se pide ni se
 * guarda ningún dato de tarjeta (RC-011).
 */
public class PaymentViewModel extends ViewModel {

    /** Lo que la pantalla necesita para pintarse. */
    public static class Datos {

        public final Booking reserva;
        public final List<Card> tarjetas;

        Datos(@NonNull Booking reserva, @NonNull List<Card> tarjetas) {
            this.reserva = reserva;
            this.tarjetas = tarjetas;
        }
    }

    private final MutableLiveData<UiState<Datos>> datos = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> pago = new MutableLiveData<>();

    private String bookingId;
    private Booking reserva;

    public LiveData<UiState<Datos>> getDatos() {
        return datos;
    }

    public LiveData<UiState<Booking>> getPago() {
        return pago;
    }

    public void cargar(@NonNull String bookingId) {
        if (bookingId.equals(this.bookingId) && datos.getValue() != null
                && datos.getValue().isSuccess()) {
            return;
        }
        this.bookingId = bookingId;
        recargar();
    }

    public void recargar() {
        if (bookingId == null) {
            return;
        }
        datos.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(bookingId, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking encontrada) {
                if (encontrada.getEstado() != org.iot.project.models.BookingStatus.PENDIENTE) {
                    datos.setValue(UiState.error(
                            "Esta reserva ya está " + encontrada.getEstado()
                                    .getDisplayName().toLowerCase() + "."));
                    return;
                }
                reserva = encontrada;
                pedirTarjetas();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                datos.setValue(UiState.error(mensaje));
            }
        });
    }

    private void pedirTarjetas() {
        ServiceLocator.usuarios().usuario(SessionManager.getUsuarioIdSeguro(),
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User usuario) {
                        datos.setValue(UiState.success(
                                new Datos(reserva, usuario.getTarjetas())));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        // Sin el perfil se puede pagar en recepción: la pantalla
                        // sigue siendo util, asi que no se tira abajo.
                        datos.setValue(UiState.success(
                                new Datos(reserva, Collections.emptyList())));
                    }
                });
    }

    /**
     * Registra el pago.
     *
     * <p>El metodo viaja como texto ya montado ("Visa ·4821" o "Pagar en
     * recepcion"): es lo unico que se guarda del medio de pago.
     */
    public void pagar(@NonNull String metodo) {
        if (bookingId == null) {
            return;
        }
        pago.setValue(UiState.loading());
        Payment registro = new Payment("P" + System.currentTimeMillis(), bookingId,
                reserva != null ? reserva.getTotal() : 0, metodo, LocalDateTime.now());
        ServiceLocator.reservas().pagar(bookingId, registro, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                reserva = datos;
                pago.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                pago.setValue(UiState.error(mensaje));
            }
        });
    }

    public double getTotal() {
        return reserva != null ? reserva.getTotal() : 0;
    }

    public void limpiarPago() {
        pago.setValue(null);
    }
}
