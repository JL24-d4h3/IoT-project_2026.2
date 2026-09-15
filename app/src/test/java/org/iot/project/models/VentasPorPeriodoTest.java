package org.iot.project.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * El reporte de reservas y ventas agrupa, suma y ordena bien (RF-055 a RF-058).
 *
 * <p>Es la parte del reporte que no se puede comprobar mirando la pantalla: un
 * periodo al que le falta una reserva, o al que se le ha colado una noche, se
 * ve igual de bien que el correcto. Y las tres granularidades comparten la misma
 * suma, así que un error en la clave de agrupación se lleva por delante los tres
 * reportes a la vez.
 *
 * <p>Las reservas se construyen aquí y no se toman de {@code MockData}: los
 * tests corren en el mismo proceso, y las fechas del conjunto compartido cambian
 * con el día en que se ejecuten.
 */
public class VentasPorPeriodoTest {

    /** Una reserva de tres noches a 100 la noche: 300 de alojamiento. */
    private Booking reserva(String id, LocalDate entrada) {
        return new Booking(id, "EST-TEST-" + id, "H1", "H1-R1", "U1",
                entrada, entrada.plusDays(3), 2, 100, BookingStatus.CONFIRMADA);
    }

    @Test
    public void elReporteDiarioSumaCadaDiaPorSuCuenta() {
        List<PeriodoDeVentas> periodos = PeriodoDeVentas.agrupar(Periodicidad.DIA, Arrays.asList(
                reserva("BT1", LocalDate.of(2026, 9, 20)),
                reserva("BT2", LocalDate.of(2026, 9, 20)),
                reserva("BT3", LocalDate.of(2026, 9, 21))));

        assertEquals(2, periodos.size());

        // El más reciente primero: el reporte se consulta para ver cómo va el
        // periodo en curso, y en orden cronológico quedaría al final de la lista.
        PeriodoDeVentas reciente = periodos.get(0);
        assertEquals(LocalDate.of(2026, 9, 21), reciente.getInicio());
        assertEquals(1, reciente.getReservas());
        assertEquals(3L, reciente.getNoches());
        assertEquals(300d, reciente.getMonto(), 0.001d);

        PeriodoDeVentas anterior = periodos.get(1);
        assertEquals(LocalDate.of(2026, 9, 20), anterior.getInicio());
        assertEquals("Dos reservas del mismo día son un solo periodo",
                2, anterior.getReservas());
        assertEquals(6L, anterior.getNoches());
        assertEquals(600d, anterior.getMonto(), 0.001d);
    }

    @Test
    public void elReporteMensualJuntaLosDiasDeUnMes() {
        List<PeriodoDeVentas> periodos = PeriodoDeVentas.agrupar(Periodicidad.MES, Arrays.asList(
                reserva("BT1", LocalDate.of(2026, 8, 31)),
                reserva("BT2", LocalDate.of(2026, 9, 1)),
                reserva("BT3", LocalDate.of(2026, 9, 30)),
                reserva("BT4", LocalDate.of(2026, 10, 1))));

        assertEquals(3, periodos.size());

        PeriodoDeVentas septiembre = periodos.get(1);
        assertEquals("Un mes se identifica por su primer día",
                LocalDate.of(2026, 9, 1), septiembre.getInicio());
        assertEquals("Los dos extremos del mes caen dentro",
                2, septiembre.getReservas());
        assertEquals(600d, septiembre.getMonto(), 0.001d);

        // El 31 de agosto y el 1 de octubre son meses distintos, por cerca que
        // estén de septiembre.
        assertEquals(LocalDate.of(2026, 10, 1), periodos.get(0).getInicio());
        assertEquals(LocalDate.of(2026, 8, 1), periodos.get(2).getInicio());
    }

    @Test
    public void elReporteAnualJuntaLosMesesDeUnAnio() {
        List<PeriodoDeVentas> periodos = PeriodoDeVentas.agrupar(Periodicidad.ANIO, Arrays.asList(
                reserva("BT1", LocalDate.of(2025, 12, 31)),
                reserva("BT2", LocalDate.of(2026, 1, 1)),
                reserva("BT3", LocalDate.of(2026, 7, 15))));

        assertEquals(2, periodos.size());

        PeriodoDeVentas esteAnio = periodos.get(0);
        assertEquals(LocalDate.of(2026, 1, 1), esteAnio.getInicio());
        assertEquals(2, esteAnio.getReservas());
        assertEquals(600d, esteAnio.getMonto(), 0.001d);

        assertEquals(2025, periodos.get(1).getInicio().getYear());
    }

    @Test
    public void elTotalDeUnPeriodoIncluyeLosConsumosDeLaEstadia() {
        Booking reserva = reserva("BT1", LocalDate.of(2026, 9, 20));
        reserva.addCargo(new Charge(45, "Minibar", "Dos botellas de agua."));

        List<PeriodoDeVentas> periodos = PeriodoDeVentas.agrupar(
                Periodicidad.DIA, Arrays.asList(reserva));

        // 300 de alojamiento más 45 de consumo. Si el reporte sumara solo las
        // noches, el total de ventas no cuadraría con lo que se cobró.
        assertEquals(345d, periodos.get(0).getMonto(), 0.001d);
    }

    @Test
    public void sinReservasNoHayPeriodos() {
        assertEquals(0, PeriodoDeVentas.agrupar(Periodicidad.MES, Arrays.asList()).size());
    }
}
