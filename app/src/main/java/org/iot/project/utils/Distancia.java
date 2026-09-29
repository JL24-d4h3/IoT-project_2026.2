package org.iot.project.utils;

import java.util.Locale;

/**
 * Distancias sobre la Tierra (RF-089).
 *
 * <p>Es una funcion pura y sin Android a proposito: la usan el filtro de
 * cercania del conductor, la tarjeta de solicitud y el plano de seguimiento, y
 * una formula mal copiada en tres sitios da tres respuestas distintas para el
 * mismo par de puntos.
 *
 * <p>Se usa el semiverseno y no la distancia euclidea sobre grados: los grados
 * de longitud miden distinto segun la latitud —a la altura de Lima un grado de
 * longitud es un 2,4% mas corto que uno de latitud—, y con distancias de
 * cientos de kilometros ese error deja de ser despreciable.
 */
public final class Distancia {

    /** Radio medio terrestre, en metros. */
    private static final double RADIO_TIERRA_M = 6371000d;

    private Distancia() {
    }

    /** Distancia en metros entre dos coordenadas. */
    public static double metrosEntre(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RADIO_TIERRA_M * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /**
     * Metros o kilometros, segun lo que se lea mejor: "850 m", "1,9 km".
     *
     * <p>Por debajo del kilometro se redondea a decenas: decir "847 m" anuncia
     * una precision que el dato no tiene.
     */
    public static String legible(double metros) {
        if (metros < 1000d) {
            return Math.round(metros / 10d) * 10 + " m";
        }
        return String.format(Locale.getDefault(), "%.1f", metros / 1000d)
                .replace('.', ',') + " km";
    }
}
