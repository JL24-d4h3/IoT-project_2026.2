package org.iot.project.ui.driver.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemSolicitudTaxiBinding;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.TaxiService;

/**
 * Solicitudes que el conductor puede aceptar (§46, RF-088).
 *
 * <p>No calcula nada: la distancia ya viene resuelta en la oferta. El adaptador
 * no puede saber donde esta el conductor —eso vive en el repositorio— y
 * intentarlo lo obligaria a leer los mocks, que es lo que prohibe §49.
 */
public class SolicitudAdapter extends ListAdapter<OfertaDeTaxi, SolicitudAdapter.Fila> {

    /** Quien decide que pasa al aceptar: la pantalla, no la lista. */
    public interface OnAceptarListener {
        void onAceptar(@NonNull TaxiService servicio);
    }

    private static final DiffUtil.ItemCallback<OfertaDeTaxi> COMPARADOR =
            new DiffUtil.ItemCallback<OfertaDeTaxi>() {
                @Override
                public boolean areItemsTheSame(@NonNull OfertaDeTaxi anterior,
                                               @NonNull OfertaDeTaxi nueva) {
                    return anterior.getServicio().getId().equals(nueva.getServicio().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull OfertaDeTaxi anterior,
                                                  @NonNull OfertaDeTaxi nueva) {
                    // Lo unico que se mueve en una solicitud ya publicada es la
                    // distancia: el trayecto y el horario los fijo el cliente al
                    // pedirla, y no cambian mientras espera.
                    return anterior.getDistanciaM() == nueva.getDistanciaM();
                }
            };

    @Nullable
    private final OnAceptarListener oyente;

    public SolicitudAdapter(@Nullable OnAceptarListener oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemSolicitudTaxiBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        OfertaDeTaxi oferta = getItem(posicion);
        TaxiService servicio = oferta.getServicio();

        titular.binding.getRoot().bind(servicio, oferta.getDistanciaM());
        // Siempre, no solo cuando cambia: RecyclerView reutiliza las vistas y el
        // boton tiene que quedar en el estado correcto en cada fila reciclada.
        titular.binding.getRoot().setOnAceptar(v -> {
            if (oyente != null) {
                oyente.onAceptar(servicio);
            }
        });
    }

    /** Una fila de la lista. */
    static class Fila extends RecyclerView.ViewHolder {

        private final ItemSolicitudTaxiBinding binding;

        Fila(@NonNull ItemSolicitudTaxiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
