package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Solicitudes reales del cliente; la asignación y ubicación deben llegar del servicio de taxistas. */
public final class FirebaseTaxiRepository implements TaxiRepository {
    private final AtomicLong generacion = new AtomicLong();
    private volatile String identidad;
    private boolean autenticacionObservada;

    public FirebaseTaxiRepository() { }

    private synchronized void observarIdentidad() {
        if (autenticacionObservada) return;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser actual = auth.getCurrentUser();
        identidad = actual == null ? null : actual.getUid();
        autenticacionObservada = true;
        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser usuario = firebaseAuth.getCurrentUser();
            String siguiente = usuario == null ? null : usuario.getUid();
            if (!Objects.equals(identidad, siguiente)) {
                identidad = siguiente;
                generacion.incrementAndGet();
            }
        });
    }

    @Override
    public void historial(@NonNull String clienteId, @NonNull ResultCallback<List<TaxiService>> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null || !propio(clienteId, acceso, callback)) return;
        conPerfil(acceso, callback, () -> consulta(acceso.uid).get(Source.SERVER)
                .addOnSuccessListener(documentos -> {
                    if (!vigente(acceso, callback)) return;
                    try {
                        callback.onExito(servicios(documentos, acceso.uid));
                    } catch (IllegalArgumentException error) {
                        callback.onError(error.getMessage());
                    }
                }).addOnFailureListener(error -> fallo(acceso, error, callback)));
    }

    @Override
    public void servicioActivo(@NonNull String clienteId, @NonNull ResultCallback<TaxiService> callback) {
        historial(clienteId, new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> servicios) {
                TaxiService activo = null;
                for (TaxiService servicio : servicios) {
                    if (servicio.getEstado().isActive() && (activo == null
                            || servicio.getEstado().ordinal() > activo.getEstado().ordinal())) activo = servicio;
                }
                if (activo == null) callback.onError("No tienes ningún servicio de taxi en curso.");
                else callback.onExito(activo);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override
    public Suscripcion escucharHistorial(@NonNull String clienteId,
                                         @NonNull ResultCallback<List<TaxiService>> callback) {
        Escucha escucha = new Escucha();
        Acceso acceso = cliente(callback);
        if (acceso == null || !propio(clienteId, acceso, callback)) return escucha;
        ResultCallback<List<TaxiService>> destino = new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> datos) {
                if (!escucha.cerrada) callback.onExito(datos);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!escucha.cerrada) callback.onError(mensaje);
            }
        };
        conPerfil(acceso, destino, () -> {
            if (escucha.cerrada) return;
            escucha.perfil = FirebaseFirestore.getInstance().collection("usuarios").document(acceso.uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (perfil, error) -> {
                        if (escucha.cerrada) return;
                        if (error != null) {
                            fallo(acceso, error, destino);
                            escucha.cerrar();
                            return;
                        }
                        if (perfil == null || perfil.getMetadata().isFromCache()
                                || perfil.getMetadata().hasPendingWrites()) return;
                        try {
                            exigirSesion(acceso);
                            perfil(perfil, acceso.uid);
                        } catch (IllegalArgumentException invalido) {
                            destino.onError(invalido.getMessage());
                            escucha.cerrar();
                        }
                    });
            Query consulta = consulta(acceso.uid);
            consulta.get(Source.SERVER).addOnSuccessListener(documentos -> {
                if (escucha.cerrada || !vigente(acceso, destino)) return;
                try {
                    destino.onExito(servicios(documentos, acceso.uid));
                } catch (IllegalArgumentException error) {
                    destino.onError(error.getMessage());
                }
                if (escucha.cerrada) return;
                escucharConsulta(consulta, escucha, acceso, destino);
            }).addOnFailureListener(error -> {
                fallo(acceso, error, destino);
                // El listener puede recuperar la conexión aunque la primera lectura haya fallado.
                escucharConsulta(consulta, escucha, acceso, destino);
            });
        });
        return escucha;
    }

    private void escucharConsulta(Query consulta, Escucha escucha, Acceso acceso,
                                  ResultCallback<List<TaxiService>> destino) {
        if (escucha.cerrada || !vigente(acceso, destino)) return;
        escucha.datos = consulta.addSnapshotListener(MetadataChanges.INCLUDE, (nuevos, error) -> {
            if (escucha.cerrada) return;
            if (!vigente(acceso, destino)) {
                escucha.cerrar();
                return;
            }
            if (error != null) {
                fallo(acceso, error, destino);
                return;
            }
            // No se presentan como confirmados estados antiguos o escrituras locales pendientes.
            if (nuevos == null || nuevos.getMetadata().isFromCache()
                    || nuevos.getMetadata().hasPendingWrites()) return;
            try {
                destino.onExito(servicios(nuevos, acceso.uid));
            } catch (IllegalArgumentException invalido) {
                destino.onError(invalido.getMessage());
            }
        });
    }

    @Override
    public void obtener(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null || !idValido(taxiId, callback)) return;
        conPerfil(acceso, callback, () -> referencia(taxiId).get(Source.SERVER).addOnSuccessListener(documento -> {
            if (!vigente(acceso, callback)) return;
            try {
                callback.onExito(leerPropio(documento, acceso.uid));
            } catch (IllegalArgumentException error) {
                callback.onError(error.getMessage());
            }
        }).addOnFailureListener(error -> fallo(acceso, error, callback)));
    }

    @Override
    public void solicitar(@NonNull TaxiService solicitud, @NonNull ResultCallback<TaxiService> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null) return;
        final TaxiService pedido;
        try {
            pedido = TaxiFirebase.pedido(solicitud, acceso.uid);
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = referencia(pedido.getBookingId());
        db.runTransaction(transaccion -> {
            exigirSesion(acceso);
            perfil(transaccion.get(db.collection("usuarios").document(acceso.uid)), acceso.uid);
            DocumentSnapshot previo = transaccion.get(referencia);
            if (previo.exists()) {
                TaxiService existente = leerPropio(previo, acceso.uid);
                if (!TaxiFirebase.mismoPedido(existente, pedido)) {
                    throw new IllegalArgumentException("Esta reserva ya tiene un traslado. Puedes consultarlo en Taxi.");
                }
                return existente;
            }
            DocumentSnapshot fichaReserva = transaccion.get(db.collection("reservas").document(pedido.getBookingId()));
            Booking reserva = BookingFirebase.desde(fichaReserva.getId(), fichaReserva.getData());
            if (!acceso.uid.equals(reserva.getClienteId())) {
                throw new IllegalArgumentException("La reserva del traslado no pertenece a tu cuenta.");
            }
            DocumentSnapshot fichaHotel = transaccion.get(db.collection("hoteles").document(reserva.getHotelId()));
            Hotel hotel = HotelFirebase.desde(fichaHotel.getId(), fichaHotel.getData());
            TaxiService taxi = TaxiFirebase.nuevo(pedido, reserva, hotel, acceso.uid);
            exigirSesion(acceso);
            Map<String, Object> datos = TaxiFirebase.documento(taxi, hotel.getId());
            datos.put("creadoEn", FieldValue.serverTimestamp());
            datos.put("actualizadoEn", FieldValue.serverTimestamp());
            transaccion.set(referencia, datos);
            NotificacionFirebase.registrar(transaccion, acceso.uid, evento(taxi.getId(), "SOLICITADO"),
                    AppNotification.Tipo.TAXI, "Traslado solicitado", "Recibimos tu traslado a " + taxi.getDestino()
                            + " para el " + taxi.getFecha() + " a las " + taxi.getHora() + ". Está pendiente de asignación.",
                    taxi.getBookingId(), taxi.getId());
            return taxi;
        }).addOnSuccessListener(taxi -> {
            if (vigente(acceso, callback)) callback.onExito(taxi);
        }).addOnFailureListener(error -> fallo(acceso, error, callback));
    }

    @Override
    public void calificar(@NonNull String taxiId, float rating, @NonNull ResultCallback<TaxiService> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null || !idValido(taxiId, callback)) return;
        if (!Float.isFinite(rating) || rating < 1f || rating > 10f) {
            callback.onError("Califica el traslado con una nota de 1 a 10.");
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = referencia(taxiId);
        db.runTransaction(transaccion -> {
            exigirSesion(acceso);
            perfil(transaccion.get(db.collection("usuarios").document(acceso.uid)), acceso.uid);
            TaxiService taxi = leerPropio(transaccion.get(referencia), acceso.uid);
            if (taxi.getEstado() != TaxiStatus.FINALIZADO) {
                throw new IllegalArgumentException("Solo puedes valorar un traslado finalizado.");
            }
            if (taxi.isValorado()) {
                if (Math.abs(taxi.getRatingCliente() - rating) < 0.0001f) return taxi;
                throw new IllegalArgumentException("Ya valoraste este traslado.");
            }
            exigirSesion(acceso);
            transaccion.update(referencia, "ratingCliente", rating, "valoradoEn", FieldValue.serverTimestamp(),
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, acceso.uid, evento(taxiId, "VALORADO"),
                    AppNotification.Tipo.TAXI, "Valoración del traslado", "Guardamos tu calificación de "
                            + Math.round(rating * 10f) / 10f + " sobre 10. Gracias por compartir tu experiencia.",
                    taxi.getBookingId(), taxiId);
            taxi.valorar(rating);
            return taxi;
        }).addOnSuccessListener(taxi -> {
            if (vigente(acceso, callback)) callback.onExito(taxi);
        }).addOnFailureListener(error -> fallo(acceso, error, callback));
    }

    /** Recupera el aviso del último estado real observado, sin duplicar avisos ni volverlos a no leídos. */
    @Override
    public void sincronizarAvisoEstado(@NonNull String taxiId, @NonNull ResultCallback<Boolean> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null || !idValido(taxiId, callback)) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaccion -> {
            exigirSesion(acceso);
            perfil(transaccion.get(db.collection("usuarios").document(acceso.uid)), acceso.uid);
            TaxiService taxi = leerPropio(transaccion.get(referencia(taxiId)), acceso.uid);
            TaxiStatus estado = taxi.getEstado();
            if (estado == TaxiStatus.SOLICITADO) return false;
            String eventoId = evento(taxiId, estado.name());
            DocumentSnapshot aviso = transaccion.get(db.collection("usuarios").document(acceso.uid)
                    .collection("notificaciones").document(eventoId));
            if (aviso.exists()) return false;
            final String mensaje;
            switch (estado) {
                case ASIGNADO: mensaje = "Tu traslado a " + taxi.getDestino() + " tiene un conductor asignado."; break;
                case EN_CAMINO: mensaje = "El conductor de tu traslado va en camino al punto de recojo."; break;
                case EN_TRASLADO: mensaje = "Tu traslado a " + taxi.getDestino() + " está en curso."; break;
                case FINALIZADO: mensaje = "Tu traslado terminó. Ya puedes calificar el servicio en Taxi."; break;
                default: return false;
            }
            exigirSesion(acceso);
            NotificacionFirebase.registrar(transaccion, acceso.uid, eventoId, AppNotification.Tipo.TAXI,
                    "Taxi: " + estado.getDisplayName(), mensaje, taxi.getBookingId(), taxi.getId());
            return true;
        }).addOnSuccessListener(creado -> {
            if (vigente(acceso, callback)) callback.onExito(creado);
        }).addOnFailureListener(error -> fallo(acceso, error, callback));
    }

    private static Query consulta(String uid) {
        return FirebaseFirestore.getInstance().collection("taxis").whereEqualTo("clienteId", uid);
    }

    private static DocumentReference referencia(String taxiId) {
        return FirebaseFirestore.getInstance().collection("taxis").document(taxiId);
    }

    private static List<TaxiService> servicios(QuerySnapshot documentos, String uid) {
        List<TaxiService> resultado = new ArrayList<>();
        for (DocumentSnapshot documento : documentos) resultado.add(leerPropio(documento, uid));
        resultado.sort(Comparator.comparing(TaxiService::getFecha).reversed()
                .thenComparing(TaxiService::getHora, Comparator.reverseOrder()).thenComparing(TaxiService::getId));
        return resultado;
    }

    private static TaxiService leerPropio(DocumentSnapshot documento, String uid) {
        TaxiService taxi = TaxiFirebase.desde(documento.getId(), documento.getData());
        if (!uid.equals(taxi.getClienteId())) throw new IllegalArgumentException("Este traslado no pertenece a tu cuenta.");
        return taxi;
    }

    private static void perfil(DocumentSnapshot documento, String uid) {
        if (PerfilFirebase.usuario(uid, "", documento.getData()).getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("Inicia sesión como cliente para consultar o pedir un taxi.");
        }
    }

    private void conPerfil(Acceso acceso, ResultCallback<?> callback, Runnable siguiente) {
        FirebaseFirestore.getInstance().collection("usuarios").document(acceso.uid).get(Source.SERVER)
                .addOnSuccessListener(documento -> {
                    if (!vigente(acceso, callback)) return;
                    try {
                        perfil(documento, acceso.uid);
                    } catch (IllegalArgumentException error) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    siguiente.run();
                }).addOnFailureListener(error -> fallo(acceso, error, callback));
    }

    private Acceso cliente(ResultCallback<?> callback) {
        observarIdentidad();
        Acceso acceso = new Acceso(SessionManager.getUsuarioId(), generacion.get());
        return vigente(acceso, callback) ? acceso : null;
    }

    private static boolean propio(String uid, Acceso acceso, ResultCallback<?> callback) {
        if (acceso.uid.equals(uid)) return true;
        callback.onError("Solo puedes consultar tus propios traslados.");
        return false;
    }

    private void exigirSesion(Acceso acceso) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (acceso.uid == null || usuario == null || !acceso.uid.equals(usuario.getUid())
                || !acceso.uid.equals(SessionManager.getUsuarioId()) || SessionManager.getRolActivo() != Role.CLIENTE
                || acceso.generacion != generacion.get()) {
            throw new IllegalArgumentException("Tu sesión cambió. Inicia sesión como cliente para usar el servicio de taxi.");
        }
    }

    private boolean vigente(Acceso acceso, ResultCallback<?> callback) {
        try {
            exigirSesion(acceso);
            return true;
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return false;
        }
    }

    private static boolean idValido(String id, ResultCallback<?> callback) {
        try {
            BookingFirebase.exigirId(id);
            return true;
        } catch (IllegalArgumentException error) {
            callback.onError("No encontramos un identificador válido para este traslado.");
            return false;
        }
    }

    private void fallo(Acceso acceso, Exception error, ResultCallback<?> callback) {
        if (!vigente(acceso, callback)) return;
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException) {
                callback.onError(causa.getMessage() == null ? "Revisa los datos del traslado." : causa.getMessage());
                return;
            }
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    callback.onError("No pudimos conectar el servicio de taxi. Revisa tu conexión e inténtalo de nuevo."); return;
                case PERMISSION_DENIED:
                    callback.onError("Firestore rechazó el acceso a los traslados de esta cuenta."); return;
                case UNAUTHENTICATED:
                    callback.onError("Tu sesión venció. Inicia sesión de nuevo."); return;
                case ABORTED:
                    callback.onError("El traslado cambió durante la operación. Actualiza e inténtalo de nuevo."); return;
                default: break;
            }
        }
        callback.onError("No pudimos completar la operación de taxi. Inténtalo de nuevo.");
    }

    private static String evento(String taxiId, String estado) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((taxiId.length() + ":" + taxiId + estado).getBytes(StandardCharsets.UTF_8));
            StringBuilder resultado = new StringBuilder("taxi_");
            for (byte valor : bytes) resultado.append(String.format(java.util.Locale.ROOT, "%02x", valor & 0xff));
            return resultado.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible.", error);
        }
    }

    private static final class Acceso {
        final String uid;
        final long generacion;
        Acceso(String uid, long generacion) { this.uid = uid; this.generacion = generacion; }
    }

    private static final class Escucha implements Suscripcion {
        volatile boolean cerrada;
        ListenerRegistration perfil;
        ListenerRegistration datos;
        @Override public void cerrar() {
            cerrada = true;
            if (perfil != null) perfil.remove();
            if (datos != null) datos.remove();
            perfil = null;
            datos = null;
        }
    }

    @Override public void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback) {
        callback.onError("El perfil de conductores todavía no está conectado al sistema de taxistas.");
    }
    @Override public void disponibles(@NonNull String conductorId, @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        callback.onError("Las ofertas para conductores todavía no están conectadas al sistema de taxistas.");
    }
    @Override public void serviciosEnCursoDe(@NonNull String conductorId, @NonNull ResultCallback<List<TaxiService>> callback) {
        callback.onError("Los servicios del conductor todavía no están conectados al sistema de taxistas.");
    }
    @Override public void aceptar(@NonNull String taxiId, @NonNull String conductorId, @NonNull ResultCallback<TaxiService> callback) {
        callback.onError("La asignación del conductor debe realizarse desde el sistema de taxistas.");
    }
    @Override public void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                                             @NonNull ResultCallback<TaxiService> callback) {
        callback.onError("Los estados del traslado deben actualizarse desde el sistema de taxistas.");
    }
    @Override public void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                                       @NonNull String codigo, @NonNull ResultCallback<TaxiService> callback) {
        callback.onError("La finalización del traslado debe validarse desde el sistema de taxistas.");
    }
}
