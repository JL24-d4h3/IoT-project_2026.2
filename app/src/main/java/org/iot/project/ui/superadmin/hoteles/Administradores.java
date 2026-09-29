package org.iot.project.ui.superadmin.hoteles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Hotel;
import org.iot.project.models.User;

import java.util.List;

/**
 * Quien lleva un hotel, resuelto contra la lista de cuentas.
 *
 * <p>El hotel guarda el identificador de su administrador (RF-008) y nada mas:
 * el nombre no viaja dentro del hotel. Las dos pantallas que lo enseñan —la
 * lista y la ficha— tienen que cruzarlo, y cruzarlo es la misma regla en las
 * dos: por eso vive aqui y no escrita dos veces.
 *
 * <p>Hay tres respuestas posibles y las tres son distintas: el hotel no apunta
 * a nadie, apunta a una cuenta activa, o apunta a una cuenta que se desactivo
 * despues (RF-006). Las dos ultimas no se pueden confundir: decirle "sin
 * administrador" a un hotel que si lo tiene, solo porque a esa persona se le
 * retiro el acceso, seria un dato falso sobre quien responde por el hotel.
 */
final class Administradores {

    private Administradores() {
    }

    /** Si al hotel todavia le falta el paso de RF-008. */
    static boolean sinAsignar(@NonNull Hotel hotel) {
        return hotel.getAdministradorId() == null;
    }

    /**
     * El nombre de quien administra el hotel, o {@code null} si no se puede
     * decir: porque no tiene a nadie, o porque la cuenta asignada ya no esta
     * entre las activas.
     */
    @Nullable
    static String nombreDe(@NonNull Hotel hotel, @NonNull List<User> activas) {
        String id = hotel.getAdministradorId();
        if (id == null) {
            return null;
        }
        for (User cuenta : activas) {
            if (id.equals(cuenta.getId())) {
                return cuenta.getNombreCompleto();
            }
        }
        return null;
    }
}
