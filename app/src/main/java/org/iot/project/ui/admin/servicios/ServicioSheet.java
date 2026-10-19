package org.iot.project.ui.admin.servicios;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetServicioAdminBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;
import org.iot.project.ui.components.FilterChipView;

import java.util.List;
import java.util.Objects;

/** Catalogo y forma de cobro, con borrador restaurado por valores e identidad. */
public class ServicioSheet extends BottomSheetDialogFragment {
    public static final String TAG = "servicio";
    private static final String ARG_SERVICE_ID = "serviceId";
    private static final String CLAVE_ELEGIDO = "servicioElegido";
    private static final String CLAVE_INCLUIDO = "servicioIncluido";
    private static final String CLAVE_PRECIO = "servicioPrecioBorrador";
    private static final String CLAVE_BORRADOR = "servicioBorradorPreparado";
    private static final String CLAVE_UID = "servicioCuenta";
    private static final String CLAVE_HOTEL = "servicioHotel";

    private SheetServicioAdminBinding binding;
    private ServiciosViewModel viewModel;
    private String editandoId;
    private String elegidoId;
    private String cuentaFormulario;
    private String hotelFormulario;
    private String precioBorrador = "";
    private boolean incluido = true;
    private boolean borradorPreparado;
    private boolean preparada;
    private boolean rellenando;
    private int idChipIncluido;
    private int idChipPago;
    private AlertDialog dialogoQuitar;

