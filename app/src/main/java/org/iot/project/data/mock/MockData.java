package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.R;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Card;
import org.iot.project.models.Charge;
import org.iot.project.models.Conversation;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.LogEntry;
import org.iot.project.models.Message;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Payment;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.Review;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.Service;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.User;
import org.iot.project.models.Vehicle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conjunto de datos simulados.
 *
 * <p>Vive fuera de Activities, Fragments y adaptadores (reglas 33-35): la
 * interfaz nunca construye datos, solo los pide a un repositorio.
 *
 * <p>Tres cosas estan puestas a proposito para que la aplicacion se pueda
 * evaluar de verdad:
 * <ul>
 *   <li>Hay hoteles con 0, con 4 y con 12 servicios, para que el estado vacio
 *       de la seccion de servicios sea real y no un caso hipotetico.</li>
 *   <li>Hay calificaciones en toda la escala 1-10, no solo en la parte alta.</li>
 *   <li>Hay una reserva en cada uno de los cinco estados, para poder recorrer
 *       Mis Reservas completo sin inventar nada.</li>
 * </ul>
 *
 * <p>Las fotografias apuntan a un servicio remoto por palabra clave. Si no
 * responde, Glide cae en su marcador y la pantalla sigue siendo correcta.
 */
public final class MockData {

    // ==================================================================
    //  Catalogos de identificadores
    // ==================================================================

    public static final String CIUDAD_LIMA = "Lima";
    public static final String CIUDAD_CUSCO = "Cusco";
    public static final String CIUDAD_AREQUIPA = "Arequipa";
    public static final String CIUDAD_TRUJILLO = "Trujillo";
    public static final String CIUDAD_PUNO = "Puno";

    /** Servicios del catalogo global (§13, §17). El administrador solo elige de aqui. */
    public static final List<Service> SERVICIOS;

    public static final List<Hotel> HOTELES;
    public static final List<User> USUARIOS;
    public static final List<Driver> CONDUCTORES;
    public static final List<Booking> RESERVAS;
    public static final List<Conversation> CONVERSACIONES;
    public static final List<AppNotification> NOTIFICACIONES;
    public static final List<TaxiService> TAXIS;
    public static final List<Review> RESENAS;
    public static final List<LogEntry> BITACORA;

    /**
     * Fecha de referencia de todos los datos relativos.
     *
     * <p>Se calcula al arrancar en lugar de fijarse en el codigo: con una fecha
     * escrita a mano, la reserva "activa" quedaria en el pasado en cuanto
     * pasara una semana y el conjunto dejaria de tener sentido.
     */
    public static final LocalDate HOY = LocalDate.now();

    /**
     * Contador para los identificadores de lo que se crea en tiempo de
     * ejecucion: reservas nuevas, cargos, notificaciones.
     *
     * <p>Se lleva por separado y no se deriva del tamano de las listas porque
     * una baja —una conversacion que se borra, un hotel que se retira— haria
     * coincidir el siguiente identificador con uno que ya existe.
     */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    /** Identificador unico para un elemento creado en tiempo de ejecucion. */
    public static String idNuevo(String prefijo) {
        return prefijo + "-" + SECUENCIA.getAndIncrement();
    }

    /**
     * Anade una notificacion al centro de notificaciones (RF-053, §39).
     *
     * <p>Es la unica puerta de entrada: las notificaciones nacen de acciones
     * —un cobro, un mensaje, una salida— y quien las provoca las deja aqui para
     * que el cliente las vea la proxima vez que abra la aplicacion. Al no haber
     * backend no hay a quien empujarlas; esta lista es lo que hay.
     *
     * <p>La mas reciente queda la primera porque asi lo espera
     * {@code MockUserRepository.notificaciones}, que ordena por marca de tiempo.
     */
    public static void notificar(@NonNull AppNotification.Tipo tipo, @NonNull String titulo,
                                 @NonNull String mensaje) {
        NOTIFICACIONES.add(new AppNotification(idNuevo("N"), tipo, titulo, mensaje,
                LocalDateTime.now()));
    }

    static {
        SERVICIOS = Collections.unmodifiableList(crearServicios());
        HOTELES = Collections.unmodifiableList(crearHoteles());
        // Mutable: el registro da de alta clientes (RF-001) y tienen que poder
        // entrar con la misma cuenta que acaban de crear.
        USUARIOS = new ArrayList<>(crearUsuarios());
        CONDUCTORES = Collections.unmodifiableList(crearConductores());
        RESERVAS = new ArrayList<>(crearReservas());
        CONVERSACIONES = new ArrayList<>(crearConversaciones());
        NOTIFICACIONES = new ArrayList<>(crearNotificaciones());
        TAXIS = new ArrayList<>(crearTaxis());
        // Mutable: las resenas que envia el cliente se suman a las del hotel
        // y tienen que aparecer en su ficha.
        RESENAS = new ArrayList<>(crearResenas());
        BITACORA = new ArrayList<>(crearBitacora());
    }

    private MockData() {
    }

    // ==================================================================
    //  Servicios
    // ==================================================================

    private static List<Service> crearServicios() {
        return Arrays.asList(
                new Service("WIFI", "Wi-Fi de alta velocidad",
                        "Conexión inalámbrica en todas las áreas del hotel", R.drawable.ic_service_wifi),
                new Service("DESAYUNO", "Desayuno buffet",
                        "Desayuno completo incluido en la tarifa", R.drawable.ic_service_restaurant),
                new Service("RESTAURANTE", "Restaurante",
                        "Cocina local e internacional a la carta", R.drawable.ic_service_restaurant),
                new Service("BAR", "Bar y lounge",
                        "Coctelería y aperitivos por la tarde", R.drawable.ic_service_restaurant),
                new Service("PISCINA", "Piscina",
                        "Piscina temperada con zona de descanso", R.drawable.ic_service_pool),
                new Service("GIMNASIO", "Gimnasio",
                        "Equipo cardiovascular y de fuerza", R.drawable.ic_service_fitness),
                new Service("SPA", "Spa y sauna",
                        "Circuito de agua y masajes con cita previa", R.drawable.ic_service_fitness),
                new Service("ESTACIONAMIENTO", "Estacionamiento",
                        "Cochera cubierta dentro del establecimiento", R.drawable.ic_service_parking),
                new Service("AIRE_ACONDICIONADO", "Aire acondicionado",
                        "Climatización individual por habitación", R.drawable.ic_service_ac),
                new Service("LAVANDERIA", "Lavandería",
                        "Servicio de lavado y planchado por encargo", R.drawable.ic_service_laundry),
                new Service("TRASLADO_AEROPUERTO", "Traslado al aeropuerto",
                        "Recojo y traslado coordinado con recepción", R.drawable.ic_service_shuttle),
                new Service("MASCOTAS", "Admite mascotas",
                        "Hasta dos mascotas pequeñas por habitación", R.drawable.ic_service_pets),
                new Service("SALA_REUNIONES", "Sala de reuniones",
                        "Espacio equipado para eventos de trabajo", R.drawable.ic_service_business),
                new Service("RECEPCION_24H", "Recepción 24 horas",
                        "Atención permanente durante toda la estadía", R.drawable.ic_service_reception));
    }

