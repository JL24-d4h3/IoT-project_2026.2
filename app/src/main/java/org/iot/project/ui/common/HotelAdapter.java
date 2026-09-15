package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemHotelCardBinding;
import org.iot.project.models.Hotel;
import org.iot.project.ui.components.HotelCardView;

/**
 * Lista de hoteles, compartida por Inicio, Buscar, Recomendados y Favoritos.
 *
 * <p>Es la otra mitad de la reutilización de §58: la tarjeta se escribe una vez
 * y la lista que la contiene también. Las pantallas solo cambian lo que
 * necesitan —el precio, el corazón, si se muestra la disponibilidad—, no la
 * forma de la lista.
 *
 * <p>Usa {@link ListAdapter} con {@link DiffUtil} en vez de
 * {@code notifyDataSetChanged}: así la lista no parpadea al recargarse y las
 * tarjetas recicladas no arrastran los datos del hotel anterior.
 */
public class HotelAdapter extends ListAdapter<Hotel, HotelAdapter.HotelViewHolder> {

    /** Qué hace la pantalla cuando el usuario toca una tarjeta. */
    public interface OnHotelClickListener {
        void onHotelSeleccionado(@NonNull Hotel hotel);
    }

    private final OnHotelClickListener oyenteSeleccion;
    private HotelCardView.OnFavoriteClickListener oyenteFavorito;
    private boolean mostrarPrecio = true;

    private static final DiffUtil.ItemCallback<Hotel> COMPARADOR =
            new DiffUtil.ItemCallback<Hotel>() {
                @Override
                public boolean areItemsTheSame(@NonNull Hotel anterior, @NonNull Hotel nuevo) {
                    return anterior.getId().equals(nuevo.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Hotel anterior, @NonNull Hotel nuevo) {
                    return anterior.getNombre().equals(nuevo.getNombre())
                            && anterior.getRating() == nuevo.getRating()
                            && anterior.getPrecioDesde() == nuevo.getPrecioDesde()
                            && anterior.getServicios().size() == nuevo.getServicios().size();
                }
            };

    public HotelAdapter(@NonNull OnHotelClickListener oyenteSeleccion) {
        super(COMPARADOR);
        this.oyenteSeleccion = oyenteSeleccion;
    }

    public void setOnFavoriteClickListener(HotelCardView.OnFavoriteClickListener oyente) {
        this.oyenteFavorito = oyente;
    }

    public void setMostrarPrecio(boolean mostrarPrecio) {
        this.mostrarPrecio = mostrarPrecio;
    }

    @NonNull
    @Override
    public HotelViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new HotelViewHolder(ItemHotelCardBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HotelViewHolder holder, int posicion) {
        holder.bind(getItem(posicion));
    }

    class HotelViewHolder extends RecyclerView.ViewHolder {

        private final ItemHotelCardBinding binding;

        HotelViewHolder(@NonNull ItemHotelCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.hotelCard.setOnClickListener(v -> {
                int posicion = getBindingAdapterPosition();
                if (posicion != RecyclerView.NO_POSITION) {
                    oyenteSeleccion.onHotelSeleccionado(getItem(posicion));
                }
            });
        }

        void bind(@NonNull Hotel hotel) {
            binding.hotelCard.bind(hotel);
            binding.hotelCard.setMostrarPrecio(mostrarPrecio);
            binding.hotelCard.setOnFavoriteClickListener(oyenteFavorito);
        }
    }
}
