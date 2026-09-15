package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/**
 * RF-032 y RC-012 son requisitos criticos: una habitacion no puede quedar
 * reservada por dos clientes en periodos superpuestos. Esta es la regla que
 * mas caro sale si se implementa mal, asi que se prueba en los bordes.
 */
public class BookingOverlapTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 20);

    private Booking reserva(String id, String roomId, String clienteId,
                            LocalDate entrada, LocalDate salida) {
        return new Booking(id, "BK-" + id, "H1", roomId, clienteId,
                entrada, salida, 2, 280, BookingStatus.CONFIRMADA);
    }

    @Test
    public void periodosQueSeCruzanSeSuperponen() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R1", "C2", HOY.plusDays(2), HOY.plusDays(6));

        assertTrue(a.seSuperponeCon(b));
        assertTrue(b.seSuperponeCon(a));
    }

    @Test
    public void periodoContenidoSeSuperpone() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(10));
        Booking b = reserva("B", "R1", "C2", HOY.plusDays(2), HOY.plusDays(4));

        assertTrue(a.seSuperponeCon(b));
    }

    @Test
    public void compartirElDiaDeRotacionNoEsSuperponerse() {
        // Salida el 24 y entrada el 24: ese dia la habitacion rota.
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R1", "C2", HOY.plusDays(4), HOY.plusDays(8));

        assertFalse(a.seSuperponeCon(b));
        assertFalse(b.seSuperponeCon(a));
    }

    @Test
    public void periodosDisjuntosNoSeSuperponen() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R1", "C2", HOY.plusDays(10), HOY.plusDays(14));

        assertFalse(a.seSuperponeCon(b));
    }

    @Test
    public void habitacionesDistintasNuncaSeSuperponen() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R2", "C2", HOY, HOY.plusDays(4));

        assertFalse(a.seSuperponeCon(b));
    }

    @Test
    public void unaReservaCanceladaLiberaLaHabitacion() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R1", "C2", HOY, HOY.plusDays(4));
        b.setEstado(BookingStatus.CANCELADA);

        assertFalse(a.seSuperponeCon(b));
        assertFalse(b.seSuperponeCon(a));
    }

    @Test
    public void unClienteNoPuedeTenerReservasSuperpuestasAunqueSeanDeHotelesDistintos() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R9", "C1", HOY.plusDays(2), HOY.plusDays(6));

        assertFalse(a.seSuperponeCon(b));
        assertTrue(a.seSuperponeConOtraDelCliente(b));
    }

    @Test
    public void clientesDistintosNoColisionanEnLaReglaDeCliente() {
        Booking a = reserva("A", "R1", "C1", HOY, HOY.plusDays(4));
        Booking b = reserva("B", "R2", "C2", HOY, HOY.plusDays(4));

        assertFalse(a.seSuperponeConOtraDelCliente(b));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noSePuedeConstruirUnaReservaConSalidaAnteriorALaEntrada() {
        reserva("A", "R1", "C1", HOY.plusDays(4), HOY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void noSePuedeConstruirUnaReservaDeCeroNoches() {
        reserva("A", "R1", "C1", HOY, HOY);
    }
}
