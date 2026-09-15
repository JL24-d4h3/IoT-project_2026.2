package org.iot.project.ui.admin.home;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemEstadiaAdminBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.utils.DateFormatter;

/**
 * Las estadías en curso del hotel (§43).
 *
 * <p>Cada fila dice quién está alojado, en qué habitación y hasta cuándo. Es la
 * información con la que el administrador atiende el mostrador, así que la
 * fecha de salida va siempre visible y la que vence hoy se marca.
 *
 * <p>La fila lleva al detalle de la reserva, el mismo que abre la lista de
 * reservas: es la misma estadía vista desde otro sitio, y desde la portada es
 * donde el administrador la tiene a mano cuando el mostrador se la nombra.
 */
public class EstadiaAdapter extends ListAdapter<ReservaDeHotel, EstadiaAdapter.EstadiaViewHolder> {

    /** Quien escucha abre el detalle de la reserva que se tocó. */
    public interface AlAbrir {
        void onAbrir(@NonNull ReservaDeHotel estadia);
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
                    // Lo que la fila dibuja. Las fechas de una reserva ya creada
                    // no cambian, pero el nombre del cliente y la habitación sí
                    // pueden corregirse desde la gestión del hotel. Del cliente
                    // se compara el identificador y no el nombre: el nombre sale
                    // de él, así que es lo mismo dicho de una forma que no
                    // necesita resolver el usuario para comparar dos filas.
                    return anterior.getReserva().getClienteId()
                                    .equals(nueva.getReserva().getClienteId())
                            && anterior.getHabitacionNumero().equals(nueva.getHabitacionNumero())
                            && anterior.getReserva().getFechaSalida()
                                    .equals(nueva.getReserva().getFechaSalida());
                }
            };

    @NonNull
    private final AlAbrir oyente;

    public EstadiaAdapter(@NonNull AlAbrir oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public EstadiaViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new EstadiaViewHolder(ItemEstadiaAdminBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull EstadiaViewHolder holder, int posicion) {
        holder.bind(getItem(posicion), oyente);
    }

    static class EstadiaViewHolder extends RecyclerView.ViewHolder {

        private final ItemEstadiaAdminBinding binding;

        EstadiaViewHolder(@NonNull ItemEstadiaAdminBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ReservaDeHotel estadia, @NonNull AlAbrir oyente) {
            Booking reserva = estadia.getReserva();
            Context contexto = itemView.getContext();

            binding.estadiaCliente.setText(estadia.getClienteNombre(
                    contexto.getString(R.string.reserva_huesped_no_disponible)));
            binding.estadiaHabitacion.setText(contexto.getString(
                    R.string.admin_estadia_habitacion,
                    estadia.getHabitacionNumero(),
                    contarHuespedes(contexto.getResources(), reserva.getNumHuespedes())));
            binding.estadiaFechas.setText(
                    DateFormatter.rango(reserva.getFechaEntrada(), reserva.getFechaSalida()));

            // La que sale hoy es la única que exige algo del administrador, y
            // por eso es la única que se marca.
            binding.estadiaHoy.setVisibility(estadia.terminaHoy() ? View.VISIBLE : View.GONE);

            binding.getRoot().setOnClickListener(v -> oyente.onAbrir(estadia));
        }

        /** "3 huéspedes" / "1 huésped", con la concordancia del plural. */
        private static String contarHuespedes(Resources recursos, int huespedes) {
            return recursos.getQuantityString(R.plurals.reserva_huespedes, huespedes, huespedes);
        }
    }
}
