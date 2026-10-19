package org.iot.project.ui.auth.registro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentRegistroBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.ui.auth.login.LoginFragment;
import org.iot.project.utils.InsetUtils;

/**
 * Registro de cliente (RF-001, RF-002, §74).
 *
 * <p>La pantalla valida la confirmacion y pasa la contrasena durante la
 * solicitud de alta. Los campos de contrasena no se guardan al recrear la vista.
 */
public class RegistroFragment extends Fragment {

    /** Validacion local; Firebase aplica tambien su politica de contrasenas. */
    private static final int LARGO_MINIMO_CONTRASENA = 8;

    private FragmentRegistroBinding binding;
    private RegistroViewModel viewModel;
    private OnBackPressedCallback bloqueoRetroceso;

    /** La cuenta que se acaba de crear, para poder devolver su correo al acceso. */
    private Cuenta cuentaCreada;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegistroBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applySystemBarsPadding(binding.getRoot());
        binding.registroContrasena.setSaveEnabled(false);
        binding.registroContrasenaRepetir.setSaveEnabled(false);

        viewModel = new ViewModelProvider(this).get(RegistroViewModel.class);
        bloqueoRetroceso = new OnBackPressedCallback(viewModel.estaCargando()) {
            @Override
            public void handleOnBackPressed() {
                // La solicitud debe terminar antes de abandonar el registro.
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), bloqueoRetroceso);
        viewModel.getAlta().observe(getViewLifecycleOwner(), this::pintar);

