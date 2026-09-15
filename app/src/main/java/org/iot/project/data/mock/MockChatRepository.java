package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementacion simulada del chat (RF-062 a RF-068).
 *
 * <p>La regla que se aplica aqui es la de RF-065: el chat vive y muere con la
 * reserva. Se consulta contra el estado de la reserva en cada operacion en vez
 * de guardar un indicador propio en la conversacion, para que no puedan
 * desincronizarse.
 *
 * <p>No hay respuestas automaticas. El historial sembrado tiene mensajes en
 * ambos sentidos y el administrador puede responder desde su bandeja, pero el
 * sistema no contesta solo: inventar una respuesta seria simular una persona
 * que no existe.
 *
 * <p>Las dos partes escriben por el mismo camino —{@link #enviar} y
 * {@link #responder} acaban en el mismo sitio— y lo unico que cambia es el
 * {@link Message.Autor} que queda grabado, que es lo que despues decide de que
 * lado se pinta la burbuja en cada pantalla.
 */
public class MockChatRepository extends MockRepository implements ChatRepository {

    private int correlativo = 100;

    @Override
    public void conversacionDe(@NonNull String bookingId,
                               @NonNull ResultCallback<Conversation> callback) {
        entregarDato(callback, () -> {
            exigirChatAbierto(bookingId);
            return conversacionDeReserva(bookingId);
        }, "No pudimos abrir esta conversación.");
    }

    @Override
    public void conversacionDeHotel(@NonNull String bookingId,
                                    @NonNull ResultCallback<ConversacionDeHotel> callback) {
        entregarDato(callback, () -> {
            exigirChatAbierto(bookingId);
            // El cruce se hace despues de comprobar el chat: asi la reserva que
            // se pasa a detallar no puede ser nula, y no hace falta un caso
            // aparte para "conversacion sin estadia", que no existe.
            return new ConversacionDeHotel(conversacionDeReserva(bookingId),
                    MockData.detallar(MockData.reserva(bookingId)));
        }, "No pudimos abrir esta conversación.");
    }

    @Override
    public void enviar(@NonNull String bookingId, @NonNull String texto,
                       @NonNull ResultCallback<Conversation> callback) {
        escribir(bookingId, texto, Message.Autor.CLIENTE, callback);
    }

    @Override
    public void responder(@NonNull String bookingId, @NonNull String texto,
                          @NonNull ResultCallback<Conversation> callback) {
        ejecutar(callback, () -> {
            // RF-023 antes que nada: responder es escribir en nombre de un
            // hotel, y solo puede hacerlo quien lo administra.
            exigirHotelPropio(MockData.reserva(bookingId));
            return escribirAhora(bookingId, texto, Message.Autor.HOTEL);
        });
    }

    @Override
    public void conversacionesDeHotel(@NonNull String hotelId,
                                      @NonNull ResultCallback<List<ConversacionDeHotel>> callback) {
        entregarLista(callback, () -> {
            exigirPuedeGestionar(hotelId);
            List<ConversacionDeHotel> bandeja = new ArrayList<>();
            for (Conversation conversacion : MockData.CONVERSACIONES) {
                if (!hotelId.equals(conversacion.getHotelId())) {
                    continue;
                }
                // RF-065: una reserva cerrada sale de la bandeja. El historial
                // del cliente no se toca; lo que se cierra es la posibilidad de
                // responder.
                Booking reserva = MockData.reserva(conversacion.getBookingId());
                if (conversacion.estaActiva(reserva)) {
                    bandeja.add(new ConversacionDeHotel(conversacion,
                            MockData.detallar(reserva)));
                }
            }
            // La mas reciente primero: es la que se va a atender.
            Collections.sort(bandeja, (a, b) -> {
                Message ultimoA = a.getUltimoMensaje();
                Message ultimoB = b.getUltimoMensaje();
                if (ultimoA == null || ultimoB == null) {
                    return ultimoA == ultimoB ? 0 : (ultimoA == null ? 1 : -1);
                }
                return ultimoB.getTimestamp().compareTo(ultimoA.getTimestamp());
            });
            return bandeja;
        });
    }

    @Override
    public void marcarLeido(@NonNull String bookingId, @NonNull Message.Autor lector,
                            @NonNull ResultCallback<Conversation> callback) {
        ejecutar(callback, () -> {
            Conversation conversacion = conversacionDeReserva(bookingId);
            conversacion.marcarLeidoPara(lector);
            return conversacion;
        });
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    /** Envio del cliente: pasa por {@code ejecutar} para informar los fallos. */
    private void escribir(@NonNull String bookingId, @NonNull String texto,
                          @NonNull Message.Autor autor,
                          @NonNull ResultCallback<Conversation> callback) {
        ejecutar(callback, () -> escribirAhora(bookingId, texto, autor));
    }

    /**
     * Anade el mensaje a la conversacion.
     *
     * <p>El texto se recorta antes de comprobar si esta vacio: un mensaje de
     * solo espacios se veria como una burbuja en blanco en las dos pantallas.
     */
    private Conversation escribirAhora(@NonNull String bookingId, @NonNull String texto,
                                       @NonNull Message.Autor autor) {
        exigirChatAbierto(bookingId);
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Escribe un mensaje antes de enviarlo.");
        }
        Conversation conversacion = conversacionDeReserva(bookingId);
        conversacion.addMensaje(new Message(
                "M" + (++correlativo),
                conversacion.getId(),
                limpio,
                LocalDateTime.now(),
                autor));
        // Un mensaje recien escrito no cuenta como no leido para quien lo
        // escribio, asi que no hay nada que marcar aqui.
        return conversacion;
    }

    /** RF-023: responder en nombre de un hotel exige administrarlo. */
    private void exigirHotelPropio(@Nullable Booking reserva) {
        if (reserva == null) {
            throw new IllegalArgumentException("No encontramos esta reserva.");
        }
        exigirPuedeGestionar(reserva.getHotelId());
    }

    private void exigirPuedeGestionar(@NonNull String hotelId) {
        if (!SessionManager.puedeGestionar(hotelId)) {
            throw new IllegalStateException(
                    "Esta conversación pertenece a otro hotel.");
        }
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    /**
     * Comprueba que la reserva exista y siga admitiendo chat (RF-065).
     *
     * <p>Se comprueba en cada operacion y no solo al abrir: una reserva puede
     * cerrarse mientras la pantalla esta abierta.
     */
    private void exigirChatAbierto(@NonNull String bookingId) {
        Booking reserva = MockData.reserva(bookingId);
        if (reserva == null) {
            throw new IllegalArgumentException("No encontramos esta reserva.");
        }
        if (!reserva.permiteChat()) {
            throw new IllegalStateException(
                    "El chat se cierra al hacer checkout. Esta reserva está "
                            + reserva.getEstado().getDisplayName().toLowerCase() + ".");
        }
    }

    /**
     * Busca la conversacion de la reserva y, si no existe, la crea vacia.
     *
     * <p>Una reserva activa recien creada no tiene conversacion todavia; sin
     * esto, el cliente no tendria forma de escribir el primer mensaje.
     */
    private Conversation conversacionDeReserva(@NonNull String bookingId) {
        for (Conversation conversacion : MockData.CONVERSACIONES) {
            if (conversacion.getBookingId().equals(bookingId)) {
                return conversacion;
            }
        }
        Booking reserva = MockData.reserva(bookingId);
        Conversation nueva = new Conversation(
                "CV" + (MockData.CONVERSACIONES.size() + 1),
                bookingId,
                reserva.getClienteId(),
                reserva.getHotelId());
        MockData.CONVERSACIONES.add(nueva);
        return nueva;
    }
}
