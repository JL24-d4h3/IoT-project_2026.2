package org.iot.project.data.firebase;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
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
import org.iot.project.data.repository.SuperadminRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Alta, asignacion y publicacion persistentes con auditoria atomica. */
public final class FirebaseSuperadminRepository implements SuperadminRepository {

    private static final AtomicLong GENERACION = new AtomicLong();
    private static boolean observandoIdentidad;
    private static String identidadAuth;
    private static String identidadSesion;
    private static Role rolSesion;

    private final FirebaseHotelRepository hoteles;

    public FirebaseSuperadminRepository(FirebaseHotelRepository hoteles) {
        this.hoteles = hoteles;
    }

    @Override
    public void hoteles(@NonNull ResultCallback<List<Hotel>> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        db().collection("hoteles").get(Source.SERVER).addOnSuccessListener(resultado -> {
            if (!vigente(uid, callback)) return;
            try {
                List<Hotel> lista = mapearHoteles(resultado);
                hoteles.reemplazarCache(lista);
                callback.onExito(lista);
            } catch (IllegalArgumentException error) {
                callback.onError(mensaje(error));
            }
        }).addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void administradores(@NonNull ResultCallback<List<User>> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        db().collection("usuarios").whereEqualTo("rol", Role.ADMIN_HOTEL.name())
                .whereEqualTo("activo", true).get(Source.SERVER).addOnSuccessListener(resultado -> {
                    if (!vigente(uid, callback)) return;
                    try {
                        List<User> lista = mapearUsuarios(resultado);
                        lista.sort(Comparator.comparing(User::getNombreCompleto));
                        callback.onExito(lista);
                    } catch (IllegalArgumentException error) {
                        callback.onError(mensaje(error));
                    }
                }).addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        final Map<String, Object> datos;
        final DocumentReference referencia;
        try {
            datos = HotelFirebase.nuevo(borrador, uid);
            // La UI conserva este ID entre reintentos y rotaciones.
            String id = idValido(borrador.getId());
            referencia = id == null ? db().collection("hoteles").document()
                    : db().collection("hoteles").document(id);
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        DocumentReference perfil = db().collection("usuarios").document(uid);
        DocumentReference evento = db().collection("auditoria").document("alta_" + referencia.getId());
        db().runTransaction(transaccion -> {
            Cuenta autor = exigirSuperadmin(uid, email, transaccion.get(perfil));
            DocumentSnapshot existente = transaccion.get(referencia);
            if (existente.exists()) {
                if (!mismoAlta(datos, existente.getData())) {
                    throw new IllegalArgumentException("Este formulario ya registró otro hotel. Abre un alta nueva.");
                }
                return HotelFirebase.desde(existente.getId(), existente.getData());
            }
            Map<String, Object> nuevo = new HashMap<>(datos);
            nuevo.put("creadoEn", FieldValue.serverTimestamp());
            nuevo.put("actualizadoEn", FieldValue.serverTimestamp());
            nuevo.put("ultimoEventoId", evento.getId());
            transaccion.set(referencia, nuevo);
            transaccion.set(evento, auditoria(autor, referencia.getId(), null, "ALTA_HOTEL",
                    "Se registró el hotel " + borrador.getNombre().trim() + "."));
            return HotelFirebase.desde(referencia.getId(), nuevo);
        }).addOnSuccessListener(hotel -> entregar(uid, hotel, callback))
                .addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                                     @NonNull ResultCallback<Hotel> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        try {
            if (idValido(hotelId) == null || idValido(usuarioId) == null) {
                throw new IllegalArgumentException("Selecciona un hotel y un administrador válidos.");
            }
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        DocumentReference autorRef = db().collection("usuarios").document(uid);
        DocumentReference hotelRef = db().collection("hoteles").document(hotelId);
        DocumentReference adminRef = db().collection("usuarios").document(usuarioId);
        DocumentReference evento = db().collection("auditoria").document();
        db().runTransaction(transaccion -> {
            Cuenta autor = exigirSuperadmin(uid, email, transaccion.get(autorRef));
            DocumentSnapshot documentoHotel = transaccion.get(hotelRef);
            DocumentSnapshot documentoAdmin = transaccion.get(adminRef);
            if (!documentoHotel.exists() || !documentoAdmin.exists()) {
                throw new IllegalArgumentException("No encontramos el hotel o el perfil del administrador.");
            }
            Hotel hotel = HotelFirebase.desde(hotelId, documentoHotel.getData());
            User administrador = usuarioAdministrativo(documentoAdmin);
            if (administrador.getRol() != Role.ADMIN_HOTEL || !administrador.isActivo()) {
                throw new IllegalArgumentException("Selecciona una cuenta de administrador de hotel activa.");
            }
            String asignado = administrador.getHotelId();
            if (asignado != null && !hotelId.equals(asignado)) {
                throw new IllegalArgumentException("Este administrador ya tiene otro hotel asignado.");
            }
            String anteriorId = hotel.getAdministradorId();
            if (usuarioId.equals(anteriorId)) {
                if (hotelId.equals(asignado)) return hotel;
                throw new IllegalArgumentException("La asignación actual es inconsistente. Revisa ambos perfiles antes de continuar.");
            }
            DocumentReference anteriorRef = null;
            if (anteriorId != null) {
                anteriorRef = db().collection("usuarios").document(anteriorId);
                DocumentSnapshot anterior = transaccion.get(anteriorRef);
                if (!anterior.exists() || !Role.ADMIN_HOTEL.name().equals(anterior.getString("rol"))
                        || !hotelId.equals(anterior.getString("hotelId"))) {
                    throw new IllegalArgumentException("No pudimos verificar la asignación del administrador anterior.");
                }
            }
            // Todas las lecturas preceden a las escrituras. Un conflicto reintenta todo.
            if (anteriorRef != null) {
                Map<String, Object> liberar = new HashMap<>();
                liberar.put("hotelId", null);
                liberar.put("actualizadoEn", FieldValue.serverTimestamp());
                transaccion.update(anteriorRef, liberar);
            }
            transaccion.update(adminRef, "hotelId", hotelId, "actualizadoEn", FieldValue.serverTimestamp());
            transaccion.update(hotelRef, "administradorId", usuarioId,
                    "actualizadoEn", FieldValue.serverTimestamp(), "ultimoEventoId", evento.getId());
            transaccion.set(evento, auditoria(autor, hotelId, usuarioId, "ASIGNAR_ADMINISTRADOR",
                    administrador.getNombreCompleto() + " quedó a cargo de " + hotel.getNombre() + "."));
            hotel.setAdministradorId(usuarioId);
            return hotel;
        }).addOnSuccessListener(hotel -> entregar(uid, hotel, callback))
                .addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                                   @NonNull ResultCallback<Hotel> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        try {
            if (idValido(hotelId) == null) {
                throw new IllegalArgumentException("Selecciona el hotel que quieres publicar o retirar.");
            }
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        DocumentReference autorRef = db().collection("usuarios").document(uid);
        DocumentReference hotelRef = db().collection("hoteles").document(hotelId);
        DocumentReference evento = db().collection("auditoria").document(UUID.randomUUID().toString());
        db().runTransaction(transaccion -> {
            Cuenta autor = exigirSuperadmin(uid, email, transaccion.get(autorRef));
            DocumentSnapshot documentoHotel = transaccion.get(hotelRef);
            if (!documentoHotel.exists() || documentoHotel.getData() == null) {
                throw new IllegalArgumentException("No encontramos este hotel en el servidor.");
            }
            Hotel hotel = HotelFirebase.desde(hotelId, documentoHotel.getData());
            if (hotel.isPublicado() == publicado) {
                return hotel;
            }
            if (publicado) {
                String adminId = hotel.getAdministradorId();
                if (adminId == null) {
                    throw new IllegalArgumentException("Asigna un administrador activo antes de publicar el hotel.");
                }
                DocumentSnapshot perfilAdmin = transaccion.get(db().collection("usuarios").document(adminId));
                if (!perfilAdmin.exists()) {
                    throw new IllegalArgumentException("No encontramos el perfil del administrador asignado.");
                }
                User administrador = usuarioAdministrativo(perfilAdmin);
                if (administrador.getRol() != Role.ADMIN_HOTEL || !administrador.isActivo()
                        || !hotelId.equals(administrador.getHotelId())) {
                    throw new IllegalArgumentException(
                            "El hotel necesita un administrador activo con esta asignación antes de publicarse.");
                }
                if (!hotel.aptoParaPublicar()) {
                    throw new IllegalArgumentException(
                            "Para publicar el hotel necesitas al menos cuatro fotografías y una habitación.");
                }
            }
            // Retirar sigue disponible aunque el hotel o su asignacion esten incompletos.
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("publicado", publicado);
            cambios.put("actualizadoEn", FieldValue.serverTimestamp());
            cambios.put("ultimoEventoId", evento.getId());
            transaccion.update(hotelRef, cambios);
            transaccion.set(evento, auditoria(autor, hotelId, hotel.getAdministradorId(),
                    publicado ? "PUBLICAR_HOTEL" : "RETIRAR_HOTEL",
                    (publicado ? "Se publicó el hotel " : "Se retiró del catálogo el hotel ")
                            + hotel.getNombre() + "."));
            hotel.setPublicado(publicado);
            return hotel;
        }).addOnSuccessListener(hotel -> entregar(uid, hotel, callback))
                .addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void usuarios(@NonNull ResultCallback<List<User>> callback) {
        Acceso acceso = acceso(callback);
        if (acceso == null) return;
        db().collection("usuarios").get(Source.SERVER).addOnSuccessListener(resultado -> {
            if (!acceso.vigente()) return;
            try {
                exigirAutor(acceso, resultado);
                List<User> lista = mapearUsuarios(resultado);
                callback.onExito(lista);
            } catch (IllegalArgumentException error) {
                callback.onError(mensaje(error));
            }
        }).addOnFailureListener(error -> {
            if (acceso.vigente()) callback.onError(mensaje(error));
        });
    }

    @Override
    public Suscripcion escucharUsuarios(@NonNull ResultCallback<List<User>> callback) {
        Acceso acceso = acceso(callback);
        if (acceso == null) return () -> { };
        EscuchaUsuarios escucha = new EscuchaUsuarios(acceso, callback);
        escucha.iniciar();
        return escucha;
    }

    @Override
    public void bitacora(@NonNull ResultCallback<List<LogEntry>> callback) {
        String uid = actor(callback);
        if (uid == null) return;
        consultaAuditoria().addOnSuccessListener(resultado -> {
            if (!vigente(uid, callback)) return;
            callback.onExito(mapearEventos(resultado));
        }).addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback) {
        Acceso acceso = acceso(callback);
        if (acceso == null) return;
        Task<QuerySnapshot> cuentas = db().collection("usuarios").get(Source.SERVER);
        Task<QuerySnapshot> alojamientos = db().collection("hoteles").get(Source.SERVER);
        Task<QuerySnapshot> eventos = consultaAuditoria();
        Tasks.whenAll(cuentas, alojamientos, eventos).addOnSuccessListener(ignorado -> {
            if (!acceso.vigente()) return;
            try {
                exigirAutor(acceso, cuentas.getResult());
                List<User> usuarios = mapearUsuarios(cuentas.getResult());
                List<Hotel> lista = mapearHoteles(alojamientos.getResult());
                hoteles.reemplazarCache(lista);
                int activos = 0, publicados = 0, sinAdmin = 0;
                for (User usuario : usuarios) if (usuario.isActivo()) activos++;
                for (Hotel hotel : lista) {
                    if (hotel.isPublicado()) publicados++;
                    if (hotel.getAdministradorId() == null) sinAdmin++;
                }
                List<LogEntry> ultimos = mapearEventos(eventos.getResult());
                callback.onExito(new ResumenSuperadmin(activos, usuarios.size(), -1, -1,
                        publicados, lista.size(), sinAdmin,
                        new ArrayList<>(ultimos.subList(0, Math.min(3, ultimos.size())))));
            } catch (IllegalArgumentException error) {
                callback.onError(mensaje(error));
            }
        }).addOnFailureListener(error -> {
            if (acceso.vigente()) callback.onError(mensaje(error));
        });
    }

    @Override
    public void cambiarActivo(@NonNull String usuarioId, boolean activo, @NonNull ResultCallback<User> callback) {
        Acceso acceso = acceso(callback);
        if (acceso == null) return;
        try {
            if (idValido(usuarioId) == null) {
                throw new IllegalArgumentException("Selecciona una cuenta válida.");
            }
        } catch (IllegalArgumentException error) {
            callback.onError(mensaje(error));
            return;
        }
        DocumentReference actorRef = db().collection("usuarios").document(acceso.uid);
        DocumentReference usuarioRef = db().collection("usuarios").document(usuarioId);
        DocumentReference eventoRef = db().collection("auditoria").document();
        db().runTransaction(transaccion -> {
            if (!acceso.vigente()) throw new IllegalArgumentException(sesionCambiada());
            Cuenta autor = exigirSuperadmin(acceso.uid, acceso.email, transaccion.get(actorRef));
            User usuario = usuarioAdministrativo(transaccion.get(usuarioRef));
            if (!activo && !usuario.esDesactivable()) {
                throw new IllegalArgumentException("La cuenta de un superadministrador no se puede desactivar.");
            }
            // El estado ya confirmado es un reintento correcto y no genera otra auditoría.
            if (usuario.isActivo() == activo) return usuario;
            boolean anterior = usuario.isActivo();
            Map<String, Object> evento = auditoria(autor, null, null,
                    activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO",
                    (activo ? "Se activó la cuenta de " : "Se desactivó la cuenta de ")
                            + usuario.getNombreCompleto().trim() + " (" + usuario.getEmail() + ").");
            evento.put("usuarioId", usuario.getId());
            evento.put("rol", usuario.getRol().name());
            evento.put("activoAnterior", anterior);
            evento.put("activo", activo);
            evento.put("estado", activo ? "ACTIVO" : "INACTIVO");
            if (!acceso.vigente()) throw new IllegalArgumentException(sesionCambiada());
            transaccion.update(usuarioRef, "activo", activo, "actualizadoEn", FieldValue.serverTimestamp());
            transaccion.set(eventoRef, evento);
            usuario.setActivo(activo);
            return usuario;
        }).addOnSuccessListener(usuario -> {
            // El objeto procede de la transacción aceptada; no se hace una lectura posterior
            // que pueda convertir una escritura confirmada en un falso error.
            if (acceso.vigente()) callback.onExito(usuario);
        }).addOnFailureListener(error -> {
            if (acceso.vigente()) callback.onError(mensaje(error));
        });
    }

    @Override
    public void conductores(@NonNull ResultCallback<List<Driver>> callback) {
        callback.onError("La gestión de conductores está pendiente de conectar con su API.");
    }

    @Override
    public void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                                   @NonNull ResultCallback<Driver> callback) {
        callback.onError("La gestión de conductores está pendiente de conectar con su API.");
    }

    private void entregar(String uid, Hotel hotel, ResultCallback<Hotel> callback) {
        if (!vigente(uid, callback)) return;
        hoteles.guardarEnCache(hotel);
        callback.onExito(hotel);
    }

    private static String actor(ResultCallback<?> callback) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || SessionManager.getRolActivo() != Role.SUPERADMIN
                || !usuario.getUid().equals(SessionManager.getIdentidadId())) {
            callback.onError("Inicia sesión como superadministrador para continuar.");
            return null;
        }
        return usuario.getUid();
    }

    private static boolean vigente(String uid, ResultCallback<?> callback) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || !uid.equals(usuario.getUid())
                || !uid.equals(SessionManager.getIdentidadId())
                || SessionManager.getRolActivo() != Role.SUPERADMIN) {
            callback.onError("La sesión cambió. Vuelve a cargar esta pantalla.");
            return false;
        }
        return true;
    }

