package org.iot.project.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.CatalogoServicios;
import org.iot.project.data.repository.HotelRepository;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.OrdenBusqueda;
import org.iot.project.models.Pagina;
import org.iot.project.models.Review;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.SearchQuery;
import org.iot.project.models.Service;
import org.iot.project.models.TipoHabitacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Catalogo y fichas de Firestore, sin hoteles de demostracion.
 * Los tipos de servicio son un catálogo local compartido; sus asignaciones y
 * precios se leen del documento real del hotel.
 */
public final class FirebaseHotelRepository implements HotelRepository {
    private static final int TAMANO_PAGINA = 4;
    private final List<Service> servicios = CatalogoServicios.crear();
    private final Map<String, Hotel> remotos = new HashMap<>();
    private final List<Hotel> catalogo = new ArrayList<>();
    private String cacheUid;
    private Role cacheRole;
    private String cacheHotelId;
    private long cacheVersion;
    private long solicitudCatalogo;

    // Crear ServiceLocator no inicializa Firebase.
    public FirebaseHotelRepository() { }

    @Override
    @NonNull
    public List<Service> catalogoServicios() {
        return servicios;
    }

    @Override
    @Nullable
    public Service servicio(@NonNull String serviceId) {
        for (Service servicio : servicios) {
            if (servicio.getServiceId().equals(serviceId)) return servicio;
        }
        return null;
    }

    @Override
    public void obtener(@NonNull String hotelId, @NonNull ResultCallback<Hotel> callback) {
        String uid = identidad(callback);
        if (uid == null) return;
        if (hotelId == null || hotelId.trim().isEmpty() || hotelId.contains("/")) {
            callback.onError("No encontramos un identificador de hotel válido.");
            return;
        }
        long version = cacheVersion;
        FirebaseFirestore.getInstance().collection("hoteles").document(hotelId).get(Source.SERVER)
                .addOnSuccessListener(documento -> {
                    if (!vigente(uid, version, callback)) return;
                    if (!documento.exists() || documento.getData() == null) {
                        remotos.remove(hotelId);
                        callback.onError("No encontramos este hotel en el servidor.");
                        return;
                    }
                    Hotel hotel;
                    try {
                        hotel = HotelFirebase.desde(documento.getId(), documento.getData());
                    } catch (IllegalArgumentException error) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (!puedeConsultar(hotel)) {
                        remotos.remove(hotelId);
                        callback.onError("Este hotel ya no está disponible para tu cuenta.");
                        return;
                    }
                    guardarEnCache(hotel);
                    callback.onExito(hotel);
                }).addOnFailureListener(error -> {
                    if (vigente(uid, version, callback)) {
                        remotos.remove(hotelId);
                        callback.onError(mensaje(error));
                    }
                });
    }

    /** El filtro de publicado forma parte de la consulta autorizada por Rules. */
    private void publicados(@NonNull ResultCallback<List<Hotel>> callback) {
        String uid = identidad(callback);
        if (uid == null) return;
        long version = cacheVersion;
        long solicitud = ++solicitudCatalogo;
        FirebaseFirestore.getInstance().collection("hoteles").whereEqualTo("publicado", true)
                .get(Source.SERVER).addOnSuccessListener(resultado -> {
                    if (!vigente(uid, version, callback)) return;
                    List<Hotel> lista = new ArrayList<>();
                    String errorDatos = null;
                    for (DocumentSnapshot documento : resultado) {
                        try {
                            Hotel hotel = HotelFirebase.desde(documento.getId(), documento.getData());
                            if (hotel.isPublicado() && puedeConsultar(hotel)) lista.add(hotel);
                        } catch (IllegalArgumentException error) {
                            // Una ficha mal importada no bloquea los demas hoteles.
                            errorDatos = error.getMessage();
                        }
                    }
                    if (lista.isEmpty() && errorDatos != null) {
                        callback.onError("No pudimos leer los datos del catálogo. " + errorDatos);
                        return;
                    }
                    if (solicitud == solicitudCatalogo) {
                        catalogo.clear();
                        catalogo.addAll(lista);
                        if (cacheRole == Role.CLIENTE) remotos.clear();
                        for (Hotel hotel : lista) remotos.put(hotel.getId(), hotel);
                    }
                    callback.onExito(new ArrayList<>(lista));
                }).addOnFailureListener(error -> {
                    if (vigente(uid, version, callback)) callback.onError(mensaje(error));
                });
    }

