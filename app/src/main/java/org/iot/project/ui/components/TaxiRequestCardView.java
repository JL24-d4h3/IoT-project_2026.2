package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.Distancia;
import org.iot.project.utils.PriceFormatter;

/**
 * Servicio de taxi solicitado (§58).
 *
 * <p>Se usa en el historial del cliente y, mas adelante, en la lista de
 * servicios del conductor. En los dos sitios la pregunta es la misma —de donde
 * a donde, cuando, y en que punto esta— asi que la tarjeta es la misma.
 *
 * <p>El precio solo aparece cuando lo hay: el taxi es gratuito si la reserva
 * supero el minimo del hotel (RF-084, RT-012), y enseñar "S/ 0" en ese caso
 * haria parecer que no se cobro por error.
 */
public class TaxiRequestCardView extends MaterialCardView {

    private final TextView codigo;
    private final TextView estado;
    private final TextView origen;
    private final TextView destino;
    private final TextView horario;
    private final TextView precio;
    private final TextView gratuito;
    private final TextView distancia;
    private final MaterialButton aceptar;
    private final MaterialButton valorar;
    private final TextView valorado;

    public TaxiRequestCardView(@NonNull Context context) {
        this(context, null);
    }

    public TaxiRequestCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaxiRequestCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.view_taxi_request_card, this, true);

        codigo = findViewById(R.id.request_code);
        estado = findViewById(R.id.request_status);
        origen = findViewById(R.id.request_origin);
        destino = findViewById(R.id.request_destination);
        horario = findViewById(R.id.request_schedule);
        precio = findViewById(R.id.request_price);
        gratuito = findViewById(R.id.request_free);
        distancia = findViewById(R.id.request_distance);
        aceptar = findViewById(R.id.request_accept);
        valorar = findViewById(R.id.request_rate);
        valorado = findViewById(R.id.request_rated);
    }

    /** La tarjeta del historial del cliente: sin distancia y sin nada que aceptar. */
    public void bind(@NonNull TaxiService servicio) {
        pintar(servicio, -1d);
    }

    /**
     * La tarjeta de una solicitud disponible (RF-089).
     *
     * @param distanciaM metros del conductor al punto de recojo, o negativo si
     *                   no se sabe
     */
    public void bind(@NonNull TaxiService servicio, double distanciaM) {
        pintar(servicio, distanciaM);
    }

    private void pintar(@NonNull TaxiService servicio, double distanciaM) {
        codigo.setText(servicio.getCodigo());
        origen.setText(servicio.getOrigen());
        destino.setText(servicio.getDestino());

        pintarEstado(servicio.getEstado());

        String cuando = servicio.getFecha() != null
                ? DateFormatter.fechaCorta(servicio.getFecha()) : "";
        String hora = DateFormatter.hora(servicio.getHora());
        String pasajeros = getContext().getResources().getQuantityString(
                R.plurals.taxi_pasajeros, servicio.getNumPasajeros(), servicio.getNumPasajeros());

        StringBuilder linea = new StringBuilder();
        linea.append(cuando).append(" · ").append(hora).append(" · ").append(pasajeros);
        if (servicio.isIdaYVuelta()) {
            linea.append(" · ").append(getContext().getString(R.string.taxi_ida_y_vuelta));
        }
        horario.setText(linea);

        boolean esGratuito = servicio.isGratuito();
        gratuito.setVisibility(esGratuito ? VISIBLE : GONE);
        precio.setVisibility(esGratuito ? GONE : VISIBLE);
        if (!esGratuito) {
            precio.setText(PriceFormatter.format(servicio.getPrecio()));
        }

        pintarDistancia(distanciaM);
        pintarValoracion(servicio);
    }

    /**
     * A que distancia le queda el recojo (RF-089).
     *
     * <p>Sin distancia conocida no se enseña nada. Un "a 0 m de ti" inventado
     * seria peor que el silencio: significaria que ya llego, y no es verdad.
     */
    private void pintarDistancia(double distanciaM) {
        boolean hayDistancia = distanciaM >= 0d;
        distancia.setVisibility(hayDistancia ? VISIBLE : GONE);
        if (hayDistancia) {
            distancia.setText(getContext().getString(R.string.driver_a_distancia,
                    Distancia.legible(distanciaM)));
        }
    }

    /**
     * Hueco de valoracion (RF-105).
     *
     * <p>Solo lo tienen los servicios terminados y sin valorar. Un traslado en
     * curso no se valora —todavia no paso nada que valorar— y uno ya valorado
     * tampoco, pero ese si dice con cuanto, porque si no el cliente veria
     * desaparecer la opcion sin explicacion.
     */
    private void pintarValoracion(@NonNull TaxiService servicio) {
        boolean terminado = servicio.getEstado().isFinished();
        boolean yaValorado = servicio.isValorado();

        valorar.setVisibility(terminado && !yaValorado ? VISIBLE : GONE);
        valorado.setVisibility(terminado && yaValorado ? VISIBLE : GONE);
        if (terminado && yaValorado) {
            valorado.setText(getContext().getString(R.string.taxi_valorado,
                    Math.round(servicio.getRatingCliente())));
        }
    }

    /**
     * Que hacer al pulsar "Valorar". La tarjeta no decide a donde lleva: eso es
     * de la pantalla que la usa.
     */
    public void setOnValorar(@Nullable OnClickListener oyente) {
        valorar.setOnClickListener(oyente);
    }

    /**
     * Que hacer al pulsar "Aceptar" (RF-090).
     *
     * <p>La visibilidad del boton la decide este metodo y no {@code pintar}: el
     * RecyclerView reutiliza las vistas, asi que quien sabe si la fila se puede
     * aceptar es quien la usa, no lo que se esta pintando. Con el oyente nulo el
     * boton desaparece, que es lo que quiere el historial del cliente.
     */
    public void setOnAceptar(@Nullable OnClickListener oyente) {
        aceptar.setOnClickListener(oyente);
        aceptar.setVisibility(oyente != null ? View.VISIBLE : View.GONE);
    }

    /**
     * El estado se pinta segun lo avanzado que este el servicio.
     *
     * <p>El color no es decorativo: un servicio en curso y uno terminado no
     * significan lo mismo y no deben parecer iguales al recorrer la lista.
     */
    private void pintarEstado(@NonNull TaxiStatus estadoServicio) {
        estado.setText(estadoServicio.getDisplayName());

        int fondo;
        int texto;
        switch (estadoServicio) {
            case SOLICITADO:
                fondo = R.drawable.bg_badge_warning;
                texto = R.color.colorOnWarningContainer;
                break;
            case ASIGNADO:
            case EN_CAMINO:
            case EN_TRASLADO:
                fondo = R.drawable.bg_badge_primary;
                texto = R.color.colorOnPrimaryContainer;
                break;
            case FINALIZADO:
            default:
                fondo = R.drawable.bg_badge_success;
                texto = R.color.colorOnSuccessContainer;
                break;
        }
        estado.setBackground(ContextCompat.getDrawable(getContext(), fondo));
        estado.setTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), texto)));
    }
}
