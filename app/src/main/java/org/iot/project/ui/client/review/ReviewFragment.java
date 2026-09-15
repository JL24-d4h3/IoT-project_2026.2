package org.iot.project.ui.client.review;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentReviewBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Review;
import org.iot.project.utils.InsetUtils;

/**
 * Valoración de una estadía terminada (RF-080, §22).
 *
 * <p>La escala es de 1 a 10 (regla 9) y por eso se usa un deslizador con el
 * número siempre a la vista, en lugar de estrellas: cinco estrellas no pueden
 * expresar diez niveles, y el valor que se envía tiene que leerse sin ambigüedad.
 */
public class ReviewFragment extends Fragment {

    private FragmentReviewBinding binding;
    private ReviewViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReviewBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        InsetUtils.applyBottomPadding(binding.valorarContenido);

        binding.header.setTitulo(R.string.titulo_valorar);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        // El deslizador no admite un valor fuera de rango, así que el número que
        // se enseña es siempre el que se va a enviar. Va sin decimales: los
        // pasos son enteros y un "8.0" sugeriría una precisión que no existe.
        binding.valorarSlider.addOnChangeListener((deslizador, valor, delUsuario) ->
                binding.valorarValor.setValorEntero(Math.round(valor)));
        binding.valorarValor.setValorEntero(Math.round(binding.valorarSlider.getValue()));
        binding.valorarEnviar.setOnClickListener(v -> enviar());
        binding.valorarVolver.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        viewModel = new ViewModelProvider(this).get(ReviewViewModel.class);
        viewModel.getReserva().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintarEnvio);

        String bookingId = getArguments() != null
                ? getArguments().getString("bookingId") : null;
        if (bookingId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<Booking> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.valorarContenido.setVisibility(View.GONE);
                binding.valorarError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.valorarError.setVisibility(View.GONE);
                binding.valorarContenido.setVisibility(View.VISIBLE);
                break;
            default:
                binding.valorarContenido.setVisibility(View.GONE);
                binding.valorarError.setVisibility(View.VISIBLE);
                binding.valorarError.conReintento(estado.getMessage(),
                        v -> viewModel.recargar());
                break;
        }
    }

    // ----------------------------------------------------------------- Enviar

    private void enviar() {
        CharSequence comentario = binding.valorarComentario.getText();
        binding.valorarEnviar.setEnabled(false);
        viewModel.valorar(binding.valorarSlider.getValue(),
                comentario != null ? comentario.toString() : "");
    }

    private void pintarEnvio(@Nullable UiState<Review> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        binding.valorarEnviar.setEnabled(true);

        if (estado.isError()) {
            // El comentario se queda escrito: perderlo obligaría a redactarlo
            // otra vez por un fallo que no es del usuario.
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.limpiarEnvio();
            return;
        }

        binding.valorarContenido.setVisibility(View.GONE);
        binding.valorarExito.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
