package org.iot.project.models;

/**
 * Estados de una reserva. Se usan tanto en las cards del cliente (proximas,
 * activas, historial) como en el panel del administrador de hotel.
 */
public enum BookingStatus {

    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    ACTIVA("Activa"),
    FINALIZADA("Finalizada"),
    CANCELADA("Cancelada");

    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Solo una reserva activa habilita el chat con el hotel (RF-063, RF-065)
     * y el checkout (RF-045).
     */
    public boolean allowsChat() {
        return this == ACTIVA;
    }

    public boolean allowsCheckout() {
        return this == ACTIVA;
    }

    public boolean allowsCancellation() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
