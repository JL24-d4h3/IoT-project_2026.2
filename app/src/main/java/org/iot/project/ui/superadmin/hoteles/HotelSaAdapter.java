package org.iot.project.ui.superadmin.hoteles;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemHotelSaBinding;

/**
 * Filas de la lista de hoteles (RF-007, RF-008).
 *
 * <p>La fila trae el nombre del administrador ya cruzado por el ViewModel: el
 * hotel solo guarda un identificador, y resolverlo aqui obligaria al adaptador
 * a conocer la lista de cuentas.
 *
 * <p>El color de la insignia sigue la misma regla que en las otras dos listas
 * del panel: verde lo que ya funciona —publicado— y ambar lo que espera algo.
 */
public class HotelSaAdapter
        extends ListAdapter<SuperadminHotelesViewModel.FilaHotel, HotelSaAdapter.Fila> {

    /** Lo que la pantalla hace cuando se pulsa una fila. */
    public interface AlElegir {
        void elegir(@NonNull String hotelId);
    }

    private final AlElegir alElegir;

    public HotelSaAdapter(@NonNull AlElegir alElegir) {
        super(DIFF);
        this.alElegir = alElegir;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemHotelSaBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.bind(getItem(posicion));
    }

    class Fila extends RecyclerView.ViewHolder {

        private final ItemHotelSaBinding binding;

        Fila(@NonNull ItemHotelSaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull SuperadminHotelesViewModel.FilaHotel fila) {
            binding.saHotelNombre.setText(fila.hotel.getNombre());
            binding.saHotelUbicacion.setText(fila.hotel.getUbicacionCorta());
            binding.saHotelAdministrador.setText(administradorDe(fila));

            boolean publicado = fila.hotel.isPublicado();
            binding.saHotelEstado.setText(publicado
                    ? R.string.sa_hotel_publicado : R.string.sa_hotel_sin_publicar);
            pintarEstado(publicado);

            binding.getRoot().setOnClickListener(v -> alElegir.elegir(fila.hotel.getId()));
        }

        /**
         * Quien responde por el hotel, en sus tres estados posibles.
         *
         * <p>"Sin administrador" se dice solo cuando al hotel le falta de verdad
         * el paso de RF-008: si apunta a una cuenta que se desactivo despues, el
         * hotel si tiene administrador y decir lo contrario seria un dato falso.
         */
        @NonNull
        private CharSequence administradorDe(@NonNull SuperadminHotelesViewModel.FilaHotel fila) {
            if (fila.sinAsignar) {
                return itemView.getContext().getString(R.string.sa_hotel_sin_administrador);
            }
            if (fila.administrador == null) {
                return itemView.getContext().getString(R.string.sa_hotel_admin_inactivo);
            }
            return itemView.getContext()
                    .getString(R.string.sa_hotel_admin_nombre, fila.administrador);
        }

        private void pintarEstado(boolean publicado) {
            fondo(publicado ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);
            binding.saHotelEstado.setTextColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), publicado
                            ? R.color.colorOnSuccessContainer : R.color.colorOnWarningContainer)));
        }

        private void fondo(@DrawableRes int fondo) {
            binding.saHotelEstado.setBackground(
                    ContextCompat.getDrawable(itemView.getContext(), fondo));
        }
    }

    private static final DiffUtil.ItemCallback<SuperadminHotelesViewModel.FilaHotel> DIFF =
            new DiffUtil.ItemCallback<SuperadminHotelesViewModel.FilaHotel>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull SuperadminHotelesViewModel.FilaHotel a,
                        @NonNull SuperadminHotelesViewModel.FilaHotel b) {
                    return a.hotel.getId().equals(b.hotel.getId());
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull SuperadminHotelesViewModel.FilaHotel a,
                        @NonNull SuperadminHotelesViewModel.FilaHotel b) {
                    return a.hotel.isPublicado() == b.hotel.isPublicado()
                            && a.hotel.getNombre().equals(b.hotel.getNombre())
                            && a.sinAsignar == b.sinAsignar
                            && mismoTexto(a.administrador, b.administrador);
                }
            };

    private static boolean mismoTexto(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
