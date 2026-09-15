package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;
import org.iot.project.databinding.ItemPriceLineBinding;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.utils.PriceFormatter;

import java.util.List;

/**
 * Ingresos por servicios adicionales de un hotel (RF-060, RF-061).
 *
 * <p>Existe como componente porque el mismo desglose se pinta en dos sitios: el
 * bloque de ingresos de la portada (§43) y el reporte de servicios (§45). En los
 * dos la fila se arma igual —el servicio, cuantas veces se cobro y lo que
 * sumo—, y tenerla una sola vez evita que la portada y el reporte acaben
 * enseñando cifras distintas del mismo servicio.
 *
 * <p><b>El orden no se toca.</b> Viene fijado por el repositorio —de menor a
 * mayor monto (RF-061)— y reordenarlo aqui, aunque fuera por instinto, seria
 * incumplir el requisito desde la pantalla.
 *
 * <p>Si no hay ingresos, el componente queda vacio sin mas. Es quien lo usa el
 * que decide si ademas esconde el titulo de la seccion.
 */
public class IngresoListView extends LinearLayout {

    public IngresoListView(@NonNull Context context) {
        this(context, null);
    }

    public IngresoListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public void setIngresos(@Nullable List<IngresoPorServicio> ingresos) {
        removeAllViews();
        if (ingresos == null || ingresos.isEmpty()) {
            return;
        }
        LayoutInflater inflador = LayoutInflater.from(getContext());
        for (IngresoPorServicio ingreso : ingresos) {
            ItemPriceLineBinding fila = ItemPriceLineBinding.inflate(inflador, this, false);
            fila.lineLabel.setText(etiquetaDe(ingreso));
            fila.lineAmount.setText(PriceFormatter.format(ingreso.getMontoTotal()));
            addView(fila.getRoot());
        }
    }

    /**
     * "Masaje descontracturante · 3 veces".
     *
     * <p>La cantidad va en la etiqueta y no en una columna propia porque el
     * monto ya ocupa la derecha: sin ella, "S/ 360" no diria si son tres
     * servicios de S/ 120 o uno de S/ 360, y esa es justo la pregunta que se le
     * hace a un reporte de ingresos.
     */
    @NonNull
    private CharSequence etiquetaDe(@NonNull IngresoPorServicio ingreso) {
        String veces = getResources().getQuantityString(
                R.plurals.admin_reportes_servicio_veces,
                ingreso.getCantidad(), ingreso.getCantidad());
        return getContext().getString(
                R.string.admin_reportes_linea, ingreso.getNombre(), veces);
    }

    /** Atajo para no repetir la comprobacion de si la seccion tiene contenido. */
    public static boolean hayIngresos(@Nullable List<IngresoPorServicio> ingresos) {
        return ingresos != null && !ingresos.isEmpty();
    }
}
