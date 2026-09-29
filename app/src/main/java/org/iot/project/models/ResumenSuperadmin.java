package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Lo que el superadministrador ve de un vistazo (§47).
 *
 * <p>Las cifras vienen contadas y no como listas para que la portada no tenga
 * que cruzarlas: contar en la pantalla es repartir la regla —que es un usuario
 * activo, que es un conductor habilitado— entre el repositorio y el fragment.
 */
public final class ResumenSuperadmin {

    private final int usuariosActivos;
    private final int usuariosTotales;
    private final int conductoresHabilitados;
    private final int conductoresTotales;
    private final int hotelesPublicados;
    private final int hotelesTotales;
    private final int hotelesSinAdministrador;
    private final List<LogEntry> ultimos;

    public ResumenSuperadmin(int usuariosActivos, int usuariosTotales,
                             int conductoresHabilitados, int conductoresTotales,
                             int hotelesPublicados, int hotelesTotales,
                             int hotelesSinAdministrador, @NonNull List<LogEntry> ultimos) {
        this.usuariosActivos = usuariosActivos;
        this.usuariosTotales = usuariosTotales;
        this.conductoresHabilitados = conductoresHabilitados;
        this.conductoresTotales = conductoresTotales;
        this.hotelesPublicados = hotelesPublicados;
        this.hotelesTotales = hotelesTotales;
        this.hotelesSinAdministrador = hotelesSinAdministrador;
        this.ultimos = Collections.unmodifiableList(ultimos);
    }

    public int getUsuariosActivos() {
        return usuariosActivos;
    }

    public int getUsuariosTotales() {
        return usuariosTotales;
    }

    public int getConductoresHabilitados() {
        return conductoresHabilitados;
    }

    public int getConductoresTotales() {
        return conductoresTotales;
    }

    public int getHotelesPublicados() {
        return hotelesPublicados;
    }

    public int getHotelesTotales() {
        return hotelesTotales;
    }

    public int getHotelesSinAdministrador() {
        return hotelesSinAdministrador;
    }

    /** Cuentas desactivadas: ninguno de ellos puede volver a entrar (RF-009). */
    public int getUsuariosInactivos() {
        return usuariosTotales - usuariosActivos;
    }

    /** Conductores que esperan aprobacion (RF-077). */
    public int getConductoresPendientes() {
        return conductoresTotales - conductoresHabilitados;
    }

    /** Hoteles que todavia no se ofrecen al cliente. */
    public int getHotelesSinPublicar() {
        return hotelesTotales - hotelesPublicados;
    }

    public boolean hayConductoresPendientes() {
        return getConductoresPendientes() > 0;
    }

    /** Si hay algo que el superadministrador tenga que resolver. */
    public boolean hayPendientes() {
        return hayConductoresPendientes() || hotelesSinAdministrador > 0;
    }

    /** Los ultimos movimientos de la bitacora, del mas reciente al mas antiguo. */
    @NonNull
    public List<LogEntry> getUltimos() {
        return ultimos;
    }
}
