package org.iot.project.models;

/** Vehiculo del conductor de taxi (RF-075, RF-076, §28). */
public class Vehicle {

    private final String placa;
    private String marca;
    private String modelo;
    private String color;
    private String fotoUrl;

    public Vehicle(String placa) {
        this.placa = placa;
    }

    public Vehicle(String placa, String marca, String modelo, String color) {
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.color = color;
    }

    public String getPlaca() {
        return placa;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public String getColor() {
        return color;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public Vehicle withFoto(String url) {
        this.fotoUrl = url;
        return this;
    }

    /** "Toyota Corolla · Blanco" — la linea que acompana la placa en la VehicleCard. */
    public String getDescripcion() {
        StringBuilder sb = new StringBuilder();
        if (marca != null) {
            sb.append(marca);
        }
        if (modelo != null) {
            sb.append(sb.length() > 0 ? " " : "").append(modelo);
        }
        if (color != null) {
            sb.append(sb.length() > 0 ? " · " : "").append(color);
        }
        return sb.toString();
    }
}
