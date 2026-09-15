package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.iot.project.R;

/**
 * Cabecera de pantalla.
 *
 * <p>Cubre los dos usos que tiene la aplicación: la cabecera de una pantalla
 * principal, con título grande y sin vuelta atrás, y la de una pantalla de
 * detalle, con flecha de vuelta y una acción a la derecha.
 */
public class AppHeaderView extends LinearLayout {

    private final ImageButton botonVolver;
    private final ImageButton botonAccion;
    private final TextView titulo;
    private final TextView subtitulo;

    public AppHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public AppHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppHeaderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.header_height));

        LayoutInflater.from(context).inflate(R.layout.view_app_header, this, true);

        botonVolver = findViewById(R.id.header_back);
        botonAccion = findViewById(R.id.header_action);
        titulo = findViewById(R.id.header_title);
        subtitulo = findViewById(R.id.header_subtitle);
    }

    public void setTitulo(@NonNull CharSequence texto) {
        titulo.setText(texto);
    }

    public void setTitulo(@StringRes int textoRes) {
        titulo.setText(textoRes);
    }

    public void setSubtitulo(@Nullable CharSequence texto) {
        boolean hay = texto != null && texto.length() > 0;
        subtitulo.setText(hay ? texto : null);
        subtitulo.setVisibility(hay ? VISIBLE : GONE);
    }

    /** Muestra la flecha de vuelta y le engancha la acción indicada. */
    public void mostrarVolver(@Nullable OnClickListener accion) {
        botonVolver.setVisibility(acceso(accion));
        botonVolver.setOnClickListener(accion);
    }

    /** Muestra un icono de acción a la derecha, con su descripción accesible. */
    public void mostrarAccion(@DrawableRes int icono, @StringRes int descripcion,
                              @Nullable OnClickListener accion) {
        botonAccion.setImageResource(icono);
        botonAccion.setContentDescription(getContext().getString(descripcion));
        botonAccion.setVisibility(acceso(accion));
        botonAccion.setOnClickListener(accion);
    }

    public void ocultarAccion() {
        botonAccion.setVisibility(GONE);
        botonAccion.setOnClickListener(null);
    }

    /**
     * Un botón solo se muestra si tiene algo que hacer. Enseñar una flecha de
     * vuelta que no lleva a ninguna parte es peor que no enseñarla.
     */
    private int acceso(@Nullable OnClickListener accion) {
        return accion != null ? VISIBLE : GONE;
    }

    public ImageButton getBotonVolver() {
        return botonVolver;
    }

    public ImageButton getBotonAccion() {
        return botonAccion;
    }

    public TextView getTituloView() {
        return titulo;
    }
}
