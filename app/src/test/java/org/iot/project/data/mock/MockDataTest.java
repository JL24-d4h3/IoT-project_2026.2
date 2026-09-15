package org.iot.project.data.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Conversation;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Message;
import org.iot.project.models.Review;
import org.iot.project.models.Room;
import org.iot.project.models.Service;
import org.junit.Test;

import java.time.LocalDate;

/**
 * Comprueba los invariantes del conjunto simulado.
 *
 * <p>No es una prueba de la interfaz, sino de los datos: si alguien anade un
 * hotel sin fotografias o una calificacion fuera de escala, esto lo detiene
 * antes de que llegue a una captura de pantalla. Varias de estas reglas son
 * requisitos del enunciado, no preferencias.
 */
public class MockDataTest {

    // ------------------------------------------------------------------
    //  Catalogo de servicios (§13, §17)
    // ------------------------------------------------------------------

    @Test
    public void elCatalogoDeServiciosNoTieneIdentificadoresRepetidos() {
        for (Service servicio : MockData.SERVICIOS) {
            int apariciones = 0;
            for (Service otro : MockData.SERVICIOS) {
                if (otro.getServiceId().equals(servicio.getServiceId())) {
                    apariciones++;
                }
            }
            assertEquals("Identificador repetido en el catálogo: " + servicio.getServiceId(),
                    1, apariciones);
        }
        assertTrue("El catálogo global debería tener servicios", MockData.SERVICIOS.size() >= 10);
    }

    @Test
    public void todoHotelSoloUsaServiciosDelCatalogoGlobal() {
        for (Hotel hotel : MockData.HOTELES) {
            for (HotelService hs : hotel.getServicios()) {
                assertNotNull("El hotel " + hotel.getId() + " usa un servicio fuera del catálogo: "
                        + hs.getServiceId(), MockData.servicio(hs.getServiceId()));
            }
        }
    }

