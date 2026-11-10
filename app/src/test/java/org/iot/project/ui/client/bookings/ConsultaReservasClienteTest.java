package org.iot.project.ui.client.bookings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Búsqueda, estado y orden combinados sin Firebase, Android ni fechas del reloj. */
public class ConsultaReservasClienteTest {
    private static final Function<Booking, String> NOMBRE = Booking::getHotelNombre;

    @Test public void fechaSalidaOrdenaTodosLosEstadosAscendenteYDescendente() {
        Booking activa = reserva("activa", "Hotel Z", 18, BookingStatus.ACTIVA);
        Booking pendiente = reserva("pendiente", "Hotel A", 25, BookingStatus.PENDIENTE);
        Booking finalizada = reserva("finalizada", "Hotel M", 8, BookingStatus.FINALIZADA);
        List<Booking> datos = Arrays.asList(activa, pendiente, finalizada);

        assertEquals(Arrays.asList("finalizada", "activa", "pendiente"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.FECHA_SALIDA, true)));
        assertEquals(Arrays.asList("pendiente", "activa", "finalizada"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.FECHA_SALIDA, false)));
    }

    @Test public void fechasDeEntradaNoSustituyenLasFechasDeSalida() {
        Booking estanciaLarga = reservaConFechas("larga", "Hotel A", 2, 25, BookingStatus.CONFIRMADA);
        Booking estanciaCorta = reservaConFechas("corta", "Hotel B", 10, 12, BookingStatus.ACTIVA);
        List<Booking> datos = Arrays.asList(estanciaLarga, estanciaCorta);

        assertEquals(Arrays.asList("corta", "larga"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.FECHA_SALIDA, true)));
        assertEquals(Arrays.asList("larga", "corta"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.FECHA_SALIDA, false)));
    }

    @Test public void nombreAscendenteYDescendenteUsaOrdenAlfabeticoEspanol() {
        List<Booking> datos = Arrays.asList(
                reserva("z", "zafiro", 8, BookingStatus.ACTIVA),
                reserva("a", "Álamo", 25, BookingStatus.CANCELADA),
                reserva("b", "brisa", 12, BookingStatus.PENDIENTE));

        assertEquals(Arrays.asList("a", "b", "z"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.NOMBRE, true)));
        assertEquals(Arrays.asList("z", "b", "a"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.NOMBRE, false)));
    }

    @Test public void nombreConEnieSeOrdenaDespuesDeEneYAntesDeO() {
        List<Booking> datos = Arrays.asList(
                reserva("enie", "Ñandú", 8, BookingStatus.ACTIVA),
                reserva("o", "Oasis", 8, BookingStatus.ACTIVA),
                reserva("ene", "Norte", 8, BookingStatus.ACTIVA));

        assertEquals(Arrays.asList("ene", "enie", "o"),
                ids(consultar(datos, "", null, ConsultaReservasCliente.Orden.NOMBRE, true)));
    }

    @Test public void busquedaDeNombreIgnoraMayusculasAcentosYEspaciosExternos() {
        Booking peru = reserva("peru", "Hotel Perú", 8, BookingStatus.ACTIVA);
        Booking otro = reserva("otro", "Hotel del Mar", 8, BookingStatus.ACTIVA);

        assertEquals(Collections.singletonList("peru"), ids(consultar(Arrays.asList(otro, peru),
                "  hOTel pErU  ", null, ConsultaReservasCliente.Orden.NOMBRE, true)));
    }

    @Test public void permiteBuscarPorCodigoDeReservaSinDistinguirMayusculas() {
        Booking codigo = reserva("codigo", "Hotel Central", 8, BookingStatus.CONFIRMADA);
        Booking otro = reserva("otro", "Hotel Central", 8, BookingStatus.CONFIRMADA);

        assertEquals(Collections.singletonList("codigo"), ids(consultar(Arrays.asList(otro, codigo),
                "  res-CODIGO  ", null, ConsultaReservasCliente.Orden.FECHA_SALIDA, false)));
    }

    @Test public void busquedaYOrdenUsanElNombreResueltoQueVeElCliente() {
        Booking renombrado = reserva("renombrado", "Zafiro antiguo", 8, BookingStatus.ACTIVA);
        Booking existente = reserva("existente", "Brisa", 8, BookingStatus.ACTIVA);
        List<Booking> datos = Arrays.asList(existente, renombrado);
        Function<Booking, String> nombreActual = reserva -> "renombrado".equals(reserva.getId())
                ? "Álamo actual" : reserva.getHotelNombre();

        assertEquals(Collections.singletonList("renombrado"), ids(ConsultaReservasCliente.aplicar(
                datos, "alamo", null, ConsultaReservasCliente.Orden.NOMBRE, true, nombreActual)));
        assertTrue(ConsultaReservasCliente.aplicar(datos, "zafiro antiguo", null,
                ConsultaReservasCliente.Orden.NOMBRE, true, nombreActual).isEmpty());
        assertEquals(Arrays.asList("renombrado", "existente"), ids(ConsultaReservasCliente.aplicar(
                datos, "", null, ConsultaReservasCliente.Orden.NOMBRE, true, nombreActual)));
    }

    @Test public void combinaBusquedaEstadoYOrdenDeSalidaSinPerderCriterios() {
        List<Booking> datos = Arrays.asList(
                reserva("confirmada", "Hotel Sol", 25, BookingStatus.CONFIRMADA),
                reserva("activa-temprana", "Sol del Valle", 8, BookingStatus.ACTIVA),
                reserva("activa-tardia", "Hotel Sol", 18, BookingStatus.ACTIVA),
                reserva("otro-hotel", "Hotel Luna", 22, BookingStatus.ACTIVA));

        assertEquals(Arrays.asList("activa-tardia", "activa-temprana"), ids(consultar(datos,
                "SOL", BookingStatus.ACTIVA, ConsultaReservasCliente.Orden.FECHA_SALIDA, false)));
        assertEquals(Arrays.asList("activa-temprana", "activa-tardia"), ids(consultar(datos,
                "SOL", BookingStatus.ACTIVA, ConsultaReservasCliente.Orden.FECHA_SALIDA, true)));
    }

    @Test public void losCincoEstadosSeFiltranIndividualmenteYTodosNoFiltra() {
        List<Booking> datos = new ArrayList<>();
        for (BookingStatus estado : BookingStatus.values()) {
            datos.add(reserva(estado.name(), "Hotel Sol", 8, estado));
        }

        for (BookingStatus estado : BookingStatus.values()) {
            List<Booking> visibles = consultar(datos, "sol", estado, ConsultaReservasCliente.Orden.NOMBRE, true);
            assertEquals(1, visibles.size());
            assertEquals(estado, visibles.get(0).getEstado());
        }
        assertEquals(5, consultar(datos, "sol", null, ConsultaReservasCliente.Orden.NOMBRE, true).size());
    }

    @Test public void ordenarYBuscarNoMutanLaListaOriginalNiLasReservas() {
        Booking primera = reserva("primera", "Zafiro", 25, BookingStatus.CANCELADA);
        Booking segunda = reserva("segunda", "Álamo", 8, BookingStatus.ACTIVA);
        List<Booking> datos = new ArrayList<>(Arrays.asList(primera, segunda));

        List<Booking> visibles = consultar(datos, "", null, ConsultaReservasCliente.Orden.NOMBRE, true);

        assertEquals(Arrays.asList("primera", "segunda"), ids(datos));
        assertEquals(Arrays.asList("segunda", "primera"), ids(visibles));
        assertSame(primera, datos.get(0));
        assertEquals("Zafiro", primera.getHotelNombre());
        assertEquals(BookingStatus.CANCELADA, primera.getEstado());
        assertEquals(LocalDate.of(2030, 1, 25), primera.getFechaSalida());
    }

    @Test public void consultaSinCoincidenciasYListadoVacioDevuelvenListaVacia() {
        List<Booking> datos = Collections.singletonList(reserva("uno", "Hotel Sol", 8, BookingStatus.ACTIVA));

        assertTrue(consultar(datos, "no existe", null, ConsultaReservasCliente.Orden.NOMBRE, true).isEmpty());
        assertTrue(consultar(datos, "sol", BookingStatus.CANCELADA,
                ConsultaReservasCliente.Orden.FECHA_SALIDA, false).isEmpty());
        assertTrue(consultar(Collections.emptyList(), "", null,
                ConsultaReservasCliente.Orden.FECHA_SALIDA, false).isEmpty());
    }

    @Test public void empatesDeNombreYFechaSonDeterministasAunqueCambieElOrdenDeOrigen() {
        Booking a = reserva("a", "Hotel Sol", 8, BookingStatus.ACTIVA);
        Booking b = reserva("b", "Hotel Sol", 8, BookingStatus.ACTIVA);
        Booking c = reserva("c", "Hotel Sol", 8, BookingStatus.ACTIVA);
        List<Booking> primera = Arrays.asList(a, b, c);
        List<Booking> segunda = Arrays.asList(c, a, b);

        for (ConsultaReservasCliente.Orden orden : ConsultaReservasCliente.Orden.values()) {
            for (boolean ascendente : new boolean[] {true, false}) {
                assertEquals(ids(consultar(primera, "", null, orden, ascendente)),
                        ids(consultar(segunda, "", null, orden, ascendente)));
            }
        }
    }

    private static List<Booking> consultar(List<Booking> datos, String texto, BookingStatus estado,
                                           ConsultaReservasCliente.Orden orden, boolean ascendente) {
        return ConsultaReservasCliente.aplicar(datos, texto, estado, orden, ascendente, NOMBRE);
    }

    private static List<String> ids(List<Booking> reservas) {
        return reservas.stream().map(Booking::getId).collect(Collectors.toList());
    }

    private static Booking reserva(String id, String nombre, int diaSalida, BookingStatus estado) {
        return reservaConFechas(id, nombre, 1, diaSalida, estado);
    }

    private static Booking reservaConFechas(String id, String nombre, int diaEntrada,
                                            int diaSalida, BookingStatus estado) {
        return new Booking(id, "RES-" + id, "hotel-" + id, "room-" + id, "cliente-1",
                LocalDate.of(2030, 1, diaEntrada), LocalDate.of(2030, 1, diaSalida), 1, 100, estado)
                .withAlojamiento(nombre, "Lima", "Miraflores", "", "Doble", "101");
    }
}
