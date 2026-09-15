package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemRoomCardBinding;
import org.iot.project.models.Room;

/**
 * Lista de habitaciones de un hotel.
 *
 * <p>Las habitaciones ocupadas en las fechas consultadas se siguen mostrando,
 * pero con el botón deshabilitado y el motivo escrito. Ocultarlas haría dudar
 * al usuario de si el hotel tiene ese tipo de habitación o no; enseñarlas
 * bloqueadas responde la pregunta que realmente se está haciendo.
 */
public class RoomAdapter extends ListAdapter<Room, RoomAdapter.RoomViewHolder> {

    /** Quien escucha decide qué hacer con la habitación elegida. */
    public interface OnRoomClickListener {
        void onElegir(@NonNull Room room);
    }

    private static final DiffUtil.ItemCallback<Room> COMPARADOR =
            new DiffUtil.ItemCallback<Room>() {
                @Override
                public boolean areItemsTheSame(@NonNull Room anterior, @NonNull Room nueva) {
                    return anterior.getId().equals(nueva.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Room anterior, @NonNull Room nueva) {
                    return anterior.getPrecioNoche() == nueva.getPrecioNoche()
                            && anterior.isDisponible() == nueva.isDisponible()
                            && anterior.getTipo().equals(nueva.getTipo());
                }
            };

    private final OnRoomClickListener oyente;

    public RoomAdapter(@NonNull OnRoomClickListener oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new RoomViewHolder(ItemRoomCardBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int posicion) {
        holder.bind(getItem(posicion), oyente);
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {

        private final ItemRoomCardBinding binding;

        RoomViewHolder(@NonNull ItemRoomCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Room room, @NonNull OnRoomClickListener oyente) {
            binding.roomCard.bind(room);

            boolean disponible = room.isDisponible();
            binding.roomCard.setAccionHabilitada(disponible);
            binding.roomCard.setTextoAccion(disponible
                    ? binding.getRoot().getContext().getString(R.string.accion_elegir)
                    : binding.getRoot().getContext().getString(R.string.habitacion_no_disponible));

            // El oyente se instala siempre: el botón deshabilitado no lo dispara,
            // y así no hay que reinstalarlo si la habitación se libera al reciclar.
            binding.roomCard.setOnActionClickListener(v -> oyente.onElegir(room));
        }
    }
}