    // ==================================================================
    //  Hoteles
    // ==================================================================

    private static List<Hotel> crearHoteles() {
        List<Hotel> hoteles = new ArrayList<>();

        // --- H1: el hotel completo, 12 servicios, el mejor calificado ---
        Hotel h1 = new Hotel("H1", "Casa del Mar", "Miraflores", CIUDAD_LIMA);
        h1.setDireccion("Av. Malecón Cisneros 1240");
        h1.setDescripcion("Casona frente al malecón con vistas abiertas al océano. "
                + "Ambientes amplios, terraza con piscina y un restaurante que trabaja "
                + "producto de mercado. A diez minutos a pie del Parque Kennedy.");
        h1.setRating(9.2f);
        h1.setNumReviews(284);
        h1.setPrecioDesde(480);
        h1.setMontoMinimoTaxi(1200);
        h1.setUbicacion(-12.1219, -77.0297);
        conFotos(h1, fotos("hotel,luxury,ocean", 101, 6));
        conLugares(h1, Arrays.asList(
                new NearbyPlace("Parque Kennedy", "Parque", 0.8),
                new NearbyPlace("Playa Waikiki", "Playa", 1.2),
                new NearbyPlace("Larcomar", "Centro comercial", 1.5)));
        h1.addServicio(HotelService.incluido("H1", "WIFI"));
        h1.addServicio(HotelService.incluido("H1", "DESAYUNO"));
        h1.addServicio(HotelService.incluido("H1", "PISCINA"));
        h1.addServicio(HotelService.incluido("H1", "GIMNASIO"));
        h1.addServicio(HotelService.incluido("H1", "AIRE_ACONDICIONADO"));
        h1.addServicio(HotelService.incluido("H1", "RECEPCION_24H"));
        h1.addServicio(HotelService.incluido("H1", "RESTAURANTE"));
        h1.addServicio(HotelService.incluido("H1", "BAR"));
        h1.addServicio(HotelService.incluido("H1", "ESTACIONAMIENTO"));
        h1.addServicio(HotelService.adicional("H1", "SPA", 120));
        h1.addServicio(HotelService.adicional("H1", "LAVANDERIA", 35));
        h1.addServicio(HotelService.adicional("H1", "TRASLADO_AEROPUERTO", 80));
        conHabitaciones(h1, Arrays.asList(
                habitacion("H1-R1", "H1", "Doble superior con vista al mar", 480, 2, 1, 32, 6, "601", 4, "room,hotel,bedroom", 201),
                habitacion("H1-R2", "H1", "Suite familiar", 720, 3, 2, 48, 6, "602", 4, "suite,hotel,room", 211),
                habitacion("H1-R3", "H1", "Individual ejecutiva", 320, 1, 0, 22, 5, "505", 3, "hotel,bedroom,simple", 221)));
        hoteles.add(h1);

        // --- H2: 6 servicios, bien calificado ---
        Hotel h2 = new Hotel("H2", "Barranco Art Hotel", "Barranco", CIUDAD_LIMA);
        h2.setDireccion("Jr. Unión 185");
        h2.setDescripcion("Hotel boutique en una casona republicana restaurada, a dos "
                + "cuadras del Puente de los Suspiros. Cada piso está dedicado a un "
                + "artista distinto y las paredes exhiben obra original.");
        h2.setRating(8.4f);
        h2.setNumReviews(167);
        h2.setPrecioDesde(310);
        h2.setMontoMinimoTaxi(900);
        h2.setUbicacion(-12.1478, -77.0207);
        conFotos(h2, fotos("boutique,hotel,courtyard", 301, 5));
        conLugares(h2, Arrays.asList(
                new NearbyPlace("Puente de los Suspiros", "Mirador", 0.3),
                new NearbyPlace("Museo de Arte Contemporáneo", "Museo", 0.9)));
        h2.addServicio(HotelService.incluido("H2", "WIFI"));
        h2.addServicio(HotelService.incluido("H2", "DESAYUNO"));
        h2.addServicio(HotelService.incluido("H2", "AIRE_ACONDICIONADO"));
        h2.addServicio(HotelService.incluido("H2", "RECEPCION_24H"));
        h2.addServicio(HotelService.adicional("H2", "LAVANDERIA", 30));
        h2.addServicio(HotelService.adicional("H2", "TRASLADO_AEROPUERTO", 70));
        conHabitaciones(h2, Arrays.asList(
                habitacion("H2-R1", "H2", "Doble clásica", 310, 2, 1, 26, 2, "201", 3, "boutique,hotel,room", 401),
                habitacion("H2-R2", "H2", "Loft con terraza", 430, 2, 0, 38, 3, "301", 3, "loft,terrace,hotel", 411)));
        hoteles.add(h2);

        // --- H3: 5 servicios, nota media ---
        Hotel h3 = new Hotel("H3", "San Isidro Business", "San Isidro", CIUDAD_LIMA);
        h3.setDireccion("Calle Los Libertadores 320");
        h3.setDescripcion("Orientado a viaje de trabajo: salas de reunión, escritorio "
                + "amplio en cada habitación y desayuno desde las cinco de la mañana.");
        h3.setRating(7.9f);
        h3.setNumReviews(96);
        h3.setPrecioDesde(420);
        h3.setMontoMinimoTaxi(1100);
        h3.setUbicacion(-12.0972, -77.0365);
        conFotos(h3, fotos("business,hotel,modern", 501, 4));
        conLugares(h3, Arrays.asList(
                new NearbyPlace("Centro Financiero", "Zona de oficinas", 0.4),
                new NearbyPlace("Parque El Olivar", "Parque", 1.1)));
        h3.addServicio(HotelService.incluido("H3", "WIFI"));
        h3.addServicio(HotelService.incluido("H3", "DESAYUNO"));
        h3.addServicio(HotelService.incluido("H3", "AIRE_ACONDICIONADO"));
        h3.addServicio(HotelService.incluido("H3", "SALA_REUNIONES"));
        h3.addServicio(HotelService.adicional("H3", "ESTACIONAMIENTO", 25));
        conHabitaciones(h3, Arrays.asList(
                habitacion("H3-R1", "H3", "Superior ejecutiva", 420, 2, 0, 30, 8, "801", 4, "business,hotel,room", 601),
                habitacion("H3-R2", "H3", "Doble estándar", 340, 2, 1, 24, 4, "402", 4, "hotel,room,standard", 611)));
        hoteles.add(h3);

        // --- H4: el mejor calificado del pais, 8 servicios ---
        Hotel h4 = new Hotel("H4", "Posada Cusco Centro", "Centro Histórico", CIUDAD_CUSCO);
        h4.setDireccion("Calle Plateros 145");
        h4.setDescripcion("Casa de piedra a media cuadra de la Plaza de Armas. Patio "
                + "interior con jardín, habitaciones con calefacción y mate de coca de "
                + "bienvenida. Desayuno andino servido desde las cinco.");
        h4.setRating(9.6f);
        h4.setNumReviews(412);
        h4.setPrecioDesde(260);
        h4.setMontoMinimoTaxi(700);
        h4.setUbicacion(-13.5165, -71.9787);
        conFotos(h4, fotos("cusco,colonial,hotel", 701, 6));
        conLugares(h4, Arrays.asList(
                new NearbyPlace("Plaza de Armas", "Plaza", 0.1),
                new NearbyPlace("Qorikancha", "Sitio arqueológico", 0.7),
                new NearbyPlace("Mercado San Pedro", "Mercado", 0.9)));
        h4.addServicio(HotelService.incluido("H4", "WIFI"));
        h4.addServicio(HotelService.incluido("H4", "DESAYUNO"));
        h4.addServicio(HotelService.incluido("H4", "RECEPCION_24H"));
        h4.addServicio(HotelService.incluido("H4", "AIRE_ACONDICIONADO"));
        h4.addServicio(HotelService.incluido("H4", "RESTAURANTE"));
        h4.addServicio(HotelService.adicional("H4", "LAVANDERIA", 25));
        h4.addServicio(HotelService.adicional("H4", "TRASLADO_AEROPUERTO", 60));
        h4.addServicio(HotelService.adicional("H4", "SPA", 90));
        conHabitaciones(h4, Arrays.asList(
                habitacion("H4-R1", "H4", "Doble con vista al patio", 260, 2, 1, 28, 1, "102", 3, "cusco,hotel,patio", 801),
                habitacion("H4-R2", "H4", "Triple familiar", 380, 3, 2, 40, 2, "203", 3, "hotel,family,room", 811),
                habitacion("H4-R3", "H4", "Simple con balcón", 190, 1, 0, 18, 3, "302", 3, "hotel,balcony,room", 821)));
        hoteles.add(h4);

        // --- H5: 10 servicios, caro ---
        Hotel h5 = new Hotel("H5", "Valle Sagrado Lodge", "Urubamba", CIUDAD_CUSCO);
        h5.setDireccion("Km 62 Carretera Urubamba-Ollantaytambo");
        h5.setDescripcion("Lodge de montaña rodeado de eucaliptos, con huerto propio y "
                + "vista a los picos del valle. Pensado para quedarse varios días.");
        h5.setRating(9.0f);
        h5.setNumReviews(158);
        h5.setPrecioDesde(540);
        h5.setMontoMinimoTaxi(1500);
        h5.setUbicacion(-13.3058, -72.1158);
        conFotos(h5, fotos("mountain,lodge,valley", 901, 5));
        conLugares(h5, Arrays.asList(
                new NearbyPlace("Mercado de Urubamba", "Mercado", 2.4),
                new NearbyPlace("Salineras de Maras", "Sitio arqueológico", 18.0)));
        h5.addServicio(HotelService.incluido("H5", "WIFI"));
        h5.addServicio(HotelService.incluido("H5", "DESAYUNO"));
        h5.addServicio(HotelService.incluido("H5", "RESTAURANTE"));
        h5.addServicio(HotelService.incluido("H5", "BAR"));
        h5.addServicio(HotelService.incluido("H5", "PISCINA"));
        h5.addServicio(HotelService.incluido("H5", "SPA"));
        h5.addServicio(HotelService.incluido("H5", "GIMNASIO"));
        h5.addServicio(HotelService.incluido("H5", "ESTACIONAMIENTO"));
        h5.addServicio(HotelService.adicional("H5", "LAVANDERIA", 30));
        h5.addServicio(HotelService.adicional("H5", "TRASLADO_AEROPUERTO", 150));
        conHabitaciones(h5, Arrays.asList(
                habitacion("H5-R1", "H5", "Cabaña con chimenea", 540, 2, 2, 45, 1, "C1", 4, "cabin,lodge,fireplace", 1001),
                habitacion("H5-R2", "H5", "Suite valle", 780, 3, 1, 55, 2, "C2", 4, "suite,mountain,lodge", 1011)));
        hoteles.add(h5);

        // --- H6: 4 servicios, precio bajo ---
        Hotel h6 = new Hotel("H6", "Arequipa Plaza", "Centro", CIUDAD_AREQUIPA);
        h6.setDireccion("Calle Mercaderes 218");
        h6.setDescripcion("Edificio de sillar con balcón a la Plaza de Armas. Sencillo, "
                + "limpio y bien ubicado, con la catedral a la vista desde los balcones.");
        h6.setRating(8.1f);
        h6.setNumReviews(203);
        h6.setPrecioDesde(230);
        h6.setMontoMinimoTaxi(600);
        h6.setUbicacion(-16.3988, -71.5369);
        conFotos(h6, fotos("arequipa,colonial,plaza", 1101, 4));
        conLugares(h6, Arrays.asList(
                new NearbyPlace("Plaza de Armas", "Plaza", 0.1),
                new NearbyPlace("Monasterio de Santa Catalina", "Monasterio", 0.6)));
        h6.addServicio(HotelService.incluido("H6", "WIFI"));
        h6.addServicio(HotelService.incluido("H6", "DESAYUNO"));
        h6.addServicio(HotelService.incluido("H6", "RECEPCION_24H"));
        h6.addServicio(HotelService.adicional("H6", "LAVANDERIA", 20));
        conHabitaciones(h6, Arrays.asList(
                habitacion("H6-R1", "H6", "Doble con balcón", 230, 2, 1, 24, 2, "202", 3, "hotel,balcony,colonial", 1201),
                habitacion("H6-R2", "H6", "Simple interior", 160, 1, 0, 16, 1, "105", 2, "hotel,simple,room", 1211)));
        hoteles.add(h6);

        // --- H7: sin servicios, rating bajo. Para el estado vacio de §23 ---
        Hotel h7 = new Hotel("H7", "Hostal Surco", "Santiago de Surco", CIUDAD_LIMA);
        h7.setDireccion("Av. Caminos del Inca 1450");
        h7.setDescripcion("Alojamiento básico de paso. Habitaciones con baño propio y "
                + "limpieza diaria. No cuenta con servicios adicionales.");
        h7.setRating(5.4f);
        h7.setNumReviews(38);
        h7.setPrecioDesde(120);
        h7.setMontoMinimoTaxi(500);
        h7.setUbicacion(-12.1455, -76.9989);
        conFotos(h7, fotos("hostel,basic,room", 1301, 4));
        conLugares(h7, new NearbyPlace("Parque de la Amistad", "Parque", 1.4));
        // Sin ningun servicio asociado: la seccion de servicios debe mostrar su estado vacio.
        conHabitaciones(h7, Arrays.asList(
                habitacion("H7-R1", "H7", "Simple con baño propio", 120, 1, 0, 14, 1, "101", 2, "hostel,room,basic", 1401),
                habitacion("H7-R2", "H7", "Doble con baño propio", 180, 2, 0, 18, 1, "102", 2, "hostel,room,double", 1411)));
        hoteles.add(h7);

        // --- H8: rating bajo, para que la escala 1-10 se vea completa ---
        Hotel h8 = new Hotel("H8", "Trujillo Colonial", "Centro Histórico", CIUDAD_TRUJILLO);
        h8.setDireccion("Jr. Pizarro 543");
        h8.setDescripcion("Casona antigua con mucho carácter pero mantenimiento "
                + "irregular. Bien ubicada para recorrer el centro a pie.");
        h8.setRating(4.2f);
        h8.setNumReviews(57);
        h8.setPrecioDesde(110);
        h8.setMontoMinimoTaxi(450);
        h8.setUbicacion(-8.1118, -79.0287);
        conFotos(h8, fotos("trujillo,colonial,street", 1501, 4));
        conLugares(h8, new NearbyPlace("Plaza de Armas", "Plaza", 0.2));
        h8.addServicio(HotelService.incluido("H8", "WIFI"));
        conHabitaciones(h8, 
                habitacion("H8-R1", "H8", "Doble estándar", 110, 2, 1, 20, 1, "204", 2, "hotel,room,old", 1601));
        hoteles.add(h8);

        // --- H9: 7 servicios ---
        Hotel h9 = new Hotel("H9", "Puno Lago", "Puno", CIUDAD_PUNO);
        h9.setDireccion("Jr. Lambayeque 180");
        h9.setDescripcion("Habitaciones orientadas al Titicaca, con calefacción reforzada "
                + "para las noches de altura. Desayuno servido antes de las excursiones "
                + "a las islas.");
        h9.setRating(8.8f);
        h9.setNumReviews(174);
        h9.setPrecioDesde(290);
        h9.setMontoMinimoTaxi(750);
        h9.setUbicacion(-15.8402, -70.0219);
        conFotos(h9, fotos("puno,titicaca,lake", 1701, 5));
        conLugares(h9, Arrays.asList(
                new NearbyPlace("Puerto de Puno", "Embarcadero", 0.9),
                new NearbyPlace("Catedral de Puno", "Iglesia", 0.3)));
        h9.addServicio(HotelService.incluido("H9", "WIFI"));
        h9.addServicio(HotelService.incluido("H9", "DESAYUNO"));
        h9.addServicio(HotelService.incluido("H9", "RESTAURANTE"));
        h9.addServicio(HotelService.incluido("H9", "RECEPCION_24H"));
        h9.addServicio(HotelService.incluido("H9", "AIRE_ACONDICIONADO"));
        h9.addServicio(HotelService.adicional("H9", "LAVANDERIA", 22));
        h9.addServicio(HotelService.adicional("H9", "TRASLADO_AEROPUERTO", 65));
        conHabitaciones(h9, Arrays.asList(
                habitacion("H9-R1", "H9", "Doble vista al lago", 290, 2, 1, 26, 3, "305", 3, "lake,hotel,room", 1801),
                habitacion("H9-R2", "H9", "Triple vista al lago", 410, 3, 1, 36, 3, "306", 3, "lake,hotel,family", 1811)));
        hoteles.add(h9);

        // --- H10: 2 servicios, el mas economico de Cusco ---
        Hotel h10 = new Hotel("H10", "Cusco Económico", "Wanchaq", CIUDAD_CUSCO);
        h10.setDireccion("Av. de la Cultura 1120");
        h10.setDescripcion("Opción económica a quince minutos del centro histórico, "
                + "con transporte público frecuente en la puerta.");
        h10.setRating(6.8f);
        h10.setNumReviews(64);
        h10.setPrecioDesde(95);
        h10.setMontoMinimoTaxi(400);
        h10.setUbicacion(-13.5319, -71.9675);
        conFotos(h10, fotos("cusco,budget,hostel", 1901, 4));
        conLugares(h10, new NearbyPlace("Mercado de Wanchaq", "Mercado", 0.5));
        h10.addServicio(HotelService.incluido("H10", "WIFI"));
        h10.addServicio(HotelService.adicional("H10", "DESAYUNO", 15));
        conHabitaciones(h10, 
                habitacion("H10-R1", "H10", "Doble económica", 95, 2, 0, 16, 1, "110", 2, "hostel,budget,room", 2001));
        hoteles.add(h10);

        return hoteles;
    }

