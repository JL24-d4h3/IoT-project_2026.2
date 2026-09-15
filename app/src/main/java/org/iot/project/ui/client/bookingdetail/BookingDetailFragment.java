package org.iot.project.ui.client.bookingdetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentBookingDetailBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Card;
import org.iot.project.models.Charge;
import org.iot.project.models.Room;
import org.iot.project.ui.components.ChargeListView;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Detalle de una reserva (§31, §32).
 *
 * <p>Los botones no se deshabilitan según el estado: se esconden. Un botón
 * apagado obliga a preguntarse por qué; uno que no está, no promete nada. Las
 * reglas de qué se puede hacer viven en BookingStatus (allowsCancellation,
 * allowsChat, allowsCheckout) y aquí solo se consultan.
 */
public class BookingDetailFragment extends Fragment {

    private FragmentBookingDetailBinding binding;
    private BookingDetailViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBookingDetailBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        InsetUtils.applyBottomPadding(binding.detalleAcciones);

        binding.header.setTitulo(R.string.titulo_detalle_reserva);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        viewModel = new ViewModelProvider(this).get(BookingDetailViewModel.class);
        viewModel.getReserva().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);

        String bookingId = getArguments() != null
                ? getArguments().getString("bookingId") : null;
        if (bookingId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<Booking> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.detalleEsqueleto.setVisibility(View.VISIBLE);
                binding.detalleContenido.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.detalleEsqueleto.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.GONE);
                binding.detalleContenido.setVisibility(View.VISIBLE);
                mostrar(estado.requireData());
                break;
            default:
                binding.detalleEsqueleto.setVisibility(View.GONE);
                binding.detalleContenido.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.VISIBLE);
                binding.detalleError.conReintento(estado.getMessage(),
                        v -> viewModel.recargar());
                break;
        }
    }

    private void mostrar(@NonNull Booking reserva) {
        binding.detalleTarjeta.bind(reserva);

        binding.detalleHabitacion.setText(nombreHabitacion(reserva));
        binding.detalleFechas.setText(DateFormatter.rangoConNoches(
                reserva.getFechaEntrada(), reserva.getFechaSalida(), reserva.getNumNoches()));
        binding.detalleHuespedes.setText(reserva.getNumHuespedes() == 1
                ? getString(R.string.detalle_huesped)
                : getString(R.string.detalle_huespedes, reserva.getNumHuespedes()));

        pintarCargos(reserva);
        pintarDesglose(reserva);
        pintarValoracion(reserva);
        pintarAcciones(reserva);
    }

    /** El nombre del cuarto vive en el hotel; la reserva solo guarda el id. */
    private String nombreHabitacion(@NonNull Booking reserva) {
        org.iot.project.models.Hotel hotel =
                ServiceLocator.hoteles().hotel(reserva.getHotelId());
        if (hotel == null) {
            return getString(R.string.reserva_hotel_desconocido);
        }
        for (Room room : hotel.getHabitaciones()) {
            if (room.getId().equals(reserva.getRoomId())) {
                return room.getTipo();
            }
        }
        return hotel.getNombre();
    }

    private void pintarCargos(@NonNull Booking reserva) {
        List<Charge> cargos = reserva.getCargos();
        binding.detalleCargosSeccion.setVisibility(
                ChargeListView.hayCargos(cargos) ? View.VISIBLE : View.GONE);
        binding.detalleCargos.setCargos(cargos);
    }

    private void pintarDesglose(@NonNull Booking reserva) {
        List<PriceBreakdownView.Linea> lineas = new ArrayList<>();
        String precioNoche = PriceFormatter.format(reserva.getPrecioNoche());
        lineas.add(new PriceBreakdownView.Linea(
                reserva.getNumNoches() == 1
                        ? getString(R.string.detalle_linea_alojamiento_una, precioNoche)
                        : getString(R.string.detalle_linea_alojamiento,
                        reserva.getNumNoches(), precioNoche),
                reserva.getSubtotalAlojamiento()));
        lineas.add(new PriceBreakdownView.Linea(
                getString(R.string.detalle_linea_servicios),
                reserva.getSubtotalServiciosAdicionales()));
        lineas.add(new PriceBreakdownView.Linea(
                getString(R.string.detalle_linea_cargos),
                reserva.getSubtotalCargos()));

        binding.detalleDesglose.setLineas(lineas);
        binding.detalleDesglose.setTotal(getString(R.string.reserva_total), reserva.getTotal());

        Card tarjeta = reserva.getTarjeta();
        boolean hayTarjeta = tarjeta != null;
        binding.detalleTarjetaPago.setVisibility(hayTarjeta ? View.VISIBLE : View.GONE);
        if (hayTarjeta) {
            binding.detalleTarjetaPago.setText(
                    getString(R.string.detalle_pagado_con, tarjeta.getDisplayName()));
        }
    }

    private void pintarValoracion(@NonNull Booking reserva) {
        boolean hayValoracion = reserva.getValoracion() != null;
        binding.detalleValoracionSeccion.setVisibility(hayValoracion ? View.VISIBLE : View.GONE);
        if (hayValoracion) {
            binding.detalleValoracionRating.setValorEntero(
                    Math.round(reserva.getValoracion().getRating()));
            binding.detalleValoracionComentario.setText(
                    reserva.getValoracion().getComentario());
        }
    }

    private void pintarAcciones(@NonNull Booking reserva) {
        MaterialButton principal = binding.accionPrincipal;
        MaterialButton secundaria = binding.accionSecundaria;
        MaterialButton cancelar = binding.accionCancelar;

        principal.setVisibility(View.GONE);
        secundaria.setVisibility(View.GONE);
        cancelar.setVisibility(View.GONE);

        switch (reserva.getEstado()) {
            case PENDIENTE:
                mostrarBoton(principal, R.string.accion_pagar,
                        v -> abrir(R.id.paymentFragment, reserva));
                break;
            case ACTIVA:
                mostrarBoton(principal, R.string.accion_checkout,
                        v -> abrir(R.id.checkoutFragment, reserva));
                mostrarBoton(secundaria, R.string.accion_hablar_hotel,
                        v -> abrir(R.id.chatFragment, reserva));
                break;
            case FINALIZADA:
                if (reserva.getValoracion() == null) {
                    mostrarBoton(principal, R.string.accion_valorar,
                            v -> abrir(R.id.reviewFragment, reserva));
                }
                break;
            case CONFIRMADA:
            case CANCELADA:
            default:
                break;
        }

        if (reserva.getEstado().allowsCancellation()) {
            cancelar.setVisibility(View.VISIBLE);
            cancelar.setOnClickListener(v -> confirmarCancelacion(reserva));
        }
    }

    /** Muestra un botón de acción. Si no tiene nada que hacer, no se llama. */
    private void mostrarBoton(@NonNull MaterialButton boton, int texto,
                              @NonNull View.OnClickListener oyente) {
        boton.setText(texto);
        boton.setVisibility(View.VISIBLE);
        boton.setOnClickListener(oyente);
    }

    private void abrir(int destino, @NonNull Booking reserva) {
        Bundle argumentos = new Bundle();
        argumentos.putString("bookingId", reserva.getId());
        Navigation.findNavController(requireView()).navigate(destino, argumentos);
    }

    // --------------------------------------------------------------- Cancelar

    /**
     * Cancelar se pregunta antes de hacerlo.
     *
     * <p>Libera la habitación y no se deshace (RC-013). Es la única acción de la
     * pantalla con consecuencias fuera de la app, así que no puede depender de
     * un toque accidental.
     */
    private void confirmarCancelacion(@NonNull Booking reserva) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirmar_cancelar_titulo)
                .setMessage(R.string.confirmar_cancelar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.accion_cancelar_reserva,
                        (dialogo, cual) -> viewModel.cancelar())
                .show();
    }

    private void pintarOperacion(@Nullable UiState<Booking> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(binding.getRoot(), R.string.cancelada_aviso, Snackbar.LENGTH_LONG)
                    .show();
        }
        viewModel.limpiarOperacion();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
