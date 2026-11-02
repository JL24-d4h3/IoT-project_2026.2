package org.iot.project.data.firebase;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;

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
import org.iot.project.data.api.TaxiApi;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Solicitudes reales del cliente; la asignación y ubicación deben llegar del servicio de taxistas. */
public final class FirebaseTaxiRepository implements TaxiRepository {
    /** Cada cuanto se pregunta a la API por el estado real mientras hay un traslado en curso. */
    private static final long INTERVALO_SONDEO_MS = 8_000L;

    private final AtomicLong generacion = new AtomicLong();
    /** Reservas que se estan registrando en la API ahora mismo (evita registros duplicados). */
    private final Set<String> registrando = ConcurrentHashMap.newKeySet();
    /** Traslados cuyo cierre ya se anoto en Firestore en esta sesion. */
    private final Set<String> finalesAnotados = ConcurrentHashMap.newKeySet();
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
                        conEstadoReal(acceso, servicios(documentos, acceso.uid), callback);
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
                if (!escucha.cerrada) recibirBase(acceso, escucha, datos, callback);
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
                conEstadoReal(acceso, Collections.singletonList(leerPropio(documento, acceso.uid)), unico(callback));
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
            if (!vigente(acceso, callback)) return;
            // La solicitud ya esta guardada. Ahora llega al sistema de taxistas para que los conductores la
            // vean. Si ese sistema no responde, el pedido sigue ahi y se registra solo en cuanto vuelva.
            registrarEnApi(acceso, taxi, () -> conEstadoReal(acceso, Collections.singletonList(taxi), unico(callback)));
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
        // El estado real lo lleva el sistema de taxistas: antes de guardar la nota se confirma que terminó.
        obtener(taxiId, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService vivo) {
                if (vivo.getEstado() != TaxiStatus.FINALIZADO) {
                    callback.onError("Solo puedes valorar un traslado finalizado.");
                    return;
                }
                guardarNota(acceso, vivo, rating, callback);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    private void guardarNota(Acceso acceso, TaxiService vivo, float rating, ResultCallback<TaxiService> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference referencia = referencia(vivo.getId());
        db.runTransaction(transaccion -> {
            exigirSesion(acceso);
            perfil(transaccion.get(db.collection("usuarios").document(acceso.uid)), acceso.uid);
            TaxiService taxi = leerPropio(transaccion.get(referencia), acceso.uid);
            if (taxi.isValorado()) {
                if (Math.abs(taxi.getRatingCliente() - rating) < 0.0001f) return true;
                throw new IllegalArgumentException("Ya valoraste este traslado.");
            }
            exigirSesion(acceso);
            transaccion.update(referencia, "ratingCliente", rating, "valoradoEn", FieldValue.serverTimestamp(),
                    "actualizadoEn", FieldValue.serverTimestamp());
            NotificacionFirebase.registrar(transaccion, acceso.uid, evento(vivo.getId(), "VALORADO"),
                    AppNotification.Tipo.TAXI, "Valoración del traslado", "Guardamos tu calificación de "
                            + Math.round(rating * 10f) / 10f + " sobre 10. Gracias por compartir tu experiencia.",
                    taxi.getBookingId(), vivo.getId());
            return true;
        }).addOnSuccessListener(listo -> {
            if (!vigente(acceso, callback)) return;
            if (!vivo.isValorado()) vivo.valorar(rating);
            // La nota también llega al taxista. Si la API no responde ahora, se reenvía sola al volver a consultar.
            TaxiApi.get().valorar(vivo.getBookingId(), rating, new ResultCallback<Boolean>() {
                @Override public void onExito(@NonNull Boolean enviada) { }
                @Override public void onError(@NonNull String mensaje) { }
            });
            callback.onExito(vivo);
        }).addOnFailureListener(error -> fallo(acceso, error, callback));
    }

    /** Recupera el aviso del último estado real observado, sin duplicar avisos ni volverlos a no leídos. */
    @Override
    public void sincronizarAvisoEstado(@NonNull String taxiId, @NonNull ResultCallback<Boolean> callback) {
        Acceso acceso = cliente(callback);
        if (acceso == null || !idValido(taxiId, callback)) return;
        obtener(taxiId, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService vivo) {
                registrarAviso(acceso, taxiId, vivo.getEstado(), callback);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    private void registrarAviso(Acceso acceso, String taxiId, TaxiStatus estadoReal, ResultCallback<Boolean> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaccion -> {
            exigirSesion(acceso);
            perfil(transaccion.get(db.collection("usuarios").document(acceso.uid)), acceso.uid);
            TaxiService taxi = leerPropio(transaccion.get(referencia(taxiId)), acceso.uid);
            TaxiStatus estado = estadoReal;
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

    // ------------------------------------------------------------------
    //  Estado real: lo que el cliente guardo (Firestore) + lo que lleva el
    //  sistema de taxistas (API). El estado, el conductor y la ubicacion
    //  salen siempre de la API (RT-006).
    // ------------------------------------------------------------------

    private static ResultCallback<List<TaxiService>> unico(ResultCallback<TaxiService> destino) {
        return new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> lista) { destino.onExito(lista.get(0)); }
            @Override public void onError(@NonNull String mensaje) { destino.onError(mensaje); }
        };
    }

    /** Una sola consulta: si la API no responde, entrega lo guardado en Firestore. */
    private void conEstadoReal(Acceso acceso, List<TaxiService> base, ResultCallback<List<TaxiService>> destino) {
        TaxiApi.get().enriquecer(base, new ResultCallback<TaxiApi.Enriquecimiento>() {
            @Override public void onExito(@NonNull TaxiApi.Enriquecimiento resultado) {
                if (!vigente(acceso, destino)) return;
                reconciliar(acceso, base, resultado);
                destino.onExito(resultado.servicios);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(acceso, destino)) destino.onExito(base);
            }
        });
    }

    /** Registra en la API lo que aun no esta y anota en Firestore los traslados que ya terminaron. */
    private void reconciliar(Acceso acceso, List<TaxiService> base, TaxiApi.Enriquecimiento resultado) {
        for (int i = 0; i < base.size() && i < resultado.servicios.size(); i++) {
            TaxiService antes = base.get(i);
            TaxiService ahora = resultado.servicios.get(i);
            if (resultado.sinRegistrar.contains(antes.getBookingId())) {
                registrarEnApi(acceso, antes, null);
            } else if (antes.getEstado() != TaxiStatus.FINALIZADO && ahora.getEstado() == TaxiStatus.FINALIZADO
                    && finalesAnotados.add(antes.getId())) {
                // Mejor esfuerzo: deja el cierre en el historial aunque la API no responda despues.
                // Si las reglas de Firestore no lo permiten, el estado real sigue viniendo de la API.
                referencia(antes.getId()).update("estado", TaxiStatus.FINALIZADO.name(),
                        "actualizadoEn", FieldValue.serverTimestamp()).addOnFailureListener(error -> { });
            }
        }
    }

    /** Lleva la solicitud guardada al sistema de taxistas (con el codigo QR que ya tiene el cliente). */
    private void registrarEnApi(Acceso acceso, TaxiService taxi, @Nullable Runnable alTerminar) {
        final String reserva = taxi.getBookingId();
        if (!registrando.add(reserva)) {
            if (alTerminar != null) alTerminar.run();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Tasks.whenAllSuccess(db.collection("usuarios").document(acceso.uid).get(Source.SERVER),
                referencia(taxi.getId()).get(Source.SERVER))
                .addOnSuccessListener(resultados -> {
                    try {
                        User usuario = PerfilFirebase.usuario(acceso.uid, "",
                                ((DocumentSnapshot) resultados.get(0)).getData());
                        Object hotel = ((DocumentSnapshot) resultados.get(1)).get("hotelId");
                        TaxiApi.get().registrar(taxi, hotel instanceof String ? (String) hotel : "",
                                usuario.getNombreCompleto(), usuario.getTelefono(), new ResultCallback<Boolean>() {
                                    @Override public void onExito(@NonNull Boolean ok) { terminarRegistro(reserva, alTerminar); }
                                    @Override public void onError(@NonNull String mensaje) { terminarRegistro(reserva, alTerminar); }
                                });
                    } catch (RuntimeException error) {
                        terminarRegistro(reserva, alTerminar);
                    }
                })
                .addOnFailureListener(error -> terminarRegistro(reserva, alTerminar));
    }

    private void terminarRegistro(String reserva, @Nullable Runnable alTerminar) {
        registrando.remove(reserva);
        if (alTerminar != null) alTerminar.run();
    }

    // ---- Seguimiento: Firestore avisa de cambios; la API se consulta cada pocos segundos ----

    private void recibirBase(Acceso acceso, Escucha escucha, List<TaxiService> base,
                             ResultCallback<List<TaxiService>> callback) {
        escucha.base = base;
        entregar(escucha, base, callback); // lo guardado, sin esperar a la API
        refrescarApi(acceso, escucha, callback);
    }

    private void refrescarApi(Acceso acceso, Escucha escucha, ResultCallback<List<TaxiService>> callback) {
        if (escucha.cerrada || !activa(acceso)) return;
        if (escucha.consultando) {
            escucha.repetir = true;
            return;
        }
        escucha.consultando = true;
        final List<TaxiService> base = escucha.base;
        TaxiApi.get().enriquecer(base, new ResultCallback<TaxiApi.Enriquecimiento>() {
            @Override public void onExito(@NonNull TaxiApi.Enriquecimiento resultado) {
                escucha.consultando = false;
                if (escucha.cerrada || !activa(acceso)) return;
                reconciliar(acceso, base, resultado);
                if (base == escucha.base) entregar(escucha, resultado.servicios, callback);
                if (escucha.repetir) {
                    escucha.repetir = false;
                    refrescarApi(acceso, escucha, callback);
                } else {
                    programarSondeo(acceso, escucha, callback, resultado.servicios);
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                escucha.consultando = false;
                programarSondeo(acceso, escucha, callback, base);
            }
        });
    }

    private void programarSondeo(Acceso acceso, Escucha escucha, ResultCallback<List<TaxiService>> callback,
                                 List<TaxiService> actuales) {
        if (escucha.cerrada) return;
        boolean hayEnCurso = false;
        for (TaxiService taxi : actuales) {
            if (taxi.getEstado() != TaxiStatus.FINALIZADO) {
                hayEnCurso = true;
                break;
            }
        }
        if (!hayEnCurso) return; // sin traslados abiertos no hay nada que consultar
        if (escucha.sondeo != null) escucha.reloj.removeCallbacks(escucha.sondeo);
        escucha.sondeo = () -> refrescarApi(acceso, escucha, callback);
        escucha.reloj.postDelayed(escucha.sondeo, INTERVALO_SONDEO_MS);
    }

    /** Solo avisa a la pantalla cuando algo que se ve cambio de verdad. */
    private static void entregar(Escucha escucha, List<TaxiService> lista, ResultCallback<List<TaxiService>> callback) {
        StringBuilder firma = new StringBuilder();
        for (TaxiService taxi : lista) {
            firma.append(taxi.getId()).append('|').append(taxi.getEstado()).append('|')
                    .append(taxi.getDriver() == null ? "" : taxi.getDriver().getId()).append('|')
                    .append(taxi.getLatConductor()).append('|').append(taxi.getLngConductor()).append('|')
                    .append(taxi.getUltimaActualizacionUbicacion()).append('|')
                    .append(taxi.getRatingCliente()).append(';');
        }
        String nueva = firma.toString();
        if (nueva.equals(escucha.ultimaFirma)) return;
        escucha.ultimaFirma = nueva;
        callback.onExito(lista);
    }

    private boolean activa(Acceso acceso) {
        try {
            exigirSesion(acceso);
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
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
        // Seguimiento del estado real (siempre en el hilo principal)
        List<TaxiService> base = Collections.emptyList();
        String ultimaFirma;
        boolean consultando;
        boolean repetir;
        final Handler reloj = new Handler(Looper.getMainLooper());
        Runnable sondeo;
        @Override public void cerrar() {
            cerrada = true;
            if (perfil != null) perfil.remove();
            if (datos != null) datos.remove();
            perfil = null;
            datos = null;
            if (sondeo != null) reloj.removeCallbacks(sondeo);
            sondeo = null;
        }
    }

    // ------------------------------------------------------------------
    //  Lado del conductor: sistema de taxistas por REST (RT-006, RT-023).
    //  El cliente HTTP vive en data/api/TaxiApi.
    // ------------------------------------------------------------------

    @Override public void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback) {
        TaxiApi.get().perfilDe(conductorId, callback);
    }
    @Override public void disponibles(@NonNull String conductorId, @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        TaxiApi.get().disponibles(conductorId, callback);
    }
    @Override public void serviciosEnCursoDe(@NonNull String conductorId, @NonNull ResultCallback<List<TaxiService>> callback) {
        TaxiApi.get().serviciosEnCursoDe(conductorId, callback);
    }
    @Override public void aceptar(@NonNull String taxiId, @NonNull String conductorId, @NonNull ResultCallback<TaxiService> callback) {
        TaxiApi.get().aceptar(taxiId, conductorId, callback);
    }
    @Override public void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                                             @NonNull ResultCallback<TaxiService> callback) {
        TaxiApi.get().avanzar(taxiId, conductorId, callback);
    }
    @Override public void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                                       @NonNull String codigo, @NonNull ResultCallback<TaxiService> callback) {
        TaxiApi.get().finalizar(taxiId, conductorId, codigo, callback);
    }
}
