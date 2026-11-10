package org.iot.project.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Alcance del detalle del hotel y separación del QR privado del cliente. Sin SDK ni red. */
public class TaxiAdministradorScopeTest {
    private static final String QR_CLIENTE = "QR-0123456789AB";
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 26);
    private static final LocalDateTime POSICION = FECHA.atTime(10, 15);

    @Test public void conservaLosCincoEstadosRealesSinEntregarElQrAlAdministrador() {
        for (TaxiStatus estado : TaxiStatus.values()) {
            TaxiService original = traslado("reserva-1", "cliente-1", estado);
            TaxiService visible = FirebaseTaxiRepository.trasladoParaAdministrador(
                    reserva(BookingStatus.CONFIRMADA), "hotel-a", "hotel-a", original);
            assertEquals(estado, visible.getEstado());
            assertEquals("25", visible.getApiId());
            assertEquals("reserva-1", visible.getBookingId());
            assertEquals("", visible.getCodigo());
            assertEquals(QR_CLIENTE, original.getCodigo());
            assertNotSame(original, visible);
        }
    }

    @Test public void rechazaConsultarLaReservaDesdeOtroHotel() {
        rechaza(reserva(BookingStatus.CONFIRMADA), "hotel-b", "hotel-a",
                traslado("reserva-1", "cliente-1", TaxiStatus.ASIGNADO));
    }

    @Test public void rechazaUnDocumentoDeTaxiMarcadoComoOtroHotel() {
        rechaza(reserva(BookingStatus.CONFIRMADA), "hotel-a", "hotel-b",
                traslado("reserva-1", "cliente-1", TaxiStatus.ASIGNADO));
    }

    @Test public void rechazaOtroClienteYOtraReservaAunqueElHotelCoincida() {
        rechaza(reserva(BookingStatus.CONFIRMADA), "hotel-a", "hotel-a",
                traslado("reserva-ajena", "cliente-1", TaxiStatus.EN_CAMINO));
        rechaza(reserva(BookingStatus.CONFIRMADA), "hotel-a", "hotel-a",
                traslado("reserva-1", "cliente-ajeno", TaxiStatus.EN_CAMINO));
    }

    @Test public void elCheckoutNoImpideSeguirElTaxiQueLlevaAlPasajeroAlAeropuerto() {
        TaxiService visible = FirebaseTaxiRepository.trasladoParaAdministrador(
                reserva(BookingStatus.FINALIZADA), "hotel-a", "hotel-a",
                traslado("reserva-1", "cliente-1", TaxiStatus.EN_TRASLADO));
        assertEquals(TaxiStatus.EN_TRASLADO, visible.getEstado());
        assertEquals(POSICION, visible.getUltimaActualizacionUbicacion());
        assertEquals(-12.11, visible.getLatConductor(), 0.00001);
        assertEquals(-77.04, visible.getLngConductor(), 0.00001);
    }

    @Test public void noConvierteUnEnvioPendienteEnServicioConfirmado() {
        TaxiService pendiente = traslado("reserva-1", "cliente-1", TaxiStatus.SOLICITADO)
                .withApiId(null).withEnvioApi("ENVIADO_SIN_CONFIRMAR");
        TaxiService visible = FirebaseTaxiRepository.trasladoParaAdministrador(
                reserva(BookingStatus.CONFIRMADA), "hotel-a", "hotel-a", pendiente);
        assertNull(visible.getApiId());
        assertEquals("ENVIADO_SIN_CONFIRMAR", visible.getEnvioApi());
        assertNull(visible.getUltimaActualizacionUbicacion());
        assertEquals("", visible.getCodigo());
    }

    private static Booking reserva(BookingStatus estado) {
        return new Booking("reserva-1", "RES-1", "hotel-a", "habitacion-1", "cliente-1",
                FECHA.minusDays(2), FECHA, 1, 150, estado);
    }

    private static TaxiService traslado(String reserva, String cliente, TaxiStatus estado) {
        TaxiService taxi = new TaxiService("taxi-1", QR_CLIENTE, reserva, cliente)
                .withApiId("25").withEnvioApi("CONFIRMADO")
                .withProgramacion(FECHA, LocalTime.of(10, 0), false)
                .withRuta("Hotel A", "Aeropuerto").withRecojo(-12.10, -77.03)
                .withEstadoPersistido(estado, estado == TaxiStatus.SOLICITADO ? null
                        : new Driver("73", "Conductor", "Prueba"), 0f);
        if (estado != TaxiStatus.SOLICITADO) taxi.actualizarUbicacion(-12.11, -77.04, POSICION);
        return taxi;
    }

    private static void rechaza(Booking reserva, String hotel, String hotelTaxi, TaxiService taxi) {
        try {
            FirebaseTaxiRepository.trasladoParaAdministrador(reserva, hotel, hotelTaxi, taxi);
            fail("El administrador no debe recibir un traslado ajeno a la reserva de su hotel.");
        } catch (IllegalArgumentException esperado) { }
    }
}
