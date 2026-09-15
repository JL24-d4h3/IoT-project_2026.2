package org.iot.project.models;

/**
 * Cargo adicional aplicado por el administrador del hotel a una estadia.
 * Monto, motivo y observacion son obligatorios (RF-052, RT-021), por eso el
 * constructor los exige en vez de dejarlos como setters opcionales.
 */
public class Charge {

    private final double monto;
    private final String motivo;
    private final String observacion;

    public Charge(double monto, String motivo, String observacion) {
        if (monto <= 0d) {
            throw new IllegalArgumentException("El monto del cargo debe ser mayor a cero");
        }
        if (isBlank(motivo)) {
            throw new IllegalArgumentException("El cargo adicional exige un motivo");
        }
        if (isBlank(observacion)) {
            throw new IllegalArgumentException("El cargo adicional exige una observacion");
        }
        this.monto = monto;
        this.motivo = motivo;
        this.observacion = observacion;
    }

    public double getMonto() {
        return monto;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getObservacion() {
        return observacion;
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
