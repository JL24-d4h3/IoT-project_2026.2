package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import org.iot.project.data.firebase.FirebaseErrores;
import org.iot.project.data.firebase.PerfilFirebase;

/**
 * Sesion activa: con que cuenta se entro y, por tanto, que rol esta en uso.
 *
 * <p>Publica una cuenta solo despues de autenticarla y comprobar su perfil
 * contra el servidor. El rol y el hotel proceden del perfil protegido.
 *
 * <p>La sesion se publica como {@link LiveData} porque cambiarla no es un
 * detalle interno: al iniciar o cerrar sesion hay que cambiar el grafo entero
 * de navegacion. {@code MainActivity} lo observa y reacciona; asi el momento en
 * que eso ocurre esta en un solo sitio y no repartido por las pantallas.
 *
 * <p>Guarda la {@link Cuenta} completa y no solo su identificador: las pantallas
 * de cada rol necesitan saber a quien estan atendiendo, y volver a preguntarlo
 * al repositorio en cada una seria pedir cuatro veces el mismo dato.
 *
 * <p><b>Un conductor no es un usuario.</b> Los roles de cliente, administrador
 * y superadmin entran con un {@link org.iot.project.models.User}; el conductor
 * entra con un {@link org.iot.project.models.Driver}, que es otro modelo. Por
 * eso la identidad se expone por separado: quien necesite al conductor llama a
 * {@link #getConductorId()} y no puede recibir por error el identificador de un
 * usuario.
 */
public final class SessionManager {

    /** Identidades de demostracion, para entrar sin escribir un correo. */
    public static final String USUARIO_CLIENTE = "U1";
    public static final String USUARIO_ADMIN = "U2";
    public static final String USUARIO_SUPERADMIN = "U3";
    public static final String CONDUCTOR_ACTIVO = "D1";

    private static final MutableLiveData<Cuenta> SESION = new MutableLiveData<>(null);
    private static final MutableLiveData<Boolean> RESTAURANDO = new MutableLiveData<>(false);
    private static final MutableLiveData<String> AVISO = new MutableLiveData<>(null);
    private static boolean inicializada;
    private static long version;
    private static ListenerRegistration perfilListener;

    private SessionManager() {
    }

