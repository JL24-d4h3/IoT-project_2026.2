package org.iot.project.data.firebase;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.google.android.gms.tasks.Task;
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
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.ChatRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.Message;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.Role;
import org.iot.project.models.Room;
import org.iot.project.models.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Chat de una estadía activa. Los envíos, resúmenes y avisos se guardan atómicamente. */
public final class FirebaseChatRepository implements ChatRepository {
    private static final String SESION = "La sesión cambió. Vuelve a abrir el chat con tu cuenta.";
    private static final String CERRADO = "El chat solo está disponible durante una estadía activa.";
    private static final AtomicLong GENERACION = new AtomicLong();
    private static boolean identidadObservada;
    private static String identidadAuth;
    private static String identidadSesion;

    public FirebaseChatRepository() { }

    private static synchronized void observarIdentidad() {
        if (identidadObservada) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(FirebaseChatRepository::observarIdentidad);
            return;
        }
        identidadObservada = true;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        identidadAuth = auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
        identidadSesion = claveSesion();
        auth.addAuthStateListener(actual -> {
            String siguiente = actual.getCurrentUser() == null ? null : actual.getCurrentUser().getUid();
            if (!Objects.equals(identidadAuth, siguiente)) {
                identidadAuth = siguiente;
                GENERACION.incrementAndGet();
            }
        });
        SessionManager.sesion().observeForever(cuenta -> {
            String siguiente = claveSesion();
            if (!Objects.equals(identidadSesion, siguiente)) {
                identidadSesion = siguiente;
                GENERACION.incrementAndGet();
            }
        });
    }

    private static String claveSesion() {
        return SessionManager.getIdentidadId() + "|" + SessionManager.getRolActivo()
                + "|" + SessionManager.getHotelAdministrado();
    }

    private static final class Sesion {
        final String uid;
        final String email;
        final Role rol;
        final String hotelId;
        final long generacion;

        Sesion(Message.Autor lado) {
            observarIdentidad();
            FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
            rol = lado == Message.Autor.CLIENTE ? Role.CLIENTE : Role.ADMIN_HOTEL;
            if (usuario == null || !usuario.getUid().equals(SessionManager.getIdentidadId())
                    || rol != SessionManager.getRolActivo()) throw new IllegalArgumentException(SESION);
            uid = usuario.getUid();
            email = usuario.getEmail();
            hotelId = SessionManager.getHotelAdministrado();
            generacion = GENERACION.get();
        }

        boolean vigente() {
            FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
            return generacion == GENERACION.get() && actual != null && uid.equals(actual.getUid())
                    && uid.equals(SessionManager.getIdentidadId()) && rol == SessionManager.getRolActivo()
                    && (rol != Role.ADMIN_HOTEL || Objects.equals(hotelId, SessionManager.getHotelAdministrado()));
        }

        void exigir() { if (!vigente()) throw new IllegalArgumentException(SESION); }
    }

    private static final class Acceso {
        final Booking reserva;
        final Hotel hotel;
        final User perfil;
        final DocumentSnapshot padre;

        Acceso(Booking reserva, Hotel hotel, User perfil, DocumentSnapshot padre) {
            this.reserva = reserva;
            this.hotel = hotel;
            this.perfil = perfil;
            this.padre = padre;
        }
    }

    private static DocumentReference padre(String bookingId) {
        return FirebaseFirestore.getInstance().collection("conversaciones").document(bookingId);
    }

    private static User perfil(DocumentSnapshot documento, Sesion sesion) {
        User usuario = PerfilFirebase.usuario(sesion.uid, sesion.email, documento.getData());
        if (usuario.getRol() != sesion.rol || (sesion.rol == Role.ADMIN_HOTEL
                && !Objects.equals(usuario.getHotelId(), sesion.hotelId))) {
            throw new IllegalArgumentException("Tu cuenta ya no tiene acceso a este chat.");
        }
        return usuario;
    }

    private static void validarReserva(Booking reserva, Sesion sesion) {
        if (!reserva.permiteChat()) throw new IllegalArgumentException(CERRADO);
        if (sesion.rol == Role.CLIENTE && !sesion.uid.equals(reserva.getClienteId())) {
            throw new IllegalArgumentException("Solo puedes abrir el chat de tus propias reservas.");
        }
        if (sesion.rol == Role.ADMIN_HOTEL && !reserva.getHotelId().equals(sesion.hotelId)) {
            throw new IllegalArgumentException("Esta reserva no pertenece a tu hotel asignado.");
        }
    }

    private static Hotel validarHotel(DocumentSnapshot documento, Sesion sesion) {
        if (!documento.exists()) throw new IllegalArgumentException("No encontramos el hotel de esta reserva.");
        Hotel hotel = HotelFirebase.desde(documento.getId(), documento.getData());
        if (sesion.rol == Role.ADMIN_HOTEL && (!hotel.getId().equals(sesion.hotelId)
                || !sesion.uid.equals(hotel.getAdministradorId()))) {
            throw new IllegalArgumentException("Tu cuenta ya no administra este hotel.");
        }
        return hotel;
    }

    private static Acceso autorizar(Transaction tx, Sesion sesion, String bookingId)
            throws FirebaseFirestoreException {
        sesion.exigir();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        User usuario = perfil(tx.get(db.collection("usuarios").document(sesion.uid)), sesion);
        DocumentSnapshot documento = tx.get(db.collection("reservas").document(bookingId));
        if (!documento.exists()) throw new IllegalArgumentException("No encontramos esta reserva en el servidor.");
        Booking reserva = BookingFirebase.desde(documento.getId(), documento.getData());
        validarReserva(reserva, sesion);
        Hotel hotel = validarHotel(tx.get(db.collection("hoteles").document(reserva.getHotelId())), sesion);
        DocumentSnapshot conversacion = tx.get(padre(bookingId));
        ChatFirebase.validarPadre(conversacion, reserva);
        sesion.exigir();
        return new Acceso(reserva, hotel, usuario, conversacion);
    }

    private static Task<Acceso> autorizar(Sesion sesion, String bookingId) {
        return FirebaseFirestore.getInstance().runTransaction(tx -> autorizar(tx, sesion, bookingId));
    }

    private static Task<Hotel> autorizarHotel(Sesion sesion, String hotelId) {
        return FirebaseFirestore.getInstance().runTransaction(tx -> {
            sesion.exigir();
            if (!hotelId.equals(sesion.hotelId)) throw new IllegalArgumentException("Este no es tu hotel asignado.");
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            perfil(tx.get(db.collection("usuarios").document(sesion.uid)), sesion);
            Hotel hotel = validarHotel(tx.get(db.collection("hoteles").document(hotelId)), sesion);
            sesion.exigir();
            return hotel;
        });
    }

    @Override public void conversacionDe(@NonNull String bookingId, @NonNull ResultCallback<Conversation> callback) {
        consultarUna(bookingId, Message.Autor.CLIENTE, callback, null);
    }

    @Override public void conversacionDeHotel(@NonNull String bookingId,
                                               @NonNull ResultCallback<ConversacionDeHotel> callback) {
        consultarUna(bookingId, Message.Autor.HOTEL, null, callback);
    }

    private void consultarUna(String bookingId, Message.Autor lado, ResultCallback<Conversation> cliente,
                              ResultCallback<ConversacionDeHotel> hotel) {
        Consumer<String> error = mensaje -> { if (cliente != null) cliente.onError(mensaje); else hotel.onError(mensaje); };
        final Sesion sesion;
        try { BookingFirebase.exigirId(bookingId); sesion = new Sesion(lado); }
        catch (IllegalArgumentException fallo) { error.accept(fallo.getMessage()); return; }
        autorizar(sesion, bookingId).addOnSuccessListener(inicial -> padre(bookingId).collection("mensajes")
                .get(Source.SERVER).addOnSuccessListener(mensajes -> autorizar(sesion, bookingId)
                        .addOnSuccessListener(acceso -> {
                            try {
                                Conversation conversacion = ChatFirebase.historial(acceso.reserva, mensajes.getDocuments());
                                if (cliente != null) {
                                    sesion.exigir();
                                    cliente.onExito(conversacion);
                                } else clienteDe(acceso.reserva.getClienteId(), usuario -> {
                                    if (!sesion.vigente()) { error.accept(SESION); return; }
                                    hotel.onExito(cruzar(acceso, conversacion, usuario));
                                }, error);
                            } catch (IllegalArgumentException fallo) { error.accept(fallo.getMessage()); }
                        }).addOnFailureListener(fallo -> error.accept(mensaje(fallo))))
                .addOnFailureListener(fallo -> error.accept(mensaje(fallo))))
                .addOnFailureListener(fallo -> error.accept(mensaje(fallo)));
    }

    @Override public void enviar(@NonNull String bookingId, @NonNull String texto,
                                  @NonNull ResultCallback<Conversation> callback) {
        enviar(bookingId, UUID.randomUUID().toString(), texto, callback);
    }

    @Override public void responder(@NonNull String bookingId, @NonNull String texto,
                                     @NonNull ResultCallback<Conversation> callback) {
        responder(bookingId, UUID.randomUUID().toString(), texto, callback);
    }

    @Override public void enviar(@NonNull String bookingId, @NonNull String messageId,
                                  @NonNull String texto, @NonNull ResultCallback<Conversation> callback) {
        escribir(bookingId, messageId, texto, Message.Autor.CLIENTE, callback);
    }

    @Override public void responder(@NonNull String bookingId, @NonNull String messageId,
                                     @NonNull String texto, @NonNull ResultCallback<Conversation> callback) {
        escribir(bookingId, messageId, texto, Message.Autor.HOTEL, callback);
    }

    private void escribir(String bookingId, String messageId, String texto, Message.Autor autor,
                           ResultCallback<Conversation> callback) {
        final Sesion sesion;
        final String contenido;
        try {
            BookingFirebase.exigirId(bookingId);
            BookingFirebase.exigirId(messageId);
            contenido = ChatFirebase.texto(texto);
            sesion = new Sesion(autor);
        } catch (IllegalArgumentException error) { callback.onError(error.getMessage()); return; }
        DocumentReference referencia = padre(bookingId).collection("mensajes").document(messageId);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(tx -> {
            Acceso acceso = autorizar(tx, sesion, bookingId);
            DocumentSnapshot previo = tx.get(referencia);
            if (previo.exists()) {
                if (!sesion.uid.equals(previo.getString("autorId"))
                        || !autor.name().equals(previo.getString("autor"))
                        || !contenido.equals(previo.getString("texto"))
                        || !bookingId.equals(previo.getString("bookingId"))) {
                    throw new IllegalArgumentException("Este envío ya tiene otro mensaje. Vuelve a abrir el chat.");
                }
                Conversation resultado = ChatFirebase.vacia(acceso.reserva);
                resultado.addMensaje(ChatFirebase.mensaje(messageId, bookingId, previo.getData()));
                return resultado;
            }
            String destinatario = autor == Message.Autor.CLIENTE
                    ? acceso.hotel.getAdministradorId() : acceso.reserva.getClienteId();
            BookingFirebase.exigirId(destinatario);
            DocumentSnapshot receptor = tx.get(db.collection("usuarios").document(destinatario));
            User usuario = PerfilFirebase.usuario(destinatario, receptor.getString("email"), receptor.getData());
            if ((autor == Message.Autor.CLIENTE && (usuario.getRol() != Role.ADMIN_HOTEL
                    || !acceso.hotel.getId().equals(usuario.getHotelId())))
                    || (autor == Message.Autor.HOTEL && usuario.getRol() != Role.CLIENTE)) {
                throw new IllegalArgumentException("El destinatario ya no tiene acceso a esta conversación.");
            }
            int total = ChatFirebase.contador(acceso.padre, "totalMensajes");
            int cliente = ChatFirebase.contador(acceso.padre, "sinLeerCliente");
            int hotel = ChatFirebase.contador(acceso.padre, "sinLeerHotel");
            if (total == Integer.MAX_VALUE || cliente == Integer.MAX_VALUE || hotel == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Esta conversación alcanzó el límite de mensajes.");
            }
            Map<String, Object> datosMensaje = ChatFirebase.mensaje(bookingId, sesion.uid, autor, contenido);
            Map<String, Object> ultimo = new HashMap<>(datosMensaje);
            ultimo.put("id", messageId);
            Map<String, Object> datos = new HashMap<>();
            datos.put("bookingId", bookingId);
            datos.put("clienteId", acceso.reserva.getClienteId());
            datos.put("hotelId", acceso.reserva.getHotelId());
            datos.put("totalMensajes", total + 1);
            datos.put("sinLeerCliente", cliente + (autor == Message.Autor.HOTEL ? 1 : 0));
            datos.put("sinLeerHotel", hotel + (autor == Message.Autor.CLIENTE ? 1 : 0));
            datos.put("ultimoMensaje", ultimo);
            datos.put("actualizadoEn", FieldValue.serverTimestamp());
            if (!acceso.padre.exists()) datos.put("creadoEn", FieldValue.serverTimestamp());
            sesion.exigir();
            tx.set(referencia, datosMensaje);
            tx.set(padre(bookingId), datos, com.google.firebase.firestore.SetOptions.merge());
            String titulo = autor == Message.Autor.CLIENTE ? "Mensaje de un huésped" : "Mensaje del hotel";
            String extracto = contenido.length() > 350 ? contenido.substring(0, 350) + "…" : contenido;
            NotificacionFirebase.registrar(tx, destinatario, notificacionId(bookingId, messageId),
                    AppNotification.Tipo.MENSAJE, titulo, extracto, bookingId, null);
            // El commit es el éxito. No depende de una consulta posterior que pueda fallar.
            Conversation resultado = ChatFirebase.vacia(acceso.reserva);
            resultado.addMensaje(new Message(messageId, bookingId, contenido, LocalDateTime.now(), autor));
            return resultado;
        }).addOnSuccessListener(resultado -> {
            if (sesion.vigente()) callback.onExito(resultado); else callback.onError(SESION);
        }).addOnFailureListener(error -> callback.onError(sesion.vigente() ? mensaje(error) : SESION));
    }

    @Override public void marcarLeido(@NonNull String bookingId, @NonNull Message.Autor lector,
                                       @NonNull ResultCallback<Conversation> callback) {
        ResultCallback<Conversation> recibida = new ResultCallback<Conversation>() {
            @Override public void onExito(@NonNull Conversation conversacion) {
                List<String> ids = new ArrayList<>();
                for (Message mensaje : conversacion.getMensajes()) {
                    if (!mensaje.esDe(lector) && !mensaje.isLeido()) ids.add(mensaje.getId());
                }
                marcarLeido(bookingId, lector, ids, callback);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        };
        if (lector == Message.Autor.CLIENTE) conversacionDe(bookingId, recibida);
        else conversacionDeHotel(bookingId, new ResultCallback<ConversacionDeHotel>() {
            @Override public void onExito(@NonNull ConversacionDeHotel resultado) { recibida.onExito(resultado.getConversacion()); }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override public void marcarLeido(@NonNull String bookingId, @NonNull Message.Autor lector,
                                       @NonNull List<String> idsEntregados,
                                       @NonNull ResultCallback<Conversation> callback) {
        final Sesion sesion;
        final List<String> ids = new ArrayList<>(new LinkedHashSet<>(idsEntregados));
        try {
            BookingFirebase.exigirId(bookingId);
            for (String id : ids) BookingFirebase.exigirId(id);
            sesion = new Sesion(lector);
        } catch (IllegalArgumentException error) { callback.onError(error.getMessage()); return; }
        acusarBloque(sesion, bookingId, lector, ids, 0, callback);
    }

    private void acusarBloque(Sesion sesion, String bookingId, Message.Autor lector, List<String> ids,
                               int inicio, ResultCallback<Conversation> callback) {
        // Cada ID puede actualizar el mensaje y su aviso: deja margen para las marcas temporales.
        int fin = Math.min(inicio + 100, ids.size());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(tx -> {
            Acceso acceso = autorizar(tx, sesion, bookingId);
            List<DocumentSnapshot> mensajes = new ArrayList<>();
            List<DocumentSnapshot> avisos = new ArrayList<>();
            for (int indice = inicio; indice < fin; indice++) {
                mensajes.add(tx.get(padre(bookingId).collection("mensajes").document(ids.get(indice))));
                avisos.add(tx.get(db.collection("usuarios").document(sesion.uid).collection("notificaciones")
                        .document(notificacionId(bookingId, ids.get(indice)))));
            }
            List<Integer> recibidos = new ArrayList<>();
            int nuevos = 0;
            for (int indice = 0; indice < mensajes.size(); indice++) {
                DocumentSnapshot documento = mensajes.get(indice);
                if (!documento.exists()) continue;
                Message mensaje = ChatFirebase.mensaje(documento.getId(), bookingId, documento.getData());
                if (mensaje.esDe(lector)) continue;
                recibidos.add(indice);
                if (!mensaje.isLeido()) nuevos++;
            }
            String campo = lector == Message.Autor.CLIENTE ? "sinLeerCliente" : "sinLeerHotel";
            int pendientes = ChatFirebase.contador(acceso.padre, campo);
            sesion.exigir();
            for (int indice : recibidos) {
                DocumentSnapshot mensaje = mensajes.get(indice);
                if (!Boolean.TRUE.equals(mensaje.getBoolean("leido"))) {
                    tx.update(mensaje.getReference(), "leido", true, "leidoEn", FieldValue.serverTimestamp());
                }
                DocumentSnapshot aviso = avisos.get(indice);
                if (aviso.exists() && bookingId.equals(aviso.getString("bookingId"))
                        && AppNotification.Tipo.MENSAJE.name().equals(aviso.getString("tipo"))
                        && !Boolean.TRUE.equals(aviso.getBoolean("leida"))) {
                    tx.update(aviso.getReference(), "leida", true, "leidaEn", FieldValue.serverTimestamp());
                }
            }
            if (nuevos > 0 && acceso.padre.exists()) {
                // Se resta del contador leído en esta transacción, nunca del mostrado en pantalla.
                tx.update(padre(bookingId), campo, Math.max(0, pendientes - nuevos));
            }
            return ChatFirebase.vacia(acceso.reserva);
        }).addOnSuccessListener(resultado -> {
            if (!sesion.vigente()) { callback.onError(SESION); return; }
            if (fin < ids.size()) acusarBloque(sesion, bookingId, lector, ids, fin, callback);
            else callback.onExito(resultado);
        }).addOnFailureListener(error -> callback.onError(sesion.vigente() ? mensaje(error) : SESION));
    }

    private static String notificacionId(String bookingId, String mensajeId) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((bookingId.length() + ":" + bookingId + mensajeId).getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder("mensaje_");
            for (byte valor : bytes) hash.append(String.format(java.util.Locale.ROOT, "%02x", valor & 255));
            return hash.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String mensaje(Exception error) {
        Throwable causa = error;
        while (causa.getCause() != null && causa != causa.getCause()) causa = causa.getCause();
        if (causa instanceof IllegalArgumentException && causa.getMessage() != null) return causa.getMessage();
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED: return "No tienes permiso para acceder al chat de esta reserva.";
                case UNAUTHENTICATED: return SESION;
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED: return "No pudimos conectar con el chat. Revisa tu conexión e inténtalo de nuevo.";
                default: break;
            }
        }
        return "No pudimos actualizar el chat. Inténtalo de nuevo.";
    }

    private static void clienteDe(String uid, Consumer<User> exito, Consumer<String> error) {
        FirebaseFirestore.getInstance().collection("usuarios").document(uid).get(Source.SERVER)
                .addOnSuccessListener(documento -> exito.accept(usuarioOpcional(documento)))
                .addOnFailureListener(fallo -> error.accept(mensaje(fallo)));
    }

    private static User usuarioOpcional(DocumentSnapshot documento) {
        if (!documento.exists()) return null;
        try { return PerfilFirebase.usuario(documento.getId(), documento.getString("email"), documento.getData()); }
        catch (IllegalArgumentException error) { return null; }
    }

    private static ConversacionDeHotel cruzar(Acceso acceso, Conversation conversacion, User cliente) {
        String numero = acceso.reserva.getHabitacionNumero();
        String tipo = acceso.reserva.getHabitacionTipo();
        for (Room habitacion : acceso.hotel.getHabitaciones()) {
            if (habitacion.getId().equals(acceso.reserva.getRoomId())) {
                numero = habitacion.getNumero();
                tipo = habitacion.getTipo();
                break;
            }
        }
        return new ConversacionDeHotel(conversacion,
                new ReservaDeHotel(acceso.reserva, cliente, numero, tipo));
    }

    private abstract static class SuscripcionBase implements Suscripcion {
        final Sesion sesion;
        final Consumer<String> error;
        final List<ListenerRegistration> registros = new ArrayList<>();
        boolean cerrada;
        boolean perfilListo;
        boolean hotelListo;
        FirebaseAuth.AuthStateListener authListener;
        Observer<Cuenta> cuentaListener;

        SuscripcionBase(Sesion sesion, Consumer<String> error) {
            this.sesion = sesion;
            this.error = error;
        }

        void observarIdentidadLocal() {
            authListener = auth -> { if (!cerrada && !sesion.vigente()) fallar(SESION); };
            cuentaListener = cuenta -> { if (!cerrada && !sesion.vigente()) fallar(SESION); };
            FirebaseAuth.getInstance().addAuthStateListener(authListener);
            SessionManager.sesion().observeForever(cuentaListener);
        }

        void agregar(ListenerRegistration registro) {
            if (cerrada) registro.remove(); else registros.add(registro);
        }

        void observarGuardas(String hotelId) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            agregar(db.collection("usuarios").document(sesion.uid).addSnapshotListener(MetadataChanges.INCLUDE,
                    (documento, fallo) -> {
                        if (!puedeRecibir(fallo) || !servidor(documento)) return;
                        try {
                            perfil(documento, sesion);
                            perfilListo = true;
                            actualizar();
                        } catch (IllegalArgumentException error) { fallar(error.getMessage()); }
                    }));
            agregar(db.collection("hoteles").document(hotelId).addSnapshotListener(MetadataChanges.INCLUDE,
                    (documento, fallo) -> {
                        if (!puedeRecibir(fallo) || !servidor(documento)) return;
                        try {
                            validarHotel(documento, sesion);
                            hotelListo = true;
                            actualizar();
                        } catch (IllegalArgumentException error) { fallar(error.getMessage()); }
                    }));
        }

        boolean puedeRecibir(Exception fallo) {
            if (cerrada) return false;
            if (!sesion.vigente()) { fallar(SESION); return false; }
            if (fallo != null) { fallar(mensaje(fallo)); return false; }
            return true;
        }

        void fallar(String mensaje) {
            if (cerrada) return;
            cerrar();
            error.accept(mensaje);
        }

        abstract void actualizar();

        @Override public void cerrar() {
            if (cerrada) return;
            cerrada = true;
            for (ListenerRegistration registro : registros) registro.remove();
            registros.clear();
            if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
            if (cuentaListener != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) SessionManager.sesion().removeObserver(cuentaListener);
                else new Handler(Looper.getMainLooper()).post(() -> SessionManager.sesion().removeObserver(cuentaListener));
            }
        }
    }

    private static boolean servidor(DocumentSnapshot documento) {
        return documento != null && !documento.getMetadata().isFromCache()
                && !documento.getMetadata().hasPendingWrites();
    }

    private static boolean servidor(QuerySnapshot documentos) {
        return documentos != null && !documentos.getMetadata().isFromCache()
                && !documentos.getMetadata().hasPendingWrites();
    }

    private static final class DetalleSuscripcion extends SuscripcionBase {
        final String bookingId;
        final ResultCallback<Conversation> cliente;
        final ResultCallback<ConversacionDeHotel> hotel;
        List<DocumentSnapshot> mensajes;
        boolean reservaLista;
        long version;

        DetalleSuscripcion(Sesion sesion, String bookingId, ResultCallback<Conversation> cliente,
                            ResultCallback<ConversacionDeHotel> hotel, Consumer<String> error) {
            super(sesion, error);
            this.bookingId = bookingId;
            this.cliente = cliente;
            this.hotel = hotel;
        }

        void iniciar() {
            observarIdentidadLocal();
            autorizar(sesion, bookingId).addOnSuccessListener(acceso -> {
                if (!puedeRecibir(null)) return;
                observarGuardas(acceso.reserva.getHotelId());
                agregar(FirebaseFirestore.getInstance().collection("reservas").document(bookingId)
                        .addSnapshotListener(MetadataChanges.INCLUDE, (documento, fallo) -> {
                            if (!puedeRecibir(fallo) || !servidor(documento)) return;
                            try {
                                if (!documento.exists()) throw new IllegalArgumentException("Esta reserva ya no existe.");
                                Booking reserva = BookingFirebase.desde(documento.getId(), documento.getData());
                                validarReserva(reserva, sesion);
                                if (!reserva.getHotelId().equals(acceso.reserva.getHotelId())
                                        || !reserva.getClienteId().equals(acceso.reserva.getClienteId())) {
                                    throw new IllegalArgumentException("La reserva cambió. Vuelve a abrir su chat.");
                                }
                                reservaLista = true;
                                actualizar();
                            } catch (IllegalArgumentException error) { fallar(error.getMessage()); }
                        }));
                agregar(padre(bookingId).collection("mensajes").addSnapshotListener(MetadataChanges.INCLUDE,
                        (documentos, fallo) -> {
                            if (!puedeRecibir(fallo) || !servidor(documentos)) return;
                            mensajes = new ArrayList<>(documentos.getDocuments());
                            actualizar();
                        }));
            }).addOnFailureListener(fallo -> fallar(mensaje(fallo)));
        }

        @Override void actualizar() {
            if (cerrada || !perfilListo || !hotelListo || !reservaLista || mensajes == null) return;
            long solicitud = ++version;
            List<DocumentSnapshot> recibidos = new ArrayList<>(mensajes);
            // Una transacción de lectura evita habilitar el compositor con una asignación antigua.
            autorizar(sesion, bookingId).addOnSuccessListener(acceso -> {
                if (!puedeRecibir(null) || solicitud != version) return;
                try {
                    Conversation conversacion = ChatFirebase.historial(acceso.reserva, recibidos);
                    if (cliente != null) cliente.onExito(conversacion);
                    else clienteDe(acceso.reserva.getClienteId(), usuario -> {
                        if (puedeRecibir(null) && solicitud == version) {
                            hotel.onExito(cruzar(acceso, conversacion, usuario));
                        }
                    }, this::fallar);
                } catch (IllegalArgumentException error) { fallar(error.getMessage()); }
            }).addOnFailureListener(fallo -> fallar(mensaje(fallo)));
        }
    }

    @NonNull @Override public Suscripcion escucharConversacionDe(@NonNull String bookingId,
                                                @NonNull ResultCallback<Conversation> callback) {
        return observarDetalle(bookingId, Message.Autor.CLIENTE, callback, null);
    }

    @NonNull @Override public Suscripcion escucharConversacionDeHotel(@NonNull String bookingId,
                                                @NonNull ResultCallback<ConversacionDeHotel> callback) {
        return observarDetalle(bookingId, Message.Autor.HOTEL, null, callback);
    }

    private Suscripcion observarDetalle(String bookingId, Message.Autor lado, ResultCallback<Conversation> cliente,
                                         ResultCallback<ConversacionDeHotel> hotel) {
        Consumer<String> error = mensaje -> { if (cliente != null) cliente.onError(mensaje); else hotel.onError(mensaje); };
        try {
            BookingFirebase.exigirId(bookingId);
            DetalleSuscripcion resultado = new DetalleSuscripcion(new Sesion(lado), bookingId, cliente, hotel, error);
            resultado.iniciar();
            return resultado;
        } catch (IllegalArgumentException fallo) { error.accept(fallo.getMessage()); return () -> { }; }
    }

    @Override public void conversacionesDeHotel(@NonNull String hotelId,
                               @NonNull ResultCallback<List<ConversacionDeHotel>> callback) {
        Suscripcion[] registro = new Suscripcion[1];
        boolean[] entregado = { false };
        registro[0] = escucharConversacionesDeHotel(hotelId, new ResultCallback<List<ConversacionDeHotel>>() {
            @Override public void onExito(@NonNull List<ConversacionDeHotel> resultado) {
                if (entregado[0]) return;
                entregado[0] = true;
                if (registro[0] != null) registro[0].cerrar();
                callback.onExito(resultado);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (entregado[0]) return;
                entregado[0] = true;
                if (registro[0] != null) registro[0].cerrar();
                callback.onError(mensaje);
            }
        });
        if (entregado[0]) registro[0].cerrar();
    }

    @NonNull @Override public Suscripcion escucharConversacionesDeHotel(@NonNull String hotelId,
                                  @NonNull ResultCallback<List<ConversacionDeHotel>> callback) {
        try {
            BookingFirebase.exigirId(hotelId);
            BandejaSuscripcion resultado = new BandejaSuscripcion(new Sesion(Message.Autor.HOTEL), hotelId, callback);
            resultado.iniciar();
            return resultado;
        } catch (IllegalArgumentException error) { callback.onError(error.getMessage()); return () -> { }; }
    }

    private static final class BandejaSuscripcion extends SuscripcionBase {
        final String hotelId;
        final ResultCallback<List<ConversacionDeHotel>> callback;
        List<DocumentSnapshot> reservas;
        List<DocumentSnapshot> conversaciones;
        long version;

        BandejaSuscripcion(Sesion sesion, String hotelId, ResultCallback<List<ConversacionDeHotel>> callback) {
            super(sesion, callback::onError);
            this.hotelId = hotelId;
            this.callback = callback;
        }

        void iniciar() {
            observarIdentidadLocal();
            autorizarHotel(sesion, hotelId).addOnSuccessListener(hotel -> {
                if (!puedeRecibir(null)) return;
                observarGuardas(hotelId);
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                agregar(db.collection("reservas").whereEqualTo("hotelId", hotelId)
                        .addSnapshotListener(MetadataChanges.INCLUDE, (documentos, fallo) -> {
                            if (!puedeRecibir(fallo) || !servidor(documentos)) return;
                            reservas = new ArrayList<>(documentos.getDocuments());
                            actualizar();
                        }));
                agregar(db.collection("conversaciones").whereEqualTo("hotelId", hotelId)
                        .addSnapshotListener(MetadataChanges.INCLUDE, (documentos, fallo) -> {
                            if (!puedeRecibir(fallo) || !servidor(documentos)) return;
                            conversaciones = new ArrayList<>(documentos.getDocuments());
                            actualizar();
                        }));
            }).addOnFailureListener(fallo -> fallar(mensaje(fallo)));
        }

        @Override void actualizar() {
            if (cerrada || !perfilListo || !hotelListo || reservas == null || conversaciones == null) return;
            long solicitud = ++version;
            final List<Booking> activas = new ArrayList<>();
            final Map<String, DocumentSnapshot> resumenes = new LinkedHashMap<>();
            final Map<String, Conversation> chats = new LinkedHashMap<>();
            try {
                for (DocumentSnapshot documento : conversaciones) resumenes.put(documento.getId(), documento);
                for (DocumentSnapshot documento : reservas) {
                    Booking reserva = BookingFirebase.desde(documento.getId(), documento.getData());
                    if (!hotelId.equals(reserva.getHotelId())) throw new IllegalArgumentException("La bandeja contiene una reserva de otro hotel.");
                    if (!reserva.permiteChat()) continue;
                    DocumentSnapshot resumen = resumenes.get(reserva.getId());
                    if (resumen == null) continue;
                    Conversation conversacion = ChatFirebase.resumen(reserva, resumen);
                    // Solo se consultan perfiles de conversaciones que aparecerán en la bandeja.
                    if (conversacion.isVacia()) continue;
                    activas.add(reserva);
                    chats.put(reserva.getId(), conversacion);
                }
            } catch (IllegalArgumentException error) { fallar(error.getMessage()); return; }
            autorizarHotel(sesion, hotelId).addOnSuccessListener(hotel -> {
                if (!puedeRecibir(null) || solicitud != version) return;
                Map<String, Task<DocumentSnapshot>> perfiles = new LinkedHashMap<>();
                for (Booking reserva : activas) {
                    if (!perfiles.containsKey(reserva.getClienteId())) {
                        perfiles.put(reserva.getClienteId(), FirebaseFirestore.getInstance().collection("usuarios")
                                .document(reserva.getClienteId()).get(Source.SERVER));
                    }
                }
                List<Task<?>> tareas = new ArrayList<>(perfiles.values());
                Tasks.whenAll(tareas).addOnSuccessListener(ignorado -> {
                    if (!puedeRecibir(null) || solicitud != version) return;
                    try {
                        List<ConversacionDeHotel> filas = new ArrayList<>();
                        for (Booking reserva : activas) {
                            DocumentSnapshot documento = resumenes.get(reserva.getId());
                            Conversation conversacion = chats.get(reserva.getId());
                            User cliente = usuarioOpcional(perfiles.get(reserva.getClienteId()).getResult());
                            filas.add(cruzar(new Acceso(reserva, hotel, null, documento), conversacion, cliente));
                        }
                        filas.sort(Comparator.comparing((ConversacionDeHotel fila) -> fila.getUltimoMensaje().getTimestamp())
                                .reversed().thenComparing(ConversacionDeHotel::getBookingId));
                        callback.onExito(filas);
                    } catch (IllegalArgumentException error) { fallar(error.getMessage()); }
                }).addOnFailureListener(fallo -> fallar(mensaje(fallo)));
            }).addOnFailureListener(fallo -> fallar(mensaje(fallo)));
        }
    }
}
