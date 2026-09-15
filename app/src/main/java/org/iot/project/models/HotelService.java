package org.iot.project.models;

/**
 * Relacion entre un hotel y un servicio del catalogo global (§18).
 *
 * <p>Aqui es donde vive el precio, no en {@link Service}: "Piscina" puede ser
 * incluida en el Hotel A y costar S/ 25 en el Hotel B (regla 19, regla 20).
 */
public class HotelService {

    private final String hotelId;
    private final String serviceId;
    private boolean included;
    private double price;

    public HotelService(String hotelId, String serviceId, boolean included, double price) {
        this.hotelId = hotelId;
        this.serviceId = serviceId;
        setIncluded(included);
        this.price = included ? 0d : price;
    }

    /** Atajo para servicios sin costo adicional. */
    public static HotelService incluido(String hotelId, String serviceId) {
        return new HotelService(hotelId, serviceId, true, 0d);
    }

    /** Atajo para servicios con costo adicional (regla 6: exige precio). */
    public static HotelService adicional(String hotelId, String serviceId, double price) {
        if (price <= 0d) {
            throw new IllegalArgumentException(
                    "Un servicio adicional debe tener precio mayor a cero: " + serviceId);
        }
        return new HotelService(hotelId, serviceId, false, price);
    }

    public String getHotelId() {
        return hotelId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public boolean isIncluded() {
        return included;
    }

    /**
     * Un servicio incluido no lleva precio (regla 7). Clasificar como incluido
     * limpia cualquier precio previo para que no queden datos incoherentes.
     */
    public void setIncluded(boolean included) {
        this.included = included;
        if (included) {
            this.price = 0d;
        }
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        if (included) {
            throw new IllegalStateException(
                    "No se puede asignar precio a un servicio incluido: " + serviceId);
        }
        this.price = price;
    }

    public boolean isAdicional() {
        return !included;
    }
}
