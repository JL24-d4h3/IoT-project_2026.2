package org.iot.project.ui.components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.iot.project.R;

/**
 * Calificación de 1 a 10 dentro de una píldora ámbar.
 *
 * <p>Existe como componente y no como estilo suelto por un motivo de
 * contraste (§56): el ámbar de la marca sobre blanco da 2.2:1 y no cumple AA.
 * Aquí el ámbar es siempre <em>fondo</em> y el texto usa su propio color de
 * contenido, que sí contrasta. Teniéndolo en una clase, es imposible usarlo al
 * revés.
 *
 * <p>La escala es 1 a 10, nunca 1 a 5 estrellas (§10, regla 9).
 */
public class RatingBadgeView extends AppCompatTextView {

    /** Radio de la píldora, en dp. */
    private static final float RADIO_DP = 8f;

    private final GradientDrawable fondo = new GradientDrawable();

    public RatingBadgeView(@NonNull Context context) {
        this(context, null);
    }

    public RatingBadgeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RatingBadgeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        fondo.setShape(GradientDrawable.RECTANGLE);
        fondo.setCornerRadius(dp(RADIO_DP));
        fondo.setColor(ContextCompat.getColor(context, R.color.colorAccentContainer));
        setBackground(fondo);
        setTextColor(ContextCompat.getColor(context, R.color.colorOnAccentContainer));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        setTypeface(getTypeface(), android.graphics.Typeface.BOLD);
        setGravity(android.view.Gravity.CENTER);
        int h = (int) dp(6);
        int v = (int) dp(2);
        setPadding(h, v, h, v);
        setIncludeFontPadding(false);
    }

    /**
     * Muestra la calificación con un decimal, que es como se lee en la ficha
     * del hotel.
     */
    public void setRating(float rating) {
        setText(String.format(java.util.Locale.getDefault(), "%.1f", rating));
    }

    /**
     * Muestra una calificación que no tiene fracción.
     *
     * <p>El deslizador de 1 a 10 da enteros exactos, y ahí un "8.0" anunciaría
     * dos cosas falsas: que la escala es decimal y que se puede calificar con
     * esa precisión. El promedio de un hotel sí es decimal, por eso conviven
     * los dos métodos.
     */
    public void setValorEntero(int valor) {
        setText(String.format(java.util.Locale.getDefault(), "%d", valor));
    }

    /**
     * Variante compacta para cuando el espacio es escaso, por ejemplo dentro de
     * una tarjeta de reserva.
     */
    public void setRatingConEtiqueta(float rating, @NonNull String etiqueta) {
        setText(String.format(java.util.Locale.getDefault(), "%.1f · %s", rating, etiqueta));
    }

    private float dp(float valor) {
        return valor * getResources().getDisplayMetrics().density;
    }
}
