package org.iot.project.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatea montos en soles peruanos: separador de miles con coma y decimales
 * con punto ("S/ 1,240.50"), que es la convencion que se ve en todo el mock
 * data y en los ejemplos de §66.
 *
 * <p>Se usa una configuracion explicita en vez del locale del dispositivo
 * porque el precio es informacion critica (§66) y no debe cambiar de formato
 * segun la configuracion del telefono.
 */
public final class PriceFormatter {

    public static final String SIMBOLO = "S/";

    /**
     * DecimalFormat no es thread-safe, y aqui se formatean precios desde
     * adaptadores y desde tareas de fondo.
     */
    private static final ThreadLocal<DecimalFormat> SIN_DECIMALES =
            ThreadLocal.withInitial(() -> crear("#,##0"));

    private static final ThreadLocal<DecimalFormat> CON_DECIMALES =
            ThreadLocal.withInitial(() -> crear("#,##0.00"));

    private PriceFormatter() {
    }

    private static DecimalFormat crear(String patron) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat formato = new DecimalFormat(patron, simbolos);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    }

    /** "S/ 1,240" — para precios de card y resumenes donde no importan los centavos. */
    public static String format(double monto) {
        return SIMBOLO + " " + SIN_DECIMALES.get().format(monto);
    }

    /** "S/ 1,240.50" — para desgloses de pago, donde el centavo si cuenta. */
    public static String formatConDecimales(double monto) {
        return SIMBOLO + " " + CON_DECIMALES.get().format(monto);
    }

    /** Solo el numero, sin simbolo: util cuando el simbolo ya esta en el layout. */
    public static String numero(double monto) {
        return SIN_DECIMALES.get().format(monto);
    }

    /**
     * "S/ 280 por noche" — la jerarquia de precio de §66, que separa la cifra
     * del periodo para que el ojo no lea todo como un bloque.
     */
    public static String formatPorNoche(double monto) {
        return format(monto) + " por noche";
    }

    /**
     * "S/ 1,120 · 4 noches" — para cuando el total ya incluye varias noches.
     */
    public static String formatTotalConNoches(double total, long noches) {
        return format(total) + " · " + noches + (noches == 1 ? " noche" : " noches");
    }
}