    @Override
    public void recomendados(int limite, @NonNull ResultCallback<List<Hotel>> callback) {
        publicados(new ResultCallback<List<Hotel>>() {
            @Override public void onExito(@NonNull List<Hotel> hoteles) {
                hoteles.sort(comparador(OrdenBusqueda.MEJOR_RATING));
                callback.onExito(recortarResultado(hoteles, limite));
            }
            @Override public void onError(@NonNull String error) { callback.onError(error); }
        });
    }

    @Override
    public void enCiudad(@NonNull String ciudad, int limite, @NonNull ResultCallback<List<Hotel>> callback) {
        publicados(new ResultCallback<List<Hotel>>() {
            @Override public void onExito(@NonNull List<Hotel> hoteles) {
                List<Hotel> resultado = new ArrayList<>();
                for (Hotel hotel : hoteles) {
                    if (hotel.getCiudad().equalsIgnoreCase(ciudad.trim())) resultado.add(hotel);
                }
                resultado.sort(comparador(OrdenBusqueda.MEJOR_RATING));
                callback.onExito(recortarResultado(resultado, limite));
            }
            @Override public void onError(@NonNull String error) { callback.onError(error); }
        });
    }

    @Override
    public void buscar(@NonNull SearchQuery consulta, int pagina, @NonNull ResultCallback<Pagina<Hotel>> callback) {
        SearchQuery filtros = consulta.copy();
        int numero = Math.max(0, pagina);
        publicados(new ResultCallback<List<Hotel>>() {
            @Override public void onExito(@NonNull List<Hotel> hoteles) {
                List<Hotel> resultado = new ArrayList<>();
                for (Hotel hotel : hoteles) if (coincideConFiltros(hotel, filtros)) resultado.add(hotel);
                resultado.sort(comparador(filtros.getOrden()));
                long inicio = (long) numero * TAMANO_PAGINA;
                if (inicio >= resultado.size()) {
                    callback.onExito(new Pagina<>(Collections.emptyList(), numero, false));
                    return;
                }
                int desde = (int) inicio;
                int hasta = Math.min(desde + TAMANO_PAGINA, resultado.size());
                callback.onExito(new Pagina<>(new ArrayList<>(resultado.subList(desde, hasta)),
                        numero, hasta < resultado.size()));
            }
            @Override public void onError(@NonNull String error) { callback.onError(error); }
        });
    }

    @Override
    public void ciudades(@NonNull ResultCallback<List<String>> callback) {
        publicados(new ResultCallback<List<Hotel>>() {
            @Override public void onExito(@NonNull List<Hotel> hoteles) {
                // El selector consulta los distritos sincronicamente desde este
                // callback: ambos destinos deben proceder de la misma respuesta.
                catalogo.clear();
                catalogo.addAll(hoteles);
                callback.onExito(destinos(hoteles, false));
            }
            @Override public void onError(@NonNull String error) { callback.onError(error); }
        });
    }

    @Override
    public void habitacionesDisponibles(@NonNull String hotelId, @NonNull LocalDate entrada,
                                        @NonNull LocalDate salida, @NonNull ResultCallback<List<Room>> callback) {
        if (entrada == null || salida == null || !salida.isAfter(entrada)) {
            callback.onError("Elige un rango de fechas válido.");
            return;
        }
        obtener(hotelId, new ResultCallback<Hotel>() {
            @Override public void onExito(@NonNull Hotel hotel) {
                // Inventario habilitado. El calendario de reservas se conecta
                // en su modulo; no se consulta ninguna reserva simulada.
                List<Room> habitaciones = new ArrayList<>();
                for (Room habitacion : hotel.getHabitaciones()) {
                    if (habitacion.isDisponible()) habitaciones.add(habitacion);
                }
                habitaciones.sort(Comparator.comparingDouble(Room::getPrecioNoche));
                callback.onExito(habitaciones);
            }
            @Override public void onError(@NonNull String error) { callback.onError(error); }
        });
    }