    // ==================================================================
    //  Usuarios
    // ==================================================================

    private static List<User> crearUsuarios() {
        User cliente = new User("U1", "Lucía", "Quispe Ramos", Role.CLIENTE);
        cliente.setEmail("lucia.quispe@correo.pe");
        cliente.setTelefono("+51 987 654 321");
        cliente.setDireccion("Av. Brasil 2380, Jesús María, Lima");
        cliente.withDocumento("DNI", "72849153").withNacimiento("14/03/1994");
        cliente.withFoto("https://loremflickr.com/200/200/portrait,woman?lock=9001");
        cliente.addTarjeta(new Card("C1", "Visa", "4821", "Lucía Quispe Ramos", "09/28"));
        cliente.addTarjeta(new Card("C2", "Mastercard", "7135", "Lucía Quispe Ramos", "02/27"));

        // El administrador de H1 tiene acceso a la operacion (§42 a §45)
        User admin = new User("U2", "Martín", "Rojas Delgado", Role.ADMIN_HOTEL);
        admin.setEmail("m.rojas@casadelmar.pe");
        admin.setTelefono("+51 956 112 233");
        admin.setDireccion("Av. Malecón Cisneros 1240, Miraflores, Lima");
        admin.withDocumento("DNI", "40918273");
        admin.withFoto("https://loremflickr.com/200/200/portrait,man?lock=9002");

        User superadmin = new User("U3", "Ana", "Ferreyra Campos", Role.SUPERADMIN);
        superadmin.setEmail("a.ferreyra@estadia.pe");
        superadmin.setTelefono("+51 913 445 566");
        superadmin.withDocumento("DNI", "09827364");
        superadmin.withFoto("https://loremflickr.com/200/200/portrait,professional?lock=9003");

        // Segundo cliente, para que el administrador tenga mas de una ficha que
        // mostrar y para que la reserva B6 —que existe desde el principio para
        // que H1-R2 aparezca ocupada en el buscador— apunte a alguien real. Una
        // reserva cuyo cliente no existe deja la fila del panel sin nombre.
        User huesped = new User("U9", "Diego", "Salas Pinto", Role.CLIENTE);
        huesped.setEmail("diego.salas@correo.pe");
        huesped.setTelefono("+51 942 118 706");
        huesped.setDireccion("Calle Cantuarias 175, Miraflores, Lima");
        huesped.withDocumento("DNI", "45120398").withNacimiento("02/11/1988");
        huesped.withFoto("https://loremflickr.com/200/200/portrait,man?lock=9004");
        huesped.addTarjeta(new Card("C3", "Visa", "3390", "Diego Salas Pinto", "11/27"));

        return Arrays.asList(cliente, admin, superadmin, huesped);
    }

