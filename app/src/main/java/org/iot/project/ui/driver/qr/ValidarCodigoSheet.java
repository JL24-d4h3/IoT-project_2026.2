package org.iot.project.ui.driver.qr;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import org.iot.project.R;
import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.core.permisos.PreferenciasPermisos;
import org.iot.project.data.rest.SesionTaxistaRest;
import org.iot.project.databinding.SheetValidarCodigoBinding;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.driver.home.DriverHomeViewModel;
import org.iot.project.utils.CodigoQrTaxi;

import java.util.List;

/** Lee o transcribe el código; el cierre siempre requiere confirmación y validación del servidor. */
public class ValidarCodigoSheet extends BottomSheetDialogFragment {
    public static final String TAG = "validar_codigo";
    private static final String ARG_TAXI = "taxiId";
    private static final String ARG_IDENTIDAD = "identidadTaxistaQr";
    private static final String PERMISO = "PERMISO";
    private static final String ESCANER = "ESCANER";
    private static final String AJUSTES = "AJUSTES";

    private SheetValidarCodigoBinding binding;
    private DriverHomeViewModel viewModel;
    private FormularioCodigo formulario;
    private PreferenciasPermisos preferenciasPermisos;
    private final ActivityResultLauncher<String> permisoCamara = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), concedido -> {
                // La elección pertenece a la instalación, aunque la hoja o la sesión cambien.
                if (preferenciasPermisos != null) preferenciasPermisos.registrarResultado(
                        PreferenciasPermisos.Permiso.CAMARA, concedido);
                if (formulario == null || !PERMISO.equals(formulario.paso())) return;
                formulario.paso("");
                if (!verificarContexto()) return;
                if (concedido && tienePermisoCamara()) abrirEscaner();
                else mostrarPermisoDenegado();
            });
    private final ActivityResultLauncher<ScanOptions> escaner = registerForActivityResult(
            new ScanContract(), this::recibirEscaneo);

    /** Código/contexto/pasos se conservan temporalmente; el JWT permanece solo en RAM. */
    public static class FormularioCodigo extends ViewModel {
        final SavedStateHandle guardado;
        final MutableLiveData<UiState<TaxiService>> envio = new MutableLiveData<>();
        private String tokenSesion;
        private boolean enviando;
        private long version;

        public FormularioCodigo(@NonNull SavedStateHandle guardado) {
            this.guardado = guardado;
            // Una escritura interrumpida por muerte del proceso nunca se repite sola.
            guardado.set("enviandoCodigoQr", false);
        }

        boolean configurar(@Nullable String taxiId, @Nullable String identidad) {
            if (!guardado.contains("identidadQr") && taxiId != null && identidad != null
                    && identidad.equals(SessionManager.getIdentidadId())) {
                tokenSesion = SesionTaxistaRest.getToken();
                if (tokenSesion == null) return false;
                guardado.set("identidadQr", identidad);
                guardado.set("taxiQr", taxiId);
            }
            return vigente(taxiId);
        }

        boolean vigente(@Nullable String taxiId) {
            String identidad = guardado.get("identidadQr");
            return tokenSesion != null && tokenSesion.equals(SesionTaxistaRest.getToken())
                    && SessionManager.getRolActivo() == Role.CONDUCTOR
                    && identidad != null && identidad.equals(SessionManager.getIdentidadId())
                    && SessionManager.getConductorId() != null && taxiId != null
                    && taxiId.equals(guardado.get("taxiQr")) && taxiId.matches("[1-9][0-9]*");
        }

        String codigo() { String dato = guardado.get("codigoQr"); return dato == null ? "" : dato; }
        String paso() { String dato = guardado.get("pasoQr"); return dato == null ? "" : dato; }
        void paso(String paso) { guardado.set("pasoQr", paso); }
        void mensaje(String texto) { guardado.set("mensajeQr", texto); }
        void invalidar(String mensaje) {
            version++;
            tokenSesion = null;
            enviando = false;
            guardado.set("codigoQr", "");
            guardado.set("enviandoCodigoQr", false);
            paso("");
            mensaje(mensaje);
        }

        void enviar(DriverHomeViewModel taxis, String taxiId, String codigo) {
            if (enviando || !paso().isEmpty() || !vigente(taxiId)) return;
            enviando = true;
            long solicitud = ++version;
            guardado.set("enviandoCodigoQr", true);
            envio.setValue(UiState.loading());
            taxis.validarCodigo(taxiId, codigo, new ResultCallback<TaxiService>() {
                @Override public void onExito(@NonNull TaxiService dato) {
                    if (solicitud != version) return;
                    enviando = false;
                    guardado.set("enviandoCodigoQr", false);
                    if (!vigente(taxiId)) {
                        invalidar("La sesión cambió. Cierra esta ventana y vuelve a abrir el servicio.");
                        envio.setValue(UiState.error("La sesión cambió. Cierra esta ventana y vuelve a abrir el servicio."));
                        return;
                    }
                    if (!taxiId.equals(dato.getId()) || !dato.getEstado().isFinished()) {
                        envio.setValue(UiState.error("El servidor no confirmó el cierre de este servicio. Actualiza los viajes."));
                        return;
                    }
                    envio.setValue(UiState.success(dato));
                }
                @Override public void onError(@NonNull String mensaje) {
                    if (solicitud != version) return;
                    enviando = false;
                    guardado.set("enviandoCodigoQr", false);
                    if (vigente(taxiId)) envio.setValue(UiState.error(mensaje));
                    else {
                        invalidar("La sesión cambió. Cierra esta ventana y vuelve a abrir el servicio.");
                        envio.setValue(UiState.error("La sesión cambió. Cierra esta ventana y vuelve a abrir el servicio."));
                    }
                }
            });
        }

        @Override protected void onCleared() {
            version++;
            tokenSesion = null;
        }
    }

    public static ValidarCodigoSheet newInstance(@NonNull String taxiId) {
        ValidarCodigoSheet hoja = new ValidarCodigoSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_TAXI, taxiId);
        argumentos.putString(ARG_IDENTIDAD, SessionManager.getIdentidadId());
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        preferenciasPermisos = PreferenciasPermisos.desde(context);
    }

    @Override public void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        viewModel = new ViewModelProvider(requireParentFragment()).get(DriverHomeViewModel.class);
        formulario = new ViewModelProvider(this).get(FormularioCodigo.class);
        // Migra el indicador de versiones anteriores; los nuevos pedidos ya son persistentes.
        if (Boolean.TRUE.equals(formulario.guardado.get("permisoCamaraSolicitadoQr"))) {
            preferenciasPermisos.registrarSolicitud(PreferenciasPermisos.Permiso.CAMARA);
            formulario.guardado.remove("permisoCamaraSolicitadoQr");
        }
        String identidad = getArguments() == null ? null : getArguments().getString(ARG_IDENTIDAD);
        if (!formulario.configurar(taxiId(), identidad)) {
            formulario.invalidar(getString(R.string.qr_taxi_contexto_cambio));
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle estado) {
        binding = SheetValidarCodigoBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        super.onViewCreated(vista, estado);
        binding.validarCerrar.setOnClickListener(v -> dismiss());
        binding.validarConfirmar.setOnClickListener(v -> confirmar());
        binding.validarEscanear.setOnClickListener(v -> pedirEscaneo());
        binding.validarCamaraAjustes.setOnClickListener(v -> abrirAjustes());
        binding.validarCodigo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int inicio, int cuenta, int despues) { }
            @Override public void onTextChanged(CharSequence s, int inicio, int antes, int cuenta) {
                String texto = s == null ? "" : s.toString();
                if (!texto.equals(formulario.codigo())) {
                    formulario.guardado.set("codigoQr", texto);
                    formulario.mensaje("");
                    binding.validarCampo.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        formulario.guardado.<String>getLiveData("codigoQr", "").observe(getViewLifecycleOwner(), codigo -> {
            String actual = binding.validarCodigo.getText() == null ? "" : binding.validarCodigo.getText().toString();
            if (!actual.equals(codigo)) {
                binding.validarCodigo.setText(codigo);
                binding.validarCodigo.setSelection(binding.validarCodigo.length());
            }
        });
        formulario.guardado.<String>getLiveData("mensajeQr", "").observe(getViewLifecycleOwner(), mensaje -> {
            binding.validarError.setText(mensaje);
            binding.validarError.setVisibility(mensaje == null || mensaje.isEmpty() ? View.GONE : View.VISIBLE);
        });
        formulario.guardado.<String>getLiveData("pasoQr", "").observe(getViewLifecycleOwner(), paso -> actualizarControles());
        formulario.guardado.<Boolean>getLiveData("ajustesCamaraQr", false).observe(getViewLifecycleOwner(), abrir ->
                binding.validarCamaraAjustes.setVisibility(Boolean.TRUE.equals(abrir) ? View.VISIBLE : View.GONE));
        formulario.envio.observe(getViewLifecycleOwner(), envio -> {
            actualizarControles();
            if (envio == null) return;
            if (envio.isError()) formulario.mensaje(envio.getMessage());
            if (envio.isSuccess() && formulario.vigente(taxiId())) {
                Toast.makeText(requireContext(), R.string.driver_servicio_cerrado, Toast.LENGTH_LONG).show();
                dismissAllowingStateLoss();
            }
        });
        viewModel.getOperando().observe(getViewLifecycleOwner(), ocupado -> actualizarControles());
        viewModel.getServicios().observe(getViewLifecycleOwner(), servicios -> actualizarControles());
        SessionManager.sesion().observe(getViewLifecycleOwner(), cuenta -> {
            verificarContexto();
            actualizarControles();
        });
        actualizarControles();
    }

    @Nullable private String taxiId() {
        return getArguments() == null ? null : getArguments().getString(ARG_TAXI);
    }

    private boolean verificarContexto() {
        if (formulario != null && formulario.vigente(taxiId())) return true;
        if (formulario != null) formulario.invalidar(getString(R.string.qr_taxi_contexto_cambio));
        return false;
    }

    private boolean servicioEnTraslado() {
        UiState<List<TaxiService>> estado = viewModel.getServicios().getValue();
        if (estado == null || !estado.isSuccess()) return false;
        String conductorId = SessionManager.getConductorId();
        for (TaxiService servicio : estado.requireData()) {
            if (servicio.getId().equals(taxiId()) && servicio.getEstado() == TaxiStatus.EN_TRASLADO
                    && servicio.getDriver() != null && servicio.getDriver().getId().equals(conductorId)) return true;
        }
        return false;
    }

    private boolean puedeAbrir() {
        return binding != null && !formulario.enviando && formulario.paso().isEmpty()
                && !Boolean.TRUE.equals(viewModel.getOperando().getValue());
    }

    private void pedirEscaneo() {
        if (!puedeAbrir() || !verificarContexto()) return;
        if (!servicioEnTraslado()) {
            formulario.mensaje(getString(R.string.qr_taxi_servicio_revisar));
            return;
        }
        if (!requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            formulario.mensaje(getString(R.string.qr_taxi_camara_ausente));
            return;
        }
        if (tienePermisoCamara()) abrirEscaner();
        else if (preferenciasPermisos.fueSolicitado(PreferenciasPermisos.Permiso.CAMARA)) {
            // Un rechazo se conserva al cerrar o volver a abrir esta hoja.
            mostrarPermisoDenegado();
        } else {
            formulario.paso(PERMISO);
            preferenciasPermisos.registrarSolicitud(PreferenciasPermisos.Permiso.CAMARA);
            permisoCamara.launch(Manifest.permission.CAMERA);
        }
    }

    private void abrirEscaner() {
        if (!puedeAbrir() || !verificarContexto() || !servicioEnTraslado()) return;
        if (!tienePermisoCamara()) {
            mostrarPermisoDenegado();
            return;
        }
        formulario.guardado.set("ajustesCamaraQr", false);
        formulario.paso(ESCANER);
        try {
            ScanOptions opciones = new ScanOptions().setCaptureActivity(EscanerTaxiActivity.class)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
                    .setBarcodeImageEnabled(false).setOrientationLocked(false)
                    .setPrompt(getString(R.string.qr_taxi_visor_instruccion))
                    .addExtra(Intents.Scan.SHOW_MISSING_CAMERA_PERMISSION_DIALOG, false)
                    .addExtra(EscanerTaxiActivity.EXTRA_IDENTIDAD, SessionManager.getIdentidadId())
                    .addExtra(EscanerTaxiActivity.EXTRA_SERVICIO, taxiId());
            escaner.launch(opciones);
        } catch (ActivityNotFoundException | SecurityException error) {
            formulario.paso("");
            formulario.mensaje(getString(R.string.qr_taxi_camara_no_disponible));
        }
    }

    private void recibirEscaneo(ScanIntentResult resultado) {
        if (formulario == null || !ESCANER.equals(formulario.paso())) return;
        formulario.paso("");
        if (!verificarContexto()) return;
        Intent original = resultado.getOriginalIntent();
        if (original != null && original.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false)) {
            mostrarPermisoDenegado();
        } else if (original != null && original.getBooleanExtra(EscanerTaxiActivity.EXTRA_CONTEXTO_INVALIDO, false)) {
            formulario.invalidar(getString(R.string.qr_taxi_contexto_cambio));
        } else if (original != null && original.getBooleanExtra(EscanerTaxiActivity.EXTRA_CAMARA_NO_DISPONIBLE, false)) {
            formulario.mensaje(getString(R.string.qr_taxi_camara_no_disponible));
        } else if (resultado.getContents() == null) {
            formulario.mensaje(getString(R.string.qr_taxi_cancelado));
        } else if (!tienePermisoCamara()) {
            mostrarPermisoDenegado();
        } else {
            try {
                String codigo = CodigoQrTaxi.normalizarEscaneado(resultado.getContents());
                formulario.guardado.set("codigoQr", codigo);
                formulario.mensaje(getString(R.string.qr_taxi_leido_confirmar));
            } catch (IllegalArgumentException error) {
                formulario.mensaje(getString(R.string.qr_taxi_formato_invalido));
            }
        }
        actualizarControles();
    }

    private boolean tienePermisoCamara() {
        if (!isAdded()) return false;
        boolean concedido = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        preferenciasPermisos.sincronizar(PreferenciasPermisos.Permiso.CAMARA, concedido);
        return concedido;
    }

    private void mostrarPermisoDenegado() {
        formulario.guardado.set("ajustesCamaraQr", !tienePermisoCamara());
        formulario.mensaje(getString(R.string.qr_taxi_permiso_denegado));
        actualizarControles();
    }

    private void abrirAjustes() {
        if (!puedeAbrir() || !verificarContexto()) return;
        formulario.paso(AJUSTES);
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName())));
        } catch (ActivityNotFoundException error) {
            formulario.paso("");
            formulario.mensaje(getString(R.string.qr_taxi_permiso_denegado));
        }
    }

    private void confirmar() {
        if (!puedeAbrir() || !verificarContexto()) return;
        if (!servicioEnTraslado()) {
            formulario.mensaje(getString(R.string.qr_taxi_servicio_revisar));
            return;
        }
        try {
            String codigo = CodigoQrTaxi.normalizarEscaneado(formulario.codigo());
            formulario.guardado.set("codigoQr", codigo);
            formulario.mensaje("");
            binding.validarCampo.setError(null);
            formulario.enviar(viewModel, taxiId(), codigo);
        } catch (IllegalArgumentException error) {
            binding.validarCampo.setError(getString(R.string.qr_taxi_formato_invalido));
        }
    }

    private void actualizarControles() {
        if (binding == null || formulario == null || viewModel == null) return;
        boolean esperando = formulario.enviando || !formulario.paso().isEmpty();
        boolean contexto = formulario.vigente(taxiId());
        boolean disponible = contexto && !esperando && !Boolean.TRUE.equals(viewModel.getOperando().getValue());
        binding.validarConfirmar.setEnabled(disponible && servicioEnTraslado());
        binding.validarEscanear.setEnabled(disponible && servicioEnTraslado());
        binding.validarCampo.setEnabled(contexto && !esperando);
        binding.validarCamaraAjustes.setEnabled(disponible);
        binding.validarCerrar.setEnabled(!esperando);
        binding.validarConfirmar.setText(formulario.enviando ? R.string.qr_taxi_validando : R.string.driver_codigo_confirmar);
        setCancelable(!esperando);
    }

    @Override public void onResume() {
        super.onResume();
        if (formulario == null) return;
        boolean concedido = tienePermisoCamara();
        if (AJUSTES.equals(formulario.paso())) formulario.paso("");
        if (!verificarContexto()) return;
        formulario.guardado.set("ajustesCamaraQr", !concedido
                && preferenciasPermisos.fueSolicitado(PreferenciasPermisos.Permiso.CAMARA));
        actualizarControles();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
