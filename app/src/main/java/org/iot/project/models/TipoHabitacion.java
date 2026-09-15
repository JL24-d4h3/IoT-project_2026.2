package org.iot.project.models;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Tipo de habitacion, para el filtro de §18.
 *
 * <p>La taxonomia se deduce del nombre de la habitacion —"Doble clásica" es una
 * doble— porque el catalogo simulado todavia no tiene un codigo de tipo. Es una
 * limitacion de los datos de demostracion, no del filtro: el dia que el hotel
 * publique un tipo propio, esto se sustituye por ese campo y ni la pantalla ni
 * la busqueda cambian.
 *
 * <p>Hay habitaciones que no caen en ninguna categoria ("Superior ejecutiva").
 * Por eso {@code coincide} se usa para <em>buscar</em> hoteles con habitaciones
 * de un tipo, no para etiquetar cada habitacion: una habitacion sin categoria
 * simplemente no aparece al filtrar, y el hotel que solo tenga de esas sigue
 * siendo alcanzable sin ningun filtro puesto.
 */
public enum TipoHabitacion {

    /** Una persona. "Simple" es como llama el catalogo a la individual. */
    INDIVIDUAL("Individual", "individual", "simple"),

    DOBLE("Doble", "doble"),

    /** Dos adultos y un nino, o tres adultos: es la que pide una familia. */
    TRIPLE("Triple", "triple"),

    SUITE("Suite", "suite");

    private final String displayName;
    private final List<String> prefijos;

    TipoHabitacion(String displayName, String... prefijos) {
        this.displayName = displayName;
        this.prefijos = Arrays.asList(prefijos);
    }

    public String getDisplayName() {
        return displayName;
    }

    /** true si la habitacion es de este tipo. */
    public boolean coincide(@Nullable Room room) {
        if (room == null || room.getTipo() == null) {
            return false;
        }
        String tipo = room.getTipo().trim().toLowerCase();
        for (String prefijo : prefijos) {
            if (tipo.startsWith(prefijo)) {
                return true;
            }
        }
        return false;
    }

    /** true si el alojamiento tiene al menos una habitacion de este tipo. */
    public boolean coincideConAlguno(@Nullable Hotel hotel) {
        if (hotel == null) {
            return false;
        }
        for (Room room : hotel.getHabitaciones()) {
            if (coincide(room)) {
                return true;
            }
        }
        return false;
    }
}
