package org.iot.project.ui.client.bookingdetail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;

/**
 * Estado del detalle de una reserva (§31, §32).
 *
 * <p>La reserva y las acciones sobre ella se siguen por separado: cancelar
 * puede fallar —una reserva activa ya no se cancela (RC-013)— sin que eso
 * borre de la pantalla la reserva que el usuario está mirando.
 */
public class BookingDetailViewModel extends ViewModel {

    private final MutableLiveData<UiState<Booking>> reserva = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> operacion = new MutableLiveData<>();

    private String bookingId;

    public LiveData<UiState<Booking>> getReserva() {
        return reserva;
    }

    /** Resultado de cancelar. Se limpia después de avisar. */
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

    public void cancelar() {
        if (bookingId == null) {
            return;
        }
        operacion.setValue(UiState.loading());
        ServiceLocator.reservas().cancelar(bookingId, new ResultCallback<Booking>() {
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

    public String getBookingId() {
        return bookingId;
    }
}
