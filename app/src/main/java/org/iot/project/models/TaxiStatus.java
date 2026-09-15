package org.iot.project.models;

/**
 * Estados del servicio de taxi (RF-106). El orden de declaracion es el orden
 * del flujo, y la distancia entre ordinales es lo que permite validar las
 * transiciones (RF-111, RC-016) sin una tabla de adyacencia aparte.
 */
public enum TaxiStatus {

    SOLICITADO("Solicitado"),
    ASIGNADO("Asignado"),
    EN_CAMINO("En camino"),
    EN_TRASLADO("En traslado"),
    FINALIZADO("Finalizado");

    private final String displayName;

    TaxiStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Un estado solo puede avanzar al inmediatamente siguiente: el flujo es
     * SOLICITADO -> ASIGNADO -> EN CAMINO -> EN TRASLADO -> FINALIZADO.
     * Cualquier otro salto se rechaza.
     */
    public boolean canTransitionTo(TaxiStatus next) {
        return next != null && next.ordinal() == this.ordinal() + 1;
    }

    public TaxiStatus next() {
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < values().length ? values()[nextOrdinal] : this;
    }

    public boolean isFinished() {
        return this == FINALIZADO;
    }

    /** El servicio sigue vivo: el conductor debe seguir reportando ubicacion (RC-025). */
    public boolean isActive() {
        return this != FINALIZADO;
    }

    /**
     * El QR del cliente solo aparece cuando el conductor ya llego al punto de
     * recogida, es decir a partir de EN CAMINO (RF-101).
     */
    public boolean allowsQrDisplay() {
        return this == EN_CAMINO || this == EN_TRASLADO;
    }
}
