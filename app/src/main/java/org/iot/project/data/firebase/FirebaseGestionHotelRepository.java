package org.iot.project.data.firebase;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.CargosDeReserva;
import org.iot.project.models.ClienteDeHotel;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.models.LogEntry;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Periodicidad;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.ResumenHotel;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.Service;
import org.iot.project.models.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contenido del hotel administrado, con autorizacion y auditoria en la misma transaccion.
 * El panel, las reservas y los informes se calculan con los documentos del hotel asignado.
 * El constructor no inicializa el SDK.
 */
public final class FirebaseGestionHotelRepository implements GestionHotelRepository {

    private enum Etapa { LEER_PERFIL, LEER_HOTEL, GUARDAR }

    // El contenido se mapea por separado; estos campos explican incompatibilidades
    // de documentos antiguos que Rules rechaza aunque la asignación sea correcta.
    private static final Set<String> CAMPOS_HOTEL = new HashSet<>(Arrays.asList(
            "nombre", "ciudad", "distrito", "direccion", "latitud", "longitud", "publicado",
            "administradorId", "creadoPor", "creadoEn", "actualizadoEn", "ultimoEventoId",
            "descripcion", "fotos", "habitaciones", "servicios", "lugaresCercanos",
            "precioDesde", "rating", "numReviews", "montoMinimoTaxi"));

    private interface Cambio {
        Map<String, Object> aplicar(Map<String, Object> original, Hotel hotel);
    }

    private static final class Actor {
        final String uid;
        final String email;
        final String hotelId;

        Actor(String uid, String email, String hotelId) {
            this.uid = uid;
            this.email = email;
            this.hotelId = hotelId;
        }
    }

    @Override
    public void resumen(@NonNull String hotelId, @NonNull ResultCallback<ResumenHotel> callback) {
        FirebaseReservasHotel.consultar(hotelId, resumenConsultado(callback));
    }

    @Override
    public Suscripcion escucharResumen(@NonNull String hotelId,
                                       @NonNull ResultCallback<ResumenHotel> callback) {
        return FirebaseReservasHotel.escuchar(hotelId, resumenConsultado(callback));
    }

    @Override
    public Suscripcion escucharReservas(@NonNull String hotelId,
                                        @NonNull ResultCallback<List<ReservaDeHotel>> callback) {
        return FirebaseReservasHotel.escuchar(hotelId, reservasConsultadas(callback));
    }

