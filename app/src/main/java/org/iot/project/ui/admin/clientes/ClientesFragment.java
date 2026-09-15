package org.iot.project.ui.admin.clientes;

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
import org.iot.project.databinding.FragmentAdminClientesBinding;
import org.iot.project.models.ClienteDeHotel;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Clientes del hotel (§44).
 *
 * <p>Es el directorio de huéspedes del hotel: quién se ha alojado aquí, cuántas
 * veces y cuánto ha dejado. §42 pide una interfaz de gestión con densidad
 * informativa, y esto es lo que el administrador consulta en recepción cuando
 * quiere reconocer a quien tiene delante.
 *
 * <p>La pantalla es de solo lectura y no se puede llegar a ninguna otra desde
 * ella, así que no se refresca al volver: no hay a dónde ir que la deje
 * desactualizada.
 */
public class ClientesFragment extends Fragment {

    private FragmentAdminClientesBinding binding;
    private ClientesViewModel viewModel;
    private ClienteAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminClientesBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminClientesHeader);

        binding.adminClientesHeader.setTitulo(R.string.nav_clientes);
        // Vuelve aunque la barra inferior siga visible: esta pantalla no es una
        // de sus secciones, y sin flecha la única salida sería adivinar qué
        // pestaña lleva de vuelta al panel.
        binding.adminClientesHeader.mostrarVolver(
                v -> Navigation.findNavController(v).navigateUp());

        adaptador = new ClienteAdapter();
        binding.adminClientesLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.adminClientesLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(ClientesViewModel.class);
        viewModel.getClientes().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<ClienteDeHotel>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminClientesEsqueleto.setVisibility(View.VISIBLE);
                binding.adminClientesLista.setVisibility(View.GONE);
                binding.adminClientesVacio.setVisibility(View.GONE);
                binding.adminClientesError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                List<ClienteDeHotel> clientes = estado.getData();
                if (clientes == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no dejar la pantalla en blanco sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminClientesEsqueleto.setVisibility(View.GONE);
                binding.adminClientesVacio.setVisibility(View.GONE);
                binding.adminClientesError.setVisibility(View.GONE);
                binding.adminClientesLista.setVisibility(View.VISIBLE);
                adaptador.submitList(clientes);
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

    private void pintarVacio() {
        binding.adminClientesEsqueleto.setVisibility(View.GONE);
        binding.adminClientesLista.setVisibility(View.GONE);
        binding.adminClientesError.setVisibility(View.GONE);
        binding.adminClientesVacio.setVisibility(View.VISIBLE);
        binding.adminClientesVacio.conIcono(R.drawable.ic_person_group);
        binding.adminClientesVacio.conTitulo(R.string.admin_clientes_vacio_titulo);
        binding.adminClientesVacio.conMensaje(R.string.admin_clientes_vacio);
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminClientesEsqueleto.setVisibility(View.GONE);
        binding.adminClientesLista.setVisibility(View.GONE);
        binding.adminClientesVacio.setVisibility(View.GONE);
        binding.adminClientesError.setVisibility(View.VISIBLE);
        binding.adminClientesError.conReintento(mensaje, v -> viewModel.recargar());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.adminClientesLista.setAdapter(null);
        binding = null;
    }
}
