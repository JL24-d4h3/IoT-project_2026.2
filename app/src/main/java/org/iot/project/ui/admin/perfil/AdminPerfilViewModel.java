package org.iot.project.ui.admin.perfil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.User;

/**
 * Estado del perfil del administrador de hotel.
 *
 * <p>Se piden dos cosas y se publican por separado: la cuenta y el hotel que
 * administra. No van juntas porque un fallo al leer el hotel no tiene por que
 * dejar al administrador sin ver su propio nombre —eso es lo que el cliente ya
 * resolvio asi en su perfil (§40) y no hay razon para hacerlo distinto aqui—.
 * Lo que si hace la pantalla es decir en su sitio que el hotel no llego, en vez
 * de esconder la tarjeta: un administrador sin hotel en su perfil se queda
 * mirando un hueco sin saber si es que no tiene hotel o es que no cargo.
 */
public class AdminPerfilViewModel extends ViewModel {

    private final MutableLiveData<UiState<User>> perfil = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();

    public LiveData<UiState<User>> getPerfil() {
        return perfil;
    }

    public LiveData<UiState<Hotel>> getHotel() {
        return hotel;
    }

    public void cargar() {
        perfil.setValue(UiState.loading());
        cargarPerfil();
        cargarHotel();
    }

    public void reintentar() {
        cargar();
    }

    private void cargarPerfil() {
        ServiceLocator.usuarios().usuario(SessionManager.getIdentidadId(),
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User datos) {
                        perfil.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        perfil.setValue(UiState.error(mensaje));
                    }
                });
    }

    private void cargarHotel() {
        String hotelId = SessionManager.getHotelAdministrado();
        if (hotelId == null) {
            // No es un fallo de red: es que esta cuenta no administra nada. Se
            // dice tal cual, porque reintentar no lo arreglaria.
            hotel.setValue(UiState.<Hotel>error("Esta cuenta no tiene un hotel asignado."));
            return;
        }
        hotel.setValue(UiState.<Hotel>loading());
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

    /** Nombre del hotel administrado, si ya se sabe. Para la cabecera. */
    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }
}
