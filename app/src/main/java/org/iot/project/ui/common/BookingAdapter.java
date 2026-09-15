package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemBookingCardBinding;
import org.iot.project.models.Booking;

/**
 * Lista de reservas. La usan las tres secciones de "Mis reservas" (§30 a §32)
 * y el historial del perfil.
 */
public class BookingAdapter extends ListAdapter<Booking, BookingAdapter.BookingViewHolder> {

    /** Quien escucha decide si abrir el detalle, el chat o el checkout. */
    public interface OnBookingClickListener {
        void onAbrir(@NonNull Booking reserva);
    }

    private static final DiffUtil.ItemCallback<Booking> COMPARADOR =
            new DiffUtil.ItemCallback<Booking>() {
                @Override
                public boolean areItemsTheSame(@NonNull Booking anterior, @NonNull Booking nueva) {
                    return anterior.getId().equals(nueva.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Booking anterior,
                                                  @NonNull Booking nueva) {
                    // El estado y el total son lo único que cambia en una reserva
                    // ya creada; las fechas no se editan.
                    return anterior.getEstado() == nueva.getEstado()
                            && anterior.getTotal() == nueva.getTotal();
                }
            };

    private final OnBookingClickListener oyente;

    public BookingAdapter(@NonNull OnBookingClickListener oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new BookingViewHolder(ItemBookingCardBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int posicion) {
        holder.bind(getItem(posicion), oyente);
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {

        private final ItemBookingCardBinding binding;

        BookingViewHolder(@NonNull ItemBookingCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Booking reserva, @NonNull OnBookingClickListener oyente) {
            binding.bookingCard.bind(reserva);
            binding.bookingCard.setOnClickListener(v -> oyente.onAbrir(reserva));
        }
    }
}
