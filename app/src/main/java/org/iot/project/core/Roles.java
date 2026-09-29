package org.iot.project.core;

import androidx.annotation.NonNull;

import org.iot.project.R;
import org.iot.project.models.Role;

/**
 * Que grafo, que barra inferior y que pantalla de inicio le tocan a cada rol.
 *
 * <p>El mapeo vive entero aqui a proposito. Repartido entre {@code MainActivity},
 * el selector de rol y las pantallas, agregar una seccion a un rol obligaria a
 * acordarse de tres sitios distintos, y el dia que uno se olvidara la barra
 * ofreceria un destino que ese grafo no tiene: la pulsacion no haria nada y el
 * fallo no apareceria hasta ejecutar la aplicacion.
 *
 * <p>Es la pieza que sostiene RF-004: cada rol recibe unicamente la interfaz
 * que le corresponde, y lo recibe de una sola decision.
 */
public final class Roles {

    private final int grafo;
    private final int menu;
    private final int inicio;

    private static final Roles CLIENTE = new Roles(
            R.navigation.nav_client, R.menu.menu_bottom_nav, R.id.homeFragment);

    private static final Roles ADMIN_HOTEL = new Roles(
            R.navigation.nav_hotel_admin, R.menu.menu_bottom_nav_admin, R.id.adminHomeFragment);

    private static final Roles CONDUCTOR = new Roles(
            R.navigation.nav_driver, R.menu.menu_bottom_nav_driver, R.id.driverHomeFragment);

    private static final Roles SUPERADMIN = new Roles(
            R.navigation.nav_superadmin, R.menu.menu_bottom_nav_superadmin,
            R.id.superadminHomeFragment);

    private Roles(int grafo, int menu, int inicio) {
        this.grafo = grafo;
        this.menu = menu;
        this.inicio = inicio;
    }

    /**
     * Los ajustes de navegacion de un rol.
     *
     * <p>Los cuatro roles de {@link Role} estan cubiertos uno por uno, sin
     * {@code default}: asi, si algun dia se agrega un rol nuevo, el caso que
     * falta salta a la vista al leer el metodo en lugar de quedar escondido
     * detras de un valor por defecto.
     */
    @NonNull
    public static Roles de(@NonNull Role rol) {
        switch (rol) {
            case ADMIN_HOTEL:
                return ADMIN_HOTEL;
            case CONDUCTOR:
                return CONDUCTOR;
            case SUPERADMIN:
                return SUPERADMIN;
            case CLIENTE:
                return CLIENTE;
        }
        // Inalcanzable con los roles de hoy. Si se llegara aqui, la interfaz del
        // cliente es el unico destino que no administra nada.
        return CLIENTE;
    }

    /** Identificador del grafo de navegacion del rol. */
    public int getGrafo() {
        return grafo;
    }

    /** Identificador del menu de la barra inferior del rol. */
    public int getMenu() {
        return menu;
    }

    /** Destino con el que arranca el rol al iniciar sesion. */
    public int getInicio() {
        return inicio;
    }
}
