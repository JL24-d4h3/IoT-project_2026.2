package org.iot.project.ui.client.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;

/**
 * Estado del chat cliente-hotel (RF-062 a RF-068).
 *
 * <p>El envio y la carga comparten un unico estado porque son la misma lista
 * vista desde dos momentos: al enviar, la respuesta ya trae la conversacion
 * entera y no hace falta pedirla otra vez.
 */
public class ChatViewModel extends ViewModel {

    private final MutableLiveData<UiState<Conversation>> conversacion = new MutableLiveData<>();
    private final MutableLiveData<UiState<Conversation>> envio = new MutableLiveData<>();

    private String bookingId;

    public LiveData<UiState<Conversation>> getConversacion() {
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
        ServiceLocator.chats().conversacionDe(bookingId, new ResultCallback<Conversation>() {
            @Override
            public void onExito(@NonNull Conversation datos) {
                conversacion.setValue(UiState.success(datos));
                // RF-068: abrir la conversacion es lo que marca los mensajes del
                // hotel como leidos. Se hace despues de publicar la lista para
                // que el estado de "sin leer" llegue a verse.
                marcarLeido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                conversacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void enviar(@NonNull String texto) {
        if (bookingId == null) {
            return;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            return;
        }
        envio.setValue(UiState.loading());
        ServiceLocator.chats().enviar(bookingId, limpio, new ResultCallback<Conversation>() {
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
        ServiceLocator.chats().marcarLeido(bookingId, Message.Autor.CLIENTE,
                new ResultCallback<Conversation>() {
            @Override
            public void onExito(@NonNull Conversation datos) {
                // Nada que pintar: los mensajes del hotel ya se vieron. Si
                // falla, tampoco: no vale la pena molestar por esto.
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
