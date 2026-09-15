package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.databinding.ItemPriceLineBinding;
import org.iot.project.models.Charge;
import org.iot.project.utils.PriceFormatter;

import java.util.List;

/**
 * Lista de cargos de una estadía (RF-052).
 *
 * <p>Existe como componente porque los cargos se pintan en tres sitios —el
 * detalle de la reserva, el checkout y la pantalla de cobros del administrador—
 * y en los tres la etiqueta se arma igual: el motivo es lo que el huésped va a
 * discutir en recepción, así que va primero y con la observación al lado cuando
 * la hay.
 *
 * <p>Si no hay cargos, el componente queda vacío sin más. Es quien lo usa el que
 * decide si además esconde el título de la sección.
 */
public class ChargeListView extends LinearLayout {

    public ChargeListView(@NonNull Context context) {
        this(context, null);
    }

    public ChargeListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public void setCargos(@Nullable List<Charge> cargos) {
        removeAllViews();
        if (cargos == null || cargos.isEmpty()) {
            return;
        }
        LayoutInflater inflador = LayoutInflater.from(getContext());
        for (Charge cargo : cargos) {
            ItemPriceLineBinding fila = ItemPriceLineBinding.inflate(inflador, this, false);
            fila.lineLabel.setText(etiquetaDe(cargo));
            fila.lineAmount.setText(PriceFormatter.format(cargo.getMonto()));
            addView(fila.getRoot());
        }
    }

    /** "Motivo · observación", o solo el motivo cuando no hay observación. */
    private String etiquetaDe(@NonNull Charge cargo) {
        String observacion = cargo.getObservacion();
        return observacion == null || observacion.trim().isEmpty()
                ? cargo.getMotivo()
                : cargo.getMotivo() + " · " + observacion;
    }

    /** Atajo para no repetir la comprobación de si la sección tiene contenido. */
    public static boolean hayCargos(@Nullable List<Charge> cargos) {
        return cargos != null && !cargos.isEmpty();
    }
}
