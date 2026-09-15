package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Card;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;
import org.iot.project.models.User;

import java.util.List;

/** Acceso a perfil, tarjetas, notificaciones y conversaciones. */
public interface UserRepository {

    void usuario(@NonNull String usuarioId, @NonNull ResultCallback<User> callback);

    void actualizar(@NonNull User datos, @NonNull ResultCallback<User> callback);

    /** Alta o edicion de tarjeta. Nunca se guarda el numero completo ni el CVV (RC-011). */
    void guardarTarjeta(@NonNull String usuarioId, @NonNull Card tarjeta,
                        @NonNull ResultCallback<User> callback);

    void eliminarTarjeta(@NonNull String usuarioId, @NonNull String tarjetaId,
                         @NonNull ResultCallback<User> callback);

    void notificaciones(@NonNull String usuarioId,
                        @NonNull ResultCallback<List<AppNotification>> callback);

    void marcarNotificacionesLeidas(@NonNull String usuarioId);

    /**
     * Conversacion de una reserva. RF-065: el chat solo existe con reserva
     * activa, asi que falla si la reserva no lo esta.
     */
    void conversacion(@NonNull String bookingId, @NonNull ResultCallback<Conversation> callback);

    void enviarMensaje(@NonNull String bookingId, @NonNull String texto,
                       @NonNull ResultCallback<Message> callback);
}
