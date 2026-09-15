package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;

/**
 * Estado vacío: la consulta funcionó pero no hay nada que mostrar.
 *
 * <p>Es distinto de un error y la interfaz debe distinguirlo (§50). "No hay
 * hoteles en Puno" no es un fallo, y ofrecer "Reintentar" ahí confundiría al
 * usuario: lo que corresponde es proponerle otra cosa.
 */
public class EmptyStateView extends StateView {

    public EmptyStateView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        conIcono(R.drawable.ic_search_off);
        conTitulo(R.string.estado_vacio_titulo);
        conMensaje(R.string.estado_vacio_mensaje);
    }
}
