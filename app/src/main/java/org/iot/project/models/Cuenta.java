package org.iot.project.models;

import androidx.annotation.NonNull;

/**
 * Lo que el acceso necesita saber para dejar entrar a alguien (RF-004).
 *
 * <p>No es un usuario: es su rol y la identidad con la que entra. Existe porque
 * un conductor no es un {@link User} sino un {@link Driver}, y el acceso no
 * tiene por que saber cual de los dos modelos tiene delante: con esto, los
 * cuatro roles llegan a la pantalla de acceso de la misma forma.
 *
 * <p>No lleva contrasena ni ningun dato secreto (RC-042, RT-038): esta entrega
 * es solo front end y no hay credencial que comprobar.
 */
public final class Cuenta {

    private final Role rol;
    private final String identidadId;
    private final String nombreCompleto;
    private final String email;

    public Cuenta(@NonNull Role rol, @NonNull String identidadId,
                  @NonNull String nombreCompleto, @NonNull String email) {
        this.rol = rol;
        this.identidadId = identidadId;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
    }

    @NonNull
    public Role getRol() {
        return rol;
    }

    /** Identificador del {@code User} o del {@code Driver}, segun el rol. */
    @NonNull
    public String getIdentidadId() {
        return identidadId;
    }

    @NonNull
    public String getNombreCompleto() {
        return nombreCompleto;
    }

    /**
     * Iniciales para el avatar cuando no hay fotografia.
     *
     * <p>Mismo criterio que en {@link User} y {@link Driver}: la cuenta se
     * presenta igual que se presenta una persona en el resto de la aplicacion.
     */
    @NonNull
    public String getIniciales() {
        String[] partes = nombreCompleto.trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) {
            return "";
        }
        String inicialNombre = partes[0].substring(0, 1);
        String inicialApellido = partes.length > 1 ? partes[1].substring(0, 1) : "";
        return (inicialNombre + inicialApellido).toUpperCase();
    }

    @NonNull
    public String getEmail() {
        return email;
    }
}
