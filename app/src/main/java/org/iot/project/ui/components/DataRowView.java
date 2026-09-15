package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.iot.project.R;

/**
 * Una fila de dato: etiqueta a la izquierda, valor a la derecha (§37, §40).
 *
 * <p>Existe para que las fichas de datos —el viaje del taxi, los datos
 * personales del perfil— no repitan dos {@code TextView} por linea. Con seis o
 * siete datos por pantalla, escribirlos a mano son catorce vistas y catorce
 * oportunidades de que una quede con otro margen.
 *
 * <p>El valor se puede dejar vacio, y entonces la fila lo dice con
 * {@link #setSinValor}: un hueco en blanco a la derecha de "Teléfono" se lee
 * como un fallo de carga, no como un dato que el usuario no registro.
 */
public class DataRowView extends LinearLayout {

    private final TextView etiqueta;
    private final TextView valor;

    public DataRowView(@NonNull Context context) {
        this(context, null);
    }

    public DataRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DataRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_data_row, this, true);

        etiqueta = findViewById(R.id.dato_etiqueta);
        valor = findViewById(R.id.dato_valor);
    }

    public void setEtiqueta(@StringRes int texto) {
        etiqueta.setText(texto);
    }

    /**
     * Rellena la fila de una vez.
     *
     * <p>Cuando el valor viene vacio se sustituye por lo que diga
     * {@code textoSiVacio}, que suele ser "Sin registrar": es una respuesta, y
     * una fila sin nada al lado no lo es. El color se decide aqui y no en un
     * metodo aparte porque el dato y su ausencia no pueden pintarse igual: al
     * reutilizar la fila, un valor real heredaria el gris de la vez anterior.
     */
    public void bind(@StringRes int textoEtiqueta, @Nullable CharSequence textoValor,
                     @StringRes int textoSiVacio) {
        etiqueta.setText(textoEtiqueta);

        boolean hay = textoValor != null && textoValor.length() > 0;
        valor.setText(hay ? textoValor : getContext().getString(textoSiVacio));
        valor.setTextColor(getContext().getColor(
                hay ? R.color.colorTextPrimary : R.color.colorTextSecondary));
    }
}
