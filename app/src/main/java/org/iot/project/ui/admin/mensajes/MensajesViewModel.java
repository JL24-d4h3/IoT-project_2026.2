package org.iot.project.ui.admin.mensajes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.ConversacionDeHotel;

import java.util.List;

/**
 * Estado de la bandeja de mensajes del hotel (§44, RF-064).
 *
 * <p>No ordena ni cuenta nada: el repositorio ya devuelve la bandeja cruzada con
 * su estadía y ordenada por la fecha del último mensaje, y el número de sin leer
 * lo sabe cada conversación. Aquí solo se pasa el estado de carga a la pantalla.
 */
public class MensajesViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<ConversacionDeHotel>>> conversaciones =
            new MutableLiveData<>();

    private boolean cargado;

    public LiveData<UiState<List<ConversacionDeHotel>>> getConversaciones() {
        return conversaciones;
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    /**
     * Carga con esqueleto. Es la primera y la del reintento.
     *
     * <p>La primera carga la dispara {@code onResume} y no {@code onViewCreated}:
     * ver {@link #refrescarEnSilencio()}.
     */
    public void recargar() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            conversaciones.setValue(UiState.<List<ConversacionDeHotel>>error(
                    "Todavía no tienes un hotel asignado."));
            return;
        }
        cargado = true;
        conversaciones.setValue(UiState.<List<ConversacionDeHotel>>loading());
        pedir(hotelId);
    }

    /**
     * Recarga sin esqueleto, para lo que ya está en pantalla.
     *
     * <p>La bandeja se desactualiza sola: abrir un chat marca sus mensajes como
     * leídos (RF-068) y volver atrás tiene que apagar el contador de esa fila.
     * Volver a pasar por el esqueleto para eso dejaría la lista en blanco un
     * instante cada vez que el administrador entra y sale de una conversación.
     *
     * <p>Si todavía no se ha cargado nada, no hay nada que conservar: se hace la
     * carga entera. Por eso la primera carga de la pantalla es esta misma y no
     * hay que llamar a las dos.
     */
    public void refrescarEnSilencio() {
        String hotelId = getHotelId();
        if (!cargado || hotelId == null) {
            recargar();
            return;
        }
        pedir(hotelId);
    }

    private void pedir(@NonNull String hotelId) {
        ServiceLocator.chats().conversacionesDeHotel(hotelId,
                new ResultCallback<List<ConversacionDeHotel>>() {
                    @Override
                    public void onExito(@NonNull List<ConversacionDeHotel> datos) {
                        conversaciones.setValue(datos.isEmpty()
                                ? UiState.<List<ConversacionDeHotel>>empty()
                                : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        conversaciones.setValue(
                                UiState.<List<ConversacionDeHotel>>error(mensaje));
                    }
                });
    }
}
