package org.iot.project.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Habitacion de un hotel. La disponibilidad depende del periodo solicitado
 * (RF-030, RF-031), por eso {@link #disponible} es el resultado de la consulta
 * y no una propiedad fija del inventario.
 */
public class Room {

    private final String id;
    private final String hotelId;
    private String tipo;
    private int capacidadAdultos;
    private int capacidadNinos;
    private double areaM2;
    private int piso;
    private String numero;
    private double precioNoche;
    private boolean disponible = true;
    private final List<String> fotos = new ArrayList<>();

    public Room(String id, String hotelId, String tipo, double precioNoche) {
        this.id = id;
        this.hotelId = hotelId;
        this.tipo = tipo;
        this.precioNoche = precioNoche;
    }

    public String getId() {
        return id;
    }

    public String getHotelId() {
        return hotelId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public int getCapacidadAdultos() {
        return capacidadAdultos;
    }

    public int getCapacidadNinos() {
        return capacidadNinos;
    }

    public double getAreaM2() {
        return areaM2;
    }

    public int getPiso() {
        return piso;
    }

    public String getNumero() {
        return numero;
    }

    public double getPrecioNoche() {
        return precioNoche;
    }

    public void setPrecioNoche(double precioNoche) {
        this.precioNoche = precioNoche;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public List<String> getFotos() {
        return Collections.unmodifiableList(fotos);
    }

    public Room addFoto(String url) {
        fotos.add(url);
        return this;
    }

    /** Capacidad total, que es lo que se compara contra el numero de huespedes. */
    public int getCapacidadTotal() {
        return capacidadAdultos + capacidadNinos;
    }

    /** "2 adultos · 1 niño · 32 m²" — el resumen que muestran las RoomCard (§25). */
    public String getResumenCapacidad() {
        String huespedes = capacidadAdultos + (capacidadAdultos == 1 ? " adulto" : " adultos");
        if (capacidadNinos > 0) {
            huespedes += " · " + capacidadNinos + (capacidadNinos == 1 ? " niño" : " niños");
        }
        return huespedes;
    }

    public Room withCapacidad(int adultos, int ninos) {
        this.capacidadAdultos = adultos;
        this.capacidadNinos = ninos;
        return this;
    }

    public Room withArea(double areaM2) {
        this.areaM2 = areaM2;
        return this;
    }

    public Room withUbicacion(int piso, String numero) {
        this.piso = piso;
        this.numero = numero;
        return this;
    }
}
