package org.iot.project.ui.client.payment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.YearMonth;
import java.util.Locale;

/**
 * Validación de los datos de una tarjeta tecleados para un pago simulado.
 *
 * <p>Es lógica pura y sin Android para poder probarla. Los datos se validan y
 * se descartan: del número solo se conserva la marca y los últimos cuatro
 * dígitos (RC-011), y el CVV nunca sale de este objeto de validación.
 */
public final class TarjetaSimulada {

    private TarjetaSimulada() {
    }

    /** Deja solo los dígitos: acepta espacios y guiones al teclear. */
    @NonNull
    public static String soloDigitos(@Nullable String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** Longitud razonable (13 a 19 dígitos) y suma de control de Luhn correcta. */
    public static boolean numeroValido(@Nullable String texto) {
        String digitos = soloDigitos(texto);
        return digitos.length() >= 13 && digitos.length() <= 19 && luhn(digitos);
    }

    static boolean luhn(@NonNull String digitos) {
        int suma = 0;
        boolean duplicar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int valor = digitos.charAt(i) - '0';
            if (duplicar) {
                valor *= 2;
                if (valor > 9) valor -= 9;
            }
            suma += valor;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** Visa, Mastercard, American Express o "Tarjeta" si no se reconoce. */
    @NonNull
    public static String marca(@Nullable String texto) {
        String d = soloDigitos(texto);
        if (d.startsWith("4")) return "Visa";
        if (d.length() >= 2) {
            int dos = Integer.parseInt(d.substring(0, 2));
            if (dos >= 51 && dos <= 55) return "Mastercard";
            if (dos == 34 || dos == 37) return "American Express";
        }
        if (d.length() >= 4) {
            int cuatro = Integer.parseInt(d.substring(0, 4));
            if (cuatro >= 2221 && cuatro <= 2720) return "Mastercard";
        }
        return "Tarjeta";
    }

    @NonNull
    public static String ultimos4(@Nullable String texto) {
        String d = soloDigitos(texto);
        return d.length() <= 4 ? d : d.substring(d.length() - 4);
    }

    /**
     * Vencimiento MM/AA, con mes entre 01 y 12, no vencida y a no más de 20 años.
     *
     * <p>El separador es opcional: "12/28", "12-28", "12 28" y "1228" valen lo
     * mismo, porque el teclado numérico de varios móviles no ofrece la barra.
     */
    public static boolean vencimientoValido(@Nullable String texto, @NonNull YearMonth hoy) {
        if (texto == null) return false;
        String limpio = texto.trim();
        if (!limpio.matches("\\d{2}\\s*[/\\-.]?\\s*\\d{2}")) return false;
        String digitos = soloDigitos(limpio);
        int mes = Integer.parseInt(digitos.substring(0, 2));
        int anio = 2000 + Integer.parseInt(digitos.substring(2));
        if (mes < 1 || mes > 12) return false;
        YearMonth vence = YearMonth.of(anio, mes);
        return !vence.isBefore(hoy) && !vence.isAfter(hoy.plusYears(20));
    }

    /** 4 dígitos para American Express, 3 para el resto. */
    public static boolean cvvValido(@Nullable String cvv, @Nullable String numero) {
        String d = soloDigitos(cvv);
        if (d.length() != (cvv == null ? -1 : cvv.trim().length())) return false;
        int esperado = "American Express".equals(marca(numero)) ? 4 : 3;
        return d.length() == esperado;
    }

    /**
     * Nombre con el que se registra el método de pago, con el mismo formato
     * que una tarjeta guardada ("Visa ****4242"). Nunca incluye el número.
     */
    @NonNull
    public static String metodo(@NonNull String numero) {
        return String.format(Locale.ROOT, "%s ****%s", marca(numero), ultimos4(numero));
    }
}