    @NonNull
    public static ServicioSheet para(@Nullable Service servicio) {
        ServicioSheet hoja = new ServicioSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_SERVICE_ID, servicio != null ? servicio.getServiceId() : null);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Override
    public void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        editandoId = getArguments() == null ? null : getArguments().getString(ARG_SERVICE_ID);
        if (estado != null) {
            elegidoId = estado.getString(CLAVE_ELEGIDO);
            incluido = estado.getBoolean(CLAVE_INCLUIDO, true);
            precioBorrador = estado.getString(CLAVE_PRECIO, "");
            borradorPreparado = estado.getBoolean(CLAVE_BORRADOR);
            cuentaFormulario = estado.getString(CLAVE_UID);
            hotelFormulario = estado.getString(CLAVE_HOTEL);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        estado.putString(CLAVE_ELEGIDO, elegidoId);
        estado.putBoolean(CLAVE_INCLUIDO, incluido);
        estado.putString(CLAVE_PRECIO, precioBorrador);
        estado.putBoolean(CLAVE_BORRADOR, borradorPreparado);
        estado.putString(CLAVE_UID, cuentaFormulario);
        estado.putString(CLAVE_HOTEL, hotelFormulario);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle estado) {
        binding = SheetServicioAdminBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estado) {
        super.onViewCreated(vista, estado);
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ServiciosViewModel.class);
        if (cuentaFormulario == null) cuentaFormulario = SessionManager.getUsuarioId();
        if (hotelFormulario == null) hotelFormulario = viewModel.getHotelId();
        preparada = false;
        binding.servicioCerrar.setOnClickListener(v -> {
            if (!viewModel.estaGuardando()) {
                viewModel.limpiarOperacion();
                dismiss();
            }
        });
        binding.servicioGuardar.setOnClickListener(v -> guardar());
        binding.servicioQuitar.setOnClickListener(v -> confirmarQuitar());
        binding.servicioReintentar.setOnClickListener(v -> viewModel.recargar());
        binding.servicioPrecio.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!rellenando) precioBorrador = s.toString();
            }
        });
        configurarForma();
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::preparar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        viewModel.getOcupado().observe(getViewLifecycleOwner(), ocupado -> pintarEspera());
        viewModel.cargar();
    }

    @Override public void onStart() { super.onStart(); pintarEspera(); }

    private void preparar(@Nullable UiState<Hotel> estado) {
        if (binding == null) return;
        if (!mismaCuenta()) {
            preparada = false;
            avisar(getString(R.string.admin_servicio_sesion_cambiada));
            pintarEspera();
            return;
        }
        if (estado == null || estado.isLoading()) {
            pintarEspera();
            return;
        }
        binding.servicioReintentar.setVisibility(estado.isError() ? View.VISIBLE : View.GONE);
        if (!estado.isSuccess()) {
            preparada = false;
            avisar(estado.getMessage());
            pintarEspera();
            return;
        }
        Hotel actual = estado.requireData();
        if (editandoId != null) {
            Service catalogo = viewModel.servicio(editandoId);
            HotelService asignado = viewModel.asignacion(editandoId);
            if (catalogo == null || asignado == null) {
                preparada = false;
                avisar(getString(R.string.admin_servicio_no_encontrado));
                pintarEspera();
                return;
            }
            binding.servicioTitulo.setText(catalogo.getName());
            binding.servicioCatalogoBloque.setVisibility(View.GONE);
            binding.servicioQuitar.setVisibility(View.VISIBLE);
            elegidoId = editandoId;
            if (!borradorPreparado) {
                incluido = asignado.isIncluded();
                precioBorrador = asignado.getPrice() > 0 ? String.valueOf(asignado.getPrice()) : "";
            }
        } else {
            binding.servicioTitulo.setText(R.string.admin_servicios_anadir);
            binding.servicioCatalogoBloque.setVisibility(View.VISIBLE);
            binding.servicioQuitar.setVisibility(View.GONE);
            List<Service> disponibles = viewModel.catalogoDisponible(actual);
            boolean elegidoDisponible = false;
            for (Service servicio : disponibles) {
                if (servicio.getServiceId().equals(elegidoId)) elegidoDisponible = true;
            }
            if (elegidoId != null && !elegidoDisponible) {
                elegidoId = null;
                avisar(getString(R.string.admin_servicio_catalogo_cambio));
            }
            rellenando = true;
            binding.servicioCatalogo.removeAllViews();
            for (Service servicio : disponibles) {
                FilterChipView chip = nuevoChip(servicio.getName());
                chip.bind(servicio);
                chip.setChecked(servicio.getServiceId().equals(elegidoId));
                chip.setOnCheckedChangeListener((boton, marcado) -> {
                    if (rellenando || !marcado) return;
                    elegidoId = servicio.getServiceId();
                    binding.servicioError.setVisibility(View.GONE);
                    pintarEspera();
                });
                binding.servicioCatalogo.addView(chip);
            }
            rellenando = false;
            binding.servicioCatalogoVacio.setVisibility(disponibles.isEmpty() ? View.VISIBLE : View.GONE);
        }
        borradorPreparado = true;
        preparada = true;
        escribirBorrador();
        pintarEspera();
    }

    private void configurarForma() {
        binding.servicioForma.removeAllViews();
        FilterChipView gratis = nuevoChip(getString(R.string.admin_servicio_incluido));
        FilterChipView pago = nuevoChip(getString(R.string.admin_servicio_de_pago));
        idChipIncluido = gratis.getId();
        idChipPago = pago.getId();
        binding.servicioForma.addView(gratis);
        binding.servicioForma.addView(pago);
        binding.servicioForma.setOnCheckedStateChangeListener((grupo, marcados) -> {
            if (rellenando || marcados.isEmpty()) return;
            incluido = marcados.get(0) == idChipIncluido;
            binding.servicioPrecioCampo.setVisibility(incluido ? View.GONE : View.VISIBLE);
            binding.servicioPrecioCampo.setError(null);
        });
    }

    private void escribirBorrador() {
        rellenando = true;
        binding.servicioForma.check(incluido ? idChipIncluido : idChipPago);
        binding.servicioPrecio.setText(precioBorrador);
        binding.servicioPrecioCampo.setVisibility(incluido ? View.GONE : View.VISIBLE);
        rellenando = false;
    }

    private void guardar() {
        if (!preparada || !mismaCuenta() || !viewModel.puedeGuardar()
                || (editandoId == null && !viewModel.puedeAgregar())) return;
        binding.servicioError.setVisibility(View.GONE);
        binding.servicioPrecioCampo.setError(null);
        if (elegidoId == null || viewModel.servicio(elegidoId) == null) {
            avisar(getString(R.string.admin_servicio_falta_elegir));
            return;
        }
        double precio = 0;
        if (!incluido) {
            Double leido = leerPrecio();
            if (leido == null) return;
            precio = leido;
        }
        viewModel.asignar(elegidoId, incluido, precio);
    }

    @Nullable
    private Double leerPrecio() {
        String texto = precioBorrador.trim();
        if (texto.isEmpty()) {
            binding.servicioPrecioCampo.setError(getString(R.string.admin_servicio_precio_obligatorio));
            return null;
        }
        try {
            double valor = Double.parseDouble(texto.replace(',', '.'));
            if (!Double.isFinite(valor) || valor <= 0) {
                binding.servicioPrecioCampo.setError(getString(R.string.admin_servicio_precio_cero));
                return null;
            }
            if (valor > 1000000) {
                binding.servicioPrecioCampo.setError(getString(R.string.admin_servicio_precio_maximo));
                return null;
            }
            return valor;
        } catch (NumberFormatException error) {
            binding.servicioPrecioCampo.setError(getString(R.string.admin_servicio_precio_invalido));
            return null;
        }
    }

    private void confirmarQuitar() {
        if (editandoId == null || !mismaCuenta() || !viewModel.puedeGuardar()
                || dialogoQuitar != null) return;
        Service servicio = viewModel.servicio(editandoId);
        if (servicio == null) return;
        dialogoQuitar = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.admin_servicio_quitar_titulo, servicio.getName()))
                .setMessage(R.string.admin_servicio_quitar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.admin_servicio_quitar, (dialogo, cual) -> {
                    if (mismaCuenta() && viewModel.puedeGuardar()) viewModel.quitar(editandoId);
                }).create();
        dialogoQuitar.setOnDismissListener(dialogo -> dialogoQuitar = null);
        dialogoQuitar.show();
    }

    private void pintarOperacion(@Nullable UiState<Hotel> estado) {
        pintarEspera();
        if (estado == null || estado.isLoading()) return;
        if (estado.isSuccess()) {
            dismiss();
            viewModel.limpiarOperacion();
        } else {
            avisar(estado.getMessage());
            viewModel.limpiarOperacion();
        }
    }

    private void pintarEspera() {
        if (binding == null || viewModel == null) return;
        boolean guardando = viewModel.estaGuardando();
        boolean ocupado = Boolean.TRUE.equals(viewModel.getOcupado().getValue());
        boolean editable = preparada && mismaCuenta() && !ocupado && viewModel.puedeGuardar();
        setCancelable(!guardando);
        binding.servicioCerrar.setEnabled(!guardando);
        binding.servicioGuardar.setEnabled(editable && elegidoId != null
                && (editandoId != null || viewModel.puedeAgregar()));
        binding.servicioGuardar.setText(guardando ? R.string.admin_servicio_guardando : R.string.admin_servicio_guardar);
        binding.servicioQuitar.setEnabled(editable);
        binding.servicioPrecioCampo.setEnabled(editable);
        binding.servicioProgreso.setVisibility(ocupado ? View.VISIBLE : View.GONE);
        binding.servicioReintentar.setEnabled(!ocupado && mismaCuenta());
        habilitarChips(binding.servicioCatalogo, editable);
        habilitarChips(binding.servicioForma, editable);
        if (editandoId == null && viewModel.puedeGuardar() && !viewModel.puedeAgregar()) {
            avisar(getString(R.string.admin_servicios_sin_cupo));
        }
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialogo = (BottomSheetDialog) getDialog();
            dialogo.setCanceledOnTouchOutside(!guardando);
            dialogo.getBehavior().setDraggable(!guardando);
        }
    }

    private static void habilitarChips(ChipGroup grupo, boolean habilitado) {
        for (int i = 0; i < grupo.getChildCount(); i++) grupo.getChildAt(i).setEnabled(habilitado);
    }

    private boolean mismaCuenta() {
        return cuentaFormulario != null && hotelFormulario != null
                && cuentaFormulario.equals(SessionManager.getUsuarioId())
                && Objects.equals(hotelFormulario, viewModel.getHotelId());
    }

    private void avisar(@Nullable String mensaje) {
        if (binding == null || mensaje == null || mensaje.isEmpty()) return;
        binding.servicioError.setText(mensaje);
        binding.servicioError.setVisibility(View.VISIBLE);
    }

    private FilterChipView nuevoChip(String etiqueta) {
        FilterChipView chip = new FilterChipView(requireContext());
        chip.setId(View.generateViewId());
        chip.setSaveEnabled(false);
        chip.setText(etiqueta);
        ChipGroup.LayoutParams params = new ChipGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int separacion = getResources().getDimensionPixelSize(R.dimen.space_sm);
        params.setMarginEnd(separacion);
        params.bottomMargin = separacion;
        chip.setLayoutParams(params);
        return chip;
    }

    @Override
    public void onDestroyView() {
        if (dialogoQuitar != null) dialogoQuitar.dismiss();
        super.onDestroyView();
        binding = null;
    }
}
