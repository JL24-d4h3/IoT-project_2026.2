package org.iot.project.ui.client.bookings;

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

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentBookingsBinding;
import org.iot.project.models.Booking;
import org.iot.project.ui.common.BookingAdapter;
import org.iot.project.utils.InsetUtils;

/**
 * Mis reservas (§30 a §32). Prioridad 6 de §79.
 *
 * <p>Tres bloques —en curso, próximas e historial— porque son tres preguntas
 * distintas. Un bloque sin nada que mostrar desaparece, en vez de dejar un
 * título suelto.
 */
public class BookingsFragment extends Fragment {

    private FragmentBookingsBinding binding;
    private BookingsViewModel viewModel;

    // Un adaptador por bloque: un RecyclerView acepta uno solo, así que
    // compartir instancia entre las tres listas las llenaría con lo mismo.
    private BookingAdapter adaptadorEnCurso;
    private BookingAdapter adaptadorProximas;
    private BookingAdapter adaptadorHistorial;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBookingsBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.nav_reservas);
        binding.header.mostrarAccion(R.drawable.ic_notifications, R.string.cd_notificaciones,
                v -> Navigation.findNavController(v).navigate(R.id.notificationsFragment));

        adaptadorEnCurso = new BookingAdapter(this::abrirReserva);
        adaptadorProximas = new BookingAdapter(this::abrirReserva);
        adaptadorHistorial = new BookingAdapter(this::abrirReserva);
        prepararLista(binding.reservasEnCurso, adaptadorEnCurso);
        prepararLista(binding.reservasProximas, adaptadorProximas);
        prepararLista(binding.reservasHistorial, adaptadorHistorial);

        viewModel = new ViewModelProvider(this).get(BookingsViewModel.class);
        viewModel.getSecciones().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Al volver de crear una reserva la lista tiene que reflejarla. Se
        // refresca sin esqueleto, porque la pantalla ya está pintada.
        if (viewModel != null) {
            viewModel.refrescarEnSilencio();
        }
    }

    private void prepararLista(@NonNull RecyclerView lista, @NonNull BookingAdapter adaptador) {
        lista.setLayoutManager(new LinearLayoutManager(getContext()));
        lista.setAdapter(adaptador);
    }

    private void pintar(@NonNull UiState<BookingsViewModel.Secciones> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.reservasEsqueleto.setVisibility(View.VISIBLE);
                binding.reservasContenido.setVisibility(View.GONE);
                binding.reservasVacio.setVisibility(View.GONE);
                binding.reservasError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.reservasEsqueleto.setVisibility(View.GONE);
                binding.reservasVacio.setVisibility(View.GONE);
                binding.reservasError.setVisibility(View.GONE);
                binding.reservasContenido.setVisibility(View.VISIBLE);
                mostrar(estado.requireData());
                break;
            case EMPTY:
                binding.reservasEsqueleto.setVisibility(View.GONE);
                binding.reservasContenido.setVisibility(View.GONE);
                binding.reservasError.setVisibility(View.GONE);
                binding.reservasVacio.setVisibility(View.VISIBLE);
                binding.reservasVacio.conIcono(R.drawable.ic_calendar);
                binding.reservasVacio.conTitulo(R.string.reservas_vacio_titulo);
                binding.reservasVacio.conMensaje(R.string.reservas_vacio);
                break;
            case ERROR:
            default:
                binding.reservasEsqueleto.setVisibility(View.GONE);
                binding.reservasContenido.setVisibility(View.GONE);
                binding.reservasVacio.setVisibility(View.GONE);
                binding.reservasError.setVisibility(View.VISIBLE);
                binding.reservasError.conReintento(estado.getMessage(),
                        v -> viewModel.recargar());
                break;
        }
    }

    private void mostrar(@NonNull BookingsViewModel.Secciones secciones) {
        adaptadorEnCurso.submitList(secciones.enCurso);
        adaptadorProximas.submitList(secciones.proximas);
        adaptadorHistorial.submitList(secciones.historial);

        binding.reservasSeccionCurso.setVisibility(
                secciones.enCurso.isEmpty() ? View.GONE : View.VISIBLE);
        binding.reservasSeccionProximas.setVisibility(
                secciones.proximas.isEmpty() ? View.GONE : View.VISIBLE);
        binding.reservasSeccionHistorial.setVisibility(
                secciones.historial.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void abrirReserva(@NonNull Booking reserva) {
        Bundle argumentos = new Bundle();
        argumentos.putString("bookingId", reserva.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.bookingDetailFragment, argumentos);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.reservasEnCurso.setAdapter(null);
        binding.reservasProximas.setAdapter(null);
        binding.reservasHistorial.setAdapter(null);
        binding = null;
    }
}
