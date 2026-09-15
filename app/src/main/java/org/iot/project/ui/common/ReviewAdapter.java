package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.iot.project.R;
import org.iot.project.databinding.ItemReviewBinding;
import org.iot.project.models.Review;
import org.iot.project.utils.DateFormatter;

import java.time.LocalDateTime;

/**
 * Lista de reseñas, compartida por la ficha del hotel y el historial de
 * estadías.
 *
 * <p>La fecha se muestra en formato relativo ("hace 12 días") porque es lo que
 * ayuda a juzgar una reseña: saber si es de la semana pasada o de hace un año
 * cambia cómo se lee.
 */
public class ReviewAdapter extends ListAdapter<Review, ReviewAdapter.ReviewViewHolder> {

    private static final DiffUtil.ItemCallback<Review> COMPARADOR =
            new DiffUtil.ItemCallback<Review>() {
                @Override
                public boolean areItemsTheSame(@NonNull Review anterior, @NonNull Review nueva) {
                    return anterior.getId().equals(nueva.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Review anterior, @NonNull Review nueva) {
                    return anterior.getRating() == nueva.getRating()
                            && anterior.getComentario().equals(nueva.getComentario());
                }
            };

    public ReviewAdapter() {
        super(COMPARADOR);
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new ReviewViewHolder(ItemReviewBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int posicion) {
        holder.bind(getItem(posicion), posicion == getItemCount() - 1);
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {

        private final ItemReviewBinding binding;

        ReviewViewHolder(@NonNull ItemReviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Review reseña, boolean esUltima) {
            binding.reviewAuthor.setText(reseña.getAutorNombre());
            // Entero: la escala de una reseña es 1 a 10 con pasos de uno (§10,
            // regla 9). El promedio del hotel sí lleva decimal, pero eso es
            // Hotel.rating, no esto.
            binding.reviewRating.setValorEntero(Math.round(reseña.getRating()));
            binding.reviewComment.setText(reseña.getComentario());
            binding.reviewDate.setText(DateFormatter.relativo(
                    reseña.getFecha().atStartOfDay(), LocalDateTime.now()));

            // La última reseña de la lista no lleva separador: una línea suelta
            // al final solo añade ruido.
            binding.reviewDivider.setVisibility(esUltima ? View.GONE : View.VISIBLE);

            if (reseña.getAutorFotoUrl() != null) {
                Glide.with(binding.reviewAvatar)
                        .load(reseña.getAutorFotoUrl())
                        .placeholder(R.drawable.bg_skeleton)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(binding.reviewAvatar);
            } else {
                // Sin foto se cae al icono neutro: un hueco vacío parece un
                // error de carga.
                binding.reviewAvatar.setImageResource(R.drawable.ic_person);
            }
        }
    }
}
