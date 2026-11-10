package com.taxi.gestion.domain.enums;

/**
 * Disponibilidad del taxista para recibir nuevos servicios.
 * NO representa el estado del viaje: eso lo maneja {@link EstadoServicio}.
 */
public enum Disponibilidad {
    DISPONIBLE("Disponible"),
    OCUPADO("Ocupado");

    private final String etiqueta;

    Disponibilidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
