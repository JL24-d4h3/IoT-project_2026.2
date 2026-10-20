package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.AppNotification;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Charge;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Payment;
import org.iot.project.models.Review;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.User;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Reservas reales de Firestore; los registros de pago y cobro son simulados. */
public final class FirebaseBookingRepository implements BookingRepository {

    public FirebaseBookingRepository() { }

    @Override
    public void reservasDe(@NonNull String clienteId, @NonNull ResultCallback<List<Booking>> callback) {
        String uid = cliente(callback);
        if (uid == null) return;
        if (!uid.equals(clienteId)) {
            callback.onError("Solo puedes consultar tus propias reservas.");
            return;
        }
        conPerfil(uid, callback, () -> FirebaseFirestore.getInstance().collection("reservas")
                .whereEqualTo("clienteId", uid).get(Source.SERVER).addOnSuccessListener(resultado -> {
                    if (!vigente(uid, callback)) return;
                    try {
                        List<Booking> reservas = new ArrayList<>();
                        for (DocumentSnapshot documento : resultado) {
                            Booking reserva = BookingFirebase.desde(documento.getId(), documento.getData());
                            propia(reserva, uid);
                            reservas.add(reserva);
                        }
                        reservas.sort(Comparator.comparing(Booking::getFechaEntrada).reversed()
                                .thenComparing(Booking::getId));
                        callback.onExito(reservas);
                    } catch (IllegalArgumentException error) {
                        callback.onError(error.getMessage());
                    }
                }).addOnFailureListener(error -> fallo(uid, error, callback)));
    }

