package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.iot.project.R;

/**
 * Estado de una pantalla sin contenido que mostrar.
 *
 * <p>Base de {@link EmptyStateView} y {@link ErrorStateView}, que solo se
 * diferencian en el icono y el color. La estructura —icono, título, mensaje y
 * acción opcional— es la misma en los dos casos de §50, así que se escribe una
 * vez.
 *
 * <p>Un estado vacío siempre explica <em>qué pasó</em> y <em>qué puede hacer el
 * usuario</em>. Un icono con "no hay datos" no sirve de nada.
 */
public class StateView extends LinearLayout {

    private final ImageView icono;
    private final TextView titulo;
    private final TextView mensaje;
    private final MaterialButton accion;

    public StateView(@NonNull Context context) {
        this(context, null);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_2xl);
        setPadding(padding, padding, padding, padding);

        LayoutInflater.from(context).inflate(R.layout.view_state, this, true);

        icono = findViewById(R.id.state_icon);
        titulo = findViewById(R.id.state_title);
        mensaje = findViewById(R.id.state_message);
        accion = findViewById(R.id.state_action);
    }

    public StateView conIcono(@DrawableRes int drawable) {
        icono.setImageResource(drawable);
        return this;
    }

    /** Tiñe el icono. El color por defecto es el de texto deshabilitado. */
    public StateView conColorIcono(int colorRes) {
        icono.setImageTintList(
                ColorStateList.valueOf(
                        ContextCompat.getColor(getContext(), colorRes)));
        return this;
    }

    public StateView conTitulo(@StringRes int texto) {
        titulo.setText(texto);
        return this;
    }

    public StateView conTitulo(@NonNull CharSequence texto) {
        titulo.setText(texto);
        return this;
    }

    public StateView conMensaje(@StringRes int texto) {
        mensaje.setText(texto);
        return this;
    }

    public StateView conMensaje(@NonNull CharSequence texto) {
        mensaje.setText(texto);
        return this;
    }

    /** Muestra el botón de acción. Sin acción, el botón no se dibuja. */
    public StateView conAccion(@StringRes int texto, @Nullable OnClickListener listener) {
        if (listener == null) {
            accion.setVisibility(GONE);
            return this;
        }
        accion.setText(texto);
        accion.setVisibility(VISIBLE);
        accion.setOnClickListener(listener);
        return this;
    }

    public StateView conAccion(@NonNull CharSequence texto, @Nullable OnClickListener listener) {
        if (listener == null) {
            accion.setVisibility(GONE);
            return this;
        }
        accion.setText(texto);
        accion.setVisibility(VISIBLE);
        accion.setOnClickListener(listener);
        return this;
    }

    public MaterialButton getAccion() {
        return accion;
    }

    /** Vista interna del icono, para casos que necesiten ajustarla. */
    protected ImageView getIcono() {
        return icono;
    }

    protected TextView getTituloView() {
        return titulo;
    }

    protected TextView getMensajeView() {
        return mensaje;
    }
}
