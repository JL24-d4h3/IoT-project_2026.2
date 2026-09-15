package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.iot.project.R;
import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;

/**
 * Seguimiento del servicio de taxi (§38).
 *
 * <p>§38 pide que el estado no sea solo texto, y con razon: "EN CAMINO" en una
 * linea no dice si queda mucho ni si ya paso. Aqui hay tres cosas que dicen lo
 * mismo de tres formas distintas —la insignia con el estado, la frase que
 * explica que esta pasando y la linea de tiempo con los cinco pasos—, porque
 * cada una responde a una pregunta diferente: en que punto estoy, que ocurre
 * ahora y cuanto falta.
 *
 * <p>Los pasos se generan recorriendo {@link TaxiStatus#values()}. Escribirlos
 * a mano en el XML obligaria a acordarse de anadir el sexto el dia que exista,
 * y el fallo seria una linea de tiempo que miente.
 */
public class TaxiStatusView extends LinearLayout {

    private final TextView insignia;
    private final TextView nota;
    private final LinearLayout linea;

    public TaxiStatusView(@NonNull Context context) {
        this(context, null);
    }

    public TaxiStatusView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaxiStatusView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_taxi_status, this, true);

        insignia = findViewById(R.id.status_badge);
        nota = findViewById(R.id.status_note);
        linea = findViewById(R.id.status_timeline);
    }

    public void bind(@NonNull TaxiService servicio) {
        TaxiStatus actual = servicio.getEstado();

        insignia.setText(actual.getDisplayName());
        nota.setText(notaDe(servicio));

        linea.removeAllViews();
        TaxiStatus[] pasos = TaxiStatus.values();
        for (int i = 0; i < pasos.length; i++) {
            linea.addView(crearPaso(pasos[i], i, actual.ordinal(), pasos.length));
        }
    }

    /**
     * Una frase que explica el estado, no que lo repite.
     *
     * <p>La insignia ya dice "En camino"; esto dice hacia donde y quien.
     */
    private CharSequence notaDe(@NonNull TaxiService servicio) {
        Driver conductor = servicio.getDriver();
        String nombre = conductor != null ? conductor.getNombres() : "";

        switch (servicio.getEstado()) {
            case SOLICITADO:
                return getContext().getString(R.string.taxi_nota_solicitado);
            case ASIGNADO:
                return getContext().getString(R.string.taxi_nota_asignado, nombre);
            case EN_CAMINO:
                return getContext().getString(R.string.taxi_nota_en_camino, nombre);
            case EN_TRASLADO:
                return getContext().getString(R.string.taxi_nota_en_traslado,
                        servicio.getDestino());
            case FINALIZADO:
                return getContext().getString(R.string.taxi_nota_finalizado);
        }
        return "";
    }

    private View crearPaso(@NonNull TaxiStatus paso, int indice, int actual, int total) {
        View fila = LayoutInflater.from(getContext())
                .inflate(R.layout.view_taxi_step, linea, false);

        TextView etiqueta = fila.findViewById(R.id.step_label);
        View punto = fila.findViewById(R.id.step_dot);
        View tramoArriba = fila.findViewById(R.id.step_line_top);
        View tramoAbajo = fila.findViewById(R.id.step_line_bottom);

        etiqueta.setText(paso.getDisplayName());

        boolean hecho = indice < actual;
        boolean esActual = indice == actual;

        int colorPunto = esActual || hecho
                ? R.color.colorPrimary
                : R.color.colorBorder;
        punto.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), colorPunto)));

        // El paso en curso se destaca en negrita: es el unico que esta pasando.
        etiqueta.setTypeface(null, esActual ? Typeface.BOLD : Typeface.NORMAL);
        etiqueta.setTextColor(ContextCompat.getColor(getContext(),
                esActual ? R.color.colorTextPrimary
                        : hecho ? R.color.colorTextSecondary : R.color.colorTextDisabled));

        // El primer paso no tiene nada encima y el ultimo nada debajo: los
        // extremos de la linea de tiempo son los extremos del flujo.
        tramoArriba.setVisibility(indice == 0 ? INVISIBLE : VISIBLE);
        tramoAbajo.setVisibility(indice == total - 1 ? INVISIBLE : VISIBLE);

        int colorTramo = ContextCompat.getColor(getContext(),
                indice <= actual ? R.color.colorPrimary : R.color.colorBorder);
        tramoArriba.setBackgroundColor(colorTramo);
        tramoAbajo.setBackgroundColor(colorTramo);

        // Un lector de pantalla recorre la lista entera: sin decir en cual esta,
        // anunciaria cinco estados seguidos sin decir cual es el de ahora.
        fila.setContentDescription(etiqueta.getText()
                + (esActual ? ", " + getContext().getString(R.string.taxi_paso_actual) : ""));

        return fila;
    }
}
