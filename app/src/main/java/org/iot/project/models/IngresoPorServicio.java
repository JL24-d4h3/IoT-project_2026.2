package org.iot.project.models;

import androidx.annotation.NonNull;

/**
 * Una fila del reporte de ingresos por servicios adicionales (RF-060).
 *
 * <p>Es un resultado calculado, no un dato guardado: se arma sumando los
 * servicios adicionales de las reservas del hotel cada vez que se pide. No
 * existe como entidad porque no tiene identidad propia —el mismo servicio
 * aparece en cien reservas— y guardarlo abriria la puerta a que el acumulado y
 * las reservas que lo justifican dejaran de cuadrar.
 */
public class IngresoPorServicio {

    private final String serviceId;
    private final String nombre;
    private final int cantidad;
    private final double montoTotal;

    public IngresoPorServicio(@NonNull String serviceId, @NonNull String nombre,
                              int cantidad, double montoTotal) {
        this.serviceId = serviceId;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.montoTotal = montoTotal;
    }

    @NonNull
    public String getServiceId() {
        return serviceId;
    }

    @NonNull
    public String getNombre() {
        return nombre;
    }

    /** Cuantas veces se cobro este servicio en el hotel. */
    public int getCantidad() {
        return cantidad;
    }

    /** Suma de lo facturado por este servicio. */
    public double getMontoTotal() {
        return montoTotal;
    }
}
