package org.iot.project.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lo que el acceso necesita saber para dejar entrar a alguien (RF-004).
 *
 * <p>Contiene el rol y la identidad de acceso de cualquiera de los cuatro
 * roles. El perfil operativo del conductor ({@link Driver}) es independiente
 * del inicio de sesión y no hace falta cargarlo para abrir su portada.
 *
 * <p>No contiene contrasenas ni tokens. La identidad es el UID autenticado;
 * el rol y el hotel asignado provienen del perfil protegido en el servidor.
 */
public final class Cuenta {

    private final Role rol;
    private final String identidadId;
    private final String nombreCompleto;
    private final String email;
    private final String hotelId;

    public Cuenta(@NonNull Role rol, @NonNull String identidadId,
                  @NonNull String nombreCompleto, @NonNull String email) {
        this(rol, identidadId, nombreCompleto, email, null);
    }

    public Cuenta(@NonNull Role rol, @NonNull String identidadId,
                  @NonNull String nombreCompleto, @NonNull String email,
                  @Nullable String hotelId) {
        this.rol = rol;
        this.identidadId = identidadId;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.hotelId = hotelId;
    }

    @Nullable
    public String getHotelId() {
        return hotelId;
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
