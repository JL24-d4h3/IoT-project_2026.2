package org.iot.project.ui.admin.mensajes;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;

/**
 * Estado del chat visto desde el hotel (§44, RF-064 a RF-068).
 *
 * <p>Es el mismo chat que el del cliente con el lado cambiado: carga con
 * {@code conversacionDeHotel} —que trae el huésped y su estadía ya resueltos—,
 * escribe con {@code responder} y marca como leído lo del cliente.
 *
 * <p>La carga y el envío comparten un único estado porque son la misma lista
 * vista desde dos momentos: al responder, la respuesta ya trae la conversación
 * entera y no hace falta pedirla otra vez.
 */
public class AdminChatViewModel extends ViewModel {

    private final MutableLiveData<UiState<ConversacionDeHotel>> conversacion =
            new MutableLiveData<>();
    private final MutableLiveData<UiState<Conversation>> envio = new MutableLiveData<>();

    private String bookingId;

    public LiveData<UiState<ConversacionDeHotel>> getConversacion() {
        return conversacion;
    }

    public LiveData<UiState<Conversation>> getEnvio() {
        return envio;
    }

    public void cargar(@NonNull String bookingId) {
        if (bookingId.equals(this.bookingId) && conversacion.getValue() != null
                && conversacion.getValue().isSuccess()) {
            return;
        }
        this.bookingId = bookingId;
        recargar();
    }

    public void recargar() {
        if (bookingId == null) {
            return;
        }
        conversacion.setValue(UiState.loading());
        ServiceLocator.chats().conversacionDeHotel(bookingId,
                new ResultCallback<ConversacionDeHotel>() {
                    @Override
                    public void onExito(@NonNull ConversacionDeHotel datos) {
                        conversacion.setValue(UiState.success(datos));
                        // RF-068: abrir la conversación es lo que marca los
                        // mensajes del cliente como leídos. Se hace después de
                        // publicar para que el estado de "sin leer" de la
                        // bandeja se apague al volver, no antes de verse.
                        marcarLeido();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        conversacion.setValue(UiState.error(mensaje));
                    }
                });
    }

    public void responder(@NonNull String texto) {
        if (bookingId == null) {
            return;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            return;
        }
        envio.setValue(UiState.loading());
        ServiceLocator.chats().responder(bookingId, limpio,
                new ResultCallback<Conversation>() {
                    @Override
                    public void onExito(@NonNull Conversation datos) {
                        envio.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        envio.setValue(UiState.error(mensaje));
                    }
                });
    }

    private void marcarLeido() {
        if (bookingId == null) {
            return;
        }
        ServiceLocator.chats().marcarLeido(bookingId, Message.Autor.HOTEL,
                new ResultCallback<Conversation>() {
                    @Override
                    public void onExito(@NonNull Conversation datos) {
                        // Nada que pintar: los mensajes del cliente ya se
                        // vieron. Si falla, tampoco: no vale la pena molestar
                        // por esto.
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                    }
                });
    }

    public void limpiarEnvio() {
        envio.setValue(null);
    }
}
