package org.iot.project.ui.superadmin.hoteles;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;

import java.util.UUID;

/**
 * Alta de un hotel (RF-007).
 *
 * <p>El hotel nace <b>sin publicar y sin administrador</b>: son los dos pasos
 * que vienen despues —el dueño elige administrador, el superadministrador lo
 * asigna, el administrador rellena el hotel y lo publica— y darlo por hecho aqui
 * los saltaria. El cliente no lo vera hasta el final de ese recorrido.
 *
 * <p>Se piden solo los datos basicos. Las fotografias, las habitaciones y los
 * servicios necesitan a alguien que conozca el hotel, y ese es su administrador.
 *
 * <p>La validacion que se puede hacer sin salir del formulario la hace la
 * pantalla, para que el error salga en el campo que lo causo; la que decide si
 * el alta vale es el repositorio, que es el unico que no se puede saltar.
 */
public class AltaHotelViewModel extends ViewModel {

    private final MutableLiveData<UiState<Hotel>> alta = new MutableLiveData<>();
    private final String borradorId;

    public AltaHotelViewModel(@NonNull SavedStateHandle estadoGuardado) {
        String id = estadoGuardado.get("hotelBorradorId");
        if (id == null) {
            id = UUID.randomUUID().toString();
            estadoGuardado.set("hotelBorradorId", id);
        }
        borradorId = id;
    }

    public LiveData<UiState<Hotel>> getAlta() {
        return alta;
    }

    public boolean estaCargando() {
        UiState<Hotel> estado = alta.getValue();
        return estado != null && estado.isLoading();
    }

    /** Arma el borrador y lo registra. La validacion la hace el repositorio. */
    public void registrar(@NonNull String nombre, @NonNull String ciudad,
                          @NonNull String distrito, @NonNull String direccion,
                          double latitud, double longitud) {
        UiState<Hotel> estado = alta.getValue();
        if (estado != null && (estado.isLoading() || estado.isSuccess())) {
            return;
        }
        // El mismo alta conserva su identificador al reintentar o recrear la vista.
        Hotel borrador = new Hotel(borradorId, nombre, distrito, ciudad);
        borrador.setDireccion(direccion);
        borrador.setUbicacion(latitud, longitud);

        alta.setValue(UiState.loading());
        ServiceLocator.superadmin().registrarHotel(borrador, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel dato) {
                alta.setValue(UiState.success(dato));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                alta.setValue(UiState.error(mensaje));
            }
        });
    }

}