    /** Se registra una sola vez por proceso; un alta no dispara restauracion. */
    public static void inicializar() {
        if (inicializada) {
            return;
        }
        inicializada = true;
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser usuario = auth.getCurrentUser();
            Cuenta cuenta = SESION.getValue();
            if (usuario == null || (cuenta != null
                    && !cuenta.getIdentidadId().equals(usuario.getUid()))) {
                quitarPerfilListener();
                SESION.setValue(null);
            }
        });
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        long solicitud = ++version;
        RESTAURANDO.setValue(true);
        ServiceLocator.acceso().restaurarSesion(new ResultCallback<Cuenta>() {
            @Override
            public void onExito(@NonNull Cuenta cuenta) {
                if (solicitud == version) {
                    iniciarSesion(cuenta);
                }
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (solicitud == version) {
                    SESION.setValue(null);
                    RESTAURANDO.setValue(false);
                    AVISO.setValue(mensaje);
                }
            }
        });
    }

    public static LiveData<Boolean> restaurando() {
        return RESTAURANDO;
    }

    public static LiveData<String> aviso() {
        return AVISO;
    }

    public static void limpiarAviso() {
        AVISO.setValue(null);
    }

    /** La cuenta en sesion, o {@code null} mientras nadie haya entrado. */
    @NonNull
    public static LiveData<Cuenta> sesion() {
        return SESION;
    }

    /**
     * Inicia sesion con una cuenta.
     *
     * <p>Se llama desde el hilo principal tras cargar el perfil autorizado.
     */
    public static void iniciarSesion(@NonNull Cuenta cuenta) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || !usuario.getUid().equals(cuenta.getIdentidadId())) {
            return;
        }
        version++;
        quitarPerfilListener();
        AVISO.setValue(null);
        SESION.setValue(cuenta);
        RESTAURANDO.setValue(false);
        observarPerfil(usuario.getUid(), version);
    }

    public static void cerrarSesion() {
        version++;
        quitarPerfilListener();
        SESION.setValue(null);
        RESTAURANDO.setValue(false);
        ServiceLocator.acceso().cerrarSesion();
    }

    private static void observarPerfil(String uid, long solicitud) {
        perfilListener = FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .addSnapshotListener((documento, error) -> {
                    FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
                    if (solicitud != version || usuario == null || !uid.equals(usuario.getUid())) {
                        return;
                    }
                    if (error != null) {
                        if (error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                                || error.getCode() == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                            cerrarSesion();
                            AVISO.setValue(FirebaseErrores.mensaje(error));
                        }
                        return;
                    }
                    // Una copia local antigua nunca vuelve a habilitar un perfil.
                    if (documento == null || documento.getMetadata().isFromCache()
                            || documento.getMetadata().hasPendingWrites()) {
                        return;
                    }
                    try {
                        SESION.setValue(PerfilFirebase.cuenta(uid, usuario.getEmail(), documento.getData()));
                    } catch (IllegalArgumentException fallo) {
                        cerrarSesion();
                        AVISO.setValue(FirebaseErrores.mensaje(fallo));
                    }
                });
    }

    private static void quitarPerfilListener() {
        if (perfilListener != null) {
            perfilListener.remove();
            perfilListener = null;
        }
    }

    public static boolean haySesion() {
        return SESION.getValue() != null;
    }

    @Nullable
    public static Cuenta getCuenta() {
        return SESION.getValue();
    }

    @Nullable
    public static Role getRolActivo() {
        Cuenta actual = SESION.getValue();
        return actual == null ? null : actual.getRol();
    }

    /** Identidad en sesion, sea un usuario o un conductor, o {@code null}. */
    @Nullable
    public static String getIdentidadId() {
        Cuenta actual = SESION.getValue();
        return actual == null ? null : actual.getIdentidadId();
    }

    /**
     * Identificador del usuario ({@code User}) en sesion.
     *
     * <p>Es {@code null} para un conductor, que no tiene uno: para el, la
     * identidad esta en {@link #getConductorId()}.
     */
    @Nullable
    public static String getUsuarioId() {
        return getRolActivo() == Role.CONDUCTOR ? null : getIdentidadId();
    }

    /**
     * Identificador del conductor ({@code Driver}) en sesion.
     *
     * <p>Es {@code null} para los demas roles. Existe para que una pantalla del
     * conductor no tenga que leer {@link #getIdentidadId()} y confiar en que
     * dentro hay un conductor.
     */
    @Nullable
    public static String getConductorId() {
        return getRolActivo() == Role.CONDUCTOR ? getIdentidadId() : null;
    }

    /**
     * Hotel que administra la sesion activa, o {@code null} si su rol no
     * administra ninguno.
     *
     * <p>Solo hay un hotel administrado por sesion porque asi es el modelo: un
     * administrador pertenece a un hotel, no a una lista (RF-008).
     *
     * <p>La asignacion se cargo desde el perfil del servidor antes de abrir la
     * interfaz; no se busca por identidades de demostracion.
     *
     * <p>Puede devolver {@code null} aunque el rol sea el de administrador: una
     * cuenta recien creada todavia no tiene hotel asignado, y ese estado es real
     * hasta que un superadministrador se lo asigne (RF-008).
     */
    @Nullable
    public static String getHotelAdministrado() {
        if (getRolActivo() != Role.ADMIN_HOTEL) {
            return null;
        }
        Cuenta cuenta = SESION.getValue();
        return cuenta != null ? cuenta.getHotelId() : null;
    }

    /**
     * Si la sesion activa puede consultar y modificar el hotel indicado.
     *
     * <p>Es RF-023: el administrador gestiona su hotel y solo el suyo. Vive
     * aqui y no en cada repositorio porque es una regla de identidad, no de
     * datos, y repetida en cinco sitios el dia que cambie se cambia en cuatro.
     *
     * <p>El superadmin es la excepcion deliberada: RF-059 le da acceso a los
     * reportes de reservas de cualquier hotel, y RF-077 a aprobar hoteles. Sin
     * esta excepcion, su propio panel no podria leer nada.
     */
    public static boolean puedeGestionar(@NonNull String hotelId) {
        Role rol = getRolActivo();
        if (rol == Role.SUPERADMIN) {
            return true;
        }
        return rol == Role.ADMIN_HOTEL && hotelId.equals(getHotelAdministrado());
    }

    /**
     * UID del cliente/administrador, o cadena vacia sin sesion de usuario.
     * Nunca sustituye una sesion ausente por la identidad de otra persona.
     */
    @NonNull
    public static String getUsuarioIdSeguro() {
        String identidad = getUsuarioId();
        return identidad != null ? identidad : "";
    }
}
