package org.iot.project.ui.auth.login;

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
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentLoginBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.utils.InsetUtils;

/**
 * Pantalla de acceso (§74).
 *
 * <p>Es la puerta de RF-004: de aqui sale el rol, y el rol decide que interfaz
 * se carga tras autenticar las credenciales y comprobar el perfil (RF-009).
 *
 * <p>Las validaciones de campo viven aqui y no en el ViewModel porque son un
 * asunto de presentacion: quien sabe cual es el campo mal y como se marca es la
 * pantalla. El ViewModel delega la autenticacion al repositorio.
 */
public class LoginFragment extends Fragment {

    /**
     * Clave con la que el registro devuelve el correo recien creado.
     *
     * <p>Vive aqui y no en el registro porque el campo que se rellena es de esta
     * pantalla: quien escribe en un campo es quien decide como se llama.
     */
    public static final String CLAVE_CORREO_REGISTRADO = "correoRegistrado";

    private FragmentLoginBinding binding;
    private LoginViewModel viewModel;
    private OnBackPressedCallback bloqueoRetroceso;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applySystemBarsPadding(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        bloqueoRetroceso = new OnBackPressedCallback(viewModel.estaCargando()) {
            @Override
            public void handleOnBackPressed() {
                // La solicitud debe terminar antes de abandonar el acceso.
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), bloqueoRetroceso);
        viewModel.getAcceso().observe(getViewLifecycleOwner(), this::pintarAcceso);

        binding.loginEntrar.setOnClickListener(v -> enviar());
        binding.loginContrasena.setOnEditorActionListener((campo, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                enviar();
                return true;
            }
            return false;
        });
        binding.loginOlvide.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.recuperacionFragment));
        binding.loginCrear.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.registroFragment));

        rellenarCorreoRegistrado(vista);
    }

    /**
     * Si se viene de crear una cuenta, deja su correo puesto.
     *
     * <p>Se lee una sola vez y se borra: si quedara ahi, volver a esta pantalla
     * mas adelante seguiria escribiendo el mismo correo encima de lo que el
     * usuario hubiera empezado a teclear.
     */
    private void rellenarCorreoRegistrado(@NonNull View vista) {
        NavBackStackEntry propia = Navigation.findNavController(vista).getCurrentBackStackEntry();
        if (propia == null) {
            return;
        }
        String correo = propia.getSavedStateHandle().get(CLAVE_CORREO_REGISTRADO);
        propia.getSavedStateHandle().remove(CLAVE_CORREO_REGISTRADO);
        if (correo != null && !correo.isEmpty()) {
            binding.loginCorreo.setText(correo);
            binding.loginContrasena.requestFocus();
        }
    }

    // ----------------------------------------------------------------- Enviar

    private void enviar() {
        if (viewModel.estaCargando()) {
            return;
        }
        binding.loginCorreoCampo.setError(null);
        binding.loginContrasenaCampo.setError(null);
        binding.loginAviso.setVisibility(View.GONE);

        String correo = binding.loginCorreo.getText() == null
                ? "" : binding.loginCorreo.getText().toString().trim();

        if (correo.isEmpty()) {
            marcarError(binding.loginCorreoCampo, binding.loginCorreo,
                    R.string.acceso_error_correo_vacio);
            return;
        }
        if (!correo.contains("@")) {
            marcarError(binding.loginCorreoCampo, binding.loginCorreo,
                    R.string.acceso_error_correo_invalido);
            return;
        }
        if (estaVacio(binding.loginContrasena)) {
            marcarError(binding.loginContrasenaCampo, binding.loginContrasena,
                    R.string.acceso_error_contrasena_vacia);
            return;
        }

        String contrasena = binding.loginContrasena.getText().toString();
        viewModel.entrar(correo, contrasena);
    }

    private void marcarError(@NonNull TextInputLayout campo,
                             @NonNull EditText entrada, int mensaje) {
        campo.setError(getString(mensaje));
        entrada.requestFocus();
    }

    private static boolean estaVacio(@NonNull EditText campo) {
        return campo.getText() == null || campo.getText().toString().isEmpty();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintarAcceso(@Nullable UiState<Cuenta> estado) {
        bloqueoRetroceso.setEnabled(estado != null && estado.isLoading());
        if (estado == null) {
            return;
        }
        habilitarFormulario(!estado.isLoading());
        switch (estado.getStatus()) {
            case LOADING:
                binding.loginEntrar.setEnabled(false);
                binding.loginEntrar.setText(R.string.acceso_entrando);
                break;
            case SUCCESS:
                binding.loginContrasena.setText(null);
                Cuenta cuenta = estado.requireData();
                // Primero se deja el estado en blanco y despues se entra: al
                // publicar la sesion se cambia el grafo entero, y si el
                // resultado siguiera ahi, al volver a esta pantalla se entraria
                // otra vez solo.
                viewModel.limpiarAcceso();
                SessionManager.iniciarSesion(cuenta);
                break;
            default:
                binding.loginEntrar.setEnabled(true);
                binding.loginEntrar.setText(R.string.acceso_entrar);
                mostrarAviso(estado.getMessage());
                break;
        }
    }

    private void mostrarAviso(@Nullable String mensaje) {
        binding.loginAviso.setVisibility(View.VISIBLE);
        binding.loginAvisoTexto.setText(mensaje != null
                ? mensaje : getString(R.string.estado_error_mensaje));
    }

    private void habilitarFormulario(boolean habilitado) {
        binding.loginCorreoCampo.setEnabled(habilitado);
        binding.loginContrasenaCampo.setEnabled(habilitado);
        binding.loginOlvide.setEnabled(habilitado);
        binding.loginCrear.setEnabled(habilitado);
    }

    @Override
    public void onDestroyView() {
        binding.loginContrasena.setText(null);
        super.onDestroyView();
        binding = null;
        bloqueoRetroceso = null;
    }
}
