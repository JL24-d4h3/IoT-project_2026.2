package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Cuenta;
import org.iot.project.models.User;

/** Autenticacion remota y carga del perfil autorizado por UID. */
public interface AccesoRepository {

    void iniciarSesion(@NonNull String email, @NonNull String contrasena,
                       @NonNull ResultCallback<Cuenta> callback);

    void registrar(@NonNull User borrador, @NonNull String contrasena,
                   @NonNull ResultCallback<Cuenta> callback);

    /**
     * Recuperacion de contrasena.
     *
     * <p>Devuelve el correo al que se habria enviado el enlace. No informa de si
     * ese correo tiene cuenta: la respuesta es la misma para todos, para que
     * este formulario no sirva para averiguar quien esta registrado.
     */
    void recuperarContrasena(@NonNull String email, @NonNull ResultCallback<String> callback);

    /** Revalida las credenciales persistidas y el perfil contra el servidor. */
    void restaurarSesion(@NonNull ResultCallback<Cuenta> callback);

    void cerrarSesion();
}
