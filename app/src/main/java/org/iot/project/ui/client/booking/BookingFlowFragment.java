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
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentBookingFlowBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.Room;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Locale;

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
        binding.reservaContinuarPago.setOnClickListener(v -> abrirPagoOReserva());
        binding.reservaDisponibilidad.setOnClickListener(v -> viewModel.reintentarDisponibilidad());
        binding.reservaExitoAviso.setText(R.string.reserva_firebase_aviso);

        observar();

        Bundle argumentos = getArguments();
        String hotelId = argumentos != null ? argumentos.getString("hotelId") : null;
        String roomId = argumentos != null ? argumentos.getString("roomId") : null;
        if (hotelId == null || roomId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(hotelId, roomId,
                fechaInicial(argumentos.getString("fechaEntrada")),
                fechaInicial(argumentos.getString("fechaSalida")));
    }

    @Nullable private LocalDate fechaInicial(@Nullable String valor) {
        if (valor == null || valor.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.refrescarReservaCreada();
    }

    private void observar() {
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintarHotel);
        viewModel.getHabitacion().observe(getViewLifecycleOwner(), this::pintarHabitacion);
        viewModel.getEntrada().observe(getViewLifecycleOwner(), f -> pintarFechas());
        viewModel.getSalida().observe(getViewLifecycleOwner(), f -> pintarFechas());
        viewModel.getHuespedes().observe(getViewLifecycleOwner(), n -> pintarHuespedes());
        viewModel.getCreacion().observe(getViewLifecycleOwner(), this::pintarCreacion);
        viewModel.getDisponibilidad().observe(getViewLifecycleOwner(), this::pintarDisponibilidad);
    }

    // ----------------------------------------------------------- Formulario

    private void pintarHotel(@NonNull UiState<Hotel> estado) {
        if (estado.isSuccess()) {
            binding.reservaHotel.setText(estado.requireData().getNombre());
        } else if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accion_reintentar, v -> viewModel.reintentarCarga())
                    .show();
        }
        actualizarBoton();
    }

    private void pintarHabitacion(@Nullable Room room) {
        if (room == null) {
            actualizarBoton();
            return;
        }
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
        pintarDisponibilidad(viewModel.getDisponibilidad().getValue());
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
        boolean cargando = viewModel.estaConfirmando();
        binding.reservaConfirmar.setEnabled(viewModel.puedeConfirmar());
        binding.reservaConfirmar.setText(cargando
                ? R.string.reserva_firebase_guardando : viewModel.esReintentoMismoBorrador()
                ? R.string.reserva_firebase_reintentar : R.string.reserva_confirmar);
        binding.reservaContinuarPago.setEnabled(!cargando);
        binding.reservaCampoEntrada.setEnabled(!cargando);
        binding.reservaCampoSalida.setEnabled(!cargando);
        Room room = viewModel.getHabitacion().getValue();
        binding.reservaMas.setEnabled(!cargando && room != null
                && viewModel.getHuespedesActuales() < room.getCapacidadTotal());
        binding.reservaMenos.setEnabled(!cargando && viewModel.getHuespedesActuales() > 1);
    }

    private void pintarDisponibilidad(@Nullable UiState<Boolean> estado) {
        boolean error = estado != null && estado.isError();
        if (viewModel.esReintentoMismoBorrador()) {
            binding.reservaDisponibilidad.setText(R.string.reserva_firebase_recuperar_intento);
        } else if (estado == null || estado.isLoading()) {
            binding.reservaDisponibilidad.setText(R.string.reserva_firebase_consultando);
        } else if (error) {
            binding.reservaDisponibilidad.setText(getString(
                    R.string.reserva_firebase_disponibilidad_error, estado.getMessage()));
        } else {
            binding.reservaDisponibilidad.setText(estado.isSuccess() && estado.requireData()
                    ? R.string.reserva_firebase_disponible : R.string.reserva_firebase_ocupada);
        }
        binding.reservaDisponibilidad.setEnabled(!viewModel.estaConfirmando());
        binding.reservaDisponibilidad.setClickable(error && !viewModel.estaConfirmando());
        binding.reservaDisponibilidad.setFocusable(error && !viewModel.estaConfirmando());
        actualizarBoton();
    }

    private void elegirFechas() {
        if (viewModel.estaConfirmando()
                || getParentFragmentManager().isStateSaved()
                || getParentFragmentManager().findFragmentByTag("selector_fechas") != null) return;
        LocalDate desde = viewModel.getEntrada().getValue();
        LocalDate hasta = viewModel.getSalida().getValue();

        MaterialDatePicker.Builder<Pair<Long, Long>> constructor =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.reserva_fechas_titulo)
                        .setCalendarConstraints(new CalendarConstraints.Builder()
                                .setValidator(DateValidatorPointForward.from(LocalDate.now()
                                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
                                .build());
        if (desde != null && hasta != null && !desde.isBefore(LocalDate.now())
                && hasta.isAfter(desde)) {
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
        viewModel.confirmar();
    }

    private void pintarCreacion(@Nullable UiState<Booking> estado) {
        pintarDisponibilidad(viewModel.getDisponibilidad().getValue());
        if (estado == null) {
            return;
        }
        if (estado.isLoading()) {
            return;
        }
        if (estado.isError()) {
            // El repositorio es quien rechaza el cruce de fechas (RF-032, §22):
            // su mensaje explica el motivo mejor que uno genérico de pantalla.
            if (viewModel.puedeRecuperarCreada()) {
                Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.accion_reintentar, v -> viewModel.reintentarCarga())
                        .show();
            } else {
                avisar(estado.getMessage());
            }
            viewModel.limpiarCreacion();
            return;
        }
        mostrarExito(estado.requireData());
    }

    private void mostrarExito(@NonNull Booking reserva) {
        Hotel hotel = viewModel.getHotel().getValue() != null
                ? viewModel.getHotel().getValue().getData() : null;

        binding.reservaExitoMensaje.setText(getString(R.string.reserva_creada_mensaje,
                reserva.getCodigo(),
                hotel != null ? hotel.getNombre() : reserva.getHotelNombre(),
                DateFormatter.rangoConNoches(reserva.getFechaEntrada(),
                        reserva.getFechaSalida(), reserva.getNumNoches())));
        binding.reservaFormulario.setVisibility(View.GONE);
        binding.reservaExito.setVisibility(View.VISIBLE);
        binding.reservaExitoAviso.setText(reserva.getEstado() == BookingStatus.PENDIENTE
                ? getString(R.string.reserva_firebase_aviso)
                : getString(R.string.reserva_firebase_estado_actual,
                reserva.getEstado().getDisplayName().toLowerCase(Locale.getDefault())));
        binding.reservaContinuarPago.setText(reserva.getEstado() == BookingStatus.PENDIENTE
                ? R.string.reserva_firebase_continuar_pago : R.string.accion_ver_reserva);
    }

    private void abrirPagoOReserva() {
        if (Navigation.findNavController(requireView()).getCurrentDestination() == null
                || Navigation.findNavController(requireView()).getCurrentDestination().getId()
                != R.id.bookingFlowFragment) return;
        UiState<Booking> estado = viewModel.getCreacion().getValue();
        if (estado == null || !estado.isSuccess()) return;
        Booking reserva = estado.requireData();
        Bundle argumentos = new Bundle();
        argumentos.putString("bookingId", reserva.getId());
        Navigation.findNavController(requireView()).navigate(
                reserva.getEstado() == BookingStatus.PENDIENTE
                        ? R.id.paymentFragment : R.id.bookingDetailFragment, argumentos);
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
