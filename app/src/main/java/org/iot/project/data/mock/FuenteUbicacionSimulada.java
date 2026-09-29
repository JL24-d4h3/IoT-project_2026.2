package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.FuenteUbicacion;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.Ubicacion;

/**
 * Ubicacion simulada del conductor (RF-098).
 *
 * <p>Sin viaje en curso devuelve su base. Con un viaje en curso lo acerca al
 * punto de recojo, que es lo que hace creible el seguimiento del cliente
 * (RF-099): un conductor que se aleja del punto de recojo no es un conductor
 * que viene a recogerte.
 */
public class FuenteUbicacionSimulada implements FuenteUbicacion {

    /**
     * Donde aparece el conductor la primera vez, medido desde el punto de
     * recojo. Algo menos de 1,2 km: lo bastante lejos para que el seguimiento
     * tenga recorrido que ensenar, y lo bastante cerca para entrar entero en el
     * plano sin salirse por el borde.
     */
    private static final double DESPLAZE_INICIAL_LAT = 0.009;
    private static final double DESPLAZE_INICIAL_LNG = 0.006;

    /** Cuanto de la distancia restante se recorta en cada reporte. */
    private static final double FRACCION_ACERCAMIENTO = 0.4;

    @NonNull
    @Override
    public Ubicacion posicion(@NonNull Driver conductor, @Nullable TaxiService enCurso) {
        if (enCurso == null || !enCurso.hasRecojo()) {
            Ubicacion base = MockData.baseDe(conductor.getId());
            return base != null ? base : new Ubicacion(0d, 0d);
        }

        double recojoLat = enCurso.getLatRecojo();
        double recojoLng = enCurso.getLngRecojo();

        if (enCurso.getUltimaActualizacionUbicacion() == null) {
            return new Ubicacion(recojoLat + DESPLAZE_INICIAL_LAT,
                    recojoLng + DESPLAZE_INICIAL_LNG);
        }

        double lat = enCurso.getLatConductor();
        double lng = enCurso.getLngConductor();
        return new Ubicacion(lat + (recojoLat - lat) * FRACCION_ACERCAMIENTO,
                lng + (recojoLng - lng) * FRACCION_ACERCAMIENTO);
    }
}
