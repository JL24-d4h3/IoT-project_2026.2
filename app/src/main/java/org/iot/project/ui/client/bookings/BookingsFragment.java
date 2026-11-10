package org.iot.project.ui.client.bookings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentBookingsBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Role;
import org.iot.project.ui.common.BookingAdapter;
import org.iot.project.utils.InsetUtils;

import java.util.Collections;
import java.util.List;

/** Mis reservas: búsqueda, estado y orden sobre un único listado del cliente. */
public class BookingsFragment extends Fragment {
    private FragmentBookingsBinding binding;
    private BookingsViewModel viewModel;
    private BookingAdapter adaptador;
    private boolean pintandoControles;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflador,
            @Nullable ViewGroup contenedor, @Nullable Bundle savedInstanceState) {
        binding = FragmentBookingsBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        binding.header.setTitulo(R.string.nav_reservas);
        binding.header.mostrarAccion(R.drawable.ic_notifications, R.string.cd_notificaciones,
                v -> Navigation.findNavController(v).navigate(R.id.notificationsFragment));

        adaptador = new BookingAdapter(this::abrirReserva, this::abrirChat);
        binding.reservasLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.reservasLista.setAdapter(adaptador);
        viewModel = new ViewModelProvider(this).get(BookingsViewModel.class);

        // SavedStateHandle conserva los valores por cuenta; la restauración del árbol
        // de vistas no debe recuperar filtros de una sesión anterior sobre ese estado.
        binding.reservasControles.setSaveFromParentEnabled(false);
        sincronizarControles();
        binding.reservasBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable texto) {
                if (pintandoControles) return;
                viewModel.buscar(texto.toString());
                volverAlInicio();
            }
        });
        binding.reservasOrden.setOnCheckedStateChangeListener((grupo, ids) -> {
            if (pintandoControles || ids.isEmpty()) return;
            viewModel.ordenarPor(ids.get(0) == R.id.reservas_orden_nombre
                    ? ConsultaReservasCliente.Orden.NOMBRE : ConsultaReservasCliente.Orden.FECHA_SALIDA);
            volverAlInicio();
        });
        binding.reservasSentido.setOnCheckedStateChangeListener((grupo, ids) -> {
            if (pintandoControles || ids.isEmpty()) return;
            viewModel.cambiarSentido(ids.get(0) == R.id.reservas_sentido_ascendente);
            volverAlInicio();
        });
        binding.reservasEstados.setOnCheckedStateChangeListener((grupo, ids) -> {
            if (pintandoControles || ids.isEmpty()) return;
            viewModel.filtrarEstado(estadoDelChip(ids.get(0)));
            volverAlInicio();
        });
        viewModel.getReservas().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refrescarEnSilencio();
    }

    @Override public void onPause() {
        if (viewModel != null) viewModel.suspender();
        super.onPause();
    }

    private void sincronizarControles() {
        pintandoControles = true;
        try {
            String busqueda = viewModel.getBusqueda();
            if (!busqueda.contentEquals(binding.reservasBuscar.getText())) {
                binding.reservasBuscar.setText(busqueda);
                binding.reservasBuscar.setSelection(busqueda.length());
            }
            binding.reservasOrden.check(viewModel.getOrden() == ConsultaReservasCliente.Orden.NOMBRE
                    ? R.id.reservas_orden_nombre : R.id.reservas_orden_salida);
            binding.reservasSentido.check(viewModel.isAscendente()
                    ? R.id.reservas_sentido_ascendente : R.id.reservas_sentido_descendente);
            binding.reservasEstados.check(chipDelEstado(viewModel.getEstadoFiltrado()));
        } finally {
            pintandoControles = false;
        }
    }

    private void pintar(@NonNull UiState<List<Booking>> estado) {
        if (binding == null) return;
        sincronizarControles();
        binding.reservasEsqueleto.setVisibility(estado.isLoading() ? View.VISIBLE : View.GONE);
        binding.reservasLista.setVisibility(estado.isSuccess() ? View.VISIBLE : View.GONE);
        binding.reservasVacio.setVisibility(estado.isEmpty() ? View.VISIBLE : View.GONE);
        binding.reservasError.setVisibility(estado.isError() ? View.VISIBLE : View.GONE);
        if (estado.isSuccess()) adaptador.submitList(estado.requireData());
        else if (estado.isEmpty()) {
            adaptador.submitList(Collections.emptyList());
            boolean hayReservas = viewModel.tieneReservas();
            binding.reservasVacio.conIcono(hayReservas ? R.drawable.ic_search_off : R.drawable.ic_calendar);
            binding.reservasVacio.conTitulo(hayReservas
                    ? R.string.reservas_listado_sin_resultados_titulo : R.string.reservas_vacio_titulo);
            binding.reservasVacio.conMensaje(hayReservas
                    ? R.string.reservas_listado_sin_resultados : R.string.reservas_vacio);
            binding.reservasVacio.conAccion(R.string.reservas_listado_limpiar,
                    hayReservas ? v -> { viewModel.limpiarFiltros(); volverAlInicio(); } : null);
        } else if (estado.isError()) {
            adaptador.submitList(Collections.emptyList());
            binding.reservasError.conReintento(estado.getMessage(), v -> viewModel.recargar());
        } else adaptador.submitList(Collections.emptyList());
    }

    private void volverAlInicio() {
        if (binding != null) binding.reservasLista.scrollToPosition(0);
    }

    @Nullable private BookingStatus estadoDelChip(int id) {
        if (id == R.id.reservas_estado_pendiente) return BookingStatus.PENDIENTE;
        if (id == R.id.reservas_estado_confirmada) return BookingStatus.CONFIRMADA;
        if (id == R.id.reservas_estado_activa) return BookingStatus.ACTIVA;
        if (id == R.id.reservas_estado_finalizada) return BookingStatus.FINALIZADA;
        if (id == R.id.reservas_estado_cancelada) return BookingStatus.CANCELADA;
        return null;
    }

    private int chipDelEstado(@Nullable BookingStatus estado) {
        if (estado == null) return R.id.reservas_estado_todos;
        switch (estado) {
            case PENDIENTE: return R.id.reservas_estado_pendiente;
            case CONFIRMADA: return R.id.reservas_estado_confirmada;
            case ACTIVA: return R.id.reservas_estado_activa;
            case FINALIZADA: return R.id.reservas_estado_finalizada;
            case CANCELADA: return R.id.reservas_estado_cancelada;
            default: return R.id.reservas_estado_todos;
        }
    }

    private void abrirReserva(@NonNull Booking reserva) { abrir(reserva, R.id.bookingDetailFragment); }

    private void abrirChat(@NonNull Booking reserva) {
        if (reserva.permiteChat()) abrir(reserva, R.id.chatFragment);
    }

    private void abrir(@NonNull Booking reserva, int destino) {
        if (binding == null || SessionManager.getRolActivo() != Role.CLIENTE
                || !reserva.getClienteId().equals(SessionManager.getUsuarioId())) return;
        NavController navegador = Navigation.findNavController(binding.getRoot());
        if (navegador.getCurrentDestination() == null
                || navegador.getCurrentDestination().getId() != R.id.bookingsFragment) return;
        Bundle argumentos = new Bundle();
        argumentos.putString("bookingId", reserva.getId());
        navegador.navigate(destino, argumentos);
    }

    @Override public void onDestroyView() {
        binding.reservasLista.setAdapter(null);
        adaptador = null;
        super.onDestroyView();
        binding = null;
    }
}
