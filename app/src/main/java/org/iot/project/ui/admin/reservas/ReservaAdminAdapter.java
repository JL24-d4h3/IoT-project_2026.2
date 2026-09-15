package org.iot.project.ui.admin.reservas;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemReservaAdminBinding;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Lista de reservas del hotel (§44).
 *
 * <p>Trabaja sobre el cruce que ya hizo el repositorio y no sobre
 * {@code Booking}: el nombre del huésped y el número de habitación no están en
 * la reserva, y resolverlos aquí obligaría al adaptador a conocer los almacenes
 * de usuarios y hoteles (reglas 33-35).
 */
public class ReservaAdminAdapter
        extends ListAdapter<ReservaDeHotel, ReservaAdminAdapter.Fila> {

    /** Quien escucha abre el detalle de la reserva que se tocó. */
    public interface AlAbrir {
        void onAbrir(@NonNull ReservaDeHotel reserva);
    }

    private static final DiffUtil.ItemCallback<ReservaDeHotel> COMPARADOR =
            new DiffUtil.ItemCallback<ReservaDeHotel>() {
                @Override
                public boolean areItemsTheSame(@NonNull ReservaDeHotel anterior,
                                               @NonNull ReservaDeHotel nueva) {
                    return anterior.getReserva().getId().equals(nueva.getReserva().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ReservaDeHotel anterior,
                                                  @NonNull ReservaDeHotel nueva) {
                    // De una reserva ya creada solo cambian el estado y lo que
                    // se le ha cargado encima; las fechas y el cliente no. Los
                    // dos se leen de la copia que hizo el cruce y no de la
                    // reserva viva: las dos filas que se comparan envuelven la
                    // misma reserva, así que leerla en vivo diría siempre que no
                    // cambió nada.
                    return anterior.getEstado() == nueva.getEstado()
                            && anterior.getTotal() == nueva.getTotal();
                }
            };

    @NonNull
    private final AlAbrir oyente;

    public ReservaAdminAdapter(@NonNull AlAbrir oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemReservaAdminBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion), oyente);
    }

    /** Una fila de la lista. */
    static class Fila extends RecyclerView.ViewHolder {

        private final ItemReservaAdminBinding binding;

        Fila(@NonNull ItemReservaAdminBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ReservaDeHotel detalle, @NonNull AlAbrir oyente) {
            binding.reservaHuesped.setText(detalle.getClienteNombre(
                    itemView.getContext().getString(R.string.reserva_huesped_no_disponible)));
            binding.reservaEstado.setEstado(detalle.getEstado());
            binding.reservaHabitacion.setText(describirHabitacion(detalle));
            binding.reservaFechas.setText(DateFormatter.rangoConNoches(
                    detalle.getReserva().getFechaEntrada(),
                    detalle.getReserva().getFechaSalida(),
                    detalle.getReserva().getNumNoches()));
            binding.reservaTotal.setText(PriceFormatter.format(detalle.getTotal()));

            binding.getRoot().setOnClickListener(v -> oyente.onAbrir(detalle));
        }

        /**
         * "EST-2026-0004 · Hab. 601 · Doble clásica".
         *
         * <p>El tipo se omite si no se pudo leer —la habitación ya no está en el
         * inventario—, en vez de dejar un separador colgando.
         */
        @NonNull
        private String describirHabitacion(@NonNull ReservaDeHotel detalle) {
            String base = itemView.getContext().getString(R.string.admin_reserva_linea,
                    detalle.getReserva().getCodigo(), detalle.getHabitacionNumero());
            return detalle.getHabitacionTipo().isEmpty()
                    ? base
                    : itemView.getContext().getString(R.string.admin_reserva_linea_tipo,
                            base, detalle.getHabitacionTipo());
        }
    }
}
