package org.iot.project.ui.superadmin.conductores;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminConductoresBinding;
import org.iot.project.models.TaxistaAdministrado;
import org.iot.project.utils.InsetUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Aprobación para trabajar y acceso de las cuentas del sistema web de taxistas. */
public class SuperadminConductoresFragment extends Fragment {
    private FragmentSuperadminConductoresBinding binding;
    private SuperadminConductoresViewModel viewModel;
    private ConductorSaAdapter adaptador;
    @Nullable private AlertDialog confirmacion;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflador,
            @Nullable ViewGroup contenedor, @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminConductoresBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_conductores);
        adaptador = new ConductorSaAdapter(this::confirmar);
        binding.saConductoresLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saConductoresLista.setAdapter(adaptador);
        viewModel = new ViewModelProvider(this).get(SuperadminConductoresViewModel.class);
        binding.saConductoresFiltros.check(viewModel.isSoloPendientes()
                ? R.id.sa_filtro_solo_pendientes : R.id.sa_filtro_todos);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getCambio().observe(getViewLifecycleOwner(), this::avisarDelCambio);
        viewModel.getPendientes().observe(getViewLifecycleOwner(), ids -> {
            adaptador.mostrarPendientes(ids);
            actualizarBotonLectura();
        });
        viewModel.getLeyendo().observe(getViewLifecycleOwner(), leyendo -> actualizarBotonLectura());
        viewModel.getAviso().observe(getViewLifecycleOwner(), mensaje -> {
            binding.saConductoresAviso.setVisibility(mensaje == null ? View.GONE : View.VISIBLE);
            binding.saConductoresAviso.setText(mensaje);
        });
        binding.saConductoresActualizar.setOnClickListener(v -> viewModel.reintentar());
        binding.saConductoresFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrar(!marcados.isEmpty()
                        && marcados.get(0) == R.id.sa_filtro_solo_pendientes));
    }

    @Override public void onStart() {
        super.onStart();
        viewModel.iniciar();
    }

    @Override public void onStop() {
        viewModel.detener();
        cerrarConfirmacion();
        super.onStop();
    }

    private void confirmar(String id, String nombre, SuperadminConductoresViewModel.Accion accion,
                           boolean valor) {
        if (!viewModel.puedeCambiar(id) || confirmacion != null) return;
        if (valor) {
            cambiar(id, accion, true);
            return;
        }
        final String cuenta = SessionManager.getIdentidadId();
        boolean habilitacion = accion == SuperadminConductoresViewModel.Accion.HABILITACION;
        confirmacion = new AlertDialog.Builder(requireContext())
                .setTitle(getString(habilitacion ? R.string.sa_taxistas_retirar_titulo
                        : R.string.sa_taxistas_desactivar_titulo, nombre))
                .setMessage(habilitacion ? R.string.sa_taxistas_retirar_mensaje
                        : R.string.sa_taxistas_desactivar_mensaje)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(habilitacion ? R.string.sa_taxistas_retirar : R.string.sa_taxistas_desactivar,
                        (dialogo, cual) -> {
                            if (Objects.equals(cuenta, SessionManager.getIdentidadId())) cambiar(id, accion, false);
                        })
                .create();
        confirmacion.setOnDismissListener(dialogo -> confirmacion = null);
        confirmacion.show();
    }

    private void cambiar(String id, SuperadminConductoresViewModel.Accion accion, boolean valor) {
        if (accion == SuperadminConductoresViewModel.Accion.HABILITACION) viewModel.habilitar(id, valor);
        else viewModel.cambiarCuenta(id, valor);
    }

    private void pintar(@NonNull UiState<List<TaxistaAdministrado>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                cerrarConfirmacion();
                adaptador.mostrar(Collections.emptyList());
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saConductoresVacio.setVisibility(View.GONE);
                binding.saConductoresLista.setVisibility(View.VISIBLE);
                adaptador.mostrar(estado.requireData());
                break;
            case EMPTY:
                adaptador.mostrar(Collections.emptyList());
                mostrar(true, false, false);
                binding.saConductoresLista.setVisibility(View.GONE);
                binding.saConductoresVacio.setVisibility(View.VISIBLE);
                binding.saConductoresVacio.conIcono(R.drawable.ic_taxi)
                        .conTitulo(viewModel.isSoloPendientes()
                                ? R.string.sa_taxistas_sin_aprobar_titulo : R.string.sa_conductores_nadie_titulo)
                        .conMensaje(viewModel.isSoloPendientes()
                                ? R.string.sa_taxistas_sin_aprobar_vacio : R.string.sa_conductores_nadie_mensaje);
                break;
            case ERROR:
            default:
                adaptador.mostrar(Collections.emptyList());
                cerrarConfirmacion();
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void actualizarBotonLectura() {
        boolean leyendo = Boolean.TRUE.equals(viewModel.getLeyendo().getValue());
        Set<String> ids = viewModel.getPendientes().getValue();
        binding.saConductoresActualizar.setEnabled(!leyendo && (ids == null || ids.isEmpty()));
        binding.saConductoresActualizar.setText(leyendo
                ? R.string.sa_taxistas_actualizando : R.string.sa_taxistas_actualizar);
    }

    private void avisarDelCambio(@Nullable UiState<SuperadminConductoresViewModel.Cambio> estado) {
        if (estado == null || estado.isLoading()) return;
        if (estado.isSuccess()) {
            SuperadminConductoresViewModel.Cambio resultado = estado.requireData();
            TaxistaAdministrado taxista = resultado.taxista;
            int texto = resultado.accion == SuperadminConductoresViewModel.Accion.HABILITACION
                    ? taxista.isHabilitado() ? R.string.sa_taxistas_aprobacion_hecha : R.string.sa_taxistas_retiro_hecho
                    : taxista.isCuentaActiva() ? R.string.sa_taxistas_activacion_hecha : R.string.sa_taxistas_desactivacion_hecha;
            Snackbar.make(binding.getRoot(), getString(texto, taxista.getPerfil().getNombreCompleto()),
                    Snackbar.LENGTH_LONG).show();
        } else if (estado.isError()) {
            String mensaje = estado.getMessage() == null ? getString(R.string.estado_error_descripcion) : estado.getMessage();
            Snackbar.make(binding.getRoot(), getString(R.string.sa_taxistas_error_cambio, mensaje),
                    Snackbar.LENGTH_LONG).setAction(R.string.sa_taxistas_actualizar, v -> viewModel.reintentar()).show();
        }
        viewModel.consumirCambio();
    }

    private void cerrarConfirmacion() {
        if (confirmacion != null) {
            confirmacion.dismiss();
            confirmacion = null;
        }
    }

    @Override public void onDestroyView() {
        cerrarConfirmacion();
        binding.saConductoresLista.setAdapter(null);
        adaptador = null;
        super.onDestroyView();
        binding = null;
    }
}