    @Override
    public void resenas(@NonNull String hotelId, @NonNull ResultCallback<List<Review>> callback) {
        callback.onError("Las reseñas todavía no están conectadas.");
    }

    public void guardarEnCache(@NonNull Hotel hotel) {
        prepararCache(SessionManager.getUsuarioId());
        if (cacheUid != null && !cacheUid.isEmpty() && puedeConsultar(hotel)) remotos.put(hotel.getId(), hotel);
        else remotos.remove(hotel.getId());
    }

    public void reemplazarCache(@NonNull List<Hotel> hoteles) {
        prepararCache(SessionManager.getUsuarioId());
        remotos.clear();
        if (cacheUid != null && !cacheUid.isEmpty()) {
            for (Hotel hotel : hoteles) if (puedeConsultar(hotel)) remotos.put(hotel.getId(), hotel);
        }
    }

    @Override @Nullable
    public Hotel hotel(@NonNull String hotelId) {
        prepararCache(SessionManager.getUsuarioId());
        Hotel hotel = remotos.get(hotelId);
        return hotel != null && puedeConsultar(hotel) ? hotel : null;
    }

    @Override @Nullable
    public Hotel hotelDeAdministrador(@NonNull String usuarioId) {
        prepararCache(SessionManager.getUsuarioId());
        for (Hotel hotel : remotos.values()) {
            if (usuarioId.equals(hotel.getAdministradorId()) && puedeConsultar(hotel)) return hotel;
        }
        return null;
    }

    @Override @NonNull public List<String> ciudadesDisponibles() {
        prepararCache(SessionManager.getUsuarioId());
        return destinos(catalogo, false);
    }
    @Override @NonNull public List<String> distritosDisponibles() {
        prepararCache(SessionManager.getUsuarioId());
        return destinos(catalogo, true);
    }
    @Override public double precioMaximo() {
        prepararCache(SessionManager.getUsuarioId());
        double maximo = 0;
        for (Hotel hotel : catalogo) maximo = Math.max(maximo, hotel.getPrecioDesde());
        return Math.ceil(maximo / 100d) * 100d;
    }

