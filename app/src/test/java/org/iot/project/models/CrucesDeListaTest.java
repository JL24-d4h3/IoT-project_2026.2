package org.iot.project.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.iot.project.data.mock.MockData;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Los cruces que alimentan las listas del administrador son fotos, no ventanas.
 *
 * <p>Es una prueba de una regla que no se ve en pantalla hasta que falla, y que
 * falla en silencio: {@code ListAdapter} compara la fila anterior con la nueva
 * para decidir si tiene que volver a pintarla, y los dos cruces envuelven el
 * mismo objeto vivo del almacén. Si la fila leyera de él, se estaría comparando
 * consigo misma y la conclusión sería siempre "no cambió nada": la lista
 * seguiría enseñando "Confirmada" después de cancelar la reserva, y el contador
 * de mensajes sin leer seguiría encendido después de abrir el chat.
 *
 * <p>Por eso lo que se comprueba aquí es justo lo contrario de lo que parece
 * natural: que dos cruces del <em>mismo</em> dato no son iguales cuando el dato
 * cambió por debajo.
 */
public class CrucesDeListaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 20);

    /**
     * Una reserva del hotel administrado, con cliente y habitación reales.
     *
     * <p>Se construye aquí y no se toma de {@code MockData.RESERVAS} para no
     * tocar el conjunto compartido: los tests corren en el mismo proceso y una
     * reserva modificada aquí aparecería modificada en los demás.
     */
    private Booking reserva() {
        return new Booking("BT", "EST-TEST-0001", "H1", "H1-R1", "U1",
                HOY, HOY.plusDays(3), 2, 100, BookingStatus.CONFIRMADA);
    }

    @Test
    public void unCruceDeReservaNoCambiaCuandoCambiaLaReserva() {
        Booking reserva = reserva();
        ReservaDeHotel antes = MockData.detallar(reserva);

        reserva.addCargo(new Charge(50, "Consumo de prueba", "Cargo añadido después del cruce."));
        reserva.setEstado(BookingStatus.CANCELADA);
        ReservaDeHotel despues = MockData.detallar(reserva);

        assertEquals("El estado del cruce es el que había al cruzar",
                BookingStatus.CONFIRMADA, antes.getEstado());
        assertEquals("El total del cruce no incluye lo que se cargó después",
                300d, antes.getTotal(), 0.001d);

        assertEquals("Un cruce posterior sí ve el estado nuevo",
                BookingStatus.CANCELADA, despues.getEstado());
        assertEquals("Un cruce posterior sí ve el total nuevo",
                350d, despues.getTotal(), 0.001d);

        // Y por eso la fila se vuelve a pintar.
        assertNotEquals(antes.getEstado(), despues.getEstado());
    }

    @Test
    public void unCruceDeConversacionNoCambiaCuandoLlegaOSeLeeUnMensaje() {
        Conversation conversacion = new Conversation("CVT", "BT", "U1", "H1");
        conversacion.addMensaje(new Message("MT1", "CVT", "¿Hay sitio para el coche?",
                LocalDateTime.of(HOY, java.time.LocalTime.of(10, 0)),
                Message.Autor.CLIENTE));

        ReservaDeHotel estadia = MockData.detallar(reserva());
        ConversacionDeHotel antes = new ConversacionDeHotel(conversacion, estadia);
        assertEquals(1, antes.getSinLeer());
        assertEquals(1, antes.getNumMensajes());

        // El hotel abre el chat: RF-068 marca como leído lo del cliente.
        conversacion.marcarLeidoPara(Message.Autor.HOTEL);
        ConversacionDeHotel despuesDeLeer = new ConversacionDeHotel(conversacion, estadia);
        assertNotEquals("Después de leerlo, la fila tiene que volver a pintarse",
                antes.getSinLeer(), despuesDeLeer.getSinLeer());

        // Y responde: el mensaje nuevo también tiene que notarse.
        conversacion.addMensaje(new Message("MT2", "CVT", "Sí, sin problema.",
                LocalDateTime.of(HOY, java.time.LocalTime.of(10, 5)),
                Message.Autor.HOTEL));
        ConversacionDeHotel despuesDeResponder = new ConversacionDeHotel(conversacion, estadia);
        assertNotEquals("Con un mensaje más, la fila tiene que volver a pintarse",
                despuesDeLeer.getNumMensajes(), despuesDeResponder.getNumMensajes());
        assertSame("La previa que enseña la fila es la del momento del cruce",
                conversacion.getMensajes().get(1), despuesDeResponder.getUltimoMensaje());
    }

    @Test
    public void elCruceSigueDandoAccesoAVivirLaConversacion() {
        Conversation conversacion = new Conversation("CVT", "BT", "U1", "H1");
        ReservaDeHotel estadia = MockData.detallar(reserva());
        ConversacionDeHotel cruce = new ConversacionDeHotel(conversacion, estadia);

        // La copia es de lo que enseña la fila, no de la conversación: el chat
        // la necesita viva para poder responder.
        assertSame(conversacion, cruce.getConversacion());
        assertSame(estadia.getReserva(), cruce.getEstadia().getReserva());
    }

    @Test
    public void laEstadiaDelCruceResuelveAlHuespedYSuHabitacion() {
        ReservaDeHotel cruce = MockData.detallar(reserva());

        assertTrue("La reserva apunta a un cliente que existe en el conjunto",
                cruce.getCliente() != null);
        assertEquals("U1", cruce.getCliente().getId());
        assertEquals("La habitación se resuelve a su número visible",
                "601", cruce.getHabitacionNumero());
    }
}
