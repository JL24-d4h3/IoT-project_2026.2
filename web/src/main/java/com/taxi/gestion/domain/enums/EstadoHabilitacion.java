package com.taxi.gestion.domain.enums;

/**
 * Habilitacion otorgada por el Superadmin tras revisar la documentacion.
 * Es independiente de {@code cuentaActiva}: un taxista HABILITADO puede
 * tener la cuenta desactivada temporalmente.
 */
public enum EstadoHabilitacion {
    PENDIENTE("Pendiente de revision"),
    HABILITADO("Habilitado");

    private final String etiqueta;

    EstadoHabilitacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
