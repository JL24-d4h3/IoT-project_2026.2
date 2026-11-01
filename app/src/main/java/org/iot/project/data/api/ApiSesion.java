package org.iot.project.data.api;

import androidx.annotation.Nullable;

/**
 * Token JWT del taxista que inicio sesion contra la API.
 *
 * <p>Vive solo en memoria: no se escribe en disco, asi que no hay nada que
 * robar de un telefono. A cambio, si el sistema mata el proceso el taxista
 * vuelve a iniciar sesion. El token expira solo (la API lo emite por 720 min).
 */
public final class ApiSesion {

    private static volatile String token;
    private static volatile String taxistaId;
    private static volatile long expiraEnMs;

    private ApiSesion() {
    }

    public static void abrir(String tokenNuevo, String idTaxista, long minutosDeVida) {
        taxistaId = idTaxista;
        expiraEnMs = System.currentTimeMillis() + minutosDeVida * 60_000L;
        token = tokenNuevo;
    }

    public static void cerrar() {
        token = null;
        taxistaId = null;
        expiraEnMs = 0L;
    }

    /** El token vigente, o {@code null} si no hay sesion o ya expiro. */
    @Nullable
    public static String token() {
        String actual = token;
        if (actual == null || System.currentTimeMillis() >= expiraEnMs) {
            return null;
        }
        return actual;
    }

    /** Id del taxista dueno del token, o {@code null} sin sesion vigente. */
    @Nullable
    public static String taxistaId() {
        return token() == null ? null : taxistaId;
    }

    public static boolean activa() {
        return token() != null;
    }
}
