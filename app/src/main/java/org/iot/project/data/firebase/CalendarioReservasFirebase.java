package org.iot.project.data.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.models.Booking;
import org.iot.project.models.Hotel;
import org.iot.project.models.Role;
import org.iot.project.models.Room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agenda mensual que comparte una transacción de reserva con habitación y cliente.
 * Las noches incluyen la entrada y excluyen la salida: el día de salida queda libre.
 */
public final class CalendarioReservasFirebase {
    private static final String HABITACIONES = "ocupacionHabitaciones";
    private static final String CLIENTES = "agendasClientes";
    private static final int MAX_NOCHES = 90;

    private CalendarioReservasFirebase() { }

    /** El llamador debe leer todas estas referencias antes de realizar escrituras. */
    public static List<DocumentReference> referencias(FirebaseFirestore db, Booking reserva) {
        List<DocumentReference> referencias = new ArrayList<>();
        for (Calendario calendario : calendarios(reserva)) {
            referencias.add(db.collection(calendario.coleccion).document(calendario.id));
        }
        return referencias;
    }

    /**
     * Ocupa simultáneamente las noches de la habitación y de la agenda del cliente.
     * Repetir el mismo identificador no crea una ocupación duplicada.
     */
    public static void reservar(Transaction tx, Booking reserva, List<DocumentSnapshot> docs) {
        identificador(reserva.getId());
        List<Calendario> calendarios = calendarios(reserva);
        Map<String, DocumentSnapshot> documentos = documentos(calendarios, docs);
        List<Cambio> cambios = new ArrayList<>();
        for (Calendario calendario : calendarios) {
            DocumentSnapshot documento = documentos.get(calendario.ruta());
            Map<String, String> noches = leerNoches(documento, calendario);
            for (LocalDate noche : noches(reserva.getFechaEntrada(), reserva.getFechaSalida())) {
                if (!YearMonth.from(noche).equals(calendario.mes)) continue;
                String ocupante = noches.get(noche.toString());
                if (ocupante != null && !ocupante.equals(reserva.getId())) {
                    throw new IllegalArgumentException(calendario.habitacion
                            ? "La habitación ya fue reservada en esas fechas. Elige otro período."
                            : "Ya tienes una reserva en esas fechas. Elige otro período.");
                }
                noches.put(noche.toString(), reserva.getId());
            }
            cambios.add(new Cambio(documento.getReference(), calendario, noches));
        }
        // Ninguna lectura ni validación puede quedar después de la primera escritura.
        for (Cambio cambio : cambios) tx.set(cambio.referencia, cambio.datos());
    }

    /** Libera exclusivamente las noches que todavía pertenecen a esta reserva. */
    public static void liberar(Transaction tx, Booking reserva, List<DocumentSnapshot> docs) {
        identificador(reserva.getId());
        List<Calendario> calendarios = calendarios(reserva);
        Map<String, DocumentSnapshot> documentos = documentos(calendarios, docs);
        List<Cambio> cambios = new ArrayList<>();
        for (Calendario calendario : calendarios) {
            DocumentSnapshot documento = documentos.get(calendario.ruta());
            Map<String, String> noches = leerNoches(documento, calendario);
            boolean modificado = false;
            for (LocalDate noche : noches(reserva.getFechaEntrada(), reserva.getFechaSalida())) {
                String fecha = noche.toString();
                if (YearMonth.from(noche).equals(calendario.mes)
                        && reserva.getId().equals(noches.get(fecha))) {
                    noches.remove(fecha);
                    modificado = true;
                }
            }
            if (modificado) cambios.add(new Cambio(documento.getReference(), calendario, noches));
        }
        for (Cambio cambio : cambios) {
            if (cambio.noches.isEmpty()) tx.delete(cambio.referencia);
            else tx.set(cambio.referencia, cambio.datos());
        }
    }

