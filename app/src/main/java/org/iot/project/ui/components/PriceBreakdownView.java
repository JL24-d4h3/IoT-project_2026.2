package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;
import org.iot.project.databinding.ItemPriceLineBinding;
import org.iot.project.utils.PriceFormatter;

import java.util.List;

/**
 * Desglose de precio: las líneas que lo componen y el total.
 *
 * <p>Se muestra siempre completo, aunque solo tenga una línea. Un total sin
 * desglose obliga a confiar; el desglose deja verificar de dónde sale la cifra,
 * que es justo lo que se pregunta quien va a pagar. Las líneas se reciben ya
 * calculadas para que el componente no decida qué se cobra.
 */
public class PriceBreakdownView extends LinearLayout {

    private final LinearLayout contenedor;
    private final TextView etiquetaTotal;
    private final TextView total;

    public PriceBreakdownView(@NonNull Context context) {
        this(context, null);
    }

    public PriceBreakdownView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PriceBreakdownView(@NonNull Context context, @Nullable AttributeSet attrs,
                              int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_price_breakdown, this, true);

        contenedor = findViewById(R.id.breakdown_lines);
        etiquetaTotal = findViewById(R.id.breakdown_total_label);
        total = findViewById(R.id.breakdown_total);
    }

    /**
     * Pinta las líneas.
     *
     * <p>Una línea de importe cero no se pinta: "Servicios adicionales S/ 0"
     * ocupa sitio para no decir nada.
     */
    public void setLineas(@NonNull List<Linea> lineas) {
        contenedor.removeAllViews();
        LayoutInflater inflador = LayoutInflater.from(getContext());
        for (Linea linea : lineas) {
            if (linea.monto == 0) {
                continue;
            }
            ItemPriceLineBinding fila = ItemPriceLineBinding.inflate(inflador, contenedor, false);
            fila.lineLabel.setText(linea.etiqueta);
            fila.lineAmount.setText(PriceFormatter.format(linea.monto));
            contenedor.addView(fila.getRoot());
        }
    }

    public void setTotal(@NonNull CharSequence etiqueta, double monto) {
        etiquetaTotal.setText(etiqueta);
        total.setText(PriceFormatter.format(monto));
    }

    /** Una línea del desglose: qué se cobra y cuánto. */
    public static class Linea {

        public final String etiqueta;
        public final double monto;

        public Linea(@NonNull String etiqueta, double monto) {
            this.etiqueta = etiqueta;
            this.monto = monto;
        }
    }
}
