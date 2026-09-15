package org.iot.project.models;

import java.time.LocalDateTime;

/** Notificacion del centro de notificaciones (§39, §41). */
public class AppNotification {

    public enum Tipo {
        RESERVA,
        PAGO,
        CHECKOUT,
        CARGO_ADICIONAL,
        TAXI,
        MENSAJE
    }

    private final String id;
    private final Tipo tipo;
    private final String titulo;
    private final String mensaje;
    private final LocalDateTime timestamp;
    private boolean leida;

    public AppNotification(String id,
                           Tipo tipo,
                           String titulo,
                           String mensaje,
                           LocalDateTime timestamp) {
        this.id = id;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }
}
