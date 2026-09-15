package org.iot.project.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Una conversacion de la bandeja del hotel, con la estadia a la que pertenece.
 *
 * <p>Es el cruce de una {@link Conversation} con su reserva. La conversacion
 * sabe de quien es por identificador —{@code clienteId}, {@code bookingId}— y
 * una bandeja de entrada tiene que decir quien escribe y de que estadia habla:
 * con identificadores, todas las filas se leerian igual. El cruce lo hace el
 * repositorio, que es quien tiene los dos conjuntos a mano; hacerlo en la
 * pantalla obligaria al adaptador a conocer el almacen de usuarios (reglas
 * 33-35).
 *
 * <p>La reserva no se guarda suelta sino como {@link ReservaDeHotel}, el mismo
 * cruce que ya usan las otras pantallas del administrador: asi la fila de una
 * conversacion y la de una reserva describen la misma estadia con las mismas
 * palabras, y la habitacion de la que se esta hablando es la misma en las dos.
 *
 * <p>La conversacion se guarda entera porque el chat la necesita viva para
 * enseñar y anadir mensajes, pero lo que la <em>fila</em> enseña —cuantos
 * mensajes hay y cuantos sin leer— se copia al cruzar. Una fila de una lista es
 * un valor, y hay que poder comparar la de antes con la de despues para saber si
 * cambio: si las dos leyeran del mismo objeto vivo, se compararia consigo mismo,
 * saldria siempre igual, y el contador de sin leer se quedaria encendido despues
 * de abrir el chat.
 */
public final class ConversacionDeHotel {

    @NonNull
    private final Conversation conversacion;

    @NonNull
    private final ReservaDeHotel estadia;

    /** Mensajes del cliente sin abrir en el momento del cruce. */
    private final int sinLeer;

    /** Cuantos mensajes habia en el momento del cruce. */
    private final int numMensajes;

    /** Ultimo mensaje en el momento del cruce, o {@code null} si no habia. */
    @Nullable
    private final Message ultimoMensaje;

    public ConversacionDeHotel(@NonNull Conversation conversacion,
                               @NonNull ReservaDeHotel estadia) {
        this.conversacion = conversacion;
        this.estadia = estadia;
        this.sinLeer = conversacion.getNumNoLeidosPara(Message.Autor.HOTEL);
        this.numMensajes = conversacion.getMensajes().size();
        this.ultimoMensaje = conversacion.getUltimoMensaje();
    }

    @NonNull
    public Conversation getConversacion() {
        return conversacion;
    }

    @NonNull
    public ReservaDeHotel getEstadia() {
        return estadia;
    }

    /**
     * Cuantos mensajes del cliente seguian sin abrir al cruzar la bandeja
     * (RF-068).
     *
     * <p>El lado no es un parametro porque esta clase ya es de un solo lado: es
     * la conversacion <em>del hotel</em>. Al hotel le cuentan los mensajes del
     * cliente, y los suyos propios no, porque uno ya sabe lo que escribio.
     *
     * <p>Es el valor del momento del cruce y no el de ahora: es lo que la
     * bandeja contesto cuando se le pidio. Volver a preguntarselo al objeto vivo
     * dejaria la fila sin forma de saber que ha cambiado.
     */
    public int getSinLeer() {
        return sinLeer;
    }

    /** Cuantos mensajes habia al cruzar la bandeja. */
    public int getNumMensajes() {
        return numMensajes;
    }

    /**
     * Ultimo mensaje al cruzar la bandeja, o {@code null} si no habia ninguno.
     *
     * <p>Se adelanta aqui para que la bandeja no tenga que encadenar
     * {@code getConversacion().getUltimoMensaje()} solo para pintar la previa.
     */
    @Nullable
    public Message getUltimoMensaje() {
        return ultimoMensaje;
    }

    /** Identificador de la reserva, que es lo que identifica la conversacion. */
    @NonNull
    public String getBookingId() {
        return conversacion.getBookingId();
    }
}
