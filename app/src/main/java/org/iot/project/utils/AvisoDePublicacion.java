package org.iot.project.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;
import org.iot.project.models.Hotel;

/**
 * Que le falta a un hotel para poder publicarse (RF-007, RF-013, RF-014).
 *
 * <p>La regla de que hace falta para publicar vive en {@link Hotel}
 * —{@code aptoParaPublicar()}— y es una sola. Lo que se repite es contarlo: lo
 * dicen las tres pantallas que enseñan un hotel sin publicar —la ficha del
 * superadministrador, los datos del administrador y su portada—, y tres copias
 * de la misma enumeracion acaban discrepando: una dice "faltan fotografias" y
 * otra "faltan imagenes", y el mismo hotel se explica de dos formas distintas
 * segun por donde se mire.
 *
 * <p>No es una funcion pura como las de {@link Distancia}: necesita un
 * {@code Context} para llegar a las cadenas. Se queda igualmente aqui, junto a
 * {@link PriceFormatter}, porque es lo mismo —un dato del modelo convertido en
 * el texto que se lee— y no de ninguna pantalla en particular.
 */
public final class AvisoDePublicacion {

    private AvisoDePublicacion() {
    }

    /**
     * Que le falta al hotel para poder publicarse, o {@code null} si no le falta
     * nada.
     *
     * <p>Se enumeran las dos cosas a la vez cuando faltan las dos: arreglar una y
     * volver a encontrarse con la otra es hacer el trabajo dos veces. El nulo no
     * es un descuido, es la respuesta a "no falta nada", y quien lo recibe decide
     * si ese caso se calla —la ficha del superadministrador esconde la frase— o
     * se dice de otra forma —el administrador lee que ya puede publicar—.
     */
    @Nullable
    public static CharSequence motivo(@NonNull Context contexto, @NonNull Hotel hotel) {
        boolean faltanFotos = !hotel.cumpleMinimoFotos();
        boolean faltanHabitaciones = hotel.getHabitaciones().isEmpty();
        if (faltanFotos && faltanHabitaciones) {
            return contexto.getString(R.string.sa_falta_publicar);
        }
        if (faltanFotos) {
            return contexto.getString(R.string.sa_falta_fotos,
                    hotel.getFotos().size(), Hotel.MIN_FOTOS);
        }
        if (faltanHabitaciones) {
            return contexto.getString(R.string.sa_falta_habitaciones);
        }
        return null;
    }
}
