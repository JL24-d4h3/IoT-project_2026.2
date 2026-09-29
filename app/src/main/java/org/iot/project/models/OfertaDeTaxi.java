package org.iot.project.models;

import androidx.annotation.NonNull;

/**
 * Una solicitud de traslado vista por un conductor: el servicio y lo que le
 * falta para llegar al punto de recojo (RF-088, RF-089).
 *
 * <p>La distancia no es una propiedad del servicio —el mismo traslado esta a
 * distinta distancia de cada conductor—, asi que no puede vivir en
 * {@link TaxiService}. Va aqui, que es el par que la pantalla necesita.
 *
 * <p>La calcula el repositorio y no la pantalla porque depende de donde esta el
 * conductor, y quien sabe eso es {@code FuenteUbicacion}.
 */
public final class OfertaDeTaxi {

    private final TaxiService servicio;
    private final double distanciaM;

    public OfertaDeTaxi(@NonNull TaxiService servicio, double distanciaM) {
        this.servicio = servicio;
        this.distanciaM = distanciaM;
    }

    @NonNull
    public TaxiService getServicio() {
        return servicio;
    }

    /** Metros del conductor al punto de recojo. */
    public double getDistanciaM() {
        return distanciaM;
    }
}
