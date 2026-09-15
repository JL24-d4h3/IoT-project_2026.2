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
 * Un acceso rapido de la portada del administrador (§43).
 *
 * <p>Es el indice de lo que no cabe en la barra inferior: §44 avisa de no
 * amontonar las opciones en una sola pantalla, y la salida no es alargar la
 * barra sino que el panel lleve a lo que ella no alcanza.
 *
 * <p>Se distingue de {@link SettingRowView} —que es la fila de una lista— en la
 * forma y no en el color: estos van en rejilla de dos columnas, con el icono
 * encima del rotulo. La diferencia importa porque en la misma pantalla conviven
 * los dos: los accesos son el indice y las filas son el contenido, y si se
 * dibujaran igual no se sabria cual de los dos lleva a otra pantalla.
 */
public class AccionRapidaView extends LinearLayout {

    private final ImageView icono;
    private final TextView titulo;

    public AccionRapidaView(@NonNull Context context) {
        this(context, null);
    }

    public AccionRapidaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AccionRapidaView(@NonNull Context context, @Nullable AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        LayoutInflater.from(context).inflate(R.layout.view_accion_rapida, this, true);

        icono = findViewById(R.id.accion_rapida_icono);
        titulo = findViewById(R.id.accion_rapida_titulo);
    }

    /**
     * Rellena el acceso y le cuelga el destino.
     *
     * <p>El destino es obligatorio —no hay sobrecarga sin el— porque un acceso
     * rapido que no lleva a ninguna parte es peor que no tenerlo: ocupa el sitio
     * de otro que si haria algo. Es la misma regla que deja fuera de la pantalla
     * a los botones sin destino.
     */
    public void setAccion(@DrawableRes int iconoRes, @StringRes int tituloRes,
                          @NonNull OnClickListener destino) {
        icono.setImageResource(iconoRes);
        titulo.setText(tituloRes);
        setOnClickListener(destino);
    }
}
