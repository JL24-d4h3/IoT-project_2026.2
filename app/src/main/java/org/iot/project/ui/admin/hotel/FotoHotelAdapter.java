package org.iot.project.ui.admin.hotel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.iot.project.R;
import org.iot.project.databinding.ItemFotoHotelBinding;

import java.util.List;

/**
 * Tira de fotografías del hotel (RF-012, RF-013).
 *
 * <p>Trabaja sobre cadenas y no sobre el hotel entero: lo que la tira enseña
 * son las direcciones de las imágenes, y el hotel que las contiene cambia
 * entero en cada operación. Comparar el hotel en el DiffUtil daría por
 * cambiada la tira cada vez, aunque las fotografías siguieran siendo las
 * mismas.
 *
 * <p>La equis se esconde cuando quitar rompería el mínimo de cuatro (RF-013).
 * El repositorio lo impediría igualmente, pero ofrecer un botón que solo puede
 * dar error es ofrecer un error: el administrador que ve seis fotografías
 * puede quitar una, y el que ve cuatro tiene que agregar antes, y eso la
 * pantalla lo puede decir sin que nadie pulse nada.
 */
public class FotoHotelAdapter extends ListAdapter<String, FotoHotelAdapter.Fila> {

    /** Lo que la pantalla hace cuando se toca la equis de una miniatura. */
    public interface AlQuitarFoto {
        void onQuitar(@NonNull String url);
    }

    @Nullable
    private AlQuitarFoto alQuitar;

    /** Si con esta lista se puede quitar alguna. */
    private boolean sePuedeQuitar;

    public FotoHotelAdapter() {
        super(DIFF);
    }

    public void setAlQuitar(@Nullable AlQuitarFoto oyente) {
        this.alQuitar = oyente;
    }

    /**
     * Prepara la tira para una lista de fotografías.
     *
     * <p>Va aparte de {@code submitList} porque el adapter no puede deducir si
     * se puede quitar: eso depende del mínimo del hotel, que es una regla del
     * modelo y no del adaptador. Quien llama la conoce y la pasa ya resuelta.
     */
    public void mostrar(@NonNull List<String> urls, boolean sePuedeQuitar) {
        this.sePuedeQuitar = sePuedeQuitar;
        submitList(urls);
    }

    private static final DiffUtil.ItemCallback<String> DIFF =
            new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull String anterior, @NonNull String nueva) {
                    // La direccion es la identidad: el hotel no guarda dos
                    // fotografias con el mismo enlace, y no tiene otro
                    // identificador que darle.
                    return anterior.equals(nueva);
                }

                @Override
                public boolean areContentsTheSame(@NonNull String anterior, @NonNull String nueva) {
                    return true;
                }
            };

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemFotoHotelBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion));
    }

    /** Una miniatura de la tira. */
    class Fila extends RecyclerView.ViewHolder {

        private final ItemFotoHotelBinding binding;

        Fila(@NonNull ItemFotoHotelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull String url) {
            // El marcador de carga y el de error son los mismos que usa la
            // galeria del cliente (HotelImageGalleryView): una fotografia que
            // no carga se ve igual en las dos pantallas.
            Glide.with(binding.fotoImagen)
                    .load(url)
                    .placeholder(R.drawable.bg_skeleton)
                    .error(R.drawable.ic_search_off)
                    .centerCrop()
                    .into(binding.fotoImagen);

            int visibilidad = sePuedeQuitar ? View.VISIBLE : View.GONE;
            binding.fotoQuitar.setVisibility(visibilidad);
            binding.fotoVelo.setVisibility(visibilidad);
            binding.fotoQuitar.setOnClickListener(v -> {
                if (alQuitar != null) {
                    alQuitar.onQuitar(url);
                }
            });
        }
    }
}
