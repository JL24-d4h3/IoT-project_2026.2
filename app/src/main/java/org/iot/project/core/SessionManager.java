package org.iot.project.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.Role;

/**
 * Sesion activa: con que cuenta se entro y, por tanto, que rol esta en uso.
 *
 * <p>No hay autenticacion real (el entregable es solo front end), asi que el
 * rol lo decide la cuenta con la que se entra o el selector de demostracion de
 * la pantalla de acceso. Lo que si es real es la consecuencia: el rol decide
 * que grafo de navegacion se carga y, por tanto, que ve el usuario (RF-004).
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

    private static final MutableLiveData<Cuenta> SESION = new MutableLiveData<>();

    private SessionManager() {
    }

    /** La cuenta en sesion, o {@code null} mientras nadie haya entrado. */
    @NonNull
    public static LiveData<Cuenta> sesion() {
        return SESION;
    }

    /**
     * Inicia sesion con una cuenta.
     *
     * <p>Se llama desde el hilo principal: el resultado del acceso y el toque en
     * el selector de demostracion llegan los dos por ahi.
     */
    public static void iniciarSesion(@NonNull Cuenta cuenta) {
        SESION.setValue(cuenta);
    }

    public static void cerrarSesion() {
        SESION.setValue(null);
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
     * <p>La relacion se lee del repositorio y no de {@code MockData} para que el
     * dia que exista un backend solo cambie el repositorio (§49). Y se lee de
     * forma sincrona —igual que {@code hoteles().hotel(id)}— porque las cinco
     * pantallas del administrador la consultan al arrancar y ninguna esta
     * escrita para esperar una respuesta.
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
        Hotel hotel = ServiceLocator.hoteles()
                .hotelDeAdministrador(getUsuarioIdSeguro());
        return hotel != null ? hotel.getId() : null;
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
     * Identificador del usuario en sesion, con el cliente de demostracion como
     * valor por defecto. Evita comprobaciones de nulo en las pantallas ya
     * construidas, que son todas del cliente.
     *
     * <p>Un conductor no es un {@code User}: si se llamara a esto con un
     * conductor en sesion, devolveria el cliente de demostracion. Quien tenga
     * que atender a un conductor debe usar {@link #getConductorId()}.
     */
    @NonNull
    public static String getUsuarioIdSeguro() {
        String identidad = getUsuarioId();
        return identidad != null ? identidad : USUARIO_CLIENTE;
    }
}
