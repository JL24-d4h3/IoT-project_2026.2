package org.iot.project.ui.client.review;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.Review;

/**
 * Estado de la valoración de una estadía (RF-080, §22).
 *
 * <p>La escala es de 1 a 10 y el repositorio la vuelve a comprobar: es una regla
 * del sistema (§10, regla 9), no una preferencia de esta pantalla.
 */
public class ReviewViewModel extends ViewModel {

    private final MutableLiveData<UiState<Booking>> reserva = new MutableLiveData<>();
    private final MutableLiveData<UiState<Review>> envio = new MutableLiveData<>();

    private String bookingId;

    public LiveData<UiState<Booking>> getReserva() {
        return reserva;
    }

    public LiveData<UiState<Review>> getEnvio() {
        return envio;
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

    public void valorar(float rating, @NonNull String comentario) {
        if (bookingId == null) {
            return;
        }
        envio.setValue(UiState.loading());
        ServiceLocator.reservas().valorar(bookingId, rating, comentario,
                new ResultCallback<Review>() {
                    @Override
                    public void onExito(@NonNull Review datos) {
                        envio.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        envio.setValue(UiState.error(mensaje));
                    }
                });
    }

    public void limpiarEnvio() {
        envio.setValue(null);
    }
}