    @Override
    public void habitaciones(@NonNull String hotelId,
                             @NonNull ResultCallback<List<Room>> callback) {
        // Una transaccion de solo lectura verifica tambien el perfil y el vinculo reciproco.
        modificar(hotelId, "ACTUALIZAR_HOTEL", "", (original, hotel) -> Collections.emptyMap(),
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel hotel) {
                        List<Room> habitaciones = new ArrayList<>(hotel.getHabitaciones());
                        habitaciones.sort(Comparator.comparing(Room::isDisponible).reversed()
                                .thenComparingDouble(Room::getPrecioNoche));
                        callback.onExito(habitaciones);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        callback.onError(mensaje);
                    }
                });
    }

    @Override
    public void reservas(@NonNull String hotelId,
                         @NonNull ResultCallback<List<ReservaDeHotel>> callback) {
        FirebaseReservasHotel.consultar(hotelId, reservasConsultadas(callback));
    }

    @Override
    public void reserva(@NonNull String hotelId, @NonNull String bookingId,
                        @NonNull ResultCallback<ReservaDeHotel> callback) {
        FirebaseReservasHotel.consultarReserva(hotelId, bookingId,
                reservasConsultadas(new ResultCallback<List<ReservaDeHotel>>() {
                    @Override public void onExito(@NonNull List<ReservaDeHotel> reservas) {
                        if (reservas.size() != 1) callback.onError("No encontramos esta reserva en tu hotel.");
                        else callback.onExito(reservas.get(0));
                    }
                    @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
                }));
    }

    @Override
    public Suscripcion escucharReserva(@NonNull String hotelId, @NonNull String bookingId,
                                        @NonNull ResultCallback<ReservaDeHotel> callback) {
        return FirebaseReservasHotel.escucharReserva(hotelId, bookingId,
                reservasConsultadas(new ResultCallback<List<ReservaDeHotel>>() {
                    @Override public void onExito(@NonNull List<ReservaDeHotel> reservas) {
                        if (reservas.size() == 1) callback.onExito(reservas.get(0));
                        else callback.onError("No encontramos esta reserva en tu hotel.");
                    }
                    @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
                }));
    }

    @Override
    public void clientes(@NonNull String hotelId,
                         @NonNull ResultCallback<List<ClienteDeHotel>> callback) {
        reservas(hotelId, new ResultCallback<List<ReservaDeHotel>>() {
            @Override public void onExito(@NonNull List<ReservaDeHotel> reservas) {
                Map<String, List<ReservaDeHotel>> agrupadas = new LinkedHashMap<>();
                for (ReservaDeHotel detalle : reservas) {
                    Booking reserva = detalle.getReserva();
                    if (detalle.getCliente() == null || reserva.getEstado() != BookingStatus.ACTIVA
                            && reserva.getEstado() != BookingStatus.FINALIZADA) continue;
                    agrupadas.computeIfAbsent(reserva.getClienteId(), id -> new ArrayList<>()).add(detalle);
                }
                List<ClienteDeHotel> clientes = new ArrayList<>();
                for (List<ReservaDeHotel> estancias : agrupadas.values()) {
                    User usuario = estancias.get(0).getCliente();
                    if (usuario == null) continue;
                    LocalDate ultima = estancias.get(0).getReserva().getFechaEntrada();
                    double total = 0d;
                    boolean activa = false;
                    for (ReservaDeHotel detalle : estancias) {
                        total += detalle.getTotal();
                        LocalDate entrada = detalle.getReserva().getFechaEntrada();
                        if (entrada.isAfter(ultima)) ultima = entrada;
                        activa |= detalle.estaEnCurso();
                    }
                    clientes.add(new ClienteDeHotel(usuario, estancias.size(), total, ultima, activa));
                }
                clientes.sort(Comparator.comparingInt((ClienteDeHotel cliente) -> cliente.estaEnCurso() ? 0 : 1)
                        .thenComparing(Comparator.comparing(ClienteDeHotel::getUltimaEstancia).reversed())
                        .thenComparing(cliente -> cliente.getCliente().getId()));
                callback.onExito(clientes);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override
    public void cargos(@NonNull String hotelId,
                       @NonNull ResultCallback<List<CargosDeReserva>> callback) {
        reservas(hotelId, new ResultCallback<List<ReservaDeHotel>>() {
            @Override public void onExito(@NonNull List<ReservaDeHotel> reservas) {
                List<CargosDeReserva> grupos = new ArrayList<>();
                for (ReservaDeHotel detalle : reservas) {
                    if (!detalle.getReserva().getCargos().isEmpty()) {
                        grupos.add(new CargosDeReserva(detalle, detalle.getReserva().getCargos()));
                    }
                }
                callback.onExito(grupos);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override
    public void guardarHabitacion(@NonNull Room habitacion, @NonNull ResultCallback<Room> callback) {
        String roomId = habitacion.getId() == null || habitacion.getId().trim().isEmpty()
                ? UUID.randomUUID().toString() : habitacion.getId();
        final Map<String, Object> datos;
        try {
            datos = HotelFirebase.habitacion(habitacion, habitacion.getHotelId(), roomId);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(habitacion.getHotelId(), "ACTUALIZAR_HOTEL", "Guardó una habitación",
                (original, hotel) -> {
                    String numero = (String) datos.get("numero");
                    // Se compara contra el snapshot de la transaccion, no contra la lista de la UI.
                    // Si otra alta escribe el mismo numero, el reintento vuelve a comprobarlo.
                    for (Room existente : hotel.getHabitaciones()) {
                        if (!roomId.equals(existente.getId())
                                && numero.equalsIgnoreCase(existente.getNumero().trim())) {
                            throw new IllegalArgumentException(
                                    "Ya existe una habitación con ese número en este hotel.");
                        }
                    }
                    List<Map<String, Object>> habitaciones = mapas(original, "habitaciones");
                    int posicion = posicion(habitaciones, "id", roomId);
                    Map<String, Object> nueva = new HashMap<>(datos);
                    if (posicion >= 0) {
                        Map<String, Object> existente = habitaciones.get(posicion);
                        // El formulario actual no edita fotos ni disponibilidad.
                        nueva.put("disponible", existente.get("disponible"));
                        nueva.put("fotos", existente.getOrDefault("fotos", Collections.emptyList()));
                        Map<String, Object> combinada = new HashMap<>(existente);
                        combinada.putAll(nueva);
                        habitaciones.set(posicion, combinada);
                    } else {
                        if (habitaciones.size() >= HotelFirebase.MAX_HABITACIONES) {
                            throw new IllegalArgumentException("El hotel admite hasta 10 habitaciones.");
                        }
                        habitaciones.add(nueva);
                    }
                    return campo("habitaciones", habitaciones);
                }, habitacionGuardada(roomId, callback));
    }

    @Override
    public void cambiarDisponibilidad(@NonNull String hotelId, @NonNull String roomId,
                                      boolean disponible, @NonNull ResultCallback<Room> callback) {
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Cambió la disponibilidad de una habitación",
                (original, hotel) -> {
                    List<Map<String, Object>> habitaciones = mapas(original, "habitaciones");
                    int posicion = posicion(habitaciones, "id", roomId);
                    if (posicion < 0) throw new IllegalArgumentException("No encontramos esa habitación.");
                    habitaciones.get(posicion).put("disponible", disponible);
                    return campo("habitaciones", habitaciones);
                }, habitacionGuardada(roomId, callback));
    }

    @Override
    public void asignarServicio(@NonNull HotelService asignacion,
                               @NonNull ResultCallback<Hotel> callback) {
        final Map<String, Object> datos;
        try {
            datos = HotelFirebase.servicio(asignacion);
            Service servicio = ServiceLocator.hoteles().servicio(asignacion.getServiceId());
            if (servicio == null || !servicio.isActive()) {
                throw new IllegalArgumentException("Ese servicio no está en el catálogo de la aplicación.");
            }
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(asignacion.getHotelId(), "ACTUALIZAR_HOTEL", "Configuró un servicio",
                (original, hotel) -> {
                    List<Map<String, Object>> servicios = mapas(original, "servicios");
                    int posicion = posicion(servicios, "serviceId", asignacion.getServiceId());
                    if (posicion >= 0) {
                        Map<String, Object> combinada = new HashMap<>(servicios.get(posicion));
                        combinada.putAll(datos);
                        servicios.set(posicion, combinada);
                    } else {
                        if (servicios.size() >= HotelFirebase.MAX_SERVICIOS) {
                            throw new IllegalArgumentException("El hotel admite hasta 12 servicios.");
                        }
                        servicios.add(new HashMap<>(datos));
                    }
                    return campo("servicios", servicios);
                }, callback);
    }

    @Override
    public void quitarServicio(@NonNull String hotelId, @NonNull String serviceId,
                               @NonNull ResultCallback<Hotel> callback) {
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Quitó un servicio", (original, hotel) -> {
            List<Map<String, Object>> servicios = mapas(original, "servicios");
            int posicion = posicion(servicios, "serviceId", serviceId);
            if (posicion < 0) throw new IllegalArgumentException("Ese servicio no estaba configurado en el hotel.");
            servicios.remove(posicion);
            return campo("servicios", servicios);
        }, callback);
    }

    @Override
    public void actualizarDatos(@NonNull String hotelId, @NonNull String nombre,
                               @NonNull String descripcion, @NonNull String direccion,
                               double latitud, double longitud,
                               @NonNull ResultCallback<Hotel> callback) {
        final Map<String, Object> datos;
        try {
            datos = HotelFirebase.actualizacionDatos(nombre, descripcion, direccion, latitud, longitud);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Actualizó los datos",
                (original, hotel) -> new HashMap<>(datos), callback);
    }

    @Override
    public void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                                   @NonNull ResultCallback<Hotel> callback) {
        modificar(hotelId, publicado ? "PUBLICAR_HOTEL" : "RETIRAR_HOTEL",
                publicado ? "Publicó el hotel" : "Retiró el hotel del catálogo",
                (original, hotel) -> {
                    if (hotel.isPublicado() == publicado) return Collections.emptyMap();
                    if (publicado && !hotel.aptoParaPublicar()) {
                        if (!hotel.cumpleMinimoFotos() && hotel.getHabitaciones().isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Para publicar el hotel faltan al menos 4 fotografías y una habitación.");
                        }
                        throw new IllegalArgumentException(!hotel.cumpleMinimoFotos()
                                ? "Para publicar el hotel faltan al menos 4 fotografías."
                                : "Para publicar el hotel falta al menos una habitación.");
                    }
                    return campo("publicado", publicado);
                }, callback);
    }

    @Override
    public void agregarFoto(@NonNull String hotelId, @NonNull String url,
                            @NonNull ResultCallback<Hotel> callback) {
        final String foto;
        try {
            foto = HotelFirebase.fotosHotel(Collections.singletonList(url)).get(0);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Agregó una fotografía", (original, hotel) -> {
            List<String> fotos = new ArrayList<>(hotel.getFotos());
            if (fotos.contains(foto)) return Collections.emptyMap();
            fotos.add(foto);
            return campo("fotos", HotelFirebase.fotosHotel(fotos));
        }, callback);
    }

    @Override
    public void quitarFoto(@NonNull String hotelId, @NonNull String url,
                           @NonNull ResultCallback<Hotel> callback) {
        final String foto;
        try {
            foto = HotelFirebase.fotosHotel(Collections.singletonList(url)).get(0);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Quitó una fotografía", (original, hotel) -> {
            List<String> fotos = new ArrayList<>(hotel.getFotos());
            if (!fotos.contains(foto)) throw new IllegalArgumentException("Esa fotografía no está en el hotel.");
            if (fotos.size() - 1 < Hotel.MIN_FOTOS) {
                throw new IllegalArgumentException(
                        "El hotel necesita al menos 4 fotografías. Agrega otra antes de quitar esta.");
            }
            fotos.remove(foto);
            return campo("fotos", HotelFirebase.fotosHotel(fotos));
        }, callback);
    }

    @Override
    public void agregarLugarCercano(@NonNull String hotelId, @NonNull NearbyPlace lugar,
                                   @NonNull ResultCallback<Hotel> callback) {
        final Map<String, Object> datos;
        try {
            datos = HotelFirebase.lugar(lugar);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        modificar(hotelId, "ACTUALIZAR_HOTEL", "Registró un lugar cercano", (original, hotel) -> {
            List<Map<String, Object>> lugares = mapas(original, "lugaresCercanos");
            for (Map<String, Object> existente : lugares) {
                if (iguales(existente, datos)) return Collections.emptyMap();
            }
            if (lugares.size() >= HotelFirebase.MAX_LUGARES) {
                throw new IllegalArgumentException("El hotel admite hasta 10 lugares cercanos.");
            }
            lugares.add(new HashMap<>(datos));
            return campo("lugaresCercanos", lugares);
        }, callback);
    }

    @Override
    public void ventas(@NonNull String hotelId, @NonNull Periodicidad periodicidad,
                       @NonNull ResultCallback<List<PeriodoDeVentas>> callback) {
        FirebaseReservasHotel.consultar(hotelId, new ResultCallback<FirebaseReservasHotel.Datos>() {
            @Override public void onExito(@NonNull FirebaseReservasHotel.Datos datos) {
                if (!datos.vigente()) return;
                List<Booking> ventas = new ArrayList<>();
                for (Booking reserva : datos.reservas) {
                    if (generaIngreso(reserva)) ventas.add(reserva);
                }
                callback.onExito(PeriodoDeVentas.agrupar(periodicidad, ventas));
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override
    public void ingresosPorServicios(@NonNull String hotelId,
                                     @NonNull ResultCallback<List<IngresoPorServicio>> callback) {
        FirebaseReservasHotel.consultar(hotelId, new ResultCallback<FirebaseReservasHotel.Datos>() {
            @Override public void onExito(@NonNull FirebaseReservasHotel.Datos datos) {
                if (datos.vigente()) callback.onExito(calcularIngresos(datos.reservas));
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    private static ResultCallback<FirebaseReservasHotel.Datos> reservasConsultadas(
            ResultCallback<List<ReservaDeHotel>> callback) {
        return new ResultCallback<FirebaseReservasHotel.Datos>() {
            @Override public void onExito(@NonNull FirebaseReservasHotel.Datos datos) {
                FirebaseReservasHotel.detallar(datos, callback);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        };
    }

    private static ResultCallback<FirebaseReservasHotel.Datos> resumenConsultado(
            ResultCallback<ResumenHotel> callback) {
        return new ResultCallback<FirebaseReservasHotel.Datos>() {
            @Override public void onExito(@NonNull FirebaseReservasHotel.Datos datos) {
                FirebaseReservasHotel.detallar(datos, new ResultCallback<List<ReservaDeHotel>>() {
                    @Override public void onExito(@NonNull List<ReservaDeHotel> reservas) {
                        if (!datos.vigente()) return;
                        List<ReservaDeHotel> activas = new ArrayList<>();
                        for (ReservaDeHotel detalle : reservas) {
                            if (detalle.getEstado() == BookingStatus.ACTIVA) activas.add(detalle);
                        }
                        activas.sort(Comparator.comparing((ReservaDeHotel detalle) ->
                                detalle.getReserva().getFechaSalida()).thenComparing(
                                detalle -> detalle.getReserva().getId()));
                        callback.onExito(new ResumenHotel(datos.hotel, activas, calcularIngresos(datos.reservas)));
                    }
                    @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
                });
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        };
    }

    private static boolean generaIngreso(Booking reserva) {
        return reserva.getEstado() == BookingStatus.CONFIRMADA
                || reserva.getEstado() == BookingStatus.ACTIVA
                || reserva.getEstado() == BookingStatus.FINALIZADA;
    }

    /** Se usa el precio contratado en la reserva, aunque luego se edite el catálogo. */
    private static List<IngresoPorServicio> calcularIngresos(List<Booking> reservas) {
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        Map<String, Double> montos = new LinkedHashMap<>();
        for (Booking reserva : reservas) {
            if (!generaIngreso(reserva)) continue;
            for (HotelService servicio : reserva.getServiciosAdicionales()) {
                if (servicio.isIncluded()) continue;
                String id = servicio.getServiceId();
                cantidades.put(id, cantidades.getOrDefault(id, 0) + 1);
                montos.put(id, montos.getOrDefault(id, 0d) + servicio.getPrice());
            }
        }
        List<IngresoPorServicio> ingresos = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : cantidades.entrySet()) {
            Service servicio = ServiceLocator.hoteles().servicio(entrada.getKey());
            ingresos.add(new IngresoPorServicio(entrada.getKey(),
                    servicio == null ? entrada.getKey() : servicio.getName(),
                    entrada.getValue(), montos.get(entrada.getKey())));
        }
        ingresos.sort(Comparator.comparingDouble(IngresoPorServicio::getMontoTotal)
                .thenComparing(IngresoPorServicio::getServiceId));
        return ingresos;
    }

    private void modificar(String hotelId, String accion, String detalle, Cambio cambio,
                           ResultCallback<Hotel> callback) {
        Actor actor = actor(hotelId, callback);
        if (actor == null) return;
        FirebaseFirestore base = FirebaseFirestore.getInstance();
        DocumentReference perfilRef = base.collection("usuarios").document(actor.uid);
        DocumentReference hotelRef = base.collection("hoteles").document(hotelId);
        DocumentReference eventoRef = base.collection("auditoria").document(UUID.randomUUID().toString());
        AtomicReference<Etapa> etapa = new AtomicReference<>(Etapa.LEER_PERFIL);
        base.runTransaction(transaccion -> {
            etapa.set(Etapa.LEER_PERFIL);
            DocumentSnapshot perfil = transaccion.get(perfilRef);
            etapa.set(Etapa.LEER_HOTEL);
            DocumentSnapshot documento = transaccion.get(hotelRef);
            Cuenta cuenta = PerfilFirebase.cuenta(actor.uid, actor.email, perfil.getData());
            if (!documento.exists() || documento.getData() == null) {
                throw new IllegalArgumentException("No encontramos el hotel en el servidor.");
            }
            Map<String, Object> original = documento.getData();
            Hotel actual = HotelFirebase.desde(hotelId, original);
            if (cuenta.getRol() != Role.ADMIN_HOTEL || !hotelId.equals(cuenta.getHotelId())
                    || !actor.uid.equals(actual.getAdministradorId())) {
                throw new IllegalArgumentException("Tu cuenta ya no administra este hotel.");
            }

            Map<String, Object> cambios = new HashMap<>(cambio.aplicar(original, actual));
            Map<String, Object> combinado = new HashMap<>(original);
            combinado.putAll(cambios);
            Hotel resultado = HotelFirebase.desde(hotelId, combinado);
            if (!cambios.isEmpty() && "ACTUALIZAR_HOTEL".equals(accion)) {
                // Rules exige este derivado también al editar datos, fotos o servicios.
                // Repara documentos anteriores sin convertir una lectura en escritura ni
                // agregar campos a las transiciones de publicación.
                double precioDesde = resultado.getHabitaciones().isEmpty()
                        ? 0d : resultado.getPrecioDesde();
                cambios.put("precioDesde", precioDesde);
                resultado.setPrecioDesde(precioDesde);
            }
            Iterator<Map.Entry<String, Object>> campos = cambios.entrySet().iterator();
            while (campos.hasNext()) {
                Map.Entry<String, Object> campo = campos.next();
                if (iguales(original.get(campo.getKey()), campo.getValue())) campos.remove();
            }
            // Un formulario sin cambios no genera un evento ni una escritura de metadatos.
            if (cambios.isEmpty()) return actual;
            if ("ACTUALIZAR_HOTEL".equals(accion)) validarBaseParaEditar(combinado);
            if (resultado.isPublicado() && !resultado.aptoParaPublicar()) {
                throw new IllegalArgumentException(
                        "Un hotel publicado debe conservar al menos 4 fotografías y una habitación.");
            }
            cambios.put("actualizadoEn", FieldValue.serverTimestamp());
            cambios.put("ultimoEventoId", eventoRef.getId());
            etapa.set(Etapa.GUARDAR);
            transaccion.update(hotelRef, cambios);
            transaccion.set(eventoRef, auditoria(cuenta, hotelId, accion,
                    detalle + ": " + resultado.getNombre() + "."));
            return resultado;
        }).addOnSuccessListener(hotel -> {
            if (!vigente(actor)) {
                callback.onError("La sesión o el hotel asignado cambió. Vuelve a cargar esta pantalla.");
                return;
            }
            if (ServiceLocator.hoteles() instanceof FirebaseHotelRepository) {
                ((FirebaseHotelRepository) ServiceLocator.hoteles()).guardarEnCache(hotel);
            }
            callback.onExito(hotel);
        }).addOnFailureListener(error -> {
            if (error instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) error).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w("HotelFirestore", "Firestore rechazó " + accion + " en " + etapa.get(), error);
            }
            callback.onError(vigente(actor) ? mensaje(error, etapa.get())
                    : "La sesión o el hotel asignado cambió. Vuelve a cargar esta pantalla.");
        });
    }

    /** No repara metadatos de origen ni elimina campos desconocidos silenciosamente. */
    private static void validarBaseParaEditar(Map<String, Object> datos) {
        for (String campo : datos.keySet()) {
            if (!CAMPOS_HOTEL.contains(campo)) {
                throw new IllegalArgumentException("El documento del hotel tiene un campo no admitido "
                        + "por las reglas: " + campo + ". Revisa sus datos en Firestore.");
            }
        }
        for (String campo : Arrays.asList("nombre", "ciudad", "distrito", "direccion", "latitud",
                "longitud", "publicado", "administradorId", "creadoPor", "creadoEn")) {
            if (!datos.containsKey(campo)) {
                throw new IllegalArgumentException("Falta el campo " + campo
                        + " en el documento del hotel. Revisa sus datos en Firestore.");
            }
        }
        if (!(datos.get("creadoEn") instanceof Timestamp)) {
            throw new IllegalArgumentException("El campo creadoEn del hotel debe ser de tipo timestamp "
                    + "en Firestore, no texto ni un número.");
        }
        Object creador = datos.get("creadoPor");
        if (!(creador instanceof String) || ((String) creador).trim().isEmpty()
                || ((String) creador).length() > 200 || ((String) creador).contains("/")) {
            throw new IllegalArgumentException("El campo creadoPor del hotel debe contener "
                    + "el UID de quien lo creó. Revisa sus datos en Firestore.");
        }
        if (datos.containsKey("numReviews") && !(datos.get("numReviews") instanceof Long)
                && !(datos.get("numReviews") instanceof Integer)) {
            throw new IllegalArgumentException("El campo numReviews del hotel debe ser un entero "
                    + "en Firestore, no un decimal ni texto.");
        }
    }

    private static Actor actor(String hotelId, ResultCallback<?> callback) {
        if (hotelId == null || hotelId.trim().isEmpty() || hotelId.contains("/") || hotelId.length() > 200) {
            callback.onError("Selecciona un hotel válido.");
            return null;
        }
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || SessionManager.getRolActivo() != Role.ADMIN_HOTEL
                || !usuario.getUid().equals(SessionManager.getIdentidadId())
                || !hotelId.equals(SessionManager.getHotelAdministrado())) {
            callback.onError("Inicia sesión con la cuenta que administra este hotel.");
            return null;
        }
        return new Actor(usuario.getUid(), usuario.getEmail(), hotelId);
    }

    private static boolean vigente(Actor actor) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario != null && actor.uid.equals(usuario.getUid())
                && actor.uid.equals(SessionManager.getIdentidadId())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                && actor.hotelId.equals(SessionManager.getHotelAdministrado());
    }

    private static Map<String, Object> auditoria(Cuenta autor, String hotelId, String accion,
                                                String detalle) {
        Map<String, Object> evento = new HashMap<>();
        evento.put("actorUid", autor.getIdentidadId());
        evento.put("actorNombre", autor.getNombreCompleto());
        evento.put("evento", LogEntry.Evento.ACCION_ADMINISTRATIVA.name());
        evento.put("accion", accion);
        evento.put("detalle", detalle);
        evento.put("hotelId", hotelId);
        evento.put("administradorId", autor.getIdentidadId());
        evento.put("timestamp", FieldValue.serverTimestamp());
        return evento;
    }

    private static ResultCallback<Hotel> habitacionGuardada(String roomId, ResultCallback<Room> callback) {
        return new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel hotel) {
                for (Room habitacion : hotel.getHabitaciones()) {
                    if (roomId.equals(habitacion.getId())) {
                        callback.onExito(habitacion);
                        return;
                    }
                }
                callback.onError("No pudimos recuperar la habitación guardada.");
            }

            @Override
            public void onError(@NonNull String mensaje) {
                callback.onError(mensaje);
            }
        };
    }

    private static Map<String, Object> campo(String nombre, Object valor) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(nombre, valor);
        return mapa;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapas(Map<String, Object> original, String campo) {
        List<Map<String, Object>> copia = new ArrayList<>();
        Object valores = original.get(campo);
        if (valores == null) return copia;
        if (!(valores instanceof List<?>)) throw new IllegalArgumentException("El campo " + campo + " no es válido.");
        for (Object valor : (List<?>) valores) {
            if (!(valor instanceof Map<?, ?>)) throw new IllegalArgumentException("El campo " + campo + " no es válido.");
            copia.add(new HashMap<>((Map<String, Object>) valor));
        }
        return copia;
    }

    private static int posicion(List<Map<String, Object>> lista, String campo, String id) {
        for (int i = 0; i < lista.size(); i++) {
            if (Objects.equals(id, lista.get(i).get(campo))) return i;
        }
        return -1;
    }

    private static boolean iguales(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        if (a instanceof List<?> && b instanceof List<?>) {
            List<?> izquierda = (List<?>) a;
            List<?> derecha = (List<?>) b;
            if (izquierda.size() != derecha.size()) return false;
            for (int i = 0; i < izquierda.size(); i++) {
                if (!iguales(izquierda.get(i), derecha.get(i))) return false;
            }
            return true;
        }
        if (a instanceof Map<?, ?> && b instanceof Map<?, ?>) {
            Map<?, ?> izquierda = (Map<?, ?>) a;
            Map<?, ?> derecha = (Map<?, ?>) b;
            if (!izquierda.keySet().equals(derecha.keySet())) return false;
            for (Object clave : izquierda.keySet()) {
                if (!iguales(izquierda.get(clave), derecha.get(clave))) return false;
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    private static String mensaje(Exception error, Etapa etapa) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            switch (etapa) {
                case LEER_PERFIL:
                    return "Firestore no permite leer tu perfil. Revisa las reglas publicadas para usuarios.";
                case LEER_HOTEL:
                    return "Tu perfil tiene un hotel asignado, pero Firestore no permite consultarlo. "
                            + "Revisa el administrador del hotel y las reglas publicadas.";
                case GUARDAR:
                default:
                    return "Firestore permitió consultar tu hotel, pero rechazó guardar el hotel o su auditoría. "
                            + "Revisa las reglas publicadas y los datos del hotel.";
            }
        }
        return mensaje(error);
    }

    private static String mensaje(Exception error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if ((causa instanceof IllegalArgumentException || causa instanceof IllegalStateException)
                    && causa.getMessage() != null) return causa.getMessage();
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                    return "No tienes permiso para cambiar este hotel. Revisa la asignación y las reglas publicadas.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.";
                case UNAUTHENTICATED:
                    return "Tu sesión venció. Inicia sesión de nuevo.";
                default:
                    break;
            }
        }
        return "No pudimos completar la operación del hotel. Inténtalo de nuevo.";
    }

}
