package org.iot.project.data.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.data.repository.HotelRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.OrdenBusqueda;
import org.iot.project.models.Pagina;
import org.iot.project.models.Review;
import org.iot.project.models.Room;
import org.iot.project.models.SearchQuery;
import org.iot.project.models.Service;
import org.iot.project.models.TipoHabitacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Implementacion simulada del catalogo de hoteles. */
public class MockHotelRepository extends MockRepository implements HotelRepository {

    /**
     * Hoteles por pagina.
     *
     * <p>Cuatro es un tamano comodo para una tarjeta con fotografia, y sobre el
     * catalogo de demostracion deja tres paginas: suficiente para ver que la
     * lista se sigue cargando y que se detiene donde debe.
     */
    private static final int TAMANO_PAGINA = 4;

    @Override
    public void buscar(@NonNull SearchQuery query, int pagina,
                       @NonNull ResultCallback<Pagina<Hotel>> callback) {
        // Se pasa por entregarLista y no por entregarDato porque el modo VACIO
        // tiene que vaciar la busqueda: es la unica forma de poder enseñar el
        // estado vacio de resultados (§50). Paginar despues de filtrar es
        // justo el orden correcto: primero se decide que entra, luego se corta.
        entregarLista(new ResultCallback<List<Hotel>>() {
            @Override
            public void onExito(@NonNull List<Hotel> datos) {
                callback.onExito(paginar(datos, pagina));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                callback.onError(mensaje);
            }
        }, () -> {
            List<Hotel> resultado = new ArrayList<>();
            for (Hotel hotel : MockData.hotelesPublicados()) {
                if (coincide(hotel, query)) {
                    resultado.add(hotel);
                }
            }
            resultado.sort(comparadorDe(query.getOrden()));
            return resultado;
        });
    }

    /**
     * El orden que pidio el usuario (§19).
     *
     * <p>Recomendados pondera la valoracion por el volumen de resenas: un 9,4
     * con doce opiniones no es mejor que un 9,2 con doscientas, y ordenar solo
     * por rating haria que el hotel con menos evidencia ganara siempre. El
     * logaritmo evita que el numero de resenas ahogue a la valoracion.
     */
    private static Comparator<Hotel> comparadorDe(OrdenBusqueda orden) {
        switch (orden) {
            case PRECIO_MENOR:
                return Comparator.comparingDouble(Hotel::getPrecioDesde);
            case PRECIO_MAYOR:
                return Comparator.comparingDouble(Hotel::getPrecioDesde).reversed();
            case MEJOR_RATING:
                return Comparator.comparingDouble(Hotel::getRating).reversed();
            case MAS_POPULARES:
                return Comparator.comparingInt(Hotel::getNumReviews).reversed();
            case RECOMENDADOS:
                return Comparator.comparingDouble(MockHotelRepository::relevancia).reversed();
        }
        return Comparator.comparingDouble(MockHotelRepository::relevancia).reversed();
    }

    /** Valoracion ponderada por evidencia: rating x log10(resenas + 10). */
    private static double relevancia(Hotel hotel) {
        return hotel.getRating() * Math.log10(hotel.getNumReviews() + 10d);
    }

    /**
     * Recorta la lista a la pagina pedida.
     *
     * <p>{@code hayMas} se calcula sobre el total, no sobre si la pagina vino
     * llena. Si el catalogo tiene ocho hoteles y se piden de a cuatro, la
     * segunda pagina llega completa y aun asi es la ultima; deducirlo del
     * tamano ofreceria un "ver mas" que no trae nada.
     */
    private static Pagina<Hotel> paginar(List<Hotel> completa, int pagina) {
        int desde = Math.max(0, pagina) * TAMANO_PAGINA;
        if (desde >= completa.size()) {
            return new Pagina<>(Collections.emptyList(), Math.max(0, pagina), false);
        }
        int hasta = Math.min(desde + TAMANO_PAGINA, completa.size());
        return new Pagina<>(completa.subList(desde, hasta), pagina, hasta < completa.size());
    }

