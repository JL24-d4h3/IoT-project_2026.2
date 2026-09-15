package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;

/**
 * Estado de error: la consulta no llegó a completarse.
 *
 * <p>Siempre con acción de reintento, porque casi todos los fallos de §50 son
 * pasajeros. El mensaje lo redacta el repositorio y llega ya listo para
 * mostrar: nunca "HTTP 500" ni el nombre de una excepción (§53).
 */
public class ErrorStateView extends StateView {

    public ErrorStateView(@NonNull Context context) {
        this(context, null);
    }

    public ErrorStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ErrorStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        conIcono(R.drawable.ic_alert);
        conColorIcono(R.color.colorError);
        conTitulo(R.string.estado_error_titulo);
        conMensaje(R.string.estado_error_mensaje);
    }

    /** Atajo para el caso habitual: mensaje del repositorio y reintento. */
    public ErrorStateView conReintento(@NonNull CharSequence mensaje,
                                       @Nullable OnClickListener reintentar) {
        conMensaje(mensaje);
        conAccion(R.string.accion_reintentar, reintentar);
        return this;
    }
}
