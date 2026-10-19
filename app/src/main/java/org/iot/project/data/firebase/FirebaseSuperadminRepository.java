package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

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
import java.util.UUID;

/** Alta, asignacion y publicacion persistentes con auditoria atomica. */
public final class FirebaseSuperadminRepository implements SuperadminRepository {

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
        String uid = actor(callback);
        if (uid == null) return;
        db().collection("usuarios").get(Source.SERVER).addOnSuccessListener(resultado -> {
            if (!vigente(uid, callback)) return;
            try {
                List<User> lista = mapearUsuarios(resultado);
                lista.sort(Comparator.comparingInt((User u) -> u.getRol().ordinal())
                        .thenComparing(User::getNombreCompleto));
                callback.onExito(lista);
            } catch (IllegalArgumentException error) {
                callback.onError(mensaje(error));
            }
        }).addOnFailureListener(error -> callback.onError(mensaje(error)));
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
        String uid = actor(callback);
        if (uid == null) return;
        Task<QuerySnapshot> cuentas = db().collection("usuarios").get(Source.SERVER);
        Task<QuerySnapshot> alojamientos = db().collection("hoteles").get(Source.SERVER);
        Task<QuerySnapshot> eventos = consultaAuditoria();
        Tasks.whenAll(cuentas, alojamientos, eventos).addOnSuccessListener(ignorado -> {
            if (!vigente(uid, callback)) return;
            try {
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
        }).addOnFailureListener(error -> callback.onError(mensaje(error)));
    }

    @Override
    public void cambiarActivo(@NonNull String usuarioId, boolean activo, @NonNull ResultCallback<User> callback) {
        callback.onError("La activación de cuentas todavía se gestiona desde la consola de Firebase.");
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
        return lista;
    }

    private static User usuarioAdministrativo(DocumentSnapshot documento) {
        Map<String, Object> datos = documento.getData();
        if (datos == null || !(datos.get("activo") instanceof Boolean)) {
            throw new IllegalArgumentException("Hay un perfil sin configuración válida.");
        }
        // La consulta administrativa incluye cuentas inactivas; no autentica con ellas.
        boolean activo = Boolean.TRUE.equals(datos.get("activo"));
        Map<String, Object> lectura = new HashMap<>(datos);
        lectura.put("activo", true);
        Object correo = datos.get("email");
        if (!(correo instanceof String)) {
            throw new IllegalArgumentException("Hay un perfil sin correo válido.");
        }
        User usuario = PerfilFirebase.usuario(documento.getId(), (String) correo, lectura);
        usuario.setActivo(activo);
        return usuario;
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
        return "No pudimos completar la operación del hotel. Inténtalo de nuevo.";
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }
}