    /** Regla 20 (§18): el precio vive en la relacion, no en el servicio. */
    @Test
    public void losServiciosIncluidosNoTienenPrecioYLosAdicionalesSi() {
        for (Hotel hotel : MockData.HOTELES) {
            for (HotelService hs : hotel.getServicios()) {
                if (hs.isIncluded()) {
                    assertEquals("Un servicio incluido no debería cobrarse: "
                                    + hotel.getId() + "/" + hs.getServiceId(),
                            0.0, hs.getPrice(), 0.001);
                } else {
                    assertTrue("Un servicio adicional debería tener precio: "
                                    + hotel.getId() + "/" + hs.getServiceId(),
                            hs.getPrice() > 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Hoteles
    // ------------------------------------------------------------------

    /** RF-013, regla 14: minimo cuatro fotografias por hotel. */
    @Test
    public void todoHotelTieneAlMenosCuatroFotografias() {
        for (Hotel hotel : MockData.HOTELES) {
            assertTrue("El hotel " + hotel.getNombre() + " tiene " + hotel.getFotos().size()
                            + " fotos y el mínimo es " + Hotel.MIN_FOTOS,
                    hotel.getFotos().size() >= Hotel.MIN_FOTOS);
            assertTrue("El hotel " + hotel.getNombre() + " no cumple cumpleMinimoFotos()",
                    hotel.cumpleMinimoFotos());
        }
    }

    /** Regla 9 (§10): la escala es 1 a 10, nunca 1 a 5 estrellas. */
    @Test
    public void todasLasCalificacionesEstanEnLaEscalaDeUnoADiez() {
        for (Hotel hotel : MockData.HOTELES) {
            assertTrue("Calificación fuera de escala en " + hotel.getNombre() + ": "
                            + hotel.getRating(),
                    hotel.getRating() >= 1f && hotel.getRating() <= 10f);
        }
        for (Review resena : MockData.RESENAS) {
            assertTrue("Reseña fuera de escala: " + resena.getRating(),
                    resena.getRating() >= Review.RATING_MIN
                            && resena.getRating() <= Review.RATING_MAX);
        }
    }

    /**
     * El conjunto tiene que cubrir los casos extremos a proposito, porque son
     * los que prueban que la interfaz resuelve bien el vacio y el rango bajo.
     */
    @Test
    public void elConjuntoCubreLosCasosLimiteQueLaInterfazDebeResolver() {
        int sinServicios = 0;
        int muchosServicios = 0;
        int ratingBajo = 0;
        int ratingAlto = 0;

        for (Hotel hotel : MockData.HOTELES) {
            if (hotel.getServicios().isEmpty()) {
                sinServicios++;
            }
            if (hotel.getServicios().size() >= 10) {
                muchosServicios++;
            }
            if (hotel.getRating() < 6f) {
                ratingBajo++;
            }
            if (hotel.getRating() >= 9f) {
                ratingAlto++;
            }
        }

        assertTrue("Falta un hotel sin servicios, para el estado vacío de la ficha",
                sinServicios >= 1);
        assertTrue("Falta un hotel con muchos servicios, para la ficha completa",
                muchosServicios >= 1);
        assertTrue("Faltan hoteles con calificación baja: la escala se vería sesgada",
                ratingBajo >= 2);
        assertTrue("Faltan hoteles con calificación alta, para la sección de recomendados",
                ratingAlto >= 2);
    }

    @Test
    public void todoHotelTieneAlMenosUnaHabitacionConCapacidadYFotos() {
        for (Hotel hotel : MockData.HOTELES) {
            assertFalse("El hotel " + hotel.getNombre() + " no tiene habitaciones",
                    hotel.getHabitaciones().isEmpty());
            for (Room room : hotel.getHabitaciones()) {
                assertTrue("La habitación " + room.getId() + " no tiene capacidad",
                        room.getCapacidadTotal() > 0);
                assertTrue("La habitación " + room.getId() + " no tiene fotos",
                        !room.getFotos().isEmpty());
                assertEquals("La habitación " + room.getId() + " apunta a otro hotel",
                        hotel.getId(), room.getHotelId());
            }
        }
    }

    @Test
    public void elPrecioDesdeDelHotelEsElDeSuHabitacionMasBarata() {
        for (Hotel hotel : MockData.HOTELES) {
            double masBarata = Double.MAX_VALUE;
            for (Room room : hotel.getHabitaciones()) {
                masBarata = Math.min(masBarata, room.getPrecioNoche());
            }
            assertEquals("El precio desde de " + hotel.getNombre() + " no cuadra con sus "
                            + "habitaciones",
                    masBarata, hotel.calcularPrecioDesde(), 0.01);
        }
    }

    // ------------------------------------------------------------------
    //  Reservas
    // ------------------------------------------------------------------

    @Test
    public void hayUnaReservaPorCadaEstadoParaPoderRecorrerLaPantalla() {
        for (BookingStatus estado : BookingStatus.values()) {
            boolean existe = false;
            for (Booking reserva : MockData.RESERVAS) {
                if (reserva.getEstado() == estado) {
                    existe = true;
                    break;
                }
            }
            assertTrue("No hay ninguna reserva en estado " + estado
                    + "; la pantalla de Mis Reservas no podría mostrar ese caso", existe);
        }
    }

    @Test
    public void todaReservaApuntaAUnHotelYUnaHabitacionQueExisten() {
        for (Booking reserva : MockData.RESERVAS) {
            assertNotNull("La reserva " + reserva.getCodigo() + " apunta a un hotel inexistente",
                    MockData.hotel(reserva.getHotelId()));
            assertNotNull("La reserva " + reserva.getCodigo() + " apunta a una habitación "
                    + "inexistente", MockData.habitacion(reserva.getRoomId()));
        }
    }

    /**
     * Toda reserva tiene que apuntar a un cliente que exista.
     *
     * <p>No es una formalidad: la portada del administrador cruza la reserva con
     * su cliente para poner un nombre en cada fila, y una reserva huérfana deja
     * esa fila sin nombre. Es un fallo que no rompe nada y por eso pasa
     * desapercibido hasta que se mira la pantalla.
     */
    @Test
    public void todaReservaApuntaAUnClienteQueExiste() {
        for (Booking reserva : MockData.RESERVAS) {
            assertNotNull("La reserva " + reserva.getCodigo() + " apunta al cliente "
                            + reserva.getClienteId() + ", que no está en USUARIOS",
                    MockData.usuario(reserva.getClienteId()));
        }
    }

    /**
     * El hotel que administra la sesión tiene que tener una estadía que cierre
     * hoy.
     *
     * <p>§43 pide los próximos checkouts como sección principal de la portada.
     * Sin una estancia que venza hoy, esa sección sale vacía en todas las
     * demostraciones y la pantalla no llega a ejercitarse.
     *
     * <p>El identificador está escrito a mano porque leerlo de
     * {@code SessionManager} cargaría clases de Android en una prueba de JVM.
     * Si algún día cambia el hotel administrado, esta prueba lo dice.
     */
    @Test
    public void elHotelAdministradoTieneUnaEstadiaQueCierraHoy() {
        String hotelAdministrado = "H1";
        boolean cierraHoy = false;
        for (Booking reserva : MockData.RESERVAS) {
            if (reserva.getHotelId().equals(hotelAdministrado)
                    && reserva.getEstado() == BookingStatus.ACTIVA
                    && reserva.getFechaSalida().equals(MockData.HOY)) {
                cierraHoy = true;
                break;
            }
        }
        assertTrue("El hotel " + hotelAdministrado + " (SessionManager.HOTEL_ADMINISTRADO) no "
                + "tiene ninguna estadía activa que cierre hoy: la portada del administrador "
                + "no tendría checkouts que mostrar", cierraHoy);
    }

    /**
     * El hotel que administra la sesión tiene que tener cobros que enseñar, y
     * más de una estadía con ellos.
     *
     * <p>§44 pide una pantalla de cobros y RF-054 que cada cobro se asocie a su
     * reserva. Con una sola estadía con cobros, esa pantalla tendría una tarjeta
     * y no se distinguiría una lista que agrupa de una que enseña la primera
     * reserva; con un solo cobro, ni siquiera se vería que una estadía puede
     * tener varios.
     *
     * <p>El identificador está escrito a mano por la misma razón que en
     * {@link #elHotelAdministradoTieneUnaEstadiaQueCierraHoy()}.
     */
    @Test
    public void elHotelAdministradoTieneCobrosEnMasDeUnaEstadia() {
        String hotelAdministrado = "H1";
        int estadiasConCobros = 0;
        int cobrosTotales = 0;

        for (Booking reserva : MockData.RESERVAS) {
            if (!reserva.getHotelId().equals(hotelAdministrado)
                    || reserva.getCargos().isEmpty()) {
                continue;
            }
            estadiasConCobros++;
            cobrosTotales += reserva.getCargos().size();
        }

        assertTrue("El hotel " + hotelAdministrado + " (SessionManager.HOTEL_ADMINISTRADO) solo "
                        + "tiene " + estadiasConCobros + " estadía con cobros: la pantalla de "
                        + "cobros no podría mostrar que agrupa por reserva",
                estadiasConCobros >= 2);
        assertTrue("El hotel " + hotelAdministrado + " tiene " + cobrosTotales + " cobros en "
                        + "total: hace falta una estadía con más de uno para ver la agrupación",
                cobrosTotales > estadiasConCobros);
    }

    /**
     * La bandeja del hotel administrado tiene que traer algo que enseñar.
     *
     * <p>Se comprueban dos cosas distintas. Más de una conversación abierta,
     * para que la lista demuestre que ordena por fecha en vez de ser una lista
     * de un elemento. Y al menos un mensaje del cliente sin abrir: sin él, el
     * contador de RF-068 nunca se vería encendido, y una pantalla que no puede
     * enseñar lo que la distingue de una lista cualquiera no está terminada.
     *
     * <p>El identificador está escrito a mano por la misma razón que en
     * {@link #elHotelAdministradoTieneUnaEstadiaQueCierraHoy()}.
     */
    @Test
    public void laBandejaDelHotelAdministradoTieneConversacionesAlgoSinLeer() {
        String hotelAdministrado = "H1";
        int abiertas = 0;
        int sinLeer = 0;

        for (Conversation conversacion : MockData.CONVERSACIONES) {
            if (!conversacion.getHotelId().equals(hotelAdministrado)) {
                continue;
            }
            Booking reserva = MockData.reserva(conversacion.getBookingId());
            // RF-065: el chat vive con la reserva. Una conversación de una
            // estadía cerrada no llega a la bandeja, así que tampoco cuenta.
            if (reserva == null || !reserva.permiteChat()) {
                continue;
            }
            abiertas++;
            sinLeer += conversacion.getNumNoLeidosPara(Message.Autor.HOTEL);
        }

        assertTrue("El hotel " + hotelAdministrado + " (SessionManager.HOTEL_ADMINISTRADO) solo "
                        + "tiene " + abiertas + " conversación abierta: la bandeja no podría "
                        + "mostrar que ordena por la más reciente",
                abiertas >= 2);
        assertTrue("Ninguna conversación del hotel " + hotelAdministrado + " tiene mensajes del "
                        + "cliente sin leer: el contador de RF-068 nunca se vería encendido",
                sinLeer > 0);
    }

    /** Las reservas tienen que estar ordenadas en el tiempo, sin solaparse consigo mismas. */
    @Test
    public void todaReservaTieneUnPeriodoValidoYSuTotalCuadra() {
        for (Booking reserva : MockData.RESERVAS) {
            assertTrue("La reserva " + reserva.getCodigo() + " termina antes de empezar",
                    reserva.getFechaSalida().isAfter(reserva.getFechaEntrada()));
            assertTrue("La reserva " + reserva.getCodigo() + " no tiene noches",
                    reserva.getNumNoches() > 0);

            double esperado = reserva.getSubtotalAlojamiento()
                    + reserva.getSubtotalServiciosAdicionales()
                    + reserva.getSubtotalCargos();
            assertEquals("El total de " + reserva.getCodigo() + " no es la suma de sus partes",
                    esperado, reserva.getTotal(), 0.01);
        }
    }

    // ------------------------------------------------------------------
    //  Regla de superposicion (RF-032, RC-012)
    // ------------------------------------------------------------------

    /**
     * Esta es la regla que decide si una habitacion aparece libre. Se comprueba
     * aqui porque es facil de romper en silencio: si el identificador de
     * habitacion no coincide, {@code seSuperponeCon} devuelve falso y todas las
     * habitaciones parecerian disponibles.
     */
    @Test
    public void lasReservasDeUnaMismaHabitacionDetectanLaSuperposicion() {
        Booking ocupada = new Booking("X1", "X", "H", "R", "C",
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 15), 2, 100,
                BookingStatus.CONFIRMADA);

        // Cruce completo: comparte tres noches.
        Booking cruce = new Booking("X2", "X", "H", "R", "C",
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 18), 2, 100,
                BookingStatus.CONFIRMADA);
        assertTrue("Dos reservas de la misma habitación que se cruzan deben detectarse",
                ocupada.seSuperponeCon(cruce));

        // Rotacion: una sale el 15 y la otra entra el 15. Esa noche es de
        // limpieza, no es un choque.
        Booking rotacion = new Booking("X3", "X", "H", "R", "C",
                LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 20), 2, 100,
                BookingStatus.CONFIRMADA);
        assertFalse("El día de rotación no debe contar como superposición",
                ocupada.seSuperponeCon(rotacion));

        // Otra habitacion del mismo hotel no choca.
        Booking otraHabitacion = new Booking("X4", "X", "H", "OTRA", "C",
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 18), 2, 100,
                BookingStatus.CONFIRMADA);
        assertFalse("Una reserva de otra habitación no debe bloquear",
                ocupada.seSuperponeCon(otraHabitacion));

        // Una reserva cancelada libera la habitacion.
        Booking cancelada = new Booking("X5", "X", "H", "R", "C",
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 18), 2, 100,
                BookingStatus.CANCELADA);
        assertFalse("Una reserva cancelada no debe bloquear la habitación",
                ocupada.seSuperponeCon(cancelada));
    }

