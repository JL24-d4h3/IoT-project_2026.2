package org.iot.project.ui.admin.cargos;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemCargosReservaBinding;
import org.iot.project.models.CargosDeReserva;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Lista de estadías con cobros adicionales (§44, RF-051 a RF-054).
 *
 * <p>Se compara por la reserva y no por el cobro: el {@code Charge} de RF-052
 * no tiene identificador —solo monto, motivo y observación—, así que no hay con
 * qué distinguir dos cobros del mismo importe dentro de la misma estadía. La
 * reserva sí lo tiene, y es lo que de verdad identifica una fila de esta lista.
 */
public class CargosAdapter extends ListAdapter<CargosDeReserva, CargosAdapter.Fila> {

    /** Quien escucha abre el detalle de la reserva cuyos cobros se tocaron. */
    public interface AlAbrir {
        void onAbrir(@NonNull CargosDeReserva grupo);
    }

    private static final DiffUtil.ItemCallback<CargosDeReserva> COMPARADOR =
            new DiffUtil.ItemCallback<CargosDeReserva>() {
                @Override
                public boolean areItemsTheSame(@NonNull CargosDeReserva anterior,
                                               @NonNull CargosDeReserva nueva) {
                    return anterior.getEstadia().getReserva().getId()
                            .equals(nueva.getEstadia().getReserva().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CargosDeReserva anterior,
                                                  @NonNull CargosDeReserva nueva) {
                    // De una estadía ya cargada solo cambian sus cobros: el
                    // cliente, la habitación y las fechas no se mueven. Y como
                    // los cobros solo se añaden —no hay operación para quitar
                    // uno—, el número de filas y su suma bastan para saber si la
                    // tarjeta tiene que repintarse.
                    return anterior.getCargos().size() == nueva.getCargos().size()
                            && anterior.getTotal() == nueva.getTotal();
                }
            };

    @NonNull
    private final AlAbrir oyente;

    public CargosAdapter(@NonNull AlAbrir oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemCargosReservaBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion), oyente);
    }

    /** Una tarjeta: una estadía con sus cobros. */
    static class Fila extends RecyclerView.ViewHolder {

        private final ItemCargosReservaBinding binding;

        Fila(@NonNull ItemCargosReservaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CargosDeReserva grupo, @NonNull AlAbrir oyente) {
            binding.cargosHuesped.setText(grupo.getEstadia().getClienteNombre(
                    itemView.getContext().getString(R.string.reserva_huesped_no_disponible)));
            binding.cargosHabitacion.setText(describirHabitacion(grupo));
            binding.cargosFechas.setText(DateFormatter.rangoConNoches(
                    grupo.getEstadia().getReserva().getFechaEntrada(),
                    grupo.getEstadia().getReserva().getFechaSalida(),
                    grupo.getEstadia().getReserva().getNumNoches()));

            // Los cobros van con el componente que usan el detalle de la
            // reserva y el checkout, para que la misma línea se lea igual en los
            // tres sitios.
            binding.cargosLista.setCargos(grupo.getCargos());
            binding.cargosTotal.setText(PriceFormatter.format(grupo.getTotal()));

            binding.getRoot().setOnClickListener(v -> oyente.onAbrir(grupo));
        }

        /**
         * "EST-2026-0420 · Hab. 602 · Triple".
         *
         * <p>Misma línea que la fila de reservas: es la misma reserva, y el
         * administrador la identifica igual desde las dos pantallas. El tipo se
         * omite si no se pudo leer —la habitación ya no está en el inventario—,
         * en vez de dejar un separador colgando.
         */
        @NonNull
        private String describirHabitacion(@NonNull CargosDeReserva grupo) {
            String base = itemView.getContext().getString(R.string.admin_reserva_linea,
                    grupo.getEstadia().getReserva().getCodigo(),
                    grupo.getEstadia().getHabitacionNumero());
            return grupo.getEstadia().getHabitacionTipo().isEmpty()
                    ? base
                    : itemView.getContext().getString(R.string.admin_reserva_linea_tipo,
                            base, grupo.getEstadia().getHabitacionTipo());
        }
    }
}