    private static Cuenta exigirSuperadmin(String uid, String email, DocumentSnapshot documento) {
        Cuenta cuenta = PerfilFirebase.cuenta(uid, email, documento.getData());
        if (cuenta.getRol() != Role.SUPERADMIN) {
            throw new IllegalArgumentException("Solo el superadministrador puede realizar esta operación.");
        }
        return cuenta;
    }

    private static List<Hotel> mapearHoteles(QuerySnapshot resultado) {
        List<Hotel> lista = new ArrayList<>();
        for (DocumentSnapshot documento : resultado) {
            lista.add(HotelFirebase.desde(documento.getId(), documento.getData()));
        }
        lista.sort(Comparator.comparing(Hotel::isPublicado).thenComparing(Hotel::getNombre));
        return lista;
    }

    private static List<User> mapearUsuarios(QuerySnapshot resultado) {
        List<User> lista = new ArrayList<>();
        for (DocumentSnapshot documento : resultado) lista.add(usuarioAdministrativo(documento));
        lista.sort(Comparator.comparingInt((User usuario) -> usuario.getRol().ordinal())
                .thenComparing(User::getNombreCompleto, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(User::getId));
        return lista;
    }

    private static User usuarioAdministrativo(DocumentSnapshot documento) {
        return FirebaseCuentasAdministradas.desde(documento);
    }

    /** Evita entregar resultados de una sesión anterior, incluso si se vuelve al mismo UID. */
    private static final class Acceso {
        final String uid;
        final String email;
        final long generacion;

        Acceso(String uid, String email, long generacion) {
            this.uid = uid;
            this.email = email;
            this.generacion = generacion;
        }

        boolean vigente() {
            FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
            return actualizarIdentidad() == generacion && actual != null
                    && uid.equals(actual.getUid()) && uid.equals(SessionManager.getIdentidadId())
                    && SessionManager.getRolActivo() == Role.SUPERADMIN;
        }
    }

    private static Acceso acceso(ResultCallback<?> callback) {
        observarIdentidad();
        String uid = actor(callback);
        if (uid == null) return null;
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (actual == null || !uid.equals(actual.getUid())) return null;
        return new Acceso(uid, actual.getEmail(), actualizarIdentidad());
    }

    private static synchronized void observarIdentidad() {
        if (observandoIdentidad) return;
        observandoIdentidad = true;
        actualizarIdentidad();
        // Estos dos observadores guardan únicamente la generación del acceso en el proceso.
        FirebaseAuth.getInstance().addAuthStateListener(auth -> actualizarIdentidad());
        SessionManager.sesion().observeForever(cuenta -> actualizarIdentidad());
    }

    private static synchronized long actualizarIdentidad() {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        String authUid = actual == null ? null : actual.getUid();
        String sesionUid = SessionManager.getIdentidadId();
        Role rol = SessionManager.getRolActivo();
        if (!Objects.equals(identidadAuth, authUid) || !Objects.equals(identidadSesion, sesionUid)
                || rolSesion != rol) {
            identidadAuth = authUid;
            identidadSesion = sesionUid;
            rolSesion = rol;
            GENERACION.incrementAndGet();
        }
        return GENERACION.get();
    }

    private static void exigirAutor(Acceso acceso, QuerySnapshot cuentas) {
        if (cuentas == null || cuentas.getMetadata().isFromCache()
                || cuentas.getMetadata().hasPendingWrites()) {
            throw new IllegalArgumentException("Hay cambios pendientes de confirmar en el servidor. Vuelve a cargar la pantalla.");
        }
        for (DocumentSnapshot documento : cuentas.getDocuments()) {
            if (acceso.uid.equals(documento.getId())) {
                exigirSuperadmin(acceso.uid, acceso.email, documento);
                return;
            }
        }
        throw new IllegalArgumentException("No encontramos el perfil del superadministrador en el servidor.");
    }

    private static String sesionCambiada() {
        return "La sesión cambió. Vuelve a cargar esta pantalla.";
    }

    private static final class EscuchaUsuarios implements Suscripcion {
        private final Acceso acceso;
        private final ResultCallback<List<User>> callback;
        private final List<ListenerRegistration> registros = new ArrayList<>();
        private FirebaseAuth.AuthStateListener authListener;
        private Observer<Cuenta> sesionObserver;
        private boolean cerrada;
        private boolean consultando;
        private boolean datosServidor;

        EscuchaUsuarios(Acceso acceso, ResultCallback<List<User>> callback) {
            this.acceso = acceso;
            this.callback = callback;
        }

        void iniciar() {
            authListener = auth -> comprobarSesion();
            FirebaseAuth.getInstance().addAuthStateListener(authListener);
            sesionObserver = cuenta -> comprobarSesion();
            SessionManager.sesion().observeForever(sesionObserver);
            if (!comprobarSesion()) return;
            agregar(db().collection("usuarios").document(acceso.uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (perfil, error) -> {
                        if (!comprobarSesion()) return;
                        if (error != null) { fallar(error); return; }
                        if (perfil == null || perfil.getMetadata().isFromCache()
                                || perfil.getMetadata().hasPendingWrites()) return;
                        try {
                            exigirSuperadmin(acceso.uid, acceso.email, perfil);
                            consultar();
                        } catch (IllegalArgumentException invalido) { fallar(invalido); }
                    }));
            // Los listeners pueden entregar únicamente caché cuando no hay red. Esta lectura
            // resuelve el arranque o muestra un error de conexión en lugar de cargar sin fin.
            db().collection("usuarios").get(Source.SERVER).addOnSuccessListener(cuentas -> {
                if (!comprobarSesion() || datosServidor) return;
                try {
                    entregar(cuentas);
                    consultar();
                } catch (IllegalArgumentException invalido) { fallar(invalido); }
            }).addOnFailureListener(error -> {
                if (comprobarSesion() && !datosServidor) fallar(error);
            });
        }

        private void consultar() {
            if (consultando || !comprobarSesion()) return;
            consultando = true;
            agregar(db().collection("usuarios").addSnapshotListener(MetadataChanges.INCLUDE,
                    (cuentas, error) -> {
                        if (!comprobarSesion()) return;
                        if (error != null) { fallar(error); return; }
                        if (cuentas == null || cuentas.getMetadata().isFromCache()
                                || cuentas.getMetadata().hasPendingWrites()) return;
                        try {
                            entregar(cuentas);
                        } catch (IllegalArgumentException invalido) { fallar(invalido); }
                    }));
        }

        private void entregar(QuerySnapshot cuentas) {
            // La misma instantánea del directorio confirma al actor activo.
            exigirAutor(acceso, cuentas);
            List<User> usuarios = mapearUsuarios(cuentas);
            if (comprobarSesion()) {
                datosServidor = true;
                callback.onExito(usuarios);
            }
        }

        private void agregar(ListenerRegistration registro) {
            if (cerrada) registro.remove();
            else registros.add(registro);
        }

        private boolean comprobarSesion() {
            if (cerrada) return false;
            if (acceso.vigente()) return true;
            cerrar();
            return false;
        }

        private void fallar(Exception error) {
            if (!comprobarSesion()) return;
            boolean sinPermiso = error instanceof FirebaseFirestoreException
                    && (((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    || ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.UNAUTHENTICATED);
            if (error instanceof IllegalArgumentException || sinPermiso) cerrar();
            callback.onError(mensaje(error));
        }

        @Override public void cerrar() {
            if (cerrada) return;
            cerrada = true;
            for (ListenerRegistration registro : registros) registro.remove();
            registros.clear();
            if (authListener != null) {
                FirebaseAuth.getInstance().removeAuthStateListener(authListener);
                authListener = null;
            }
            if (sesionObserver != null) {
                SessionManager.sesion().removeObserver(sesionObserver);
                sesionObserver = null;
            }
        }
    }

    private static Task<QuerySnapshot> consultaAuditoria() {
        return db().collection("auditoria").orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100).get(Source.SERVER);
    }

    private static List<LogEntry> mapearEventos(QuerySnapshot resultado) {
        List<LogEntry> lista = new ArrayList<>();
        for (DocumentSnapshot documento : resultado) {
            Timestamp tiempo = documento.getTimestamp("timestamp");
            if (tiempo == null) continue;
            lista.add(new LogEntry(LocalDateTime.ofInstant(tiempo.toDate().toInstant(), ZoneId.systemDefault()),
                    documento.getString("actorNombre"), LogEntry.Evento.ACCION_ADMINISTRATIVA,
                    documento.getString("detalle")));
        }
        return lista;
    }

    private static Map<String, Object> auditoria(Cuenta autor, String hotelId, String adminId,
                                                 String accion, String detalle) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("actorUid", autor.getIdentidadId());
        datos.put("actorNombre", autor.getNombreCompleto());
        datos.put("evento", LogEntry.Evento.ACCION_ADMINISTRATIVA.name());
        datos.put("accion", accion);
        datos.put("detalle", detalle);
        datos.put("hotelId", hotelId);
        datos.put("administradorId", adminId);
        datos.put("timestamp", FieldValue.serverTimestamp());
        return datos;
    }

    private static boolean mismoAlta(Map<String, Object> esperado, Map<String, Object> existente) {
        if (existente == null) return false;
        for (String campo : new String[]{"nombre", "ciudad", "distrito", "direccion", "creadoPor"}) {
            if (!esperado.get(campo).equals(existente.get(campo))) return false;
        }
        for (String campo : new String[]{"latitud", "longitud"}) {
            if (!(existente.get(campo) instanceof Number)
                    || Double.compare(((Number) esperado.get(campo)).doubleValue(),
                    ((Number) existente.get(campo)).doubleValue()) != 0) return false;
        }
        return true;
    }

    private static String idValido(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.trim().isEmpty() || id.contains("/") || id.length() > 200) {
            throw new IllegalArgumentException("El identificador no es válido.");
        }
        return id;
    }

    private static String mensaje(Exception error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException && causa.getMessage() != null) return causa.getMessage();
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code codigo = ((FirebaseFirestoreException) error).getCode();
            if (codigo == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "No tienes permiso para esta operación. Revisa el rol de tu cuenta y las reglas publicadas.";
            }
            if (codigo == FirebaseFirestoreException.Code.UNAVAILABLE
                    || codigo == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return "No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.";
            }
        }
        return "No pudimos completar la operación administrativa. Inténtalo de nuevo.";
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }
}
