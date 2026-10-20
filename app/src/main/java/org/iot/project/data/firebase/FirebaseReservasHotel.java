package org.iot.project.data.firebase;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Consultas del hotel asignado, sin recurrir a reservas ni perfiles de demostración. */
public final class FirebaseReservasHotel {
    private static final AtomicLong GENERACION = new AtomicLong();
    private static String identidad;
    private static boolean observandoIdentidad;

    private FirebaseReservasHotel() { }

    public static final class Contexto {
        public final String uid;
        public final Hotel hotel;
        private final String email;
        private final long generacion;

        private Contexto(String uid, String email, Hotel hotel, long generacion) {
            this.uid = uid;
            this.email = email;
            this.hotel = hotel;
            this.generacion = generacion;
        }

        public boolean vigente() {
            FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
            return generacion == GENERACION.get() && actual != null
                    && uid.equals(actual.getUid()) && uid.equals(SessionManager.getIdentidadId())
                    && SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                    && hotel.getId().equals(SessionManager.getHotelAdministrado());
        }
    }

    public static final class Datos {
        public final Hotel hotel;
        public final List<Booking> reservas;
        private final Contexto contexto;
        private final BooleanSupplier solicitudVigente;

        private Datos(Contexto contexto, List<Booking> reservas) {
            this(contexto, reservas, () -> true);
        }

        private Datos(Contexto contexto, List<Booking> reservas, BooleanSupplier solicitudVigente) {
            this.contexto = contexto;
            this.hotel = contexto.hotel;
            this.reservas = Collections.unmodifiableList(new ArrayList<>(reservas));
            this.solicitudVigente = solicitudVigente;
        }

        public boolean vigente() {
            return contexto.vigente() && solicitudVigente.getAsBoolean();
        }
    }

