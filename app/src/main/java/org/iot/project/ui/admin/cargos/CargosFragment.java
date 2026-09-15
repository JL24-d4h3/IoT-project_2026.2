package org.iot.project.ui.admin.cargos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminCargosBinding;
import org.iot.project.models.CargosDeReserva;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

/**
 * Cobros adicionales del hotel (§44, RF-051 a RF-054).
 *
 * <p>La pantalla es de consulta. Registrar un cobro se hace en el detalle de la
 * reserva, que es donde están el huésped y su tarjeta y donde el cobro tiene a
 * qué asociarse (RF-054); desde aquí se llega a esa reserva tocando su tarjeta.
 * Poner un botón de "nuevo cobro" aquí obligaría a elegir estadía en un
 * formulario aparte, con las prisas de un cobro que se hace en recepción.
 *
 * <p>El encabezado suma todos los cobros del hotel. Debajo, una tarjeta por
 * estadía, que es como RF-054 pide que se lean: cada cobro con su reserva.
 */
public class CargosFragment extends Fragment {

    /**
     * Nombre del argumento que declara {@code nav_hotel_admin.xml} para el
     * detalle de reserva. Vive aquí y no en la pantalla de reservas porque el
     * argumento es del grafo, no de una de sus pantallas; si allí cambia, aquí
     * también.
     */
    private static final String ARG_BOOKING_ID = "bookingId";

    private FragmentAdminCargosBinding binding;
    private CargosViewModel viewModel;
    private CargosAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminCargosBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminCargosHeader);

        binding.adminCargosHeader.setTitulo(R.string.nav_cargos);
        // Vuelve aunque la barra inferior siga visible: esta pantalla no es una
        // de sus secciones, y sin flecha la única salida sería adivinar qué
        // pestaña lleva de vuelta al panel.
        binding.adminCargosHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptador = new CargosAdapter(this::abrirDetalle);
        binding.adminCargosLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.adminCargosLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(CargosViewModel.class);
        viewModel.getCobros().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver del detalle se pudo haber registrado un cobro: la estadía
        // que ya estaba puede tener una fila más y el total haber cambiado. Se
        // refresca sin esqueleto, porque la pantalla ya está pintada.
        viewModel.refrescarEnSilencio();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<CargosViewModel.Cobros> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminCargosEsqueleto.setVisibility(View.VISIBLE);
                binding.adminCargosContenido.setVisibility(View.GONE);
                binding.adminCargosVacio.setVisibility(View.GONE);
                binding.adminCargosError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                CargosViewModel.Cobros cobros = estado.getData();
                if (cobros == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminCargosEsqueleto.setVisibility(View.GONE);
                binding.adminCargosVacio.setVisibility(View.GONE);
                binding.adminCargosError.setVisibility(View.GONE);
                binding.adminCargosContenido.setVisibility(View.VISIBLE);
                mostrar(cobros);
                break;
            case EMPTY:
                pintarVacio();
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void mostrar(@NonNull CargosViewModel.Cobros cobros) {
        binding.adminCargosTotal.setText(PriceFormatter.format(cobros.total));
        binding.adminCargosResumen.setText(getResources().getQuantityString(
                R.plurals.admin_cargos_resumen, cobros.numCargos, cobros.numCargos));
        adaptador.submitList(cobros.grupos);
    }

    private void pintarVacio() {
        binding.adminCargosEsqueleto.setVisibility(View.GONE);
        binding.adminCargosContenido.setVisibility(View.GONE);
        binding.adminCargosError.setVisibility(View.GONE);
        binding.adminCargosVacio.setVisibility(View.VISIBLE);
        binding.adminCargosVacio.conIcono(R.drawable.ic_credit_card);
        binding.adminCargosVacio.conTitulo(R.string.admin_cargos_vacio_titulo);
        binding.adminCargosVacio.conMensaje(R.string.admin_cargos_vacio);
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminCargosEsqueleto.setVisibility(View.GONE);
        binding.adminCargosContenido.setVisibility(View.GONE);
        binding.adminCargosVacio.setVisibility(View.GONE);
        binding.adminCargosError.setVisibility(View.VISIBLE);
        binding.adminCargosError.conReintento(mensaje, v -> viewModel.recargar());
    }

    // ------------------------------------------------------------- Navegación

    private void abrirDetalle(@NonNull CargosDeReserva grupo) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_BOOKING_ID, grupo.getEstadia().getReserva().getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.adminReservaDetalleFragment, argumentos);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.adminCargosLista.setAdapter(null);
        binding = null;
    }
}