    /**
     * Aplica los filtros de la busqueda. El destino se compara contra ciudad y
     * contra distrito, para que "Miraflores" y "Cusco" funcionen igual.
     */
    private boolean coincide(Hotel hotel, SearchQuery query) {
        String destino = query.getDestino();
        if (destino != null && !destino.trim().isEmpty()) {
            String buscado = destino.trim().toLowerCase();
            boolean coincideDestino =
                    hotel.getCiudad().toLowerCase().contains(buscado)
                            || hotel.getDistrito().toLowerCase().contains(buscado);
            if (!coincideDestino) {
                return false;
            }
        }

        if (query.getPrecioMax() > 0 && hotel.getPrecioDesde() > query.getPrecioMax()) {
            return false;
        }
        if (query.getPrecioMin() > 0 && hotel.getPrecioDesde() < query.getPrecioMin()) {
            return false;
        }
        if (query.getRatingMinimo() > 0 && hotel.getRating() < query.getRatingMinimo()) {
            return false;
        }

        // Los servicios se piden con Y, no con O: quien marca piscina y
        // gimnasio espera hoteles que tengan ambos (§20).
        if (query.hasServiciosSeleccionados()) {
            for (String serviceId : query.getServiciosSeleccionados()) {
                if (!hotelTieneServicio(hotel, serviceId)) {
                    return false;
                }
            }
        }

        // El tipo de habitacion y las fechas se resuelven juntos: si se piden
        // fechas, no basta con que el hotel tenga una suite, tiene que tenerla
        // libre esos dias. Sin fechas basta con que exista.
        TipoHabitacion tipo = query.getTipoHabitacion();
        if (query.hasFechas()) {
            return !habitacionesLibres(hotel, query.getFechaEntrada(), query.getFechaSalida(),
                    query.getNumHuespedes(), tipo).isEmpty();
        }
        return !query.hasTipoHabitacion() || tipo.coincideConAlguno(hotel);
    }