    private static List<Driver> crearConductores() {
        // D1 y D2 ya pasaron por la aprobacion del Superadmin y pueden prestar
        // servicios (RF-077). Hay que decirlo explicitamente: habilitado nace en
        // falso, porque un conductor recien dado de alta no esta aprobado.
        Driver d1 = new Driver("D1", "Julio", "Mendoza Ayala");
        d1.withDocumento("DNI", "45120987")
                .withContacto("j.mendoza@taxi.pe", "+51 998 776 655", "Av. Los Álamos 340, San Juan de Lurigancho")
                .withFoto("https://loremflickr.com/200/200/taxi,driver?lock=9101")
                .withRating(9.1f, 842)
                .withVehiculo(new Vehicle("V1K-482", "Toyota", "Corolla", "Blanco")
                        .withFoto("https://loremflickr.com/400/250/taxi,car?lock=9201"));
        d1.setHabilitado(true);

        Driver d2 = new Driver("D2", "Rosa", "Palomino Vega");
        d2.withDocumento("DNI", "43820116")
                .withContacto("r.palomino@taxi.pe", "+51 977 223 344", "Calle Los Nogales 120, Surco")
                .withFoto("https://loremflickr.com/200/200/taxi,driver,woman?lock=9102")
                .withRating(8.6f, 513)
                .withVehiculo(new Vehicle("A2B-917", "Hyundai", "Accent", "Gris plata")
                        .withFoto("https://loremflickr.com/400/250/taxi,vehicle?lock=9202"));
        d2.setHabilitado(true);

        // Conductor deshabilitado: sirve para RF-077, no debe poder recibir servicios
        Driver d3 = new Driver("D3", "Pedro", "Ccahuana Loayza");
        d3.withDocumento("DNI", "41278390")
                .withContacto("p.ccahuana@taxi.pe", "+51 966 887 221", "Av. Grau 890, Cusco")
                .withRating(6.2f, 118)
                .withVehiculo(new Vehicle("X3Y-204", "Kia", "Rio", "Azul")
                        .withFoto("https://loremflickr.com/400/250/taxi,cab?lock=9203"));
        d3.setHabilitado(false);

        return Arrays.asList(d1, d2, d3);
    }

