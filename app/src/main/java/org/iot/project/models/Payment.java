package org.iot.project.models;

import java.time.LocalDateTime;

/**
 * Pago simulado asociado a una reserva (RF-054). Nunca representa una
 * transaccion financiera real (RF-039, RF-050, RT-010).
 */
public class Payment {

    private final String id;
    private final String bookingId;
    private final double monto;
    private final String metodo;
    private final LocalDateTime fecha;

    public Payment(String id, String bookingId, double monto, String metodo, LocalDateTime fecha) {
        this.id = id;
        this.bookingId = bookingId;
        this.monto = monto;
        this.metodo = metodo;
        this.fecha = fecha;
    }

    public String getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public double getMonto() {
        return monto;
    }

    public String getMetodo() {
        return metodo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    /** El cobro siempre es simulado; la UI lo declara explicitamente. */
    public boolean isSimulado() {
        return true;
    }
}
