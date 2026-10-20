package org.iot.project.core;

import org.iot.project.data.firebase.FirebaseAccesoRepository;
import org.iot.project.data.firebase.FirebaseUserRepository;
import org.iot.project.data.firebase.FirebaseHotelRepository;
import org.iot.project.data.firebase.FirebaseSuperadminRepository;
import org.iot.project.data.firebase.FirebaseGestionHotelRepository;
import org.iot.project.data.firebase.FirebaseBookingRepository;
import org.iot.project.data.firebase.FirebaseTaxiRepository;
import org.iot.project.data.firebase.FirebaseChatRepository;
import org.iot.project.data.repository.AccesoRepository;
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.data.repository.HotelRepository;
import org.iot.project.data.repository.SuperadminRepository;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.data.repository.UserRepository;

/**
 * Punto unico de acceso a los repositorios.
 *
 * <p>Es la pieza que permite que las pantallas dependan de una interfaz y no
 * de la implementacion. Acceso, perfiles, reservas y gestión de hoteles usan
 * Firebase. Los tipos de servicio son un catálogo local compartido y las
 * conversaciones se guardan por reserva en Firestore.
 *
 * <p>Las instancias se comparten entre pantallas. Las reservas se consultan
 * desde Firestore y sobreviven al cierre de la aplicación.
 */
public final class ServiceLocator {

    private static final FirebaseHotelRepository HOTELES = new FirebaseHotelRepository();
    private static final BookingRepository RESERVAS = new FirebaseBookingRepository();
    private static final TaxiRepository TAXIS = new FirebaseTaxiRepository();
    private static final UserRepository USUARIOS = new FirebaseUserRepository();
    private static final ChatRepository CHATS = new FirebaseChatRepository();
    private static final AccesoRepository ACCESO = new FirebaseAccesoRepository();
    private static final GestionHotelRepository GESTION = new FirebaseGestionHotelRepository();
    private static final SuperadminRepository SUPERADMIN = new FirebaseSuperadminRepository(HOTELES);

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

    /** Gestion del hotel: los modulos pendientes informan el error sin usar datos simulados. */
    public static GestionHotelRepository gestion() {
        return GESTION;
    }

    /** Panel del superadministrador: cuentas, conductores, hoteles y bitacora (§47). */
    public static SuperadminRepository superadmin() {
        return SUPERADMIN;
    }
}