        binding.registroCrear.setOnClickListener(v -> enviar());
        binding.registroContrasenaRepetir.setOnEditorActionListener((campo, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                enviar();
                return true;
            }
            return false;
        });
        binding.registroIniciar.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.registroExitoEntrar.setOnClickListener(v -> volverAlAcceso());
    }

    // ----------------------------------------------------------------- Enviar

    private void enviar() {
        if (viewModel.estaCargando() || cuentaCreada != null) {
            return;
        }
        limpiarErrores();

        String nombres = texto(binding.registroNombres);
        String apellidos = texto(binding.registroApellidos);
        String correo = texto(binding.registroCorreo);
        String telefono = texto(binding.registroTelefono);

        if (nombres.isEmpty()) {
            marcarError(binding.registroNombresCampo, binding.registroNombres,
                    R.string.registro_error_nombres);
            return;
        }
        if (apellidos.isEmpty()) {
            marcarError(binding.registroApellidosCampo, binding.registroApellidos,
                    R.string.registro_error_apellidos);
            return;
        }
        if (correo.isEmpty()) {
            marcarError(binding.registroCorreoCampo, binding.registroCorreo,
                    R.string.acceso_error_correo_vacio);
            return;
        }
        if (!correo.contains("@")) {
            marcarError(binding.registroCorreoCampo, binding.registroCorreo,
                    R.string.acceso_error_correo_invalido);
            return;
        }
        if (!contrasenasValidas()) {
            return;
        }

        String contrasena = binding.registroContrasena.getText().toString();
        viewModel.registrar(nombres, apellidos, correo, telefono, contrasena);
    }

    /**
     * Comprueba las dos contrasenas antes de enviarlas al repositorio.
     *
     * <p>Se leen a variables locales que mueren al terminar el metodo: no se
     * guardan en un campo ni se registran en ningun sitio.
     */
    private boolean contrasenasValidas() {
        String contrasena = binding.registroContrasena.getText() == null
                ? "" : binding.registroContrasena.getText().toString();
        String repetida = binding.registroContrasenaRepetir.getText() == null
                ? "" : binding.registroContrasenaRepetir.getText().toString();

        if (contrasena.isEmpty()) {
            marcarError(binding.registroContrasenaCampo, binding.registroContrasena,
                    R.string.acceso_error_contrasena_vacia);
            return false;
        }
        if (contrasena.length() < LARGO_MINIMO_CONTRASENA) {
            marcarError(binding.registroContrasenaCampo, binding.registroContrasena,
                    R.string.registro_error_contrasena_corta);
            return false;
        }
        if (!contrasena.equals(repetida)) {
            marcarError(binding.registroContrasenaRepetirCampo,
                    binding.registroContrasenaRepetir,
                    R.string.registro_error_contrasena_no_coincide);
            return false;
        }
        return true;
    }

    private void limpiarErrores() {
        binding.registroNombresCampo.setError(null);
        binding.registroApellidosCampo.setError(null);
        binding.registroCorreoCampo.setError(null);
        binding.registroContrasenaCampo.setError(null);
        binding.registroContrasenaRepetirCampo.setError(null);
        binding.registroAviso.setVisibility(View.GONE);
    }

    private void marcarError(@NonNull TextInputLayout campo, @NonNull EditText entrada,
                             int mensaje) {
        campo.setError(getString(mensaje));
        entrada.requestFocus();
    }

    private static String texto(@NonNull EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@Nullable UiState<Cuenta> estado) {
        bloqueoRetroceso.setEnabled(estado != null && estado.isLoading());
        if (estado == null) {
            return;
        }
        habilitarFormulario(!estado.isLoading() && !estado.isSuccess());
        switch (estado.getStatus()) {
            case LOADING:
                binding.registroCrear.setEnabled(false);
                binding.registroCrear.setText(R.string.registro_creando);
                break;
            case SUCCESS:
                limpiarContrasenas();
                binding.registroCrear.setEnabled(false);
                binding.registroCrear.setText(R.string.registro_crear);
                mostrarExito(estado.requireData());
                break;
            default:
                binding.registroCrear.setEnabled(true);
                binding.registroCrear.setText(R.string.registro_crear);
                mostrarAviso(estado.getMessage());
                break;
        }
    }

    private void mostrarExito(@NonNull Cuenta cuenta) {
        cuentaCreada = cuenta;
        binding.registroExitoMensaje.setText(
                getString(R.string.registro_listo_mensaje, cuenta.getEmail()));
        binding.registroExito.setVisibility(View.VISIBLE);
    }

    private void mostrarAviso(@Nullable String mensaje) {
        // El aviso va justo encima del boton que se acaba de pulsar, asi que
        // queda a la vista sin tener que desplazar nada.
        binding.registroAviso.setVisibility(View.VISIBLE);
        binding.registroAvisoTexto.setText(mensaje != null
                ? mensaje : getString(R.string.estado_error_mensaje));
    }

    private void habilitarFormulario(boolean habilitado) {
        binding.registroNombresCampo.setEnabled(habilitado);
        binding.registroApellidosCampo.setEnabled(habilitado);
        binding.registroCorreoCampo.setEnabled(habilitado);
        binding.registroTelefonoCampo.setEnabled(habilitado);
        binding.registroContrasenaCampo.setEnabled(habilitado);
        binding.registroContrasenaRepetirCampo.setEnabled(habilitado);
        binding.registroIniciar.setEnabled(habilitado);
    }

    private void limpiarContrasenas() {
        binding.registroContrasena.setText(null);
        binding.registroContrasenaRepetir.setText(null);
    }

    // ------------------------------------------------------------- Volver

    /**
     * Vuelve al acceso dejando el correo recien creado puesto.
     *
     * <p>Se pasa por el {@code SavedStateHandle} de la entrada anterior, que es
     * el mecanismo de Navigation para devolver un dato a la pantalla que quedo
     * detras; escribir en su campo directamente obligaria a esta pantalla a
     * conocer los campos de la otra.
     */
    private void volverAlAcceso() {
        NavController controlador = Navigation.findNavController(requireView());
        NavBackStackEntry acceso = controlador.getPreviousBackStackEntry();
        if (acceso != null && cuentaCreada != null) {
            acceso.getSavedStateHandle().set(
                    LoginFragment.CLAVE_CORREO_REGISTRADO, cuentaCreada.getEmail());
        }
        viewModel.limpiarAlta();
        controlador.popBackStack();
    }

    @Override
    public void onDestroyView() {
        limpiarContrasenas();
        cuentaCreada = null;
        super.onDestroyView();
        binding = null;
        bloqueoRetroceso = null;
    }
}