    // ==================================================================
    //  Reservas: una por cada estado, para poder recorrer la pantalla entera
    // ==================================================================

    private static List<Booking> crearReservas() {
        List<Booking> reservas = new ArrayList<>();

        // B1 — ACTIVA ahora mismo: habilita chat (RF-065) y checkout (RF-056)
        Booking b1 = new Booking("B1", "EST-2026-0418", "H1", "H1-R1", "U1",
                HOY.minusDays(2), HOY.plusDays(2), 2, 480, BookingStatus.ACTIVA);
        b1.setTarjeta(tarjetaCliente());
        b1.addServicioAdicional(HotelService.adicional("H1", "SPA", 120));
        b1.addCargo(new Charge(45, "Consumo de minibar",
                "Dos botellas de agua y una bolsa de frutos secos, cargadas el "
                        + "14/09 por la mañana."));
        // Un segundo cargo en la misma estadia, para que la pantalla de cobros
        // (§44, RF-051 a RF-054) tenga una estadia con mas de una fila: con una
        // sola, la agrupacion por reserva de RF-054 no se llega a ver.
        b1.addCargo(new Charge(38, "Servicio a la habitación",
                "Cena de dos personas, cargada el 15/09 por la noche."));
        reservas.add(b1);

        // B2 — CONFIRMADA a futuro: se puede cancelar (RC-013)
        Booking b2 = new Booking("B2", "EST-2026-0551", "H4", "H4-R1", "U1",
                HOY.plusDays(20), HOY.plusDays(25), 2, 260, BookingStatus.CONFIRMADA);
        b2.setTarjeta(tarjetaCliente());
        b2.addServicioAdicional(HotelService.adicional("H4", "TRASLADO_AEROPUERTO", 60));
        reservas.add(b2);

        // B3 — FINALIZADA: se puede valorar (§22)
        Booking b3 = new Booking("B3", "EST-2026-0192", "H2", "H2-R1", "U1",
                HOY.minusDays(40), HOY.minusDays(36), 2, 310, BookingStatus.FINALIZADA);
        b3.setTarjeta(tarjetaCliente());
        reservas.add(b3);

        // B4 — CANCELADA: libera la habitacion (RF-032)
        Booking b4 = new Booking("B4", "EST-2026-0087", "H3", "H3-R1", "U1",
                HOY.minusDays(70), HOY.minusDays(67), 1, 420, BookingStatus.CANCELADA);
        reservas.add(b4);

        // B5 — PENDIENTE: falta pagar, el flujo puede retomarse
        Booking b5 = new Booking("B5", "EST-2026-0623", "H9", "H9-R1", "U1",
                HOY.plusDays(45), HOY.plusDays(49), 2, 290, BookingStatus.PENDIENTE);
        reservas.add(b5);

        // B6 — de otro cliente, para que H1-R2 aparezca ocupada en el buscador
        Booking b6 = new Booking("B6", "EST-2026-0420", "H1", "H1-R2", "U9",
                HOY.minusDays(1), HOY.plusDays(4), 3, 720, BookingStatus.ACTIVA);
        b6.setTarjeta(tarjetaHuesped());
        // Dos estadias activas con cobros y no una: con una sola, la lista de
        // cobros del administrador tendria una tarjeta y no se distinguiria una
        // pantalla que agrupa de una que solo enseña la primera reserva.
        b6.addCargo(new Charge(28, "Uso de la lavandería",
                "Tres prendas entregadas el 15/09 por la mañana."));
        reservas.add(b6);

        // B7 — cierra hoy en H1. Sin ella, la portada del administrador no
        // tendria ni un checkout que enseñar y la seccion que §43 pide como
        // principal saldria vacia en todas las demostraciones.
        Booking b7 = new Booking("B7", "EST-2026-0402", "H1", "H1-R3", "U9",
                HOY.minusDays(3), HOY, 1, 320, BookingStatus.ACTIVA);
        b7.setTarjeta(tarjetaHuesped());
        b7.addServicioAdicional(HotelService.adicional("H1", "LAVANDERIA", 35));
        b7.addCargo(new Charge(32, "Desayuno adicional",
                "Un desayuno extra para un acompañante, el 15/09."));
        reservas.add(b7);

        return reservas;
    }

