package org.iot.project.models;

/**
 * Orden de los resultados de busqueda (§19).
 *
 * <p>El orden es parte de la busqueda, no de la pantalla: viaja dentro del
 * {@link SearchQuery} para que volver a resultados desde el detalle de un hotel
 * no reordene la lista que el usuario estaba mirando.
 */
public enum OrdenBusqueda {

    /**
     * El orden por defecto: relevancia.
     *
     * <p>No es lo mismo que {@link #MEJOR_RATING}, y la diferencia importa. Una
     * valoracion de 9,4 con doce resenas y otra de 9,2 con doscientas no son
     * comparables: la primera es una promesa sin evidencia. Por eso la
     * relevancia pondera la valoracion por el volumen de resenas, y un hotel
     * muy bien valorado pero casi sin opiniones no adelanta a uno consolidado.
     */
    RECOMENDADOS("Recomendados"),

    PRECIO_MENOR("Precio menor"),
    PRECIO_MAYOR("Precio mayor"),
    MEJOR_RATING("Mejor valorados"),

    /** Mas resenas primero: es lo que mide "popularidad" en los datos que hay. */
    MAS_POPULARES("Mas populares");

    private final String displayName;

    OrdenBusqueda(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
