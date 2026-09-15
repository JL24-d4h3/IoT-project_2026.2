package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.iot.project.R;

/**
 * Una fila que lleva a otra pantalla (§40).
 *
 * <p>Se distingue de {@link DataRowView} en que esta se toca: lleva icono a la
 * izquierda y chevron a la derecha. El chevron es la unica pista de que hay algo
 * detras, y por eso no es opcional.
 *
 * <p>La fila entera es el objetivo del toque, no solo el texto: el minimo de
 * 48dp de alto sale de ahi, y una fila de una linea de texto no lo alcanza sola.
 */
public class SettingRowView extends LinearLayout {

    private final ImageView icono;
    private final TextView titulo;
    private final TextView resumen;

    public SettingRowView(@NonNull Context context) {
        this(context, null);
    }

    public SettingRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_setting_row, this, true);

        icono = findViewById(R.id.fila_icono);
        titulo = findViewById(R.id.fila_titulo);
        resumen = findViewById(R.id.fila_resumen);
    }

    public void bind(@DrawableRes int iconoRes, @StringRes int tituloRes,
                     @Nullable CharSequence textoResumen) {
        icono.setImageResource(iconoRes);
        titulo.setText(tituloRes);

        boolean hay = textoResumen != null && textoResumen.length() > 0;
        resumen.setText(hay ? textoResumen : null);
        resumen.setVisibility(hay ? VISIBLE : GONE);
    }

    /**
     * El resumen cambia sin que cambie el resto de la fila.
     *
     * <p>Existe aparte porque el contador de notificaciones llega despues que el
     * perfil, y volver a llamar a {@code bind} con todo lo demas obligaria a
     * tener a mano el icono y el titulo solo para actualizar un numero.
     */
    public void setResumen(@Nullable CharSequence textoResumen) {
        boolean hay = textoResumen != null && textoResumen.length() > 0;
        resumen.setText(hay ? textoResumen : null);
        resumen.setVisibility(hay ? VISIBLE : GONE);
    }
}
