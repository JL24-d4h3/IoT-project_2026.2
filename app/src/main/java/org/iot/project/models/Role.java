package org.iot.project.models;

/**
 * Roles del sistema (RT-009). Cada rol determina que grafo de navegacion
 * se monta en MainActivity y, por tanto, que interfaz ve el usuario.
 */
public enum Role {

    CLIENTE("Cliente"),
    ADMIN_HOTEL("Administrador de hotel"),
    CONDUCTOR("Conductor de taxi"),
    SUPERADMIN("Superadministrador");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * El administrador de hotel no funciona como cliente (regla 10), por eso
     * ningun rol administrativo comparte la interfaz del cliente.
     */
    public boolean isAdministrative() {
        return this == ADMIN_HOTEL || this == SUPERADMIN;
    }
}
