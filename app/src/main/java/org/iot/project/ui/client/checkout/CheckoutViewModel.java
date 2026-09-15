package org.iot.project.ui.client.checkout;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;

/**
 * Estado del checkout (RF-056).
 *
 * <p>Solo se puede cerrar una estadía activa. La comprobación se hace aquí para
 * no ofrecer un botón que va a fallar, pero la que manda es la del repositorio:
 * una pantalla se puede saltar, un repositorio no.
 */
public class CheckoutViewModel extends ViewModel {

    private final MutableLiveData<UiState<Booking>> reserva = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> operacion = new MutableLiveData<>();

    private String bookingId;

    public LiveData<UiState<Booking>> getReserva() {
        return reserva;
    }

    public LiveData<UiState<Booking>> getOperacion() {
        return operacion;
    }

    public void cargar(@NonNull String bookingId) {
        if (bookingId.equals(this.bookingId) && reserva.getValue() != null
                && reserva.getValue().isSuccess()) {
            return;
        }
        this.bookingId = bookingId;
        recargar();
    }

    public void recargar() {
        if (bookingId == null) {
            return;
        }
        reserva.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(bookingId, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                reserva.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                reserva.setValue(UiState.error(mensaje));
            }
        });
    }

    public void finalizar() {
        if (bookingId == null) {
            return;
        }
        operacion.setValue(UiState.loading());
        ServiceLocator.reservas().finalizar(bookingId, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                operacion.setValue(UiState.success(datos));
                reserva.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                operacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void limpiarOperacion() {
        operacion.setValue(null);
    }
}