    @Test
    public void hayAlMenosUnaHabitacionOcupadaParaQueElFiltroDeFechasSignifiqueAlgo() {
        LocalDate hoy = MockData.HOY;
        boolean algunaOcupada = false;
        for (Booking reserva : MockData.RESERVAS) {
            if (reserva.getEstado() == BookingStatus.CANCELADA) {
                continue;
            }
            // Una reserva que cubre hoy mismo.
            if (!reserva.getFechaEntrada().isAfter(hoy) && reserva.getFechaSalida().isAfter(hoy)) {
                algunaOcupada = true;
                break;
            }
        }
        assertTrue("Debería haber una estadía en curso: es la que habilita el chat y el checkout",
                algunaOcupada);
    }

    // ------------------------------------------------------------------
    //  Auxiliares de consulta
    // ------------------------------------------------------------------

    @Test
    public void lasCiudadesSalenOrdenadasYSinRepetir() {
        java.util.List<String> ciudades = MockData.ciudades();
        assertFalse("Sin hoteles no hay ciudades", ciudades.isEmpty());
        for (int i = 1; i < ciudades.size(); i++) {
            assertTrue("Las ciudades deberían venir ordenadas",
                    ciudades.get(i - 1).compareTo(ciudades.get(i)) < 0);
        }
    }

    @Test
    public void buscarUnIdentificadorQueNoExisteDevuelveNuloYNoFalla() {
        assertEquals(null, MockData.hotel("NO_EXISTE"));
        assertEquals(null, MockData.reserva("NO_EXISTE"));
        assertEquals(null, MockData.servicio("NO_EXISTE"));
        assertEquals(null, MockData.habitacion("NO_EXISTE"));
        assertEquals("Hotel", MockData.nombreHotel("NO_EXISTE"));
    }
}
