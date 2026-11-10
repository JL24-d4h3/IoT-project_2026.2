package org.iot.project.ui.auth.login;

import android.content.MutableContextWrapper;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.GetCredentialUnsupportedException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

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
    @Nullable private CancellationSignal solicitudGoogle;
    @Nullable private MutableContextWrapper contextoGoogle;
    private long generacionGoogle;

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
        viewModel.getModoTaxista().observe(getViewLifecycleOwner(), this::pintarModo);
        binding.loginTipoCuenta.setOnCheckedChangeListener((grupo, id) -> {
            boolean taxista = id == R.id.login_modo_taxista;
            if (taxista != viewModel.esModoTaxista() && !viewModel.estaCargando()) {
                // No reutiliza la contraseña de una cuenta al cambiar de proveedor.
                binding.loginContrasena.setText(null);
                binding.loginAviso.setVisibility(View.GONE);
                binding.loginCorreoCampo.setError(null);
                binding.loginContrasenaCampo.setError(null);
                viewModel.seleccionarModoTaxista(taxista);
            }
        });

        binding.loginEntrar.setOnClickListener(v -> enviar());
        binding.loginGoogle.setOnClickListener(v -> iniciarGoogle());
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
            viewModel.seleccionarModoTaxista(false);
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

    /** El selector se abre únicamente desde el botón y nunca guarda su token. */
    private void iniciarGoogle() {
        if (binding == null || viewModel.esModoTaxista() || !viewModel.comenzarGoogle()) return;
        binding.loginContrasena.setText(null);
        binding.loginCorreoCampo.setError(null);
        binding.loginContrasenaCampo.setError(null);
        binding.loginAviso.setVisibility(View.GONE);
        int recursoCliente = getResources().getIdentifier("default_web_client_id", "string",
                requireContext().getPackageName());
        String clienteWeb = recursoCliente == 0 ? "" : getString(recursoCliente).trim();
        if (clienteWeb.isEmpty() || !clienteWeb.endsWith(".apps.googleusercontent.com")) {
            viewModel.fallarGoogle(getString(R.string.acceso_google_configuracion));
            return;
        }
        final long version = ++generacionGoogle;
        solicitudGoogle = new CancellationSignal();
        contextoGoogle = new MutableContextWrapper(requireActivity());
        try {
            GetSignInWithGoogleOption opcion = new GetSignInWithGoogleOption.Builder(clienteWeb).build();
            GetCredentialRequest peticion = new GetCredentialRequest.Builder()
                    .addCredentialOption(opcion).build();
            CredentialManager.create(requireContext().getApplicationContext()).getCredentialAsync(
                    contextoGoogle, peticion, solicitudGoogle,
                    ContextCompat.getMainExecutor(requireContext().getApplicationContext()),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(@NonNull GetCredentialResponse respuesta) {
                            if (!selectorVigente(version)) return;
                            liberarSelectorGoogle();
                            Credential credencial = respuesta.getCredential();
                            if (!(credencial instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(credencial.getType())) {
                                viewModel.fallarGoogle(getString(R.string.acceso_google_respuesta_invalida));
                                return;
                            }
                            final String token;
                            try {
                                token = GoogleIdTokenCredential.createFrom(credencial.getData()).getIdToken();
                            } catch (Exception fallo) {
                                viewModel.fallarGoogle(getString(R.string.acceso_google_respuesta_invalida));
                                return;
                            }
                            viewModel.completarGoogle(token);
                        }

                        @Override public void onError(@NonNull GetCredentialException fallo) {
                            if (!selectorVigente(version)) return;
                            liberarSelectorGoogle();
                            if (fallo instanceof GetCredentialCancellationException) {
                                viewModel.cancelarGoogle();
                            } else if (fallo instanceof NoCredentialException) {
                                viewModel.fallarGoogle(getString(R.string.acceso_google_sin_cuenta));
                            } else if (fallo instanceof GetCredentialProviderConfigurationException
                                    || fallo instanceof GetCredentialUnsupportedException) {
                                viewModel.fallarGoogle(getString(R.string.acceso_google_no_disponible));
                            } else {
                                viewModel.fallarGoogle(getString(R.string.acceso_google_error));
                            }
                        }
                    });
        } catch (RuntimeException fallo) {
            if (!selectorVigente(version)) return;
            liberarSelectorGoogle();
            viewModel.fallarGoogle(getString(R.string.acceso_google_no_disponible));
        }
    }

    private boolean selectorVigente(long version) {
        return version == generacionGoogle && binding != null && isAdded()
                && viewModel.estaEsperandoGoogle() && !viewModel.esModoTaxista();
    }

    private void liberarSelectorGoogle() {
        solicitudGoogle = null;
        if (contextoGoogle != null) {
            contextoGoogle.setBaseContext(contextoGoogle.getApplicationContext());
        }
        contextoGoogle = null;
    }

    // ------------------------------------------------------------------ Pinta

    private void pintarAcceso(@Nullable UiState<Cuenta> estado) {
        if (binding == null || bloqueoRetroceso == null) return;
        bloqueoRetroceso.setEnabled(estado != null && estado.isLoading());
        habilitarFormulario(estado == null || !estado.isLoading());
        if (estado == null) {
            binding.loginEntrar.setText(R.string.acceso_entrar);
            binding.loginGoogle.setText(R.string.acceso_google_continuar);
            binding.loginAviso.setVisibility(View.GONE);
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.loginEntrar.setEnabled(false);
                binding.loginEntrar.setText(R.string.acceso_entrando);
                binding.loginGoogle.setText(viewModel.estaEsperandoGoogle()
                        ? R.string.acceso_google_eligiendo : R.string.acceso_google_continuar);
                break;
            case SUCCESS:
                binding.loginContrasena.setText(null);
                Cuenta cuenta = estado.requireData();
                // Primero se deja el estado en blanco y despues se entra: al
                // publicar la sesion se cambia el grafo entero, y si el
                // resultado siguiera ahi, al volver a esta pantalla se entraria
                // otra vez solo.
                boolean taxista = viewModel.esModoTaxista();
                viewModel.limpiarAcceso();
                if (taxista) {
                    SessionManager.iniciarSesionTaxistaRest(cuenta);
                } else {
                    SessionManager.iniciarSesion(cuenta);
                }
                break;
            default:
                binding.loginEntrar.setEnabled(true);
                binding.loginEntrar.setText(R.string.acceso_entrar);
                binding.loginGoogle.setText(R.string.acceso_google_continuar);
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
        binding.loginModoNormal.setEnabled(habilitado);
        binding.loginModoTaxista.setEnabled(habilitado);
        binding.loginGoogle.setEnabled(habilitado);
    }

    private void pintarModo(@Nullable Boolean modoTaxista) {
        boolean taxista = Boolean.TRUE.equals(modoTaxista);
        binding.loginTipoCuenta.check(taxista ? R.id.login_modo_taxista : R.id.login_modo_normal);
        binding.loginCuentaWeb.setVisibility(taxista ? View.VISIBLE : View.GONE);
        binding.loginCuentaReservas.setVisibility(taxista ? View.GONE : View.VISIBLE);
        binding.loginGoogleAcciones.setVisibility(taxista ? View.GONE : View.VISIBLE);
        binding.loginOlvide.setVisibility(taxista ? View.GONE : View.VISIBLE);
        binding.loginRegistroAcciones.setVisibility(taxista ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        generacionGoogle++;
        if (solicitudGoogle != null) solicitudGoogle.cancel();
        liberarSelectorGoogle();
        // Tras entregar el token, Firebase puede terminar durante una recreación.
        if (viewModel != null && viewModel.estaEsperandoGoogle()) viewModel.cancelarGoogle();
        binding.loginContrasena.setText(null);
        super.onDestroyView();
        binding = null;
        bloqueoRetroceso = null;
    }
}