    /**
     * Devuelve la tarjeta del cliente. Se construye bajo demanda en lugar de
     * leerla de un campo estatico: el bloque {@code static} que arma las
     * reservas corre antes que cualquier campo declarado mas abajo, asi que un
     * campo daria {@code null} y las reservas quedarian sin tarjeta.
     */
    private static Card tarjetaCliente() {
        return new Card("C1", "Visa", "4821", "Lucía Quispe Ramos", "09/28");
    }

    /** La del segundo cliente, por la misma razon que la anterior. */
    private static Card tarjetaHuesped() {
        return new Card("C3", "Visa", "3390", "Diego Salas Pinto", "11/27");
    }

    // ==================================================================
    //  Conversaciones (solo con reserva activa, RF-065)
    // ==================================================================

    private static List<Conversation> crearConversaciones() {
        Conversation c1 = new Conversation("CV1", "B1", "U1", "H1");
        c1.addMensaje(mensaje("M1", "CV1",
                "Buenas tardes, ¿el spa tiene disponibilidad mañana por la mañana?",
                LocalDateTime.of(HOY.minusDays(1), LocalTime.of(16, 20)),
                Message.Autor.CLIENTE, true));
        c1.addMensaje(mensaje("M2", "CV1",
                "Buenas tardes, Lucía. Sí, tenemos turno a las 10:00 y a las 11:30. "
                        + "¿Le reservo alguno?",
                LocalDateTime.of(HOY.minusDays(1), LocalTime.of(16, 34)),
                Message.Autor.HOTEL, true));
        c1.addMensaje(mensaje("M3", "CV1",
                "Perfecto, a las 10:00 por favor.",
                LocalDateTime.of(HOY.minusDays(1), LocalTime.of(16, 41)),
                Message.Autor.CLIENTE, true));
        c1.addMensaje(mensaje("M4", "CV1",
                "Listo, queda reservado para mañana a las 10:00. Cualquier cosa "
                        + "nos avisa por aquí.",
                LocalDateTime.of(HOY, LocalTime.of(9, 5)),
                // Sin leer: el cliente todavia no lo ha abierto. Para el hotel
                // es un mensaje propio y no cuenta como pendiente.
                Message.Autor.HOTEL, false));

        Conversation c2 = new Conversation("CV2", "B2", "U1", "H4");
        c2.addMensaje(mensaje("M5", "CV2",
                "¿El traslado desde el aeropuerto incluye espera si el vuelo se retrasa?",
                LocalDateTime.of(HOY.minusDays(3), LocalTime.of(11, 12)),
                Message.Autor.CLIENTE, true));
        c2.addMensaje(mensaje("M6", "CV2",
                "Sí, el conductor espera hasta 45 minutos sin costo adicional.",
                LocalDateTime.of(HOY.minusDays(3), LocalTime.of(11, 48)),
                Message.Autor.HOTEL, true));

        // Tercera conversacion, y la unica con algo pendiente. Hace falta por
        // dos motivos: sin una fila sin leer, el contador de RF-068 nunca se
        // veria encendido, y con una sola conversacion la bandeja no
        // distinguiria una lista ordenada por fecha de una lista de un
        // elemento. Es de otro huesped (U9) porque es la suya la otra estadia
        // activa del hotel (B6), y de otro hotel no valdria: la bandeja filtra
        // por el hotel administrado.
        Conversation c3 = new Conversation("CV3", "B6", "U9", "H1");
        c3.addMensaje(mensaje("M7", "CV3",
                "Buenos días, llegamos al aeropuerto a las 22:00. ¿El check-in "
                        + "cierra a alguna hora o podemos registrarnos a esa hora?",
                LocalDateTime.of(HOY, LocalTime.of(10, 20)),
                // Sin leer, y ademas el ultimo: es el caso que la bandeja tiene
                // que destacar, porque es el unico que pide una respuesta.
                Message.Autor.CLIENTE, false));

        return Arrays.asList(c1, c2, c3);
    }

    // ==================================================================
    //  Notificaciones
    // ==================================================================

    private static List<AppNotification> crearNotificaciones() {
        return Arrays.asList(
                new AppNotification("N1", AppNotification.Tipo.MENSAJE,
                        "Nuevo mensaje de Casa del Mar",
                        "Listo, queda reservado para mañana a las 10:00.",
                        LocalDateTime.of(HOY, LocalTime.of(9, 5))),
                new AppNotification("N2", AppNotification.Tipo.TAXI,
                        "Tu conductor está en camino",
                        "Julio Mendoza llegará en aproximadamente 6 minutos.",
                        LocalDateTime.of(HOY, LocalTime.of(8, 40))),
                new AppNotification("N3", AppNotification.Tipo.CARGO_ADICIONAL,
                        "Se registró un consumo",
                        "S/ 45 por consumo de minibar en tu estadía actual.",
                        LocalDateTime.of(HOY.minusDays(1), LocalTime.of(20, 15))),
                new AppNotification("N4", AppNotification.Tipo.PAGO,
                        "Pago confirmado",
                        "Registramos el pago de tu reserva en Posada Cusco Centro.",
                        LocalDateTime.of(HOY.minusDays(5), LocalTime.of(13, 2))),
                new AppNotification("N5", AppNotification.Tipo.CHECKOUT,
                        "Checkout disponible",
                        "Ya puedes registrar tu salida cuando lo necesites.",
                        LocalDateTime.of(HOY.minusDays(1), LocalTime.of(7, 30))));
    }