    @Override
    public void obtener(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        String uid = cuenta(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        Sesion solicitud = new Sesion(uid);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaccion -> {
            solicitud.exigir();
            Booking reserva = leer(transaccion.get(db.collection("reservas").document(bookingId)));
            autorizarReserva(transaccion, db, reserva, uid, false);
            solicitud.exigir();
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (solicitud.vigente(callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(solicitud, error, callback));
    }

    @Override
    public void crear(@NonNull Booking borrador, @NonNull ResultCallback<Booking> callback) {
        String uid = cliente(callback);
        if (uid == null) return;
        final Booking pedido;
        try {
            if (borrador == null) throw new IllegalArgumentException("Completa los datos de la reserva.");
            BookingFirebase.exigirId(borrador.getId());
            BookingFirebase.exigirId(borrador.getHotelId());
            BookingFirebase.exigirId(borrador.getRoomId());
            propia(borrador, uid);
            // Un reintento puede llegar después de medianoche: primero recupera
            // el UUID existente; solo una reserva nueva exige entrada >= hoy.
            BookingFirebase.validarPeriodo(borrador.getFechaEntrada(), borrador.getFechaSalida(), false);
            // La transacción puede reintentarse: conserva un pedido inmutable respecto de la pantalla.
            pedido = new Booking(borrador.getId(), "", borrador.getHotelId(), borrador.getRoomId(), uid,
                    borrador.getFechaEntrada(), borrador.getFechaSalida(), borrador.getNumHuespedes(),
                    borrador.getPrecioNoche(), BookingStatus.PENDIENTE);
            for (HotelService servicio : borrador.getServiciosAdicionales()) {
                pedido.addServicioAdicional(HotelService.adicional(pedido.getHotelId(),
                        servicio.getServiceId(), servicio.getPrice()));
            }
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(pedido.getId());
        db.runTransaction(transaccion -> {
            exigirSesion(uid);
            perfil(transaccion.get(db.collection("usuarios").document(uid)), uid);
            DocumentSnapshot previa = transaccion.get(referencia);
            if (previa.exists()) {
                Booking existente = leer(previa);
                propia(existente, uid);
                if (!BookingFirebase.mismoPedido(existente, pedido)) {
                    throw new IllegalArgumentException("Este formulario ya creó otra reserva. Abre un formulario nuevo.");
                }
                return existente;
            }
            DocumentSnapshot ficha = transaccion.get(db.collection("hoteles").document(pedido.getHotelId()));
            if (!ficha.exists()) throw new IllegalArgumentException("El hotel ya no existe.");
            Hotel hotel = HotelFirebase.desde(ficha.getId(), ficha.getData());
            Room habitacion = null;
            for (Room candidata : hotel.getHabitaciones()) {
                if (candidata.getId().equals(pedido.getRoomId())) habitacion = candidata;
            }
            if (habitacion == null) throw new IllegalArgumentException("La habitación ya no existe en este hotel.");
            Booking reserva = BookingFirebase.nueva(pedido, hotel, habitacion, uid);
            List<DocumentSnapshot> calendario = leerCalendario(transaccion, db, reserva);
            exigirSesion(uid);
            CalendarioReservasFirebase.reservar(transaccion, reserva, calendario);
            Map<String, Object> datos = BookingFirebase.documento(reserva);
            datos.put("creadoEn", FieldValue.serverTimestamp());
            datos.put("actualizadoEn", FieldValue.serverTimestamp());
            transaccion.set(referencia, datos);
            NotificacionFirebase.registrar(transaccion, uid, "reserva_" + reserva.getId() + "_creada",
                    AppNotification.Tipo.RESERVA, "Reserva creada",
                    "Tu reserva " + reserva.getCodigo() + " en " + reserva.getHotelNombre()
                            + " está guardada. Puedes consultar sus detalles en Mis reservas.",
                    reserva.getId(), null);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (vigente(uid, callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(uid, error, callback));
    }

    @Override
    public void cancelar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        String uid = cuenta(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        Sesion solicitud = new Sesion(uid);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        db.runTransaction(transaccion -> {
            solicitud.exigir();
            Booking reserva = leer(transaccion.get(referencia));
            autorizarReserva(transaccion, db, reserva, uid, false);
            if (reserva.getEstado() == BookingStatus.CANCELADA) return reserva;
            if (!reserva.getEstado().allowsCancellation()) {
                throw new IllegalArgumentException("Esta reserva ya no permite cancelación.");
            }
            List<DocumentSnapshot> calendario = leerCalendario(transaccion, db, reserva);
            solicitud.exigir();
            CalendarioReservasFirebase.liberar(transaccion, reserva, calendario);
            transaccion.update(referencia, "estado", BookingStatus.CANCELADA.name(),
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, reserva.getClienteId(), "reserva_" + bookingId + "_cancelada",
                    AppNotification.Tipo.RESERVA, "Reserva cancelada",
                    "Tu reserva " + reserva.getCodigo() + " fue cancelada.", bookingId, null);
            reserva.setEstado(BookingStatus.CANCELADA);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (solicitud.vigente(callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(solicitud, error, callback));
    }

    @Override
    public void pagar(@NonNull String bookingId, @NonNull Payment pago, @NonNull ResultCallback<Booking> callback) {
        String uid = cliente(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        if (pago == null || !bookingId.equals(pago.getBookingId()) || !Double.isFinite(pago.getMonto())
                || pago.getMonto() <= 0 || pago.getMetodo() == null || pago.getMetodo().trim().isEmpty()
                || pago.getMetodo().length() > 120) {
            callback.onError("No encontramos un método o importe válido para el pago simulado.");
            return;
        }
        final double monto = pago.getMonto();
        final String metodo = pago.getMetodo().trim();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        db.runTransaction(transaccion -> {
            exigirSesion(uid);
            perfil(transaccion.get(db.collection("usuarios").document(uid)), uid);
            Booking reserva = leer(transaccion.get(referencia));
            propia(reserva, uid);
            if (Math.abs(reserva.getTotal() - monto) > 0.01) {
                throw new IllegalArgumentException("El total cambió. Vuelve a abrir el pago de esta reserva.");
            }
            if (reserva.getEstado() == BookingStatus.CONFIRMADA) return reserva;
            if (reserva.getEstado() != BookingStatus.PENDIENTE) {
                throw new IllegalArgumentException("Solo puedes confirmar el pago simulado de una reserva pendiente.");
            }
            exigirSesion(uid);
            Map<String, Object> registro = new HashMap<>();
            registro.put("bookingId", bookingId);
            registro.put("clienteId", uid);
            registro.put("hotelId", reserva.getHotelId());
            registro.put("monto", reserva.getTotal());
            registro.put("metodo", metodo);
            registro.put("simulado", true);
            registro.put("creadoEn", FieldValue.serverTimestamp());
            // Una reserva tiene una confirmación: reintentar nunca duplica el registro.
            transaccion.set(db.collection("pagos").document(bookingId), registro);
            transaccion.update(referencia, "estado", BookingStatus.CONFIRMADA.name(),
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, uid, "reserva_" + bookingId + "_pago",
                    AppNotification.Tipo.PAGO, "Reserva confirmada",
                    "Tu reserva " + reserva.getCodigo()
                            + " está confirmada. La confirmación de pago es simulada.", bookingId, null);
            reserva.setEstado(BookingStatus.CONFIRMADA);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (vigente(uid, callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(uid, error, callback));
    }

    private static List<DocumentSnapshot> leerCalendario(Transaction transaccion, FirebaseFirestore db,
                                                          Booking reserva) throws FirebaseFirestoreException {
        List<DocumentSnapshot> documentos = new ArrayList<>();
        for (DocumentReference referencia : CalendarioReservasFirebase.referencias(db, reserva)) {
            documentos.add(transaccion.get(referencia));
        }
        return documentos;
    }

    private static Booking leer(DocumentSnapshot documento) {
        if (!documento.exists()) throw new IllegalArgumentException("No encontramos esta reserva en el servidor.");
        return BookingFirebase.desde(documento.getId(), documento.getData());
    }

    private static void propia(Booking reserva, String uid) {
        if (!uid.equals(reserva.getClienteId())) {
            throw new IllegalArgumentException("Esta reserva no pertenece a tu cuenta.");
        }
    }

    private static void perfil(DocumentSnapshot documento, String uid) {
        if (PerfilFirebase.usuario(uid, "", documento.getData()).getRol() != Role.CLIENTE
                || SessionManager.getRolActivo() != Role.CLIENTE) {
            throw new IllegalArgumentException("Inicia sesión con tu cuenta de cliente para reservar.");
        }
    }

    /** Perfil y asignación se vuelven a leer dentro de cada operación; la sesión no concede permisos. */
    private static User autorizarReserva(Transaction transaccion, FirebaseFirestore db,
                                         Booking reserva, String uid, boolean soloAdministrador)
            throws FirebaseFirestoreException {
        exigirSesion(uid);
        User usuario = PerfilFirebase.usuario(uid, "",
                transaccion.get(db.collection("usuarios").document(uid)).getData());
        if (usuario.getRol() != SessionManager.getRolActivo()) {
            throw new IllegalArgumentException("Tu perfil cambió. Inicia sesión de nuevo.");
        }
        if (usuario.getRol() == Role.CLIENTE && !soloAdministrador) {
            propia(reserva, uid);
            return usuario;
        }
        if (usuario.getRol() != Role.ADMIN_HOTEL
                || !reserva.getHotelId().equals(usuario.getHotelId())
                || !reserva.getHotelId().equals(SessionManager.getHotelAdministrado())) {
            throw new IllegalArgumentException("Esta reserva no pertenece a tu hotel asignado.");
        }
        DocumentSnapshot hotel = transaccion.get(db.collection("hoteles").document(reserva.getHotelId()));
        if (!hotel.exists() || !uid.equals(hotel.getString("administradorId"))) {
            throw new IllegalArgumentException("La asignación del administrador y su hotel no coincide.");
        }
        return usuario;
    }

    private static void conPerfil(String uid, ResultCallback<?> callback, Runnable siguiente) {
        FirebaseFirestore.getInstance().collection("usuarios").document(uid).get(Source.SERVER)
                .addOnSuccessListener(documento -> {
                    if (!vigente(uid, callback)) return;
                    try {
                        perfil(documento, uid);
                    } catch (IllegalArgumentException error) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    siguiente.run();
                }).addOnFailureListener(error -> fallo(uid, error, callback));
    }

    private static String cliente(ResultCallback<?> callback) {
        if (SessionManager.getRolActivo() != Role.CLIENTE) {
            callback.onError("Inicia sesión con tu cuenta de cliente para reservar.");
            return null;
        }
        return cuenta(callback);
    }

    private static String administrador(ResultCallback<?> callback) {
        if (SessionManager.getRolActivo() != Role.ADMIN_HOTEL) {
            callback.onError("Esta operación requiere la cuenta del administrador del hotel.");
            return null;
        }
        return cuenta(callback);
    }

    private static String cuenta(ResultCallback<?> callback) {
        String uid = SessionManager.getUsuarioId();
        try {
            exigirSesion(uid);
            return uid;
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return null;
        }
    }

    private static void exigirSesion(String uid) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        Role rol = SessionManager.getRolActivo();
        if (uid == null || usuario == null || !uid.equals(usuario.getUid())
                || !uid.equals(SessionManager.getUsuarioId()) || (rol != Role.CLIENTE && rol != Role.ADMIN_HOTEL)) {
            throw new IllegalArgumentException("Tu sesión cambió. Inicia sesión de nuevo para consultar las reservas.");
        }
    }

    private static boolean vigente(String uid, ResultCallback<?> callback) {
        try {
            exigirSesion(uid);
            return true;
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return false;
        }
    }

    /** Conserva el rol y el hotel de la solicitud para descartar resultados de una asignación anterior. */
    private static final class Sesion {
        final String uid;
        final Role rol;
        final String hotelId;

        Sesion(String uid) {
            this.uid = uid;
            this.rol = SessionManager.getRolActivo();
            this.hotelId = SessionManager.getHotelAdministrado();
        }

        void exigir() {
            exigirSesion(uid);
            if (rol != SessionManager.getRolActivo()
                    || !Objects.equals(hotelId, SessionManager.getHotelAdministrado())) {
                throw new IllegalArgumentException("La sesión o el hotel asignado cambió. Vuelve a cargar esta pantalla.");
            }
        }

        boolean vigente(ResultCallback<?> callback) {
            try {
                exigir();
                return true;
            } catch (IllegalArgumentException error) {
                callback.onError(error.getMessage());
                return false;
            }
        }
    }

    private static void fallo(Sesion solicitud, Exception error, ResultCallback<?> callback) {
        if (solicitud.vigente(callback)) fallo(solicitud.uid, error, callback);
    }

    private static boolean idValido(String id, ResultCallback<?> callback) {
        try {
            BookingFirebase.exigirId(id);
            return true;
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return false;
        }
    }

    private static void fallo(String uid, Exception error, ResultCallback<?> callback) {
        if (!vigente(uid, callback)) return;
        Throwable causa = error;
        while (causa != null) {
            if (causa instanceof IllegalArgumentException) {
                callback.onError(causa.getMessage() == null ? "Revisa los datos de la reserva." : causa.getMessage());
                return;
            }
            causa = causa.getCause();
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    callback.onError("No pudimos conectar con Firestore. Revisa tu conexión e inténtalo de nuevo.");
                    return;
                case PERMISSION_DENIED:
                    callback.onError("Firestore rechazó el acceso a las reservas de esta cuenta.");
                    return;
                case UNAUTHENTICATED:
                    callback.onError("Tu sesión venció. Inicia sesión de nuevo.");
                    return;
                case ABORTED:
                    callback.onError("La disponibilidad cambió mientras reservabas. Inténtalo de nuevo.");
                    return;
                default: break;
            }
        }
        callback.onError("No pudimos completar la operación de reserva. Inténtalo de nuevo.");
    }

    @Override public void reservasDeHotel(@NonNull String hotelId, @NonNull ResultCallback<List<Booking>> callback) {
        if (administrador(callback) == null || !idValido(hotelId, callback)) return;
        FirebaseReservasHotel.consultar(hotelId, new ResultCallback<FirebaseReservasHotel.Datos>() {
            @Override public void onExito(@NonNull FirebaseReservasHotel.Datos datos) {
                if (datos.vigente()) callback.onExito(datos.reservas);
                else callback.onError("La sesión o el hotel asignado cambió. Vuelve a cargar las reservas.");
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override public void iniciarEstadia(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        String uid = administrador(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        Sesion solicitud = new Sesion(uid);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        db.runTransaction(transaccion -> {
            solicitud.exigir();
            Booking reserva = leer(transaccion.get(referencia));
            autorizarReserva(transaccion, db, reserva, uid, true);
            if (reserva.getEstado() == BookingStatus.ACTIVA) return reserva;
            if (reserva.getEstado() != BookingStatus.CONFIRMADA) {
                throw new IllegalArgumentException("Solo puedes registrar la llegada de una reserva confirmada.");
            }
            LocalDate hoy = LocalDate.now();
            if (hoy.isBefore(reserva.getFechaEntrada()) || !hoy.isBefore(reserva.getFechaSalida())) {
                throw new IllegalArgumentException("Registra la llegada dentro de las fechas de la estadía.");
            }
            solicitud.exigir();
            transaccion.update(referencia, "estado", BookingStatus.ACTIVA.name(),
                    "llegadaEn", FieldValue.serverTimestamp(), "llegadaPor", uid,
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, reserva.getClienteId(), "reserva_" + bookingId + "_llegada",
                    AppNotification.Tipo.RESERVA, "Estadía iniciada",
                    "El hotel registró tu llegada para la reserva " + reserva.getCodigo()
                            + ". Puedes consultar tu estadía y realizar el checkout desde la app.", bookingId, null);
            reserva.setEstado(BookingStatus.ACTIVA);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (solicitud.vigente(callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(solicitud, error, callback));
    }

    @Override public void finalizar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        String uid = cliente(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        db.runTransaction(transaccion -> {
            exigirSesion(uid);
            perfil(transaccion.get(db.collection("usuarios").document(uid)), uid);
            Booking reserva = leer(transaccion.get(referencia));
            propia(reserva, uid);
            if (reserva.getEstado() == BookingStatus.FINALIZADA) return reserva;
            if (reserva.getEstado() != BookingStatus.ACTIVA) {
                throw new IllegalArgumentException("Solo puedes realizar el checkout de una estadía activa.");
            }
            exigirSesion(uid);
            transaccion.update(referencia, "estado", BookingStatus.FINALIZADA.name(),
                    "finalizadaEn", FieldValue.serverTimestamp(), "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, uid, "reserva_" + bookingId + "_checkout",
                    AppNotification.Tipo.CHECKOUT, "Checkout realizado",
                    "Finalizaste la estadía de tu reserva " + reserva.getCodigo()
                            + ". Ya puedes valorar tu experiencia.", bookingId, null);
            reserva.setEstado(BookingStatus.FINALIZADA);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (vigente(uid, callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(uid, error, callback));
    }

    @Override public void cobrar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        String uid = administrador(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        Sesion solicitud = new Sesion(uid);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        DocumentReference cobro = db.collection("cobros").document(bookingId);
        db.runTransaction(transaccion -> {
            solicitud.exigir();
            Booking reserva = leer(transaccion.get(referencia));
            autorizarReserva(transaccion, db, reserva, uid, true);
            DocumentSnapshot previo = transaccion.get(cobro);
            if (reserva.getEstado() != BookingStatus.FINALIZADA) {
                throw new IllegalArgumentException("El cliente debe realizar el checkout antes de registrar el cobro.");
            }
            if (reserva.isCobrado()) return reserva;
            if (previo.exists()) {
                throw new IllegalArgumentException("Esta reserva ya tiene un registro de cobro. Revisa sus datos en el servidor.");
            }
            if (!Double.isFinite(reserva.getTotal()) || reserva.getTotal() <= 0) {
                throw new IllegalArgumentException("La reserva no tiene un total válido para registrar el cobro.");
            }
            Map<String, Object> registro = new HashMap<>();
            registro.put("bookingId", bookingId);
            registro.put("hotelId", reserva.getHotelId());
            registro.put("clienteId", reserva.getClienteId());
            registro.put("administradorId", uid);
            registro.put("monto", reserva.getTotal());
            registro.put("metodo", "RECEPCION");
            registro.put("simulado", true);
            registro.put("creadoEn", FieldValue.serverTimestamp());
            solicitud.exigir();
            transaccion.set(cobro, registro);
            transaccion.update(referencia, "cobrado", true, "cobradoEn", FieldValue.serverTimestamp(),
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, reserva.getClienteId(), "reserva_" + bookingId + "_cobro",
                    AppNotification.Tipo.PAGO, "Cobro simulado registrado",
                    "El hotel registró en recepción el cobro simulado de tu reserva " + reserva.getCodigo()
                            + ". Esta operación no realiza ningún cargo bancario.", bookingId, null);
            reserva.marcarCobrado();
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (solicitud.vigente(callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(solicitud, error, callback));
    }

    @Override public void agregarCargo(@NonNull String bookingId, @NonNull Charge cargo, @NonNull ResultCallback<Booking> callback) {
        agregarCargo(bookingId, UUID.randomUUID().toString(), cargo, callback);
    }

    @Override public void agregarCargo(@NonNull String bookingId, @NonNull String operacionId,
                                       @NonNull Charge cargo, @NonNull ResultCallback<Booking> callback) {
        String uid = administrador(callback);
        if (uid == null || !idValido(bookingId, callback) || !idValido(operacionId, callback)) return;
        Sesion solicitud = new Sesion(uid);
        final Charge pedido;
        try {
            if (cargo == null || !Double.isFinite(cargo.getMonto()) || cargo.getMonto() <= 0
                    || cargo.getMonto() > 1_000_000 || cargo.getMotivo() == null || cargo.getObservacion() == null
                    || cargo.getMotivo().trim().length() > 200 || cargo.getObservacion().trim().length() > 1000) {
                throw new IllegalArgumentException("Revisa el cargo: monto hasta 1 000 000, motivo hasta 200 caracteres y observación hasta 1000.");
            }
            pedido = new Charge(cargo.getMonto(), cargo.getMotivo().trim(), cargo.getObservacion().trim());
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        DocumentReference operacion = referencia.collection("cargos").document(operacionId);
        db.runTransaction(transaccion -> {
            solicitud.exigir();
            Booking reserva = leer(transaccion.get(referencia));
            autorizarReserva(transaccion, db, reserva, uid, true);
            DocumentSnapshot previo = transaccion.get(operacion);
            if (previo.exists()) {
                Object monto = previo.get("monto");
                if (!(monto instanceof Number) || !Double.isFinite(((Number) monto).doubleValue())
                        || Math.abs(((Number) monto).doubleValue() - pedido.getMonto()) > 0.001
                        || !Objects.equals(previo.get("motivo"), pedido.getMotivo())
                        || !Objects.equals(previo.get("observacion"), pedido.getObservacion())) {
                    throw new IllegalArgumentException("Este formulario ya registró un cargo distinto. Abre un formulario nuevo.");
                }
                return reserva;
            }
            if (reserva.getEstado() != BookingStatus.ACTIVA || reserva.isCobrado()) {
                throw new IllegalArgumentException("Solo puedes añadir cargos a una estadía activa sin cobrar.");
            }
            if (reserva.getCargos().size() >= 100) {
                throw new IllegalArgumentException("Esta reserva alcanzó el límite de 100 cargos adicionales.");
            }
            reserva.addCargo(pedido);
            Map<String, Object> registro = new HashMap<>();
            registro.put("bookingId", bookingId);
            registro.put("hotelId", reserva.getHotelId());
            registro.put("clienteId", reserva.getClienteId());
            registro.put("administradorId", uid);
            registro.put("monto", pedido.getMonto());
            registro.put("motivo", pedido.getMotivo());
            registro.put("observacion", pedido.getObservacion());
            registro.put("creadoEn", FieldValue.serverTimestamp());
            solicitud.exigir();
            transaccion.set(operacion, registro);
            transaccion.update(referencia, "cargos", BookingFirebase.cargos(reserva),
                    "actualizadoEn", FieldValue.serverTimestamp());
            String eventoId = "cargo_" + UUID.nameUUIDFromBytes(operacion.getPath().getBytes(StandardCharsets.UTF_8));
            NotificacionFirebase.registrar(transaccion, reserva.getClienteId(), eventoId,
                    AppNotification.Tipo.CARGO_ADICIONAL, "Cargo adicional registrado",
                    "El hotel añadió un cargo por " + pedido.getMotivo() + " a tu reserva " + reserva.getCodigo()
                            + ". Consulta el detalle y el nuevo total en Mis reservas.", bookingId, null);
            return reserva;
        }).addOnSuccessListener(reserva -> {
            if (solicitud.vigente(callback)) callback.onExito(reserva);
        }).addOnFailureListener(error -> fallo(solicitud, error, callback));
    }

    @Override public void valorar(@NonNull String bookingId, float rating, @NonNull String comentario,
                                  @NonNull ResultCallback<Review> callback) {
        String uid = cliente(callback);
        if (uid == null || !idValido(bookingId, callback)) return;
        if (!Float.isFinite(rating) || rating < Review.RATING_MIN || rating > Review.RATING_MAX
                || comentario == null || comentario.trim().isEmpty() || comentario.trim().length() > 2000) {
            callback.onError("Indica una valoración de 1 a 10 y un comentario de hasta 2000 caracteres.");
            return;
        }
        String texto = comentario.trim();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = db.collection("reservas").document(bookingId);
        db.runTransaction(transaccion -> {
            exigirSesion(uid);
            User usuario = PerfilFirebase.usuario(uid, "", transaccion.get(db.collection("usuarios").document(uid)).getData());
            if (usuario.getRol() != Role.CLIENTE || SessionManager.getRolActivo() != Role.CLIENTE) {
                throw new IllegalArgumentException("Inicia sesión como cliente para valorar tu reserva.");
            }
            Booking reserva = leer(transaccion.get(referencia));
            propia(reserva, uid);
            if (reserva.getEstado() != BookingStatus.FINALIZADA) {
                throw new IllegalArgumentException("Solo puedes valorar una estadía finalizada.");
            }
            if (reserva.getValoracion() != null) {
                Review previa = reserva.getValoracion();
                if (Math.abs(previa.getRating() - rating) > 0.001 || !previa.getComentario().equals(texto)) {
                    throw new IllegalArgumentException("Esta estadía ya tiene una valoración registrada.");
                }
                return previa;
            }
            String nombre = usuario.getNombreCompleto().trim();
            Review valoracion = new Review(bookingId, reserva.getHotelId(), nombre.isEmpty() ? "Cliente" : nombre,
                    rating, texto, LocalDate.now()).withAutorFoto(usuario.getFotoUrl());
            Map<String, Object> datos = BookingFirebase.valoracion(valoracion);
            datos.put("clienteId", uid);
            datos.put("bookingId", bookingId);
            datos.put("creadoEn", FieldValue.serverTimestamp());
            exigirSesion(uid);
            transaccion.set(db.collection("hoteles").document(reserva.getHotelId()).collection("resenas").document(bookingId), datos);
            transaccion.update(referencia, "valoracion", BookingFirebase.valoracion(valoracion),
                    "actualizadoEn", FieldValue.serverTimestamp());
            return valoracion;
        }).addOnSuccessListener(valoracion -> {
            if (vigente(uid, callback)) callback.onExito(valoracion);
        }).addOnFailureListener(error -> fallo(uid, error, callback));
    }
}
