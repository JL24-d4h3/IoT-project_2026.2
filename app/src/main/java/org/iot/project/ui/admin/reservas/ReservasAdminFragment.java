package org.iot.project.ui.admin.reservas;

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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminReservasBinding;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Reservas del hotel (§44, RF-041).
 *
 * <p>La pantalla es de consulta: se entra a mirar quién llega y quién está
 * dentro, y lo que se hace con una reserva —cerrar la estadía, cargar un
 * consumo, escribir al huésped— se hace en su detalle, donde están los datos
 * que hacen falta para decidirlo.
 */
public class ReservasAdminFragment extends Fragment {

    /** Nombre del argumento declarado en el grafo para el detalle de reserva. */
    static final String ARG_BOOKING_ID = "bookingId";

    private FragmentAdminReservasBinding binding;
    private ReservasAdminViewModel viewModel;

    // Un adaptador por bloque: un RecyclerView acepta uno solo, así que
    // compartir instancia entre las tres listas las llenaría con lo mismo.
    private ReservaAdminAdapter adaptadorEnCurso;
    private ReservaAdminAdapter adaptadorProximas;
    private ReservaAdminAdapter adaptadorHistorial;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminReservasBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminReservasHeader);
        binding.adminReservasHeader.setTitulo(R.string.nav_reservas_admin);

        adaptadorEnCurso = new ReservaAdminAdapter(this::abrirDetalle);
        adaptadorProximas = new ReservaAdminAdapter(this::abrirDetalle);
        adaptadorHistorial = new ReservaAdminAdapter(this::abrirDetalle);
        prepararLista(binding.adminReservasCurso, adaptadorEnCurso);
        prepararLista(binding.adminReservasProximas, adaptadorProximas);
        prepararLista(binding.adminReservasHistorial, adaptadorHistorial);

        viewModel = new ViewModelProvider(this).get(ReservasAdminViewModel.class);
        viewModel.getSecciones().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver del detalle se pudo haber cerrado la estadía: la reserva
        // cambia de bloque y esta lista tiene que reflejarlo. Se refresca sin
        // esqueleto, porque la pantalla ya está pintada.
        viewModel.refrescarEnSilencio();
    }

    private void prepararLista(@NonNull RecyclerView lista,
                               @NonNull ReservaAdminAdapter adaptador) {
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adaptador);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ReservasAdminViewModel.Secciones> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminReservasEsqueleto.setVisibility(View.VISIBLE);
                binding.adminReservasContenido.setVisibility(View.GONE);
                binding.adminReservasVacio.setVisibility(View.GONE);
                binding.adminReservasError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                ReservasAdminViewModel.Secciones secciones = estado.getData();
                if (secciones == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminReservasEsqueleto.setVisibility(View.GONE);
                binding.adminReservasVacio.setVisibility(View.GONE);
                binding.adminReservasError.setVisibility(View.GONE);
                binding.adminReservasContenido.setVisibility(View.VISIBLE);
                mostrar(secciones);
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

    private void mostrar(@NonNull ReservasAdminViewModel.Secciones secciones) {
        adaptadorEnCurso.submitList(secciones.enCurso);
        adaptadorProximas.submitList(secciones.proximas);
        adaptadorHistorial.submitList(secciones.historial);

        // El bloque entero desaparece y no solo su lista: un encabezado sin
        // filas debajo se lee como un fallo de carga, y que no haya nadie
        // alojado a las once de la mañana es lo normal.
        pintarSeccion(binding.adminReservasSeccionCurso, secciones.enCurso);
        pintarSeccion(binding.adminReservasSeccionProximas, secciones.proximas);
        pintarSeccion(binding.adminReservasSeccionHistorial, secciones.historial);
    }

    private void pintarSeccion(@NonNull View bloque, @NonNull List<ReservaDeHotel> reservas) {
        bloque.setVisibility(reservas.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void pintarVacio() {
        binding.adminReservasEsqueleto.setVisibility(View.GONE);
        binding.adminReservasContenido.setVisibility(View.GONE);
        binding.adminReservasError.setVisibility(View.GONE);
        binding.adminReservasVacio.setVisibility(View.VISIBLE);
        binding.adminReservasVacio.conIcono(R.drawable.ic_receipt);
        binding.adminReservasVacio.conTitulo(R.string.admin_reservas_vacio_titulo);
        binding.adminReservasVacio.conMensaje(R.string.admin_reservas_vacio);
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminReservasEsqueleto.setVisibility(View.GONE);
        binding.adminReservasContenido.setVisibility(View.GONE);
        binding.adminReservasVacio.setVisibility(View.GONE);
        binding.adminReservasError.setVisibility(View.VISIBLE);
        binding.adminReservasError.conReintento(mensaje, v -> viewModel.recargar());
    }

    private void pintarOperacion(@Nullable UiState<ReservaDeHotel> estado) {
        if (estado == null || !estado.isError()) {
            return;
        }
        Snackbar.make(binding.getRoot(),
                estado.getMessage() != null
                        ? estado.getMessage() : getString(R.string.estado_error_descripcion),
                Snackbar.LENGTH_LONG).show();
        viewModel.limpiarOperacion();
    }

    // ------------------------------------------------------------------ Navegación

    private void abrirDetalle(@NonNull ReservaDeHotel reserva) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_BOOKING_ID, reserva.getReserva().getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.adminReservaDetalleFragment, argumentos);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
