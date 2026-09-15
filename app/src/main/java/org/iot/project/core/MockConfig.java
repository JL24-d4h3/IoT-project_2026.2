package org.iot.project.core;

/**
 * Interruptores de demostracion.
 *
 * <p>Los estados de UI de §50 tienen que ser <em>demostrables</em>, no
 * decorativos: si solo existiera el camino feliz, no habria forma de ensenar
 * que la aplicacion resuelve bien el vacio y el error. Estos interruptores
 * permiten forzarlos sin tocar los datos.
 *
 * <p>Vive fuera de Activities, Fragments y adaptadores (reglas 33-35): la capa
 * de datos lo consulta, la interfaz solo lo cambia.
 */
public final class MockConfig {

    /** Como deben responder los repositorios simulados. */
    public enum Modo {
        /** Comportamiento normal. */
        NORMAL,
        /** Toda consulta devuelve vacio, para ensenar el estado vacio. */
        VACIO,
        /** Toda consulta falla, para ensenar el estado de error y el reintento. */
        ERROR,
        /** Toda consulta falla con un mensaje de red, para ensenar el modo sin conexion. */
        SIN_CONEXION
    }

    /** Latencia simulada. Un backend real nunca responde al instante. */
    private static final long LATENCIA_POR_DEFECTO_MS = 400L;

    private static Modo modo = Modo.NORMAL;
    private static long latenciaMs = LATENCIA_POR_DEFECTO_MS;

    private MockConfig() {
    }

    public static Modo getModo() {
        return modo;
    }

    public static void setModo(Modo nuevoModo) {
        modo = (nuevoModo == null) ? Modo.NORMAL : nuevoModo;
    }

    public static long getLatenciaMs() {
        return latenciaMs;
    }

    public static void setLatenciaMs(long ms) {
        latenciaMs = Math.max(0L, ms);
    }

    /** Mensaje de error acorde al modo activo, ya redactado para el usuario. */
    public static String mensajeDeError() {
        return modo == Modo.SIN_CONEXION
                ? "Parece que no tienes conexión. Revisa tu red e inténtalo de nuevo."
                : "No pudimos cargar la información. Inténtalo de nuevo en un momento.";
    }
}
