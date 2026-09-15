package org.iot.project.ui.admin.clientes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.ClienteDeHotel;

import java.util.List;

/**
 * Estado de la lista de clientes del hotel (§44).
 *
 * <p>No agrupa ni suma nada: el repositorio ya devuelve a cada cliente con sus
 * estancias resumidas y en el orden en que se leen. Aquí solo se pasa el estado
 * de carga a la pantalla.
 */
public class ClientesViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<ClienteDeHotel>>> clientes = new MutableLiveData<>();

    private boolean cargado;

    public LiveData<UiState<List<ClienteDeHotel>>> getClientes() {
        return clientes;
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    public void cargar() {
        if (cargado) {
            return;
        }
        recargar();
    }

    public void recargar() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            clientes.setValue(UiState.<List<ClienteDeHotel>>error(
                    "Esta cuenta no tiene un hotel asignado."));
            return;
        }
        cargado = true;
        clientes.setValue(UiState.<List<ClienteDeHotel>>loading());
        ServiceLocator.gestion().clientes(hotelId,
                new ResultCallback<List<ClienteDeHotel>>() {
                    @Override
                    public void onExito(@NonNull List<ClienteDeHotel> datos) {
                        clientes.setValue(datos.isEmpty()
                                ? UiState.<List<ClienteDeHotel>>empty()
                                : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        clientes.setValue(UiState.<List<ClienteDeHotel>>error(mensaje));
                    }
                });
    }
}
