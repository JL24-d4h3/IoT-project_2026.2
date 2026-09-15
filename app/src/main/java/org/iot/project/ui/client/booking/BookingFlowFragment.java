package org.iot.project.ui.client.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentBookingFlowBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Hotel;
import org.iot.project.models.Room;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;

/**
 * Pantalla de reserva: fechas, huéspedes y confirmación.
 *
 * <p>La confirmación ocurre en la misma pantalla en vez de en una pantalla
 * aparte. El usuario acaba de pulsar "confirmar" y lo que necesita saber es si
 * salió bien y con qué código; mandarlo a otra pantalla para contárselo solo
 * añade un paso que tiene que deshacer.
 */
public class BookingFlowFragment extends Fragment {

    private FragmentBookingFlowBinding binding;
    private BookingFlowViewModel viewModel;
    private boolean confirmando;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBookingFlowBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        InsetUtils.applyBottomPadding(binding.reservaBarra);

        viewModel = new ViewModelProvider(this).get(BookingFlowViewModel.class);

        binding.header.setTitulo(R.string.titulo_reservar);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        binding.reservaCampoEntrada.setOnClickListener(v -> elegirFechas());
        binding.reservaCampoSalida.setOnClickListener(v -> elegirFechas());
        binding.reservaMenos.setOnClickListener(v ->
                viewModel.setHuespedes(viewModel.getHuespedesActuales() - 1));
        binding.reservaMas.setOnClickListener(v ->
                viewModel.setHuespedes(viewModel.getHuespedesActuales() + 1));
        binding.reservaConfirmar.setOnClickListener(v -> confirmar());
        binding.reservaVerLista.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.bookingsFragment));

        observar();

        Bundle argumentos = getArguments();
        String hotelId = argumentos != null ? argumentos.getString("hotelId") : null;
        String roomId = argumentos != null ? argumentos.getString("roomId") : null;
        if (hotelId == null || roomId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(hotelId, roomId);
    }

    private void observar() {
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintarHotel);
        viewModel.getHabitacion().observe(getViewLifecycleOwner(), this::pintarHabitacion);
        viewModel.getEntrada().observe(getViewLifecycleOwner(), f -> pintarFechas());
        viewModel.getSalida().observe(getViewLifecycleOwner(), f -> pintarFechas());
        viewModel.getHuespedes().observe(getViewLifecycleOwner(), n -> pintarHuespedes());
        viewModel.getCreacion().observe(getViewLifecycleOwner(), this::pintarCreacion);
    }

    // ----------------------------------------------------------- Formulario

    private void pintarHotel(@NonNull UiState<Hotel> estado) {
        if (estado.isSuccess()) {
            binding.reservaHotel.setText(estado.requireData().getNombre());
        } else if (estado.isError()) {
            avisar(estado.getMessage());
        }
        actualizarBoton();
    }

    private void pintarHabitacion(@NonNull Room room) {
        binding.reservaHabitacion.setText(getString(R.string.reserva_habitacion,
                room.getTipo(), room.getResumenCapacidad()));
        binding.reservaCapacidad.setText(
                getString(R.string.reserva_capacidad_max, room.getCapacidadTotal()));
        Glide.with(this)
                .load(room.getFotos().isEmpty() ? null : room.getFotos().get(0))
                .placeholder(R.drawable.bg_skeleton)
                .error(R.drawable.ic_search_off)
                .centerCrop()
                .into(binding.reservaFoto);
        pintarHuespedes();
        pintarDesglose();
    }

    private void pintarFechas() {
        LocalDate desde = viewModel.getEntrada().getValue();
        LocalDate hasta = viewModel.getSalida().getValue();
        binding.reservaEntrada.setText(desde != null
                ? DateFormatter.fechaCorta(desde) : getString(R.string.reserva_elegir_fecha));
        binding.reservaSalida.setText(hasta != null
                ? DateFormatter.fechaCorta(hasta) : getString(R.string.reserva_elegir_fecha));
        pintarDesglose();
    }

    private void pintarHuespedes() {
        binding.reservaNumHuespedes.setText(String.valueOf(viewModel.getHuespedesActuales()));
        Room room = viewModel.getHabitacion().getValue();
        // El contador se detiene en la capacidad de la habitación: ofrecer un
        // huésped de más sería prometer algo que el cuarto no admite.
        binding.reservaMas.setEnabled(room == null
                || viewModel.getHuespedesActuales() < room.getCapacidadTotal());
        binding.reservaMenos.setEnabled(viewModel.getHuespedesActuales() > 1);
        pintarDesglose();
    }

    private void pintarDesglose() {
        Room room = viewModel.getHabitacion().getValue();
        long noches = viewModel.getNumNoches();
        if (room == null || noches <= 0) {
            binding.reservaDesglose.setLineas(Collections.emptyList());
            binding.reservaDesglose.setTotal(getString(R.string.reserva_total), 0);
            actualizarBoton();
            return;
        }
        String precio = PriceFormatter.format(room.getPrecioNoche());
        String etiqueta = noches == 1
                ? getString(R.string.reserva_linea_noche, precio)
                : getString(R.string.reserva_linea_noches, noches, precio);

        binding.reservaDesglose.setLineas(Collections.singletonList(
                new PriceBreakdownView.Linea(etiqueta, viewModel.getTotal())));
        binding.reservaDesglose.setTotal(getString(R.string.reserva_total),
                viewModel.getTotal());
        actualizarBoton();
    }

    private void actualizarBoton() {
        binding.reservaConfirmar.setEnabled(viewModel.puedeConfirmar() && !confirmando);
    }

    private void elegirFechas() {
        LocalDate desde = viewModel.getEntrada().getValue();
        LocalDate hasta = viewModel.getSalida().getValue();

        MaterialDatePicker.Builder<Pair<Long, Long>> constructor =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.reserva_fechas_titulo);
        if (desde != null && hasta != null) {
            constructor.setSelection(new Pair<>(
                    desde.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    hasta.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }

        MaterialDatePicker<Pair<Long, Long>> selector = constructor.build();
        selector.addOnPositiveButtonClickListener(seleccion -> {
            if (seleccion == null || seleccion.first == null || seleccion.second == null) {
                return;
            }
            // El selector devuelve milisegundos en UTC; interpretarlos en la zona
            // local desplazaría la fecha un día según dónde esté el usuario.
            LocalDate nuevaEntrada = Instant.ofEpochMilli(seleccion.first)
                    .atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate nuevaSalida = Instant.ofEpochMilli(seleccion.second)
                    .atZone(ZoneOffset.UTC).toLocalDate();
            viewModel.setFechas(nuevaEntrada, nuevaSalida);
        });
        selector.show(getParentFragmentManager(), "selector_fechas");
    }

    // ---------------------------------------------------------- Confirmación

    private void confirmar() {
        confirmando = true;
        actualizarBoton();
        viewModel.confirmar();
    }

    private void pintarCreacion(@Nullable UiState<Booking> estado) {
        if (estado == null) {
            return;
        }
        if (estado.isLoading()) {
            return;
        }
        confirmando = false;
        actualizarBoton();

        if (estado.isError()) {
            // El repositorio es quien rechaza el cruce de fechas (RF-032, §22):
            // su mensaje explica el motivo mejor que uno genérico de pantalla.
            avisar(estado.getMessage());
            return;
        }
        mostrarExito(estado.requireData());
    }

    private void mostrarExito(@NonNull Booking reserva) {
        Hotel hotel = viewModel.getHotel().getValue() != null
                ? viewModel.getHotel().getValue().getData() : null;

        binding.reservaExitoMensaje.setText(getString(R.string.reserva_creada_mensaje,
                reserva.getCodigo(),
                hotel != null ? hotel.getNombre() : "",
                DateFormatter.rangoConNoches(reserva.getFechaEntrada(),
                        reserva.getFechaSalida(), reserva.getNumNoches())));
        binding.reservaFormulario.setVisibility(View.GONE);
        binding.reservaExito.setVisibility(View.VISIBLE);
    }

    private void avisar(@Nullable String mensaje) {
        if (mensaje == null) {
            return;
        }
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
