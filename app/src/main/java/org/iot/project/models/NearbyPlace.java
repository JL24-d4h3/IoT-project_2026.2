package org.iot.project.models;

/** Lugar historico o de interes cercano al hotel (RF-011). */
public class NearbyPlace {

    private final String nombre;
    private final String tipo;
    private final double distanciaKm;

    public NearbyPlace(String nombre, String tipo, double distanciaKm) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.distanciaKm = distanciaKm;
    }

    public String getNombre() {
        return nombre;
    }

    /** Categoria legible: "Centro historico", "Museo", "Parque", "Playa"... */
    public String getTipo() {
        return tipo;
    }

    public double getDistanciaKm() {
        return distanciaKm;
    }
}
