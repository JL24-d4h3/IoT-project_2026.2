package org.iot.project.data.api;

/**
 * Configuracion de la REST API del sistema de gestion de taxistas (RT-006, RT-023).
 *
 * <p>Es el unico sitio que sabe donde vive la API: cuando se despliegue en la
 * nube, solo cambia {@link #BASE_URL}.
 */
public final class ApiConfig {

    /**
     * Direccion base de la API.
     *
     * <ul>
     *   <li>Emulador de Android Studio: {@code http://10.0.2.2:8080} (10.0.2.2 es tu PC).</li>
     *   <li>Celular real en la misma WiFi: {@code http://IP-DE-TU-PC:8080}.</li>
     *   <li>Nube: la URL {@code https://...} del despliegue.</li>
     * </ul>
     * El HTTP sin cifrar solo esta permitido en la variante debug
     * ({@code src/debug/AndroidManifest.xml}); una release exige HTTPS.
     */
    public static final String BASE_URL = "http://10.0.2.2:8080";

    public static final int TIMEOUT_CONEXION_MS = 6_000;
    public static final int TIMEOUT_LECTURA_MS = 10_000;

    /**
     * TEMPORAL: la API valora de 1 a 5 y la app de 1 a 10. Este factor convierte
     * la nota de la API a la escala de la app en un unico punto. Cuando la API
     * pase a 1-10, ponerlo en {@code 1f}.
     */
    public static final float FACTOR_VALORACION = 2f;

    private ApiConfig() {
    }
}
