package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.User;

import java.util.List;

/**
 * Panel del superadministrador (§47): RF-005, RF-006, RF-007, RF-008, RF-059,
 * RF-077, RF-078 y RF-118 a RF-120.
 *
 * <p>Es un repositorio propio y no un puñado de consultas repartidas entre los
 * que ya existen porque las reglas que aplica son suyas y de nadie mas: quien
 * puede desactivarse, que hace falta para dar de alta un hotel, a quien se le
 * puede asignar uno. Repartidas por ahi, la proxima pantalla que las necesite
 * las escribiria otra vez.
 */
public interface SuperadminRepository {

    /** Cifras de la plataforma y ultimos movimientos, en una sola llamada. */
    void resumen(@NonNull ResultCallback<ResumenSuperadmin> callback);

    /** RF-005: cuentas de cliente, administrador y superadministrador. */
    void usuarios(@NonNull ResultCallback<List<User>> callback);

    /** RF-006: activar o desactivar una cuenta. */
    void cambiarActivo(@NonNull String usuarioId, boolean activo,
                       @NonNull ResultCallback<User> callback);

    /** RF-077: conductores, habilitados y pendientes. */
    void conductores(@NonNull ResultCallback<List<Driver>> callback);

    /** RF-078: aprobar a un conductor o retirarle la habilitacion. */
    void habilitarConductor(@NonNull String conductorId, boolean habilitado,
                            @NonNull ResultCallback<Driver> callback);

    /** Los hoteles de la plataforma, con su estado de publicacion. */
    void hoteles(@NonNull ResultCallback<List<Hotel>> callback);

    /** RF-008: administradores de hotel, para poder asignar uno. */
    void administradores(@NonNull ResultCallback<List<User>> callback);

    /** RF-007: dar de alta un hotel. Nace sin publicar y sin administrador. */
    void registrarHotel(@NonNull Hotel borrador, @NonNull ResultCallback<Hotel> callback);

    /** RF-008: asignar un administrador a un hotel. */
    void asignarAdministrador(@NonNull String hotelId, @NonNull String usuarioId,
                              @NonNull ResultCallback<Hotel> callback);

    /** RF-118 a RF-120: la bitacora, del movimiento mas reciente al mas antiguo. */
    void bitacora(@NonNull ResultCallback<List<LogEntry>> callback);
}
