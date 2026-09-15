package org.iot.project.models;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Cada cuanto se agrupa un reporte de ventas (RF-056 a RF-058).
 *
 * <p>No lleva nombre visible, al contrario que {@link BookingStatus}: lo que la
 * pantalla pone en el selector no es el nombre del valor, sino el del reporte
 * —"Diario", "Mensual", "Anual"—, que es otra palabra. Escribirla aqui obligaria
 * al modelo a elegir entre "Dia" y "Diario" para un boton que no es asunto suyo.
 */
public enum Periodicidad {

    /** Un periodo por dia (RF-056). */
    DIA,

    /** Un periodo por mes (RF-057). */
    MES,

    /** Un periodo por anio (RF-058). */
    ANIO;

    /**
     * Primer dia del periodo que contiene la fecha.
     *
     * <p>Es la clave con la que se agrupa, y por eso devuelve una fecha
     * normalizada y no un numero de periodo: asi las tres granularidades se
     * suman igual —acumulando en un mapa por fecha— sin inventar una clave
     * distinta para cada una.
     */
    @NonNull
    public LocalDate inicioDe(@NonNull LocalDate fecha) {
        switch (this) {
            case MES:
                return fecha.withDayOfMonth(1);
            case ANIO:
                return fecha.withDayOfYear(1);
            case DIA:
            default:
                return fecha;
        }
    }
}
