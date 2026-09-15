package org.iot.project.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Fechas en espanol para toda la interfaz: fechas de reserva, marcas de tiempo
 * del chat y marcas relativas del centro de notificaciones.
 *
 * <p>Los nombres de mes van en tabla propia en vez de depender del locale del
 * dispositivo, para que una demo en un telefono en ingles siga mostrando la
 * interfaz en espanol.
 */
public final class DateFormatter {

    private static final String[] MESES_CORTOS = {
            "ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "set", "oct", "nov", "dic"
    };

    private static final String[] MESES_LARGOS = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    private static final String[] DIAS_CORTOS = {
            "lun", "mar", "mie", "jue", "vie", "sab", "dom"
    };

    private DateFormatter() {
    }

    /** "20 set" — el formato compacto del selector de fechas y las cards. */
    public static String fechaCorta(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.getDayOfMonth() + " " + MESES_CORTOS[fecha.getMonthValue() - 1];
    }

    /** "20 de septiembre de 2026" — para detalles y confirmaciones. */
    public static String fechaLarga(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.getDayOfMonth()
                + " de " + MESES_LARGOS[fecha.getMonthValue() - 1]
                + " de " + fecha.getYear();
    }

    /** "vie 20 set" — cuando conviene que el usuario ubique el dia de la semana. */
    public static String fechaConDia(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return DIAS_CORTOS[fecha.getDayOfWeek().getValue() - 1] + " " + fechaCorta(fecha);
    }

    /**
     * "septiembre 2026" — el periodo de un reporte mensual (RF-057).
     *
     * <p>En minuscula, como el resto de los meses de la aplicacion: la fila que
     * lo enseña no es un titulo, es un dato mas de la lista.
     */
    public static String mesDe(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return MESES_LARGOS[fecha.getMonthValue() - 1] + " " + fecha.getYear();
    }

    /** "20 set – 24 set" — el rango que resume una reserva en la BookingCard. */
    public static String rango(LocalDate entrada, LocalDate salida) {
        if (entrada == null || salida == null) {
            return "";
        }
        return fechaCorta(entrada) + " – " + fechaCorta(salida);
    }

    /**
     * "20 set – 24 set · 4 noches". Si el rango cruza de anio se agrega el
     * anio de salida, para que "28 dic – 2 ene" no se lea como un error.
     */
    public static String rangoConNoches(LocalDate entrada, LocalDate salida, long noches) {
        String base = rango(entrada, salida);
        if (entrada != null && salida != null && entrada.getYear() != salida.getYear()) {
            base = fechaCorta(entrada) + " " + entrada.getYear()
                    + " – " + fechaCorta(salida) + " " + salida.getYear();
        }
        return base + " · " + noches + (noches == 1 ? " noche" : " noches");
    }

    /** "14:30" — hora de recojo del taxi y marcas del chat. */
    public static String hora(LocalTime hora) {
        if (hora == null) {
            return "";
        }
        return String.format(java.util.Locale.US, "%02d:%02d", hora.getHour(), hora.getMinute());
    }

    /** "14:30" a partir de una marca completa. */
    public static String horaDe(LocalDateTime momento) {
        return momento == null ? "" : hora(momento.toLocalTime());
    }

    /**
     * "hace 5 min", "ayer", "20 set" — el formato escalonado que usa el centro
     * de notificaciones (§41) y la lista de conversaciones.
     */
    public static String relativo(LocalDateTime momento, LocalDateTime ahora) {
        if (momento == null || ahora == null) {
            return "";
        }
        long minutos = Duration.between(momento, ahora).toMinutes();
        if (minutos < 1) {
            return "ahora";
        }
        if (minutos < 60) {
            return "hace " + minutos + " min";
        }
        long horas = minutos / 60;
        if (horas < 24) {
            return "hace " + horas + (horas == 1 ? " hora" : " horas");
        }
        long dias = horas / 24;
        if (dias == 1) {
            return "ayer";
        }
        if (dias < 7) {
            return "hace " + dias + " dias";
        }
        return fechaCorta(momento.toLocalDate());
    }

    /** "20 set – 24 set" a partir de un rango ya calculado en noches. */
    public static String rangoCorto(LocalDate entrada, LocalDate salida) {
        return rango(entrada, salida);
    }
}
