package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import org.iot.project.R;

/**
 * Control de cuenta: {@code −  N  +} (§16).
 *
 * <p>Es la pieza que se repite tres veces en el selector de huespedes —adultos,
 * ninos, habitaciones— y una mas en la solicitud de taxi, para los pasajeros.
 * Por eso el comportamiento de los topes vive aqui y no en cada pantalla: al
 * llegar al minimo o al maximo el boton correspondiente se apaga, que es la
 * forma de decir "por aqui no se puede seguir" sin un mensaje de error.
 *
 * <p>El valor nunca sale del rango: quien lo usa puede leerlo tranquilo.
 */
public class StepperView extends LinearLayout {

    private final TextView etiqueta;
    private final TextView nota;
    private final TextView valor;
    private final ImageButton menos;
    private final ImageButton mas;

    private int minimo = 0;
    private int maximo = Integer.MAX_VALUE;
    private int actual;

    private OnValorCambiadoListener oyente;

    /** Se avisa con el valor ya recortado al rango. */
    public interface OnValorCambiadoListener {
        void onValorCambiado(int valor);
    }

    public StepperView(@NonNull Context context) {
        this(context, null);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepperView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_stepper, this, true);

        etiqueta = findViewById(R.id.stepper_label);
        nota = findViewById(R.id.stepper_hint);
        valor = findViewById(R.id.stepper_valor);
        menos = findViewById(R.id.stepper_menos);
        mas = findViewById(R.id.stepper_mas);

        menos.setOnClickListener(v -> mover(-1));
        mas.setOnClickListener(v -> mover(1));
    }

    /**
     * @param maximo tope superior. Un valor no positivo significa "sin tope".
     */
    public void configurar(@StringRes int etiquetaRes, @Nullable String notaTexto,
                           int minimo, int maximo) {
        etiqueta.setText(etiquetaRes);
        this.minimo = minimo;
        this.maximo = maximo > 0 ? maximo : Integer.MAX_VALUE;

        boolean hayNota = notaTexto != null && !notaTexto.isEmpty();
        nota.setText(notaTexto);
        nota.setVisibility(hayNota ? VISIBLE : GONE);

        setValor(Math.max(minimo, Math.min(this.maximo, actual)));
    }

    public int getValor() {
        return actual;
    }

    /**
     * Fija el valor sin avisar al oyente.
     *
     * <p>Es el setter de quien carga el estado inicial; si avisara, rellenar el
     * selector desde la busqueda se confundiria con el usuario cambiando algo.
     */
    public void setValor(int nuevo) {
        actual = Math.max(minimo, Math.min(maximo, nuevo));
        valor.setText(String.valueOf(actual));
        refrescarBotones();
    }

    public void setOnValorCambiadoListener(@Nullable OnValorCambiadoListener oyente) {
        this.oyente = oyente;
    }

    /**
     * La descripcion accesible lleva la etiqueta delante: con tres controles
     * iguales en la misma pantalla, un lector de pantalla que solo anuncie
     * "aumentar" no dice a que se refiere.
     */
    public void setDescripcionAumentar(@StringRes int etiquetaRes) {
        mas.setContentDescription(getContext().getString(R.string.cd_aumentar_campo,
                getContext().getString(etiquetaRes)));
    }

    public void setDescripcionReducir(@StringRes int etiquetaRes) {
        menos.setContentDescription(getContext().getString(R.string.cd_reducir_campo,
                getContext().getString(etiquetaRes)));
    }

    private void mover(int delta) {
        int nuevo = Math.max(minimo, Math.min(maximo, actual + delta));
        if (nuevo == actual) {
            return;
        }
        actual = nuevo;
        valor.setText(String.valueOf(actual));
        refrescarBotones();
        if (oyente != null) {
            oyente.onValorCambiado(actual);
        }
    }

    /** Un boton se apaga en el tope, en vez de quedarse ahi sin hacer nada. */
    private void refrescarBotones() {
        boolean puedeBajar = actual > minimo;
        boolean puedeSubir = actual < maximo;

        menos.setEnabled(puedeBajar);
        mas.setEnabled(puedeSubir);

        int apagado = ContextCompat.getColor(getContext(), R.color.colorTextDisabled);
        int activo = ContextCompat.getColor(getContext(), R.color.colorPrimary);
        menos.setImageTintList(ColorStateList.valueOf(puedeBajar ? activo : apagado));
        mas.setImageTintList(ColorStateList.valueOf(puedeSubir ? activo : apagado));
        valor.setTextColor(ContextCompat.getColor(getContext(),
                actual > minimo ? R.color.colorTextPrimary : R.color.colorTextSecondary));
    }
}
