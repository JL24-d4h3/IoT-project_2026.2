package org.iot.project.ui.auth.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import java.util.List;

/**
 * Pantalla de acceso (§74).
 *
 * <p>Es la puerta de RF-004: de aqui sale el rol, y el rol decide que interfaz
 * se carga. No hay autenticacion real —esta entrega es solo front end—, asi que
 * la contrasena no se comprueba; lo que si ocurre de verdad es que una cuenta
 * deshabilitada no entra (RF-009).
 *
 * <p>Las validaciones de campo viven aqui y no en el ViewModel porque son un
 * asunto de presentacion: quien sabe cual es el campo mal y como se marca es la
 * pantalla. El ViewModel solo se ocupa de resolver la cuenta.
 */
public class LoginFragment extends Fragment {

    /**
     * Clave con la que el registro devuelve el correo recien creado.
     *
     * <p>Vive aqui y no en el registro porque el campo que se rellena es de esta
     * pantalla: quien escribe en un campo es quien decide como se llama.
     */
    public static final String CLAVE_CORREO_REGISTRADO = "correoRegistrado";

    /**
     * Lo que se escribe en el campo de contrasena al elegir una cuenta de
     * ejemplo. No se comprueba contra nada: sirve para que el formulario quede
     * coherente a la vista, no para entrar.
     */
    private static final String CONTRASENA_DE_EJEMPLO = "demo1234";

    private FragmentLoginBinding binding;
    private LoginViewModel viewModel;

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
        viewModel.getAcceso().observe(getViewLifecycleOwner(), this::pintarAcceso);
        viewModel.getEjemplos().observe(getViewLifecycleOwner(), this::pintarEjemplos);

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
        viewModel.cargarEjemplos();
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

        // La contrasena no se lee siquiera a una variable: se comprueba que se
        // haya escrito algo, que es lo unico que esta pantalla puede hacer con
        // ella, y se queda en el campo (RC-042, RT-038).
        viewModel.entrar(correo);
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
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.loginEntrar.setEnabled(false);
                binding.loginEntrar.setText(R.string.acceso_entrando);
                break;
            case SUCCESS:
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

    // ------------------------------------------------------- Cuentas de ejemplo

    private void pintarEjemplos(@Nullable UiState<List<Cuenta>> estado) {
        binding.loginDemoLista.removeAllViews();
        boolean hay = estado != null && estado.isSuccess() && !estado.requireData().isEmpty();
        binding.loginDemoBloque.setVisibility(hay ? View.VISIBLE : View.GONE);
        if (!hay) {
            return;
        }

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        List<Cuenta> cuentas = estado.requireData();
        for (int i = 0; i < cuentas.size(); i++) {
            Cuenta cuenta = cuentas.get(i);
            View fila = inflador.inflate(R.layout.view_cuenta_demo,
                    binding.loginDemoLista, false);
            ((TextView) fila.findViewById(R.id.cuenta_demo_nombre))
                    .setText(cuenta.getNombreCompleto());
            ((TextView) fila.findViewById(R.id.cuenta_demo_rol))
                    .setText(cuenta.getRol().getDisplayName());

            // Elegir un ejemplo rellena el formulario y lo envia; no salta
            // ninguna comprobacion. Es lo que hace que el atajo no pueda llevar
            // a nadie a una interfaz que su cuenta no tenga derecho a ver.
            fila.setOnClickListener(v -> entrarDeEjemplo(cuenta));
            binding.loginDemoLista.addView(fila);

            if (i < cuentas.size() - 1) {
                binding.loginDemoLista.addView(separador());
            }
        }
    }

    private void entrarDeEjemplo(@NonNull Cuenta cuenta) {
        binding.loginCorreo.setText(cuenta.getEmail());
        binding.loginContrasena.setText(CONTRASENA_DE_EJEMPLO);
        enviar();
    }

    private View separador() {
        View linea = new View(requireContext());
        linea.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.divider_thickness)));
        linea.setBackgroundColor(requireContext().getColor(R.color.colorDivider));
        return linea;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
