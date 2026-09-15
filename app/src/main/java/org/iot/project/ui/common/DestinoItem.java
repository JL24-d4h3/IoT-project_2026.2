package org.iot.project.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.iot.project.R;

/**
 * Fila de la pantalla de destino (§14).
 *
 * <p>La lista mezcla dos cosas que no son lo mismo —lo que el usuario ya buscó
 * y lo que le proponemos— y por eso lleva encabezados. Se modelan como una fila
 * mas, y no como dos listas pegadas, para que el desplazamiento sea uno solo:
 * dos listas dentro de un contenedor obligarian a anidar desplazamientos, que
 * es justo lo que §57 pide evitar.
 *
 * <p>Es un modelo de presentacion: no sale de la capa de datos. Lo arma la
 * pantalla cruzando el catalogo de ciudades con el historial de busquedas.
 *
 * <p>La fila lleva solo el texto del destino. Un detalle debajo —"12
 * alojamientos"— obligaria a consultar el catalogo una vez por fila para
 * escribir un adorno, y el buscador no los tiene contados de antemano.
 */
public final class DestinoItem {

    public enum Tipo {
        /** Titulo de seccion. No se pulsa. */
        ENCABEZADO,
        /** Una ciudad propuesta o una busqueda reciente. */
        DESTINO
    }

    private final Tipo tipo;
    private final String texto;
    @StringRes
    private final int tituloRes;
    @DrawableRes
    private final int icono;
    private final boolean eliminable;

    private DestinoItem(Tipo tipo, @NonNull String texto, @StringRes int tituloRes,
                        @DrawableRes int icono, boolean eliminable) {
        this.tipo = tipo;
        this.texto = texto;
        this.tituloRes = tituloRes;
        this.icono = icono;
        this.eliminable = eliminable;
    }

    /**
     * Titulo de seccion: "Búsquedas recientes", "Destinos sugeridos".
     *
     * <p>Guarda el identificador y no el texto ya resuelto porque quien lo
     * construye —el ViewModel— no tiene por que tener un {@code Context} a
     * mano, y pasarle uno solo para traducir una etiqueta fija seria darle a la
     * capa de estado una dependencia de Android que no necesita.
     */
    public static DestinoItem encabezado(@StringRes int titulo) {
        return new DestinoItem(Tipo.ENCABEZADO, "", titulo, 0, false);
    }

    /** Ciudad del catalogo. */
    public static DestinoItem sugerencia(@NonNull String ciudad) {
        return new DestinoItem(Tipo.DESTINO, ciudad, 0, R.drawable.ic_location, false);
    }

    /**
     * Una busqueda reciente. Es eliminable porque un historial que solo crece
     * deja de ser util: el usuario tiene que poder descartar lo que ya no le
     * interesa.
     */
    public static DestinoItem reciente(@NonNull String texto) {
        return new DestinoItem(Tipo.DESTINO, texto, 0, R.drawable.ic_search, true);
    }

    @NonNull
    public Tipo getTipo() {
        return tipo;
    }

    /** Texto de una fila de destino. Vacio en los encabezados. */
    @NonNull
    public String getTexto() {
        return texto;
    }

    /** Recurso del titulo, solo en los encabezados. Cero en las demas filas. */
    @StringRes
    public int getTituloRes() {
        return tituloRes;
    }

    @DrawableRes
    public int getIcono() {
        return icono;
    }

    public boolean isEliminable() {
        return eliminable;
    }

    public boolean esEncabezado() {
        return tipo == Tipo.ENCABEZADO;
    }
}
