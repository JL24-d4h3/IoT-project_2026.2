package org.iot.project.utils;

import java.util.Locale;
import java.util.regex.Pattern;

/** Token de cierre del servicio web; el escaneo nunca interpreta URLs ni documentos JSON. */
public final class CodigoQrTaxi {
    private static final Pattern FORMATO = Pattern.compile("^QR-[0-9A-F]{12}$");
    private static final String INVALIDO = "Este código QR no corresponde a un servicio de taxi. "
            + "Escanea el código que muestra el cliente o escríbelo manualmente.";

    private CodigoQrTaxi() { }

    /** La autorización del taxista, el estado y la coincidencia del token los valida el backend. */
    public static String normalizarEscaneado(String contenido) {
        if (contenido == null) throw new IllegalArgumentException(INVALIDO);
        // Un lector debe devolver el token completo. No se admiten controles ocultos
        // ni caracteres Unicode que al pasar a mayúsculas puedan convertirse en letras ASCII.
        for (int indice = 0; indice < contenido.length(); indice++) {
            char caracter = contenido.charAt(indice);
            if (caracter < 0x20 || caracter > 0x7e) throw new IllegalArgumentException(INVALIDO);
        }
        String codigo = contenido.trim().toUpperCase(Locale.ROOT);
        if (!FORMATO.matcher(codigo).matches()) throw new IllegalArgumentException(INVALIDO);
        return codigo;
    }
}
