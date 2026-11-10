package org.iot.project.models;

import androidx.annotation.NonNull;

/** Aprobación y acceso web independientes del estado operativo del perfil público. */
public final class TaxistaAdministrado {
    private final Driver perfil;
    private final boolean habilitado;
    private final boolean cuentaActiva;

    public TaxistaAdministrado(@NonNull Driver perfil, boolean habilitado, boolean cuentaActiva) {
        this.perfil = perfil;
        this.habilitado = habilitado;
        this.cuentaActiva = cuentaActiva;
    }

    @NonNull public String getId() { return perfil.getId(); }
    @NonNull public Driver getPerfil() { return perfil; }
    public boolean isHabilitado() { return habilitado; }
    public boolean isCuentaActiva() { return cuentaActiva; }
}
