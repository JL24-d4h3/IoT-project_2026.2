package org.iot.project.models;

/**
 * Un punto sobre la Tierra.
 *
 * <p>Existe para que las coordenadas del conductor viajen juntas. Sueltas son
 * dos doubles que se pueden intercambiar sin que el compilador diga nada, y
 * latitud y longitud intercambiadas no dan un error: dan un punto en el golfo
 * de Guinea.
 */
public final class Ubicacion {

    private final double latitud;
    private final double longitud;

    public Ubicacion(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }
}
