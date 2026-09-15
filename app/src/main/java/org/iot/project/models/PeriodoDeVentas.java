package org.iot.project.models;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Una fila del reporte de reservas y ventas del hotel (RF-055 a RF-058).
 *
 * <p>Es un resultado calculado, no un dato guardado, como
 * {@link IngresoPorServicio}: se arma sumando las reservas del hotel cada vez
 * que se pide. No existe como entidad porque no tiene identidad propia —el mismo
 * dia aparece en el reporte diario, en el mensual y en el anual— y guardarlo
 * abriria la puerta a que los tres dejaran de cuadrar entre si.
 *
 * <p><b>La reserva entera cuenta en el periodo de su fecha de entrada</b>, y no
 * repartida entre las noches que dura. Es una decision, no un descuido: con un
 * ingreso prorrateado por noche, una estadia que cruza de diciembre a enero
 * haria que el total del anio no fuera la suma de sus meses, y un reporte cuyas
 * partes no suman el todo no sirve para lo unico que se le pide. Se reconoce el
 * ingreso al llegar, que es ademas cuando el hotel empieza a cobrarlo.
 *
 * <p>Las noches si se cuentan enteras en ese mismo periodo, por coherencia con
 * el monto: son las noches vendidas de las reservas que entraron ese dia.
 */
public final class PeriodoDeVentas {

    @NonNull
    private final Periodicidad periodicidad;

    /** Primer dia del periodo: la fecha por la que se agrupo. */
    @NonNull
    private final LocalDate inicio;

    private final int reservas;

    private final long noches;

    private final double monto;

    public PeriodoDeVentas(@NonNull Periodicidad periodicidad, @NonNull LocalDate inicio,
                           int reservas, long noches, double monto) {
        this.periodicidad = periodicidad;
        this.inicio = inicio;
        this.reservas = reservas;
        this.noches = noches;
        this.monto = monto;
    }

    /**
     * Con que granularidad se agrupo.
     *
     * <p>Viaja en la fila y no se lo queda la pantalla porque es lo que dice como
     * leer {@link #getInicio()}: el mismo 1 de septiembre es "1 set" en un
     * reporte diario y "septiembre 2026" en uno mensual. Sin este dato, la fila
     * no se puede rotular sola.
     */
    @NonNull
    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    /** Primer dia del periodo, que es el que lo identifica. */
    @NonNull
    public LocalDate getInicio() {
        return inicio;
    }

    /** Cuantas reservas entraron en el periodo. */
    public int getReservas() {
        return reservas;
    }

    /** Cuantas noches se vendieron en esas reservas. */
    public long getNoches() {
        return noches;
    }

    /** Lo que facturan esas reservas, con sus consumos incluidos. */
    public double getMonto() {
        return monto;
    }

    /**
     * Agrupa unas reservas en periodos (RF-055 a RF-058).
     *
     * <p>Cuales entran lo decide quien llama: filtrarlas es una regla del
     * repositorio, que es el que sabe que una reserva pendiente no es una venta.
     * A partir de ahi esto es calculo puro sobre lo que recibe, y por eso vive
     * aqui y no en el repositorio que lo estreno: los simulados no se pueden
     * probar sin Android —responden por el hilo principal—, y la agrupacion es
     * justo la parte del reporte que conviene tener probada, porque sumar de mas
     * o de menos no se nota mirando la pantalla.
     */
    @NonNull
    public static List<PeriodoDeVentas> agrupar(@NonNull Periodicidad periodicidad,
                                                @NonNull List<Booking> reservas) {
        // El TreeMap mantiene los periodos en orden cronologico mientras se
        // suman, asi que despues no hay que ordenar por una clave que ya lo
        // estaba: solo hay que recorrerlo al reves.
        NavigableMap<LocalDate, Acumulado> porPeriodo = new TreeMap<>();

        for (Booking reserva : reservas) {
            LocalDate inicio = periodicidad.inicioDe(reserva.getFechaEntrada());
            Acumulado acumulado = porPeriodo.get(inicio);
            if (acumulado == null) {
                acumulado = new Acumulado();
                porPeriodo.put(inicio, acumulado);
            }
            acumulado.reservas++;
            acumulado.noches += reserva.getNumNoches();
            acumulado.monto += reserva.getTotal();
        }

        List<PeriodoDeVentas> reporte = new ArrayList<>();
        // Del periodo mas reciente al mas antiguo: el reporte se consulta para
        // ver como va el mes en curso, y en orden cronologico quedaria al final.
        for (Map.Entry<LocalDate, Acumulado> entrada : porPeriodo.descendingMap().entrySet()) {
            Acumulado acumulado = entrada.getValue();
            reporte.add(new PeriodoDeVentas(periodicidad, entrada.getKey(),
                    acumulado.reservas, acumulado.noches, acumulado.monto));
        }
        return reporte;
    }

    /** Suma corriente de un periodo mientras se recorre el conjunto de reservas. */
    private static final class Acumulado {

        private int reservas;
        private long noches;
        private double monto;
    }
}