    private boolean hotelTieneServicio(Hotel hotel, String serviceId) {
        for (HotelService hs : hotel.getServicios()) {
            if (hs.getServiceId().equals(serviceId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void obtener(@NonNull String hotelId, @NonNull ResultCallback<Hotel> callback) {
        entregarDato(callback, () -> MockData.hotel(hotelId),
                "No encontramos este hotel. Puede que ya no esté disponible.");
    }

    @Override
    public void recomendados(int limite, @NonNull ResultCallback<List<Hotel>> callback) {
        entregarLista(callback, () -> {
            List<Hotel> ordenados = new ArrayList<>(MockData.hotelesPublicados());
            ordenados.sort(Comparator
                    .comparingDouble(Hotel::getRating).reversed()
                    .thenComparing(Comparator.comparingInt(Hotel::getNumReviews).reversed()));
            return recortar(ordenados, limite);
        });
    }

    @Override
    public void enCiudad(@NonNull String ciudad, int limite,
                         @NonNull ResultCallback<List<Hotel>> callback) {
        entregarLista(callback, () -> {
            List<Hotel> resultado = new ArrayList<>();
            for (Hotel hotel : MockData.hotelesPublicados()) {
                if (hotel.getCiudad().equalsIgnoreCase(ciudad)) {
                    resultado.add(hotel);
                }
            }
            resultado.sort(Comparator.comparingDouble(Hotel::getRating).reversed());
            return recortar(resultado, limite);
        });
    }

    @Override
    public void ciudades(@NonNull ResultCallback<List<String>> callback) {
        entregarLista(callback, MockData::ciudades);
    }

    @Override
    public void resenas(@NonNull String hotelId, @NonNull ResultCallback<List<Review>> callback) {
        entregarLista(callback, () -> {
            List<Review> resultado = new ArrayList<>();
            for (Review resena : MockData.RESENAS) {
                if (resena.getEntidadId().equals(hotelId)) {
                    resultado.add(resena);
                }
            }
            // Mas recientes primero (RF-020).
            resultado.sort(Comparator.comparing(Review::getFecha).reversed());
            return resultado;
        });
    }

    @Override
    public void habitacionesDisponibles(@NonNull String hotelId,
                                        @NonNull LocalDate entrada,
                                        @NonNull LocalDate salida,
                                        @NonNull ResultCallback<List<Room>> callback) {
        entregarLista(callback, () -> {
            Hotel hotel = MockData.hotel(hotelId);
            if (hotel == null) {
                return new ArrayList<Room>();
            }
            return habitacionesLibres(hotel, entrada, salida, 0, null);
        });
    }

    /**
     * Habitaciones libres en el rango pedido.
     *
     * @param huespedes si es mayor que cero, descarta las que no alcanzan esa
     *                  capacidad. Cero significa "no filtrar por capacidad".
     * @param tipo      si no es nulo, descarta las que no sean de ese tipo.
     */
    private List<Room> habitacionesLibres(Hotel hotel, LocalDate entrada, LocalDate salida,
                                          int huespedes, @Nullable TipoHabitacion tipo) {
        List<Room> libres = new ArrayList<>();
        if (entrada == null || salida == null || !salida.isAfter(entrada)) {
            // Sin un rango valido no hay superposicion que evaluar: se
            // devuelven todas las habitaciones habilitadas.
            for (Room room : hotel.getHabitaciones()) {
                if (room.isDisponible() && acepta(room, huespedes, tipo)) {
                    libres.add(room);
                }
            }
        } else {
            Booking consulta = periodoDeConsulta(null, entrada, salida);
            for (Room room : hotel.getHabitaciones()) {
                if (!room.isDisponible() || !acepta(room, huespedes, tipo)) {
                    continue;
                }
                if (!estaOcupada(room.getId(), consulta)) {
                    libres.add(room);
                }
            }
        }
        libres.sort(Comparator.comparingDouble(Room::getPrecioNoche));
        return libres;
    }

    /** true si la habitacion pasa los dos descartes que no dependen de fechas. */
    private static boolean acepta(Room room, int huespedes, @Nullable TipoHabitacion tipo) {
        if (huespedes > 0 && room.getCapacidadTotal() < huespedes) {
            return false;
        }
        return tipo == null || tipo.coincide(room);
    }

    /**
     * Comprueba si la habitacion tiene alguna reserva vigente que se superponga.
     *
     * <p>Delega en {@link Booking#seSuperponeCon(Booking)} en lugar de comparar
     * fechas aqui: asi la regla de RF-032 y RC-012 —intervalos semiabiertos, y
     * la reserva cancelada que libera la habitacion— vive en un solo sitio.
     */
    private boolean estaOcupada(String roomId, Booking consulta) {
        consulta = periodoDeConsulta(roomId, consulta.getFechaEntrada(), consulta.getFechaSalida());
        for (Booking reserva : MockData.RESERVAS) {
            if (reserva.seSuperponeCon(consulta)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reserva de sondeo con el mismo periodo, para reutilizar la comparacion de
     * intervalos de {@link Booking}.
     *
     * <p>El identificador de habitacion importa: {@code seSuperponeCon} exige
     * que coincida, asi que pasarle uno ficticio haria que nunca detectara un
     * choque y toda habitacion pareciera libre.
     */
    private Booking periodoDeConsulta(String roomId, LocalDate entrada, LocalDate salida) {
        return new Booking("_consulta", "_consulta", "_consulta",
                roomId != null ? roomId : "_consulta", "_consulta",
                entrada, salida, 1, 0, BookingStatus.PENDIENTE);
    }

    // ------------------------------------------------------------------
    //  Consultas sincronas: el catalogo ya esta en memoria, no hay espera
    // ------------------------------------------------------------------

    @Override
    public List<Service> catalogoServicios() {
        return MockData.SERVICIOS;
    }

    @Override
    public Service servicio(@NonNull String serviceId) {
        return MockData.servicio(serviceId);
    }

    @Override
    public List<String> ciudadesDisponibles() {
        return MockData.ciudades();
    }

    @Override
    @NonNull
    public List<String> distritosDisponibles() {
        return MockData.distritos();
    }

    @Override
    public double precioMaximo() {
        double maximo = 0d;
        for (Hotel hotel : MockData.hotelesPublicados()) {
            maximo = Math.max(maximo, hotel.getPrecioDesde());
        }
        // Se redondea hacia arriba a la centena para que el extremo del
        // deslizador sea un numero legible y no "S/ 1,237".
        return Math.ceil(maximo / 100d) * 100d;
    }

    @Override
    public Hotel hotel(@NonNull String hotelId) {
        return MockData.hotel(hotelId);
    }

    /**
     * Recorre {@code MockData.HOTELES} y no {@code hotelesPublicados()}: un
     * administrador cuyo hotel esta sin publicar sigue siendo su administrador,
     * y es justo quien tiene que publicarlo.
     */
    @Override
    @Nullable
    public Hotel hotelDeAdministrador(@NonNull String usuarioId) {
        for (Hotel hotel : MockData.HOTELES) {
            if (usuarioId.equals(hotel.getAdministradorId())) {
                return hotel;
            }
        }
        return null;
    }

    private static <T> List<T> recortar(List<T> lista, int limite) {
        if (limite <= 0 || limite >= lista.size()) {
            return lista;
        }
        return new ArrayList<>(lista.subList(0, limite));
    }
}
