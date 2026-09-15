package org.iot.project.ui.auth.recuperacion;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentRecuperacionBinding;
import org.iot.project.utils.InsetUtils;

/**
 * Recuperacion de contrasena (§74).
 *
 * <p>La pantalla dice que se envio el enlace y no simula un buzon: no hay
 * backend que mande nada, y ensenar una bandeja de entrada inventada seria
 * mentir sobre lo que hace la aplicacion. El texto del exito esta redactado
 * para ser cierto exista o no la cuenta.
 */
public class RecuperacionFragment extends Fragment {

    private FragmentRecuperacionBinding binding;
    private RecuperacionViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecuperacionBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applySystemBarsPadding(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RecuperacionViewModel.class);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintar);

        binding.recuperarEnviar.setOnClickListener(v -> enviar());
        binding.recuperarCorreo.setOnEditorActionListener((campo, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                enviar();
                return true;
            }
            return false;
        });
        binding.recuperarCancelar.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.recuperarExitoVolver.setOnClickListener(v -> {
            viewModel.limpiarEnvio();
            Navigation.findNavController(v).navigateUp();
        });
    }

    private void enviar() {
        binding.recuperarCorreoCampo.setError(null);
        binding.recuperarAviso.setVisibility(View.GONE);

        String correo = binding.recuperarCorreo.getText() == null
                ? "" : binding.recuperarCorreo.getText().toString().trim();

        if (correo.isEmpty()) {
            binding.recuperarCorreoCampo.setError(getString(R.string.acceso_error_correo_vacio));
            binding.recuperarCorreo.requestFocus();
            return;
        }
        if (!correo.contains("@")) {
            binding.recuperarCorreoCampo.setError(getString(R.string.acceso_error_correo_invalido));
            binding.recuperarCorreo.requestFocus();
            return;
        }

        viewModel.recuperar(correo);
    }

    private void pintar(@Nullable UiState<String> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.recuperarEnviar.setEnabled(false);
                binding.recuperarEnviar.setText(R.string.recuperar_enviando);
                break;
            case SUCCESS:
                binding.recuperarEnviar.setEnabled(true);
                binding.recuperarEnviar.setText(R.string.recuperar_enviar);
                binding.recuperarExitoMensaje.setText(
                        getString(R.string.recuperar_listo_mensaje, estado.requireData()));
                binding.recuperarExito.setVisibility(View.VISIBLE);
                break;
            default:
                binding.recuperarEnviar.setEnabled(true);
                binding.recuperarEnviar.setText(R.string.recuperar_enviar);
                binding.recuperarAviso.setVisibility(View.VISIBLE);
                binding.recuperarAvisoTexto.setText(estado.getMessage() != null
                        ? estado.getMessage() : getString(R.string.estado_error_mensaje));
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
