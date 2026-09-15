package org.iot.project.models;

/**
 * Servicio del catalogo global (§17). El administrador nunca escribe el
 * nombre de un servicio a mano (regla 11): selecciona uno de estos.
 *
 * <p>El precio y la clasificacion incluido/adicional NO viven aqui, sino en
 * {@link HotelService}, porque el mismo servicio puede ser incluido en un
 * hotel y tener costo en otro (regla 19).
 */
public class Service {

    private final String serviceId;
    private final String name;
    private final String description;
    private final int iconRes;
    private boolean active = true;

    public Service(String serviceId, String name, String description, int iconRes) {
        this.serviceId = serviceId;
        this.name = name;
        this.description = description;
        this.iconRes = iconRes;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Drawable local del catalogo. Los iconos son del sistema, no del hotel. */
    public int getIconRes() {
        return iconRes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