    /** Lee del servidor la ocupación de todas las habitaciones habilitadas. */
    public static void disponibles(Hotel hotel, LocalDate entrada, LocalDate salida,
                                   ResultCallback<List<Room>> callback) {
        final String uid = SessionManager.getUsuarioId();
        final Role rol = SessionManager.getRolActivo();
        final String hotelAdministrado = SessionManager.getHotelAdministrado();
        if (!vigente(uid, rol, hotelAdministrado)) {
            callback.onError("Inicia sesión de nuevo para consultar la disponibilidad.");
            return;
        }
        final List<LocalDate> periodo;
        try {
            identificador(hotel.getId());
            periodo = noches(entrada, salida);
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        if (rol == Role.CLIENTE && !hotel.isPublicado()) {
            callback.onError("Este hotel ya no está disponible para reservar.");
            return;
        }

        List<Room> habitaciones = new ArrayList<>();
        Map<String, Calendario> calendarios = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> lecturas = new ArrayList<>();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        try {
            for (Room habitacion : hotel.getHabitaciones()) {
                if (!habitacion.isDisponible()) continue;
                identificador(habitacion.getId());
                if (!hotel.getId().equals(habitacion.getHotelId())) {
                    throw new IllegalArgumentException("Los datos de la habitación no corresponden al hotel.");
                }
                habitaciones.add(habitacion);
                for (YearMonth mes : meses(periodo)) {
                    Calendario calendario = Calendario.habitacion(hotel.getId(), habitacion.getId(), mes);
                    if (!calendarios.containsKey(calendario.ruta())) {
                        calendarios.put(calendario.ruta(), calendario);
                        lecturas.add(db.collection(calendario.coleccion).document(calendario.id).get(Source.SERVER));
                    }
                }
            }
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        if (lecturas.isEmpty()) {
            callback.onExito(habitaciones);
            return;
        }
        Tasks.<DocumentSnapshot>whenAllSuccess(lecturas).addOnSuccessListener(documentos -> {
            if (!vigente(uid, rol, hotelAdministrado)) {
                callback.onError("La sesión cambió. Vuelve a consultar la disponibilidad.");
                return;
            }
            try {
                Map<String, Map<String, String>> ocupacion = new HashMap<>();
                for (DocumentSnapshot documento : documentos) {
                    Calendario calendario = calendarios.get(documento.getReference().getPath());
                    if (calendario == null) throw datosInvalidos();
                    ocupacion.put(calendario.ruta(), leerNoches(documento, calendario));
                }
                List<Room> libres = new ArrayList<>();
                for (Room habitacion : habitaciones) {
                    boolean libre = true;
                    for (LocalDate noche : periodo) {
                        Calendario calendario = Calendario.habitacion(hotel.getId(), habitacion.getId(), YearMonth.from(noche));
                        Map<String, String> mes = ocupacion.get(calendario.ruta());
                        if (mes == null) throw datosInvalidos();
                        if (mes.containsKey(noche.toString())) {
                            libre = false;
                            break;
                        }
                    }
                    if (libre) libres.add(habitacion);
                }
                libres.sort(Comparator.comparingDouble(Room::getPrecioNoche));
                callback.onExito(libres);
            } catch (IllegalArgumentException error) {
                callback.onError(error.getMessage());
            }
        }).addOnFailureListener(error -> {
            if (!vigente(uid, rol, hotelAdministrado)) {
                callback.onError("La sesión cambió. Vuelve a consultar la disponibilidad.");
                return;
            }
            callback.onError(FirebaseErrores.mensaje(error));
        });
    }

    private static boolean vigente(String uid, Role rol, String hotelAdministrado) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return uid != null && !uid.isEmpty() && rol != null && rol != Role.CONDUCTOR
                && usuario != null && uid.equals(usuario.getUid())
                && uid.equals(SessionManager.getUsuarioId()) && rol == SessionManager.getRolActivo()
                && Objects.equals(hotelAdministrado, SessionManager.getHotelAdministrado());
    }

    private static List<Calendario> calendarios(Booking reserva) {
        identificador(reserva.getHotelId());
        identificador(reserva.getRoomId());
        identificador(reserva.getClienteId());
        List<Calendario> calendarios = new ArrayList<>();
        for (YearMonth mes : meses(noches(reserva.getFechaEntrada(), reserva.getFechaSalida()))) {
            calendarios.add(Calendario.habitacion(reserva.getHotelId(), reserva.getRoomId(), mes));
            calendarios.add(Calendario.cliente(reserva.getClienteId(), mes));
        }
        return calendarios;
    }

    private static List<LocalDate> noches(LocalDate entrada, LocalDate salida) {
        if (entrada == null || salida == null || entrada.getYear() < 1 || salida.getYear() > 9999
                || !salida.isAfter(entrada)) {
            throw new IllegalArgumentException("La salida debe ser posterior a la entrada.");
        }
        long cantidad = ChronoUnit.DAYS.between(entrada, salida);
        if (cantidad > MAX_NOCHES) {
            throw new IllegalArgumentException("La reserva puede tener hasta 90 noches.");
        }
        List<LocalDate> noches = new ArrayList<>();
        for (LocalDate fecha = entrada; fecha.isBefore(salida); fecha = fecha.plusDays(1)) noches.add(fecha);
        return noches;
    }

    private static List<YearMonth> meses(List<LocalDate> noches) {
        List<YearMonth> meses = new ArrayList<>();
        for (LocalDate noche : noches) {
            YearMonth mes = YearMonth.from(noche);
            if (meses.isEmpty() || !meses.get(meses.size() - 1).equals(mes)) meses.add(mes);
        }
        return meses;
    }

    private static Map<String, DocumentSnapshot> documentos(List<Calendario> esperados,
                                                           List<DocumentSnapshot> docs) {
        if (docs == null || docs.size() != esperados.size()) throw datosInvalidos();
        Map<String, DocumentSnapshot> documentos = new HashMap<>();
        for (DocumentSnapshot documento : docs) {
            if (documento == null || documentos.put(documento.getReference().getPath(), documento) != null) {
                throw datosInvalidos();
            }
        }
        for (Calendario calendario : esperados) {
            if (!documentos.containsKey(calendario.ruta())) throw datosInvalidos();
        }
        return documentos;
    }

    /** Una agenda existente mal formada nunca equivale a un mes vacío. */
    private static Map<String, String> leerNoches(DocumentSnapshot documento, Calendario calendario) {
        Map<String, String> noches = new HashMap<>();
        if (!documento.exists()) return noches;
        Map<String, Object> datos = documento.getData();
        if (datos == null || !calendario.mes.toString().equals(datos.get("mes"))) throw datosInvalidos();
        if (calendario.habitacion) {
            if (!calendario.hotelId.equals(datos.get("hotelId")) || !calendario.roomId.equals(datos.get("roomId"))) {
                throw datosInvalidos();
            }
        } else if (!calendario.clienteId.equals(datos.get("clienteId"))) throw datosInvalidos();
        Object valor = datos.get("noches");
        if (!(valor instanceof Map)) throw datosInvalidos();
        Map<?, ?> mapa = (Map<?, ?>) valor;
        if (mapa.size() > calendario.mes.lengthOfMonth()) throw datosInvalidos();
        for (Map.Entry<?, ?> noche : mapa.entrySet()) {
            if (!(noche.getKey() instanceof String) || !(noche.getValue() instanceof String)) throw datosInvalidos();
            String fecha = (String) noche.getKey();
            String reservaId = (String) noche.getValue();
            identificador(reservaId);
            try {
                LocalDate dia = LocalDate.parse(fecha);
                if (!dia.toString().equals(fecha) || !YearMonth.from(dia).equals(calendario.mes)) throw datosInvalidos();
            } catch (DateTimeParseException error) {
                throw datosInvalidos();
            }
            noches.put(fecha, reservaId);
        }
        return noches;
    }

    private static void identificador(String id) {
        if (id == null || id.trim().isEmpty() || id.length() > 200 || id.contains("/")
                || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("El identificador de la reserva o del alojamiento no es válido.");
        }
    }

    private static IllegalArgumentException datosInvalidos() {
        return new IllegalArgumentException("La agenda de reservas contiene datos incompletos. Revisa los datos del servidor.");
    }

    private static String huella(String... componentes) {
        StringBuilder origen = new StringBuilder();
        for (String componente : componentes) origen.append(componente.length()).append(':').append(componente);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(origen.toString().getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder id = new StringBuilder(hash.length * 2);
            for (byte valor : hash) id.append(hex[(valor & 0xff) >>> 4]).append(hex[valor & 0x0f]);
            return id.toString();
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("No está disponible el identificador de la agenda.", imposible);
        }
    }

    private static final class Calendario {
        final boolean habitacion;
        final String coleccion;
        final String id;
        final String hotelId;
        final String roomId;
        final String clienteId;
        final YearMonth mes;

        private Calendario(boolean habitacion, String hotelId, String roomId, String clienteId, YearMonth mes) {
            this.habitacion = habitacion;
            this.hotelId = hotelId;
            this.roomId = roomId;
            this.clienteId = clienteId;
            this.mes = mes;
            this.coleccion = habitacion ? HABITACIONES : CLIENTES;
            this.id = habitacion ? huella(hotelId, roomId, mes.toString()) : huella(clienteId, mes.toString());
        }

        static Calendario habitacion(String hotelId, String roomId, YearMonth mes) {
            return new Calendario(true, hotelId, roomId, null, mes);
        }

        static Calendario cliente(String clienteId, YearMonth mes) {
            return new Calendario(false, null, null, clienteId, mes);
        }

        String ruta() { return coleccion + "/" + id; }
    }

    private static final class Cambio {
        final DocumentReference referencia;
        final Calendario calendario;
        final Map<String, String> noches;

        Cambio(DocumentReference referencia, Calendario calendario, Map<String, String> noches) {
            this.referencia = referencia;
            this.calendario = calendario;
            this.noches = noches;
        }

        Map<String, Object> datos() {
            Map<String, Object> datos = new HashMap<>();
            datos.put("mes", calendario.mes.toString());
            if (calendario.habitacion) {
                datos.put("hotelId", calendario.hotelId);
                datos.put("roomId", calendario.roomId);
            } else datos.put("clienteId", calendario.clienteId);
            datos.put("noches", noches);
            datos.put("actualizadoEn", FieldValue.serverTimestamp());
            return datos;
        }
    }
}
