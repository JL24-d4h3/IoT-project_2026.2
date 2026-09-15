package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.iot.project.R;

/**
 * Un dato con su etiqueta: el numero arriba y que cuenta debajo.
 *
 * <p>Lo comparten los tres paneles de rol. Existe para que un panel no obligue
 * a repetir el mismo par de textos cuatro veces y para que los tres roles
 * cuenten lo suyo con la misma tipografia y el mismo espaciado (§71): la
 * diferencia entre roles tiene que estar en la informacion, no en como se
 * dibuja.
 */
public class StatView extends LinearLayout {

    private final TextView valor;
    private final TextView etiqueta;

    public StatView(@NonNull Context context) {
        this(context, null);
    }

    public StatView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_stat, this, true);

        valor = findViewById(R.id.stat_valor);
        etiqueta = findViewById(R.id.stat_etiqueta);
    }

    /**
     * Muestra el dato.
     *
     * <p>El valor llega ya formateado porque cada panel cuenta cosas distintas:
     * unos numeros sueltos, otros un importe. Formatearlo aqui obligaria a este
     * componente a saber de precios y de reservas.
     */
    public void setDato(@StringRes int textoEtiqueta, @NonNull CharSequence valorMostrado) {
        etiqueta.setText(textoEtiqueta);
        valor.setText(valorMostrado);
    }
}
