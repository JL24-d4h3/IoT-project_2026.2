package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;
import org.iot.project.models.SearchQuery;

/**
 * Selector de huespedes (§16): adultos, ninos y habitaciones, con su resumen.
 *
 * <p>Los tres {@link StepperView} son el mismo control repetido, y los topes no
 * son decorativos. El de adultos empieza en uno porque una busqueda sin nadie
 * no es una busqueda; el de habitaciones tambien, porque una reserva sin
 * habitacion tampoco. Los ninos si pueden ser cero.
 *
 * <p>El resumen —"2 adultos · 1 niño · 1 habitación"— lo redacta
 * {@link SearchQuery}, que es quien sabe pluralizar: escribirlo aqui obligaria
 * a mantener las mismas reglas en dos sitios.
 */
public class GuestSelectorView extends LinearLayout {

    /** Mas de este numero de personas no cabe en ninguna habitacion del catalogo. */
    private static final int MAX_ADULTOS = 8;
    private static final int MAX_NINOS = 6;
    private static final int MAX_HABITACIONES = 4;

    private final StepperView adultos;
    private final StepperView ninos;
    private final StepperView habitaciones;

    private OnCambioListener oyente;

    /** Avisa de que alguno de los tres numeros cambio. */
    public interface OnCambioListener {
        void onCambio();
    }

    public GuestSelectorView(@NonNull Context context) {
        this(context, null);
    }

    public GuestSelectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuestSelectorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_guest_selector, this, true);

        adultos = findViewById(R.id.guests_adultos);
        ninos = findViewById(R.id.guests_ninos);
        habitaciones = findViewById(R.id.guests_habitaciones);

        adultos.configurar(R.string.huespedes_adultos,
                getContext().getString(R.string.huespedes_adultos_nota), 1, MAX_ADULTOS);
        ninos.configurar(R.string.huespedes_ninos,
                getContext().getString(R.string.huespedes_ninos_nota), 0, MAX_NINOS);
        habitaciones.configurar(R.string.huespedes_habitaciones,
                getContext().getString(R.string.huespedes_habitaciones_nota), 1, MAX_HABITACIONES);

        // Con tres controles identicos en pantalla, "aumentar" a secas no dice
        // a que se refiere: la descripcion lleva delante la etiqueta del campo.
        adultos.setDescripcionAumentar(R.string.huespedes_adultos);
        adultos.setDescripcionReducir(R.string.huespedes_adultos);
        ninos.setDescripcionAumentar(R.string.huespedes_ninos);
        ninos.setDescripcionReducir(R.string.huespedes_ninos);
        habitaciones.setDescripcionAumentar(R.string.huespedes_habitaciones);
        habitaciones.setDescripcionReducir(R.string.huespedes_habitaciones);

        // Los tres avisan de lo mismo: quien escucha quiere refrescar el
        // resumen, y el resumen habla de los tres juntos.
        adultos.setOnValorCambiadoListener(valor -> avisar());
        ninos.setOnValorCambiadoListener(valor -> avisar());
        habitaciones.setOnValorCambiadoListener(valor -> avisar());
    }

    /**
     * Escucha los cambios de cualquiera de los tres controles.
     *
     * <p>Es la forma de que la hoja (§16) pueda enseñar el resumen al dia sin
     * tener que aplicar la seleccion: los toques en los controles no escriben
     * en la busqueda hasta que se pulsa "Aplicar".
     */
    public void setOnCambioListener(@Nullable OnCambioListener oyente) {
        this.oyente = oyente;
    }

    private void avisar() {
        if (oyente != null) {
            oyente.onCambio();
        }
    }

    /** Vuelca una busqueda en los tres controles. */
    public void escribir(@NonNull SearchQuery query) {
        adultos.setValor(query.getAdultos());
        ninos.setValor(query.getNinos());
        habitaciones.setValor(query.getHabitaciones());
    }

    /** Copia lo elegido a la busqueda. El resumen no se guarda: se deduce. */
    public void leer(@NonNull SearchQuery query) {
        query.setAdultos(adultos.getValor());
        query.setNinos(ninos.getValor());
        query.setHabitaciones(habitaciones.getValor());
    }

    /**
     * El resumen de lo que hay ahora mismo en los tres controles.
     *
     * <p>Lo redacta {@link SearchQuery}: es quien sabe pluralizar y quien decide
     * que los ninos solo se nombran cuando los hay.
     */
    @NonNull
    public String getResumen() {
        return SearchQuery.resumenHuespedes(
                adultos.getValor(), ninos.getValor(), habitaciones.getValor());
    }
}
