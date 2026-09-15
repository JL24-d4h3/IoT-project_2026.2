package org.iot.project.models;

import java.time.LocalDate;

/**
 * Valoracion de 1 a 10 (regla 9). Sirve tanto para hoteles como para
 * conductores de taxi (RF-080, RF-105).
 */
public class Review {

    /** Limites de la escala. Se usan tambien en los sliders de checkout. */
    public static final int RATING_MIN = 1;
    public static final int RATING_MAX = 10;

    private final String id;
    private final String entidadId;
    private final String autorNombre;
    private String autorFotoUrl;
    private final float rating;
    private final String comentario;
    private final LocalDate fecha;

    public Review(String id,
                  String entidadId,
                  String autorNombre,
                  float rating,
                  String comentario,
                  LocalDate fecha) {
        this.id = id;
        this.entidadId = entidadId;
        this.autorNombre = autorNombre;
        this.rating = clamp(rating);
        this.comentario = comentario;
        this.fecha = fecha;
    }

    private static float clamp(float valor) {
        return Math.max(RATING_MIN, Math.min(RATING_MAX, valor));
    }

    public String getId() {
        return id;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getAutorFotoUrl() {
        return autorFotoUrl;
    }

    public Review withAutorFoto(String url) {
        this.autorFotoUrl = url;
        return this;
    }

    public float getRating() {
        return rating;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    /**
     * Etiqueta cualitativa que acompana al numero en el RatingBadge (§22).
     * La escala es 1-10, asi que los cortes son distintos a los de 5 estrellas.
     */
    public String getEtiqueta() {
        return etiquetaPara(rating);
    }

    public static String etiquetaPara(float rating) {
        if (rating >= 9f) {
            return "Excepcional";
        }
        if (rating >= 8f) {
            return "Excelente";
        }
        if (rating >= 7f) {
            return "Muy bueno";
        }
        if (rating >= 6f) {
            return "Bueno";
        }
        if (rating >= 5f) {
            return "Aceptable";
        }
        return "Mejorable";
    }
}
