package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conversacion privada entre un cliente y un hotel (RF-062, RF-068).
 *
 * <p>Solo existe mientras la reserva asociada este activa: al hacer checkout
 * el acceso al chat queda cerrado (RF-065).
 */
public class Conversation {

    private final String id;
    private final String bookingId;
    private final String clienteId;
    private final String hotelId;
    private final List<Message> mensajes = new ArrayList<>();
    private int totalResumen = -1;
    private int sinLeerCliente = -1;
    private int sinLeerHotel = -1;
    private Message ultimoResumen;

    public Conversation(String id, String bookingId, String clienteId, String hotelId) {
        this.id = id;
        this.bookingId = bookingId;
        this.clienteId = clienteId;
        this.hotelId = hotelId;
    }

    public String getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getHotelId() {
        return hotelId;
    }

    public List<Message> getMensajes() {
        return Collections.unmodifiableList(mensajes);
    }

    public void addMensaje(Message mensaje) {
        mensajes.add(mensaje);
    }

    /** La bandeja usa metadatos del servidor sin descargar cada historial. */
    public Conversation withResumen(int total, int cliente, int hotel, Message ultimo) {
        totalResumen = Math.max(0, total);
        sinLeerCliente = Math.max(0, cliente);
        sinLeerHotel = Math.max(0, hotel);
        ultimoResumen = ultimo;
        return this;
    }

    public int getTotalMensajes() {
        return totalResumen >= 0 ? totalResumen : mensajes.size();
    }

    public boolean isVacia() {
        return getTotalMensajes() == 0;
    }

    /** Ultimo mensaje de la lista, o null si aun no hay conversacion. */
    public Message getUltimoMensaje() {
        return mensajes.isEmpty() ? ultimoResumen : mensajes.get(mensajes.size() - 1);
    }

    /**
     * Cuantos mensajes del <em>otro</em> lado siguen sin leer.
     *
     * <p>El conteo depende de quien mire: los mensajes que el hotel no ha leido
     * son los del cliente, y al reves. Por eso el lector es un parametro y no
     * un campo de la conversacion: una conversacion no tiene un numero de no
     * leidos, tiene uno por cada lado.
     *
     * <p>Los mensajes propios del lector no cuentan nunca: uno ya sabe lo que
     * escribio.
     */
    public int getNumNoLeidosPara(@NonNull Message.Autor lector) {
        int resumen = lector == Message.Autor.CLIENTE ? sinLeerCliente : sinLeerHotel;
        if (resumen >= 0) return resumen;
        int total = 0;
        for (Message mensaje : mensajes) {
            if (!mensaje.esDe(lector) && !mensaje.isLeido()) {
                total++;
            }
        }
        return total;
    }

    /**
     * Marca como leidos los mensajes del otro lado (RF-068).
     *
     * <p>Los propios se dejan como estan: quien escribe un mensaje no lo "lee",
     * y darlo por leido borraria el hecho de que el destinatario todavia no lo
     * ha visto.
     */
    public void marcarLeidoPara(@NonNull Message.Autor lector) {
        if (lector == Message.Autor.CLIENTE && sinLeerCliente >= 0) sinLeerCliente = 0;
        if (lector == Message.Autor.HOTEL && sinLeerHotel >= 0) sinLeerHotel = 0;
        for (Message mensaje : mensajes) {
            if (!mensaje.esDe(lector)) {
                mensaje.setLeido(true);
            }
        }
    }

    /**
     * RF-065: el chat solo esta disponible con reserva activa. Se consulta
     * contra la reserva asociada en vez de guardar un flag propio, para que no
     * puedan desincronizarse.
     *
     * <p>Se compara contra el id de la reserva, que es lo que guarda
     * {@code bookingId}; el codigo corto (EST-2026-0418) es otra cosa.
     */
    public boolean estaActiva(Booking reserva) {
        return reserva != null && reserva.getId().equals(bookingId) && reserva.permiteChat();
    }
}
