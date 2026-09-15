package org.iot.project.core;

import org.iot.project.data.mock.MockAccesoRepository;
import org.iot.project.data.mock.MockBookingRepository;
import org.iot.project.data.mock.MockChatRepository;
import org.iot.project.data.mock.MockGestionHotelRepository;
import org.iot.project.data.mock.MockHotelRepository;
import org.iot.project.data.mock.MockTaxiRepository;
import org.iot.project.data.mock.MockUserRepository;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.data.repository.HotelRepository;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.data.repository.UserRepository;

/**
 * Punto unico de acceso a los repositorios.
 *
 * <p>Es la pieza que permite que las pantallas dependan de una interfaz y no
 * de la implementacion: hoy devuelve los simulados, y el dia que exista un
 * backend de verdad solo cambia esta clase, no los ViewModel ni los Fragments.
 *
 * <p>Las instancias se crean una sola vez y se comparten, porque los
 * repositorios simulados guardan estado en memoria: si cada pantalla creara el
 * suyo, una reserva creada en el flujo de pago no apareceria en Mis Reservas.
 */
public final class ServiceLocator {

    private static final HotelRepository HOTELES = new MockHotelRepository();
    private static final BookingRepository RESERVAS = new MockBookingRepository();
    private static final TaxiRepository TAXIS = new MockTaxiRepository();
    private static final UserRepository USUARIOS = new MockUserRepository();
    private static final ChatRepository CHATS = new MockChatRepository();
    private static final AccesoRepository ACCESO = new MockAccesoRepository();
    private static final GestionHotelRepository GESTION = new MockGestionHotelRepository();

    private ServiceLocator() {
    }

    public static HotelRepository hoteles() {
        return HOTELES;
    }

    public static BookingRepository reservas() {
        return RESERVAS;
    }

    public static TaxiRepository taxis() {
        return TAXIS;
    }

    public static UserRepository usuarios() {
        return USUARIOS;
    }

    public static ChatRepository chats() {
        return CHATS;
    }

    public static AccesoRepository acceso() {
        return ACCESO;
    }

    /** Gestion del hotel: habitaciones, servicios, datos y reportes (§42 a §45). */
    public static GestionHotelRepository gestion() {
        return GESTION;
    }
}