    // ==================================================================
    //  Servicios de taxi: uno en curso y dos cerrados
    // ==================================================================

    private static List<TaxiService> crearTaxis() {
        // En curso: EN_CAMINO, con conductor asignado. El QR ya es visible (RF-109)
        TaxiService t1 = new TaxiService("T1", "TAX-2026-0733", "B1", "U1");
        t1.withRuta("Av. Malecón Cisneros 1240, Miraflores", "Aeropuerto Jorge Chávez")
                .withRecojo(-12.1219, -77.0297)
                .withProgramacion(HOY, LocalTime.of(9, 30), false)
                .withPasajeros(2)
                .withPrecio(0);
        t1.asignarA(CONDUCTORES.get(0));
        t1.avanzarA(TaxiStatus.EN_CAMINO);
        // A algo menos de 1,2 km del hotel de la reserva (H1), que es el punto
        // de recojo: el plano de seguimiento (RF-099) se dibuja desde ahi.
        t1.actualizarUbicacion(-12.1129, -77.0357, LocalDateTime.of(HOY, LocalTime.of(9, 12)));

        // Cerrado y calificado
        TaxiService t2 = new TaxiService("T2", "TAX-2026-0518", "B3", "U1");
        t2.withRuta("Jr. Unión 185, Barranco", "Aeropuerto Jorge Chávez")
                .withRecojo(-12.1466, -77.0206)
                .withProgramacion(HOY.minusDays(36), LocalTime.of(6, 0), false)
                .withPasajeros(2)
                .withPrecio(0);
        t2.asignarA(CONDUCTORES.get(1));
        t2.avanzarA(TaxiStatus.EN_CAMINO);
        t2.avanzarA(TaxiStatus.EN_TRASLADO);
        t2.avanzarA(TaxiStatus.FINALIZADO);
        t2.valorar(9f);

        // Recien solicitado, sin conductor todavia (RF-106)
        TaxiService t3 = new TaxiService("T3", "TAX-2026-0734", "B2", "U9");
        t3.withRuta("Calle Plateros 145, Cusco", "Aeropuerto Velasco Astete")
                .withRecojo(-13.5156, -71.9785)
                .withProgramacion(HOY.plusDays(20), LocalTime.of(15, 0), true)
                .withPasajeros(2)
                .withPrecio(TaxiService.TARIFA_AEROPUERTO);

        return Arrays.asList(t1, t2, t3);
    }

    // ==================================================================
    //  Resenas: cubren la escala completa 1-10
    // ==================================================================

    private static List<Review> crearResenas() {
        return Arrays.asList(
                new Review("R1", "H1", "Camila Torres", 10f,
                        "La vista desde la habitación es exactamente la de las fotos. "
                                + "El desayuno es generoso y el personal muy atento.",
                        HOY.minusDays(12)),
                new Review("R2", "H1", "Diego Salazar", 9f,
                        "Muy buena estadía. La piscina está temperada y se puede usar "
                                + "temprano. El estacionamiento es angosto, eso sí.",
                        HOY.minusDays(28)),
                new Review("R3", "H1", "Valeria Núñez", 7f,
                        "Bien en general, aunque el spa tiene precios altos para lo que "
                                + "ofrece. La ubicación es inmejorable.",
                        HOY.minusDays(45)),
                new Review("R4", "H1", "Marco Antonio Ríos", 4f,
                        "El aire acondicionado de la habitación hacía ruido toda la noche "
                                + "y no lo pudieron cambiar porque estaba lleno.",
                        HOY.minusDays(63)),
                new Review("R5", "H4", "Sofía Bermúdez", 10f,
                        "El patio interior es un remanso después de caminar todo el día. "
                                + "El mate de coca de bienvenida se agradece con la altura.",
                        HOY.minusDays(8)),
                new Review("R6", "H4", "Andrés Paredes", 9f,
                        "Muy bien ubicado, a media cuadra de la Plaza. Las habitaciones "
                                + "son cálidas pese al frío de la noche cusqueña.",
                        HOY.minusDays(21)),
                new Review("R7", "H7", "Rocío del Águila", 3f,
                        "Cumple para dormir una noche de paso, nada más. El wifi no "
                                + "llegaba bien a la habitación del segundo piso.",
                        HOY.minusDays(15)),
                new Review("R8", "H8", "Jorge Luis Campos", 2f,
                        "La casona tiene encanto pero le falta mantenimiento. La ducha "
                                + "no tenía presión y el desayuno era muy básico.",
                        HOY.minusDays(33)));
    }

    // ==================================================================
    //  Bitacora (RC-042: el detalle nunca contiene contrasenas ni secretos)
    // ==================================================================

    private static List<LogEntry> crearBitacora() {
        return Arrays.asList(
                new LogEntry(LocalDateTime.of(HOY, LocalTime.of(8, 40)),
                        "Julio Mendoza", LogEntry.Evento.CAMBIO_ESTADO_TAXI,
                        "Servicio TAX-2026-0733 pasó de ASIGNADO a EN CAMINO."),
                new LogEntry(LocalDateTime.of(HOY, LocalTime.of(9, 5)),
                        "Martín Rojas", LogEntry.Evento.ACCION_ADMINISTRATIVA,
                        "Respondió la conversación de la reserva EST-2026-0418."),
                new LogEntry(LocalDateTime.of(HOY.minusDays(1), LocalTime.of(20, 15)),
                        "Martín Rojas", LogEntry.Evento.CARGO_ADICIONAL,
                        "Registró un cargo de S/ 45 en la reserva EST-2026-0418."),
                new LogEntry(LocalDateTime.of(HOY.minusDays(5), LocalTime.of(13, 2)),
                        "Lucía Quispe", LogEntry.Evento.PAGO,
                        "Pago registrado para la reserva EST-2026-0551."),
                new LogEntry(LocalDateTime.of(HOY.minusDays(40), LocalTime.of(11, 30)),
                        "Lucía Quispe", LogEntry.Evento.RESERVA,
                        "Creó la reserva EST-2026-0192 en Barranco Art Hotel."));
    }

    // ==================================================================
    //  Constructores auxiliares
    // ==================================================================

