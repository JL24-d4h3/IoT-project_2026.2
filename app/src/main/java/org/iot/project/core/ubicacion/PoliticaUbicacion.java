package org.iot.project.core.ubicacion;

/** Criterios compartidos para transmitir GPS y ofrecer viajes con una posición vigente. */
public final class PoliticaUbicacion {
    public static final long INTERVALO_ENVIO_MS = 10_000L;
    public static final long MAX_ANTIGUEDAD_MS = 120_000L;
    public static final float MAX_PRECISION_METROS = 100f;

    private PoliticaUbicacion() { }

    /**
     * La antigüedad procede del reloj monotónico del dispositivo. Una precisión
     * ausente, imposible o superior a 100 m no confirma un punto de recogida.
     */
    public static boolean posicionValida(double latitud, double longitud,
                                          float precision, long antiguedadMs) {
        return Double.isFinite(latitud) && Double.isFinite(longitud)
                && latitud >= -90d && latitud <= 90d
                && longitud >= -180d && longitud <= 180d
                && Float.isFinite(precision) && precision > 0f
                && precision <= MAX_PRECISION_METROS && vigente(antiguedadMs);
    }

    /**
     * Haber enviado una posición antes no mantiene las ofertas habilitadas
     * después de revocar el permiso, apagar GPS o dejar caducar ese reporte.
     */
    public static boolean puedeMostrarOfertas(boolean permisoPreciso, boolean gpsActivo,
                                              boolean posicionEnviada, long antiguedadMs) {
        return permisoPreciso && gpsActivo && posicionEnviada && vigente(antiguedadMs);
    }

    private static boolean vigente(long antiguedadMs) {
        return antiguedadMs >= 0L && antiguedadMs <= MAX_ANTIGUEDAD_MS;
    }
}