    private static List<String> destinos(List<Hotel> hoteles, boolean distrito) {
        Set<String> nombres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Hotel hotel : hoteles) {
            String nombre = distrito ? hotel.getDistrito() : hotel.getCiudad();
            if (nombre != null && !nombre.trim().isEmpty()) nombres.add(nombre.trim());
        }
        return new ArrayList<>(nombres);
    }

    private static boolean coincideConFiltros(Hotel hotel, SearchQuery consulta) {
        String destino = consulta.getDestino();
        if (destino != null && !destino.trim().isEmpty()) {
            String texto = destino.trim().toLowerCase(Locale.ROOT);
            if (!hotel.getCiudad().toLowerCase(Locale.ROOT).contains(texto)
                    && !hotel.getDistrito().toLowerCase(Locale.ROOT).contains(texto)) return false;
        }
        double precio = hotel.getPrecioDesde();
        if (precio < consulta.getPrecioMin() || (consulta.getPrecioMax() > 0 && precio > consulta.getPrecioMax())
                || hotel.getRating() < consulta.getRatingMinimo()) return false;
        for (String servicio : consulta.getServiciosSeleccionados()) {
            boolean tiene = false;
            for (HotelService asignado : hotel.getServicios()) {
                if (servicio.equals(asignado.getServiceId())) { tiene = true; break; }
            }
            if (!tiene) return false;
        }
        if (!consulta.hasFechas() && !consulta.hasTipoHabitacion()) return true;
        TipoHabitacion tipo = consulta.getTipoHabitacion();
        List<Room> candidatas = new ArrayList<>();
        for (Room habitacion : hotel.getHabitaciones()) {
            if (habitacion.isDisponible() && (tipo == null || tipo.coincide(habitacion))) candidatas.add(habitacion);
        }
        int cantidad = consulta.getHabitaciones();
        if (cantidad > candidatas.size()) return false;
        // El agregado admite hasta diez habitaciones: recorrer sus subconjuntos
        // permite repartir el grupo entre las elegidas, sin exigir que todo el
        // grupo quepa por separado en cada habitacion.
        for (int seleccion = 1; seleccion < (1 << candidatas.size()); seleccion++) {
            if (Integer.bitCount(seleccion) != cantidad) continue;
            int capacidad = 0;
            int adultos = 0;
            for (int i = 0; i < candidatas.size(); i++) {
                if ((seleccion & (1 << i)) != 0) {
                    capacidad += candidatas.get(i).getCapacidadTotal();
                    adultos += candidatas.get(i).getCapacidadAdultos();
                }
            }
            if (capacidad >= consulta.getNumHuespedes() && adultos >= consulta.getAdultos()) return true;
        }
        return false;
    }

    private static Comparator<Hotel> comparador(OrdenBusqueda orden) {
        Comparator<Hotel> criterio;
        switch (orden) {
            case PRECIO_MENOR: criterio = Comparator.comparingDouble(Hotel::getPrecioDesde); break;
            case PRECIO_MAYOR: criterio = Comparator.comparingDouble(Hotel::getPrecioDesde).reversed(); break;
            case MEJOR_RATING: criterio = Comparator.comparingDouble(Hotel::getRating).reversed(); break;
            case MAS_POPULARES: criterio = Comparator.comparingInt(Hotel::getNumReviews).reversed(); break;
            default: criterio = Comparator.comparingDouble((Hotel hotel) ->
                    hotel.getRating() * Math.log10(hotel.getNumReviews() + 10d)).reversed();
        }
        return criterio.thenComparing(Hotel::getNombre, String.CASE_INSENSITIVE_ORDER).thenComparing(Hotel::getId);
    }

    private static List<Hotel> recortarResultado(List<Hotel> hoteles, int limite) {
        return limite > 0 && limite < hoteles.size()
                ? new ArrayList<>(hoteles.subList(0, limite)) : new ArrayList<>(hoteles);
    }

    private boolean puedeConsultar(Hotel hotel) {
        Role rol = SessionManager.getRolActivo();
        if (rol == Role.SUPERADMIN) return true;
        if (rol == Role.CLIENTE) return hotel.isPublicado();
        return rol == Role.ADMIN_HOTEL && hotel.getId().equals(SessionManager.getHotelAdministrado())
                && Objects.equals(hotel.getAdministradorId(), SessionManager.getUsuarioId());
    }

    @Nullable private String identidad(ResultCallback<?> callback) {
        String uid = SessionManager.getUsuarioId();
        prepararCache(uid);
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (uid == null || uid.isEmpty() || actual == null || !uid.equals(actual.getUid())) {
            callback.onError("Inicia sesión para consultar los hoteles.");
            return null;
        }
        return uid;
    }

    private void prepararCache(@Nullable String uid) {
        Role rol = SessionManager.getRolActivo();
        String hotelId = SessionManager.getHotelAdministrado();
        if (!Objects.equals(cacheUid, uid) || cacheRole != rol || !Objects.equals(cacheHotelId, hotelId)) {
            remotos.clear();
            catalogo.clear();
            cacheUid = uid;
            cacheRole = rol;
            cacheHotelId = hotelId;
            cacheVersion++;
            solicitudCatalogo++;
        }
    }

    private boolean vigente(String uid, long version, ResultCallback<?> callback) {
        prepararCache(SessionManager.getUsuarioId());
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (!uid.equals(cacheUid) || version != cacheVersion || actual == null || !uid.equals(actual.getUid())) {
            callback.onError("La sesión cambió. Vuelve a cargar esta pantalla.");
            return false;
        }
        return true;
    }

    private static String mensaje(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED: return "No tienes permiso para consultar estos hoteles. Revisa las reglas publicadas.";
                case UNAUTHENTICATED: return "Tu sesión venció. Inicia sesión de nuevo.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED: return "No pudimos cargar los hoteles. Revisa tu conexión e inténtalo de nuevo.";
                default: break;
            }
        }
        return "No pudimos cargar los hoteles. Inténtalo de nuevo.";
    }
}
