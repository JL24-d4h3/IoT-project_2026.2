package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;

import java.util.List;

/**
 * Acceso al chat cliente-hotel (RF-062 a RF-068).
 *
 * <p>La conversacion se identifica por la reserva y no por si misma: el usuario
 * entra al chat desde una reserva concreta y no desde una bandeja de entrada.
 * El administrador es la excepcion —atiende varias reservas a la vez y necesita
 * una lista— y por eso tiene {@link #conversacionesDeHotel(String,
 * ResultCallback)}.
 *
 * <p>Las dos partes de una conversacion usan el mismo repositorio, pero no los
 * mismos metodos: {@link #enviar} escribe como cliente y {@link #responder}
 * como hotel. No se unifican en un "enviar como quien sea" porque el lado no es
 * una preferencia de la pantalla, es quien esta en sesion (RF-004).
 */
public interface ChatRepository {

    /**
     * Devuelve la conversacion de una reserva, vista desde el cliente.
     *
     * <p>Falla si la reserva no admite chat, que es el caso de una estadia ya
     * cerrada (RF-065). Si la reserva si lo admite pero todavia no hay mensajes,
     * devuelve una conversacion vacia: la pantalla tiene que poder abrirse para
     * escribir el primero.
     */
    void conversacionDe(@NonNull String bookingId, @NonNull ResultCallback<Conversation> callback);

    /**
     * La misma conversacion, vista desde el hotel (RF-064).
     *
     * <p>No es un capricho que sean dos metodos: el hotel necesita saber quien
     * escribe y de que estadia se trata, y eso no esta en la conversacion, que
     * solo guarda identificadores. El cliente, en cambio, ya sabe quien es y en
     * que hotel esta —lo tiene en pantalla— y no gana nada con el cruce.
     *
     * <p>Es el mismo criterio que separa {@link #enviar} de {@link #responder}:
     * el lado no es una preferencia de la pantalla, es quien esta en sesion.
     */
    void conversacionDeHotel(@NonNull String bookingId,
                             @NonNull ResultCallback<ConversacionDeHotel> callback);

    /** Envia un mensaje del cliente (RF-066). El texto no puede ir vacio. */
    void enviar(@NonNull String bookingId, @NonNull String texto,
                @NonNull ResultCallback<Conversation> callback);

    /** Responde un mensaje como hotel (RF-064). El texto no puede ir vacio. */
    void responder(@NonNull String bookingId, @NonNull String texto,
                   @NonNull ResultCallback<Conversation> callback);

    /**
     * Bandeja del hotel: conversaciones de sus reservas con chat abierto
     * (RF-064, RF-065), la mas reciente primero.
     *
     * <p>Solo devuelve las que admiten chat. Una reserva ya cerrada no
     * desaparece del historial del cliente, pero tampoco tiene sentido en una
     * bandeja de mensajes que se pueden responder.
     */
    void conversacionesDeHotel(@NonNull String hotelId,
                               @NonNull ResultCallback<List<ConversacionDeHotel>> callback);

    /**
     * Marca como leidos los mensajes del otro lado (RF-068).
     *
     * <p>El lector es un parametro y no algo que el repositorio deduzca: el
     * mismo metodo sirve a las dos pantallas, y quien lo llama sabe de que lado
     * esta.
     */
    void marcarLeido(@NonNull String bookingId, @NonNull Message.Autor lector,
                     @NonNull ResultCallback<Conversation> callback);
}
