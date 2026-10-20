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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ficha revalidada contra el servidor cada vez que vuelve a estar visible. */
public class HotelDetailViewModel extends ViewModel {
    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Room>>> habitaciones = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Review>>> resenas = new MutableLiveData<>();
    private final MutableLiveData<Boolean> esFavorito = new MutableLiveData<>(false);
    private String hotelId;
    private int generacion;
    private boolean cargando;

    public LiveData<UiState<Hotel>> getHotel() { return hotel; }
    public LiveData<UiState<List<Room>>> getHabitaciones() { return habitaciones; }
    public LiveData<UiState<List<Review>>> getResenas() { return resenas; }
    public LiveData<Boolean> getEsFavorito() { return esFavorito; }

    /** onResume inicia la carga; onViewCreated solo monta los observadores. */
    public void cargar(@NonNull String id) {
        if (!id.equals(hotelId)) {
            suspender();
            hotelId = id;
            esFavorito.setValue(false);
        }
        recargar();
    }

    public void recargar() {
        if (hotelId == null || cargando) return;
        cargando = true;
        int solicitud = ++generacion;
        hotel.setValue(UiState.loading());
        habitaciones.setValue(UiState.loading());
        resenas.setValue(UiState.loading());
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                if (solicitud != generacion) return;
                cargando = false;
                hotel.setValue(UiState.success(datos));
                // Sin fechas seleccionadas se muestra el inventario habilitado.
                // El formulario consulta el calendario para el período elegido.
                List<Room> disponibles = new ArrayList<>();
                for (Room habitacion : datos.getHabitaciones()) {
                    if (habitacion.isDisponible()) disponibles.add(habitacion);
                }
                disponibles.sort(Comparator.comparingDouble(Room::getPrecioNoche));
                habitaciones.setValue(disponibles.isEmpty()
                        ? UiState.empty() : UiState.success(disponibles));
                cargarResenas(solicitud);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (solicitud != generacion) return;
                cargando = false;
                hotel.setValue(UiState.error(mensaje));
                habitaciones.setValue(UiState.error(mensaje));
                resenas.setValue(UiState.error(mensaje));
            }
        });
    }

    public void suspender() {
        generacion++;
        cargando = false;
    }

    @Override
    protected void onCleared() {
        suspender();
    }

    private void cargarResenas(int solicitud) {
        ServiceLocator.hoteles().resenas(hotelId, new ResultCallback<List<Review>>() {
            @Override
            public void onExito(@NonNull List<Review> datos) {
                if (solicitud != generacion) return;
                resenas.setValue(datos.isEmpty() ? UiState.empty() : UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (solicitud == generacion) resenas.setValue(UiState.error(mensaje));
            }
        });
    }

    public void alternarFavorito() {
        esFavorito.setValue(!Boolean.TRUE.equals(esFavorito.getValue()));
    }
}