    /**
     * Fotografias remotas por palabra clave. Devuelve siempre al menos
     * {@link Hotel#MIN_FOTOS} imagenes (RF-013, regla 14).
     */
    private static List<String> fotos(String tema, int desde, int cantidad) {
        List<String> urls = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            urls.add("https://loremflickr.com/800/500/" + tema + "?lock=" + (desde + i));
        }
        return urls;
    }

    /*
     * Hotel expone sus colecciones como vistas inmutables y obliga a pasar por
     * addFoto, addLugarCercano y addHabitacion. Estos tres auxiliares existen
     * para que la ficha de cada hotel se siga leyendo de un vistazo, en vez de
     * repetir un bucle en cada una de las diez.
     */

    private static void conFotos(Hotel hotel, List<String> urls) {
        for (String url : urls) {
            hotel.addFoto(url);
        }
    }

    private static void conLugares(Hotel hotel, List<NearbyPlace> lugares) {
        for (NearbyPlace lugar : lugares) {
            hotel.addLugarCercano(lugar);
        }
    }

    private static void conLugares(Hotel hotel, NearbyPlace... lugares) {
        for (NearbyPlace lugar : lugares) {
            hotel.addLugarCercano(lugar);
        }
    }

    private static void conHabitaciones(Hotel hotel, List<Room> habitaciones) {
        for (Room habitacion : habitaciones) {
            hotel.addHabitacion(habitacion);
        }
    }

    private static void conHabitaciones(Hotel hotel, Room... habitaciones) {
        for (Room habitacion : habitaciones) {
            hotel.addHabitacion(habitacion);
        }
    }

    private static Room habitacion(String id, String hotelId, String tipo, double precio,
                                   int adultos, int ninos, double area, int piso, String numero,
                                   int numFotos, String temaFotos, int semilla) {
        Room room = new Room(id, hotelId, tipo, precio);
        room.withCapacidad(adultos, ninos).withArea(area).withUbicacion(piso, numero);
        for (String url : fotos(temaFotos, semilla, numFotos)) {
            room.addFoto(url);
        }
        return room;
    }

    /**
     * El estado de lectura se asigna despues de construir, no en el constructor.
     *
     * <p>{@code leido} significa que lo leyo quien lo recibio, o sea el lado
     * contrario al autor: un mensaje del hotel nace sin leer para el cliente.
     */
    private static Message mensaje(String id, String conversacionId, String texto,
                                   LocalDateTime cuando, Message.Autor autor, boolean leido) {
        Message m = new Message(id, conversacionId, texto, cuando, autor);
        m.setLeido(leido);
        return m;
    }

    // ==================================================================
    //  Utilidades de consulta
    // ==================================================================

    /** Busca un hotel por identificador, o {@code null} si no existe. */
    public static Hotel hotel(String hotelId) {
        for (Hotel h : HOTELES) {
            if (h.getId().equals(hotelId)) {
                return h;
            }
        }
        return null;
    }

    /** Busca una reserva por identificador, o {@code null} si no existe. */
    public static Booking reserva(String bookingId) {
        for (Booking b : RESERVAS) {
            if (b.getId().equals(bookingId)) {
                return b;
            }
        }
        return null;
    }

    public static User usuario(String usuarioId) {
        for (User u : USUARIOS) {
            if (u.getId().equals(usuarioId)) {
                return u;
            }
        }
        return null;
    }

    public static Driver conductor(String driverId) {
        for (Driver d : CONDUCTORES) {
            if (d.getId().equals(driverId)) {
                return d;
            }
        }
        return null;
    }

    public static TaxiService taxi(String taxiId) {
        for (TaxiService t : TAXIS) {
            if (t.getId().equals(taxiId)) {
                return t;
            }
        }
        return null;
    }

    public static Service servicio(String serviceId) {
        for (Service s : SERVICIOS) {
            if (s.getServiceId().equals(serviceId)) {
                return s;
            }
        }
        return null;
    }

    /** Ciudades con al menos un hotel, en orden alfabetico. */
    public static List<String> ciudades() {
        List<String> ciudades = new ArrayList<>();
        for (Hotel h : HOTELES) {
            if (!ciudades.contains(h.getCiudad())) {
                ciudades.add(h.getCiudad());
            }
        }
        Collections.sort(ciudades);
        return ciudades;
    }

    /**
     * Distritos donde hay alojamiento, sin repetir.
     *
     * <p>Existe porque la busqueda compara el destino contra ciudad <em>y</em>
     * contra distrito (RF-017): quien escribe "Miraflores" encuentra hoteles, y
     * el selector de destino tiene que poder ofrecer lo mismo que acepta la
     * busqueda. "Centro" y "Centro Histórico" son distritos de ciudades
     * distintas: son entradas diferentes y las dos se ofrecen.
     */
    public static List<String> distritos() {
        List<String> distritos = new ArrayList<>();
        for (Hotel h : HOTELES) {
            if (!distritos.contains(h.getDistrito())) {
                distritos.add(h.getDistrito());
            }
        }
        Collections.sort(distritos);
        return distritos;
    }

    /** Distritos de una ciudad, sin repetir. */
    public static List<String> distritosDe(String ciudad) {
        List<String> distritos = new ArrayList<>();
        for (Hotel h : HOTELES) {
            if (h.getCiudad().equals(ciudad) && !distritos.contains(h.getDistrito())) {
                distritos.add(h.getDistrito());
            }
        }
        Collections.sort(distritos);
        return distritos;
    }

    /** Nombre para mostrar del hotel, o un texto neutro si el identificador no existe. */
    public static String nombreHotel(String hotelId) {
        Hotel h = hotel(hotelId);
        return h != null ? h.getNombre() : "Hotel";
    }

    /** Habitacion de un hotel, o {@code null}. */
    public static Room habitacion(String roomId) {
        for (Hotel h : HOTELES) {
            for (Room r : h.getHabitaciones()) {
                if (r.getId().equals(roomId)) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Cruce de una reserva con su cliente y su habitacion.
     *
     * <p>Vive aqui, junto a los tres conjuntos que cruza, y no en el repositorio
     * que lo estreno: lo usan la gestion del hotel —portada, lista de reservas,
     * detalle y cobros— y el chat, que necesita decir de que estadia habla cada
     * conversacion. Con una copia en cada repositorio, un dia la bandeja diria
     * que la habitacion es la 302 donde la lista de reservas dice la 601, y no
     * habria forma de saber cual de las dos miente.
     *
     * <p>El cliente se pasa tal cual, aunque falte. Antes se sustituia aqui por
     * un texto de relleno, y eso ponia una cadena de la interfaz —y en un solo
     * idioma— dentro del almacen de datos; ahora el relleno lo elige la pantalla,
     * que es la unica que sabe en que idioma esta.
     */
    @NonNull
    public static ReservaDeHotel detallar(@NonNull Booking reserva) {
        User cliente = usuario(reserva.getClienteId());
        Room habitacion = habitacion(reserva.getRoomId());
        return new ReservaDeHotel(
                reserva,
                cliente,
                habitacion != null ? habitacion.getNumero() : reserva.getRoomId(),
                habitacion != null && habitacion.getTipo() != null ? habitacion.getTipo() : "");
    }
}
