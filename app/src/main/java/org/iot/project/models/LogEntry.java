package org.iot.project.models;

import java.time.LocalDateTime;

/**
 * Evento de auditoria (RF-118, RF-119, RF-120).
 *
 * <p>Nunca debe contener contrasenas ni secretos de autenticacion
 * (RC-042, RT-038), por eso {@link #detalle} se documenta como texto ya
 * saneado.
 */
public class LogEntry {

    public enum Evento {
        INICIO_SESION("Inicio de sesion"),
        REGISTRO("Registro"),
        ACTIVACION("Activacion de cuenta"),
        DESACTIVACION("Desactivacion de cuenta"),
        RESERVA("Reserva"),
        CANCELACION("Cancelacion"),
        PAGO("Pago"),
        CARGO_ADICIONAL("Cargo adicional"),
        CHECKOUT("Checkout"),
        CAMBIO_ESTADO_TAXI("Cambio de estado de taxi"),
        APROBACION("Aprobacion"),
        ACCION_ADMINISTRATIVA("Accion administrativa");

        private final String displayName;

        Evento(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final LocalDateTime timestamp;
    private final String usuario;
    private final Evento evento;
    private final String detalle;

    public LogEntry(LocalDateTime timestamp, String usuario, Evento evento, String detalle) {
        this.timestamp = timestamp;
        this.usuario = usuario;
        this.evento = evento;
        this.detalle = detalle;
    }

    /** RC-040: todo evento conserva al menos una marca temporal. */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /** RC-041: identifica al usuario que produjo la operacion. */
    public String getUsuario() {
        return usuario;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getDetalle() {
        return detalle;
    }
}
