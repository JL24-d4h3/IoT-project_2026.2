package org.iot.project.ui.client.hoteldetail;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.Review;
import org.iot.project.models.Room;

import java.time.LocalDate;
import java.util.List;

/**
 * Estado de la ficha de un hotel.
 *
 * <p>Carga cuatro cosas por separado —el hotel, sus habitaciones, sus reseñas y
 * sus servicios— porque cada una puede fallar por su cuenta. Si las reseñas no
 * llegan, la ficha sigue siendo útil; sería un error tirar la pantalla entera.
 */
public class HotelDetailViewModel extends ViewModel {

    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Room>>> habitaciones = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Review>>> resenas = new MutableLiveData<>();
    private final MutableLiveData<Boolean> esFavorito = new MutableLiveData<>(false);

    private String hotelId;

    public LiveData<UiState<Hotel>> getHotel() {
        return hotel;
    }

    public LiveData<UiState<List<Room>>> getHabitaciones() {
        return habitaciones;
    }

    public LiveData<UiState<List<Review>>> getResenas() {
        return resenas;
    }

    public LiveData<Boolean> getEsFavorito() {
        return esFavorito;
    }

    /** Carga la ficha. Solo se pide una vez por hotel. */
    public void cargar(@NonNull String hotelId) {
        if (hotelId.equals(this.hotelId) && hotel.getValue() != null
                && hotel.getValue().isSuccess()) {
            return;
        }
        this.hotelId = hotelId;
        recargar();
    }

    public void recargar() {
        if (hotelId == null) {
            return;
        }
        cargarHotel();
        cargarHabitaciones();
        cargarResenas();
    }

    private void cargarHotel() {
        hotel.setValue(UiState.loading());
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                hotel.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                hotel.setValue(UiState.error(mensaje));
            }
        });
    }

    /**
     * Habitaciones disponibles para esta noche.
     *
     * <p>La ficha muestra lo que el hotel ofrece hoy; el rango elegido por el
     * usuario se aplica al reservar, más adelante.
     */
    private void cargarHabitaciones() {
        habitaciones.setValue(UiState.loading());
        LocalDate hoy = LocalDate.now();
        ServiceLocator.hoteles().habitacionesDisponibles(hotelId, hoy, hoy.plusDays(1),
                new ResultCallback<List<Room>>() {
                    @Override
                    public void onExito(@NonNull List<Room> datos) {
                        habitaciones.setValue(datos.isEmpty()
                                ? UiState.empty() : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        habitaciones.setValue(UiState.error(mensaje));
                    }
                });
    }

    private void cargarResenas() {
        resenas.setValue(UiState.loading());
        ServiceLocator.hoteles().resenas(hotelId, new ResultCallback<List<Review>>() {
            @Override
            public void onExito(@NonNull List<Review> datos) {
                resenas.setValue(datos.isEmpty()
                        ? UiState.empty() : UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                resenas.setValue(UiState.error(mensaje));
            }
        });
    }

    public void alternarFavorito() {
        Boolean actual = esFavorito.getValue();
        esFavorito.setValue(actual == null || !actual);
    }
}
