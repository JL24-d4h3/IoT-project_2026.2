package org.iot.project.models;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;

/**
 * Mensaje del chat cliente-hotel (RF-066). Cada mensaje conserva su fecha y
 * hora (RF-067, RT-020).
 *
 * <p>El autor se guarda como parte del mensaje y <b>no</b> como un indicador de
 * "es mio". Un mensaje no es de nadie en concreto: es del cliente o del hotel,
 * y quien lo mire decide de que lado pintarlo. Guardar "es mio" obligaba a que
 * el dato significara una cosa para el cliente y la contraria para el
 * administrador, que es la clase de campo que acaba invertido en una de las dos
 * pantallas.
 */
public class Message {

    /** Quien escribio el mensaje. */
    public enum Autor {

        CLIENTE("Cliente"),
        HOTEL("Hotel");

        private final String displayName;

        Autor(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String id;
    private final String conversacionId;
    private final String texto;
    private final LocalDateTime timestamp;
    private final Autor autor;
    private boolean leido;

    public Message(String id,
                   String conversacionId,
                   String texto,
                   LocalDateTime timestamp,
                   @NonNull Autor autor) {
        this.id = id;
        this.conversacionId = conversacionId;
        this.texto = texto;
        this.timestamp = timestamp;
        this.autor = autor;
    }

    public String getId() {
        return id;
    }

    public String getConversacionId() {
        return conversacionId;
    }

    public String getTexto() {
        return texto;
    }

    /** Marca temporal que se muestra bajo cada burbuja. */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @NonNull
    public Autor getAutor() {
        return autor;
    }

    /**
     * Si el mensaje lo escribio el lado indicado.
     *
     * <p>Es lo que consultan las pantallas: cada una pregunta por el lado que
     * representa, en vez de leer un campo que ya viene decidido.
     */
    public boolean esDe(@NonNull Autor quien) {
        return autor == quien;
    }

    /** Si lo leyo quien lo recibio, que es el lado contrario al autor. */
    public boolean isLeido() {
        return leido;
    }

    public void setLeido(boolean leido) {
        this.leido = leido;
    }
}
