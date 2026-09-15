package org.iot.project.models;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Lo que la portada del administrador muestra de su hotel (§43).
 *
 * <p>Reune el hotel, sus estadias en curso y el desglose de ingresos por
 * servicios adicionales en un solo objeto, para que la pantalla lo reciba de
 * una vez en lugar de encadenar tres consultas y quedarse esperando a la mas
 * lenta.
 *
 * <p>El total de servicios se calcula aqui, en el constructor, y no en la
 * pantalla: asi el numero que se enseña y la lista que lo desglosa no pueden
 * decir cosas distintas.
 */
public final class ResumenHotel {

    @NonNull
    private final Hotel hotel;

    /** Estadia en curso con el cliente y la habitacion ya resueltos. */
    @NonNull
    private final List<ReservaDeHotel> estadias;

    /** Ingresos por servicio adicional, de menor a mayor monto (RF-060, RF-061). */
    @NonNull
    private final List<IngresoPorServicio> ingresos;

    private final double totalServicios;

    public ResumenHotel(@NonNull Hotel hotel, @NonNull List<ReservaDeHotel> estadias,
                        @NonNull List<IngresoPorServicio> ingresos) {
        this.hotel = hotel;
        this.estadias = Collections.unmodifiableList(estadias);
        this.ingresos = Collections.unmodifiableList(ingresos);

        double total = 0d;
        for (IngresoPorServicio ingreso : ingresos) {
            total += ingreso.getMontoTotal();
        }
        this.totalServicios = total;
    }

    @NonNull
    public Hotel getHotel() {
        return hotel;
    }

    @NonNull
    public List<ReservaDeHotel> getEstadias() {
        return estadias;
    }

    @NonNull
    public List<IngresoPorServicio> getIngresos() {
        return ingresos;
    }

    /** Total facturado por servicios adicionales en todas las estadias. */
    public double getTotalServicios() {
        return totalServicios;
    }

    /**
     * Cuantas estadias terminan hoy.
     *
     * <p>Es la cifra que el administrador mira al abrir la aplicacion: son las
     * habitaciones que quedan libres y las cuentas que hay que cerrar.
     */
    public int getSalidasDeHoy() {
        int total = 0;
        for (ReservaDeHotel estadia : estadias) {
            if (estadia.terminaHoy()) {
                total++;
            }
        }
        return total;
    }
}