    private static synchronized void observarIdentidad() {
        if (observandoIdentidad) return;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser actual = auth.getCurrentUser();
        identidad = actual == null ? null : actual.getUid();
        observandoIdentidad = true;
        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser siguiente = firebaseAuth.getCurrentUser();
            String uid = siguiente == null ? null : siguiente.getUid();
            if (!Objects.equals(identidad, uid)) {
                identidad = uid;
                GENERACION.incrementAndGet();
            }
        });
    }

    /** La transacción de lectura verifica perfil activo y asignación recíproca en el servidor. */
    public static void autorizar(String hotelId, ResultCallback<Contexto> callback) {
        observarIdentidad();
        try {
            BookingFirebase.exigirId(hotelId);
        } catch (IllegalArgumentException error) {
            callback.onError("Tu cuenta todavía no tiene un hotel válido asignado.");
            return;
        }
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (actual == null || !actual.getUid().equals(SessionManager.getIdentidadId())
                || SessionManager.getRolActivo() != Role.ADMIN_HOTEL
                || !hotelId.equals(SessionManager.getHotelAdministrado())) {
            callback.onError("Inicia sesión con la cuenta que administra este hotel.");
            return;
        }
        String uid = actual.getUid();
        String email = actual.getEmail();
        long generacion = GENERACION.get();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaccion -> {
            DocumentSnapshot perfil = transaccion.get(db.collection("usuarios").document(uid));
            DocumentSnapshot documento = transaccion.get(db.collection("hoteles").document(hotelId));
            Cuenta cuenta = PerfilFirebase.cuenta(uid, email, perfil.getData());
            if (!documento.exists()) throw new IllegalArgumentException("No encontramos tu hotel en el servidor.");
            Hotel hotel = HotelFirebase.desde(hotelId, documento.getData());
            Contexto contexto = new Contexto(uid, email, hotel, generacion);
            exigirAsignacion(contexto, cuenta, hotel);
            if (!contexto.vigente()) throw new IllegalArgumentException(sesionCambiada());
            return contexto;
        }).addOnSuccessListener(contexto -> {
            if (!contexto.vigente()) {
                callback.onError(sesionCambiada());
                return;
            }
            if (ServiceLocator.hoteles() instanceof FirebaseHotelRepository) {
                ((FirebaseHotelRepository) ServiceLocator.hoteles()).guardarEnCache(contexto.hotel);
            }
            callback.onExito(contexto);
        }).addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    public static void consultar(String hotelId, ResultCallback<Datos> callback) {
        autorizar(hotelId, new ResultCallback<Contexto>() {
            @Override public void onExito(@NonNull Contexto contexto) {
                FirebaseFirestore.getInstance().collection("reservas").whereEqualTo("hotelId", hotelId)
                        .get(Source.SERVER).addOnSuccessListener(documentos -> {
                            if (!contexto.vigente()) {
                                callback.onError(sesionCambiada());
                                return;
                            }
                            try {
                                List<Booking> reservas = new ArrayList<>();
                                for (DocumentSnapshot documento : documentos.getDocuments()) {
                                    reservas.add(leerReserva(documento, hotelId));
                                }
                                reservas.sort(Comparator.comparing(Booking::getFechaEntrada).reversed()
                                        .thenComparing(Booking::getId));
                                callback.onExito(new Datos(contexto, reservas));
                            } catch (IllegalArgumentException error) {
                                callback.onError(mensaje(error));
                            }
                        }).addOnFailureListener(error -> callback.onError(
                                contexto.vigente() ? mensaje(error) : sesionCambiada()));
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    public static void consultarReserva(String hotelId, String bookingId, ResultCallback<Datos> callback) {
        try {
            BookingFirebase.exigirId(bookingId);
        } catch (IllegalArgumentException error) {
            callback.onError("No encontramos esta reserva en tu hotel.");
            return;
        }
        autorizar(hotelId, new ResultCallback<Contexto>() {
            @Override public void onExito(@NonNull Contexto contexto) {
                FirebaseFirestore.getInstance().collection("reservas").document(bookingId).get(Source.SERVER)
                        .addOnSuccessListener(documento -> {
                            if (!contexto.vigente()) {
                                callback.onError(sesionCambiada());
                                return;
                            }
                            try {
                                callback.onExito(new Datos(contexto,
                                        Collections.singletonList(leerReserva(documento, hotelId))));
                            } catch (IllegalArgumentException error) {
                                callback.onError(mensaje(error));
                            }
                        }).addOnFailureListener(error -> callback.onError(
                                contexto.vigente() ? mensaje(error) : sesionCambiada()));
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    /** Una lectura por cliente, aunque tenga varias reservas; una cuenta eliminada conserva su estancia. */
    public static void detallar(Datos datos, ResultCallback<List<ReservaDeHotel>> callback) {
        if (!datos.vigente()) return;
        Map<String, Integer> posiciones = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> tareas = new ArrayList<>();
        for (Booking reserva : datos.reservas) {
            if (posiciones.containsKey(reserva.getClienteId())) continue;
            posiciones.put(reserva.getClienteId(), tareas.size());
            tareas.add(FirebaseFirestore.getInstance().collection("usuarios")
                    .document(reserva.getClienteId()).get(Source.SERVER));
        }
        if (tareas.isEmpty()) {
            callback.onExito(Collections.emptyList());
            return;
        }
        Tasks.<DocumentSnapshot>whenAllSuccess(tareas).addOnSuccessListener(perfiles -> {
            if (!datos.vigente()) return;
            try {
                Map<String, User> clientes = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entrada : posiciones.entrySet()) {
                    clientes.put(entrada.getKey(), clienteHistorico(perfiles.get(entrada.getValue())));
                }
                List<ReservaDeHotel> resultado = new ArrayList<>();
                for (Booking reserva : datos.reservas) {
                    String numero = reserva.getHabitacionNumero();
                    String tipo = reserva.getHabitacionTipo();
                    for (Room habitacion : datos.hotel.getHabitaciones()) {
                        if (!habitacion.getId().equals(reserva.getRoomId())) continue;
                        if (numero.isEmpty()) numero = habitacion.getNumero();
                        if (tipo.isEmpty()) tipo = habitacion.getTipo();
                        break;
                    }
                    resultado.add(new ReservaDeHotel(reserva, clientes.get(reserva.getClienteId()), numero, tipo));
                }
                callback.onExito(resultado);
            } catch (IllegalArgumentException error) {
                callback.onError(mensaje(error));
            }
        }).addOnFailureListener(error -> {
            if (datos.vigente()) callback.onError(mensaje(error));
        });
    }

    public static GestionHotelRepository.Suscripcion escuchar(String hotelId, ResultCallback<Datos> callback) {
        Escucha escucha = new Escucha(hotelId, callback);
        autorizar(hotelId, new ResultCallback<Contexto>() {
            @Override public void onExito(@NonNull Contexto contexto) {
                if (!escucha.cerrada) escucha.iniciar(contexto);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!escucha.cerrada) callback.onError(mensaje);
            }
        });
        return escucha;
    }

    public static GestionHotelRepository.Suscripcion escucharReserva(String hotelId, String bookingId,
                                                                     ResultCallback<Datos> callback) {
        try {
            BookingFirebase.exigirId(bookingId);
        } catch (IllegalArgumentException error) {
            callback.onError("No encontramos esta reserva en tu hotel.");
            return () -> { };
        }
        return escuchar(hotelId, new ResultCallback<Datos>() {
            @Override public void onExito(@NonNull Datos datos) {
                for (Booking reserva : datos.reservas) {
                    if (bookingId.equals(reserva.getId())) {
                        callback.onExito(new Datos(datos.contexto, Collections.singletonList(reserva),
                                datos.solicitudVigente));
                        return;
                    }
                }
                callback.onError("No encontramos esta reserva en tu hotel.");
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    private static final class Escucha implements GestionHotelRepository.Suscripcion {
        private final String hotelId;
        private final ResultCallback<Datos> callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final List<ListenerRegistration> listeners = new ArrayList<>();
        private boolean cerrada;
        private long revision;
        private Runnable pendiente;
        private Contexto contexto;

        private Escucha(String hotelId, ResultCallback<Datos> callback) {
            this.hotelId = hotelId;
            this.callback = callback;
        }

        private void iniciar(Contexto acceso) {
            contexto = acceso;
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            registrar(db.collection("usuarios").document(acceso.uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (perfil, error) -> {
                        if (cerrada || !comprobarSesion()) return;
                        if (error != null) { fallar(error); return; }
                        if (!servidor(perfil)) return;
                        try {
                            Cuenta cuenta = PerfilFirebase.cuenta(acceso.uid, acceso.email, perfil.getData());
                            exigirAsignacion(acceso, cuenta, acceso.hotel);
                            programar();
                        } catch (IllegalArgumentException invalido) { fallar(invalido); }
                    }));
            registrar(db.collection("hoteles").document(hotelId)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (hotel, error) -> {
                        if (cerrada || !comprobarSesion()) return;
                        if (error != null) { fallar(error); return; }
                        if (!servidor(hotel)) return;
                        try {
                            Hotel actual = HotelFirebase.desde(hotelId, hotel.getData());
                            if (!acceso.uid.equals(actual.getAdministradorId())) {
                                throw new IllegalArgumentException("Tu cuenta ya no administra este hotel.");
                            }
                            programar();
                        } catch (IllegalArgumentException invalido) { fallar(invalido); }
                    }));
            registrar(db.collection("reservas").whereEqualTo("hotelId", hotelId)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (reservas, error) -> {
                        if (cerrada || !comprobarSesion()) return;
                        if (error != null) { fallar(error); return; }
                        if (reservas == null || reservas.getMetadata().isFromCache()
                                || reservas.getMetadata().hasPendingWrites()) return;
                        programar();
                    }));
            programar();
        }

        private void registrar(ListenerRegistration listener) {
            if (cerrada) listener.remove();
            else listeners.add(listener);
        }

        private void programar() {
            if (cerrada) return;
            long solicitud = ++revision;
            if (pendiente != null) handler.removeCallbacks(pendiente);
            pendiente = () -> consultar(hotelId, new ResultCallback<Datos>() {
                @Override public void onExito(@NonNull Datos datos) {
                    if (!cerrada && solicitud == revision && comprobarSesion()) {
                        callback.onExito(new Datos(datos.contexto, datos.reservas,
                                () -> !cerrada && solicitud == revision));
                    }
                }
                @Override public void onError(@NonNull String mensaje) {
                    if (!cerrada && solicitud == revision) callback.onError(mensaje);
                }
            });
            // Las tres suscripciones emiten una primera instantánea: se agrupan en una sola lectura.
            handler.postDelayed(pendiente, 80L);
        }

        private boolean comprobarSesion() {
            if (contexto.vigente()) return true;
            callback.onError(sesionCambiada());
            cerrar();
            return false;
        }

        private void fallar(Exception error) {
            if (cerrada) return;
            callback.onError(mensaje(error));
            if (error instanceof IllegalArgumentException || error instanceof FirebaseFirestoreException
                    && (((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.UNAUTHENTICATED)) {
                cerrar();
            }
        }

        @Override public void cerrar() {
            cerrada = true;
            revision++;
            if (pendiente != null) handler.removeCallbacks(pendiente);
            for (ListenerRegistration listener : listeners) listener.remove();
            listeners.clear();
        }
    }

    private static boolean servidor(DocumentSnapshot documento) {
        return documento != null && !documento.getMetadata().isFromCache()
                && !documento.getMetadata().hasPendingWrites();
    }

    private static Booking leerReserva(DocumentSnapshot documento, String hotelId) {
        if (!documento.exists() || !hotelId.equals(documento.get("hotelId"))) {
            throw new IllegalArgumentException("No encontramos esta reserva en tu hotel.");
        }
        return BookingFirebase.desde(documento.getId(), documento.getData());
    }

    private static void exigirAsignacion(Contexto contexto, Cuenta cuenta, Hotel hotel) {
        if (cuenta.getRol() != Role.ADMIN_HOTEL || !hotel.getId().equals(cuenta.getHotelId())
                || !contexto.uid.equals(hotel.getAdministradorId())) {
            throw new IllegalArgumentException("Tu cuenta ya no administra este hotel.");
        }
    }

    /** El estado o rol actual de un antiguo huésped no elimina la información de su reserva. */
    private static User clienteHistorico(DocumentSnapshot documento) {
        if (!documento.exists()) return null;
        Map<String, Object> datos = documento.getData();
        if (datos == null) return null;
        User cliente = new User(documento.getId(), texto(datos, "nombres"), texto(datos, "apellidos"), Role.CLIENTE);
        cliente.setEmail(texto(datos, "email"));
        if (cliente.getNombreCompleto().trim().isEmpty()) cliente.setNombres(cliente.getEmail());
        cliente.setTelefono(texto(datos, "telefono"));
        cliente.setDireccion(texto(datos, "direccion"));
        cliente.withFoto(texto(datos, "fotoUrl"));
        cliente.withDocumento(texto(datos, "tipoDocumento"), texto(datos, "numeroDocumento"));
        cliente.withNacimiento(texto(datos, "fechaNacimiento"));
        cliente.setActivo(Boolean.TRUE.equals(datos.get("activo")));
        return cliente;
    }

    private static String texto(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return "";
        if (!(valor instanceof String)) throw new IllegalArgumentException("Un perfil de huésped tiene un campo inválido: " + campo + ".");
        return ((String) valor).trim();
    }

    private static String sesionCambiada() {
        return "La sesión o el hotel asignado cambió. Vuelve a cargar esta pantalla.";
    }

    public static String mensaje(Exception error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException && causa.getMessage() != null) return causa.getMessage();
            if (causa instanceof FirebaseFirestoreException) {
                switch (((FirebaseFirestoreException) causa).getCode()) {
                    case PERMISSION_DENIED: return "Firestore rechazó la consulta de tu hotel, sus reservas o sus huéspedes.";
                    case UNAUTHENTICATED: return "Tu sesión venció. Inicia sesión de nuevo.";
                    case UNAVAILABLE:
                    case DEADLINE_EXCEEDED: return "No pudimos consultar el servidor. Revisa tu conexión e inténtalo de nuevo.";
                    default: break;
                }
            }
        }
        return "No pudimos cargar las reservas de tu hotel. Inténtalo de nuevo.";
    }
}
