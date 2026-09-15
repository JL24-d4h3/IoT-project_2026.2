package org.iot.project.ui.client.payment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentPaymentBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Card;
import org.iot.project.models.Hotel;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pago de una reserva (RF-045).
 *
 * <p>El cobro es simulado y la pantalla no pide ningún dato de tarjeta: solo
 * elige entre las que el usuario ya tiene guardadas —de las que se conoce la
 * marca y los últimos cuatro dígitos (RC-011)— o pagar en recepción. Pedir el
 * número completo para luego no guardarlo sería un riesgo sin ninguna
 * contrapartida.
 */
public class PaymentFragment extends Fragment {

    private FragmentPaymentBinding binding;
    private PaymentViewModel viewModel;
    private boolean pagando;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPaymentBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.titulo_pago);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());
        binding.pagoConfirmar.setOnClickListener(v -> pagar());
        binding.pagoVerReserva.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        viewModel = new ViewModelProvider(this).get(PaymentViewModel.class);
        viewModel.getDatos().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getPago().observe(getViewLifecycleOwner(), this::pintarPago);

        String bookingId = getArguments() != null
                ? getArguments().getString("bookingId") : null;
        if (bookingId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    private void pintar(@NonNull UiState<PaymentViewModel.Datos> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.pagoFormulario.setVisibility(View.GONE);
                binding.pagoError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.pagoError.setVisibility(View.GONE);
                binding.pagoFormulario.setVisibility(View.VISIBLE);
                mostrar(estado.requireData());
                break;
            default:
                binding.pagoFormulario.setVisibility(View.GONE);
                binding.pagoError.setVisibility(View.VISIBLE);
                binding.pagoError.conReintento(estado.getMessage(), v -> viewModel.recargar());
                break;
        }
    }

    private void mostrar(@NonNull PaymentViewModel.Datos datos) {
        Booking reserva = datos.reserva;
        Hotel hotel = ServiceLocator.hoteles().hotel(reserva.getHotelId());
        binding.pagoReserva.setText(getString(R.string.detalle_reserva_codigo,
                hotel != null ? hotel.getNombre() : reserva.getHotelId(),
                reserva.getCodigo()));

        pintarDesglose(reserva);
        pintarMetodos(datos.tarjetas);

        binding.pagoConfirmar.setText(getString(R.string.pago_confirmar,
                PriceFormatter.format(reserva.getTotal())));
        binding.pagoConfirmar.setEnabled(!pagando);
    }

    private void pintarDesglose(@NonNull Booking reserva) {
        List<PriceBreakdownView.Linea> lineas = new ArrayList<>();
        String precioNoche = PriceFormatter.format(reserva.getPrecioNoche());
        lineas.add(new PriceBreakdownView.Linea(
                reserva.getNumNoches() == 1
                        ? getString(R.string.detalle_linea_alojamiento_una, precioNoche)
                        : getString(R.string.detalle_linea_alojamiento,
                        reserva.getNumNoches(), precioNoche), reserva.getSubtotalAlojamiento()));
        lineas.add(new PriceBreakdownView.Linea(getString(R.string.detalle_linea_servicios),
                reserva.getSubtotalServiciosAdicionales()));
        lineas.add(new PriceBreakdownView.Linea(getString(R.string.detalle_linea_cargos),
                reserva.getSubtotalCargos()));
        binding.pagoDesglose.setLineas(lineas);
        binding.pagoDesglose.setTotal(getString(R.string.reserva_total), reserva.getTotal());
    }

    /**
     * Arma las opciones de pago.
     *
     * <p>Cada tarjeta guardada es una opción y "pagar en recepción" siempre está
     * presente. Esa última es también la salida cuando el usuario no tiene
     * tarjetas: nunca se queda sin poder pagar.
     */
    private void pintarMetodos(@NonNull List<Card> tarjetas) {
        RadioGroup grupo = binding.pagoMetodos;
        grupo.removeAllViews();

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (Card tarjeta : tarjetas) {
            MaterialRadioButton opcion = (MaterialRadioButton) inflador.inflate(
                    R.layout.item_payment_method, grupo, false);
            opcion.setId(View.generateViewId());
            opcion.setText(getString(R.string.pago_metodo_tarjeta,
                    tarjeta.getDisplayName(), tarjeta.getExpiracion()));
            opcion.setTag(tarjeta.getDisplayName());
            grupo.addView(opcion);
        }

        MaterialRadioButton recepcion = (MaterialRadioButton) inflador.inflate(
                R.layout.item_payment_method, grupo, false);
        recepcion.setId(View.generateViewId());
        recepcion.setText(R.string.pago_metodo_recepcion);
        recepcion.setTag(getString(R.string.pago_metodo_recepcion));
        grupo.addView(recepcion);

        // La primera opción marcada: sin selección, el botón de pagar no sabría
        // qué registrar.
        grupo.check(grupo.getChildAt(0).getId());
    }

    private void pagar() {
        RadioGroup grupo = binding.pagoMetodos;
        if (grupo.getCheckedRadioButtonId() == View.NO_ID) {
            return;
        }
        View elegido = grupo.findViewById(grupo.getCheckedRadioButtonId());
        Object etiqueta = elegido != null ? elegido.getTag() : null;
        if (!(etiqueta instanceof String)) {
            return;
        }
        pagando = true;
        binding.pagoConfirmar.setEnabled(false);
        viewModel.pagar((String) etiqueta);
    }

    private void pintarPago(@Nullable UiState<Booking> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        pagando = false;
        binding.pagoConfirmar.setEnabled(true);

        if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.limpiarPago();
            return;
        }

        Booking reserva = estado.requireData();
        binding.pagoExitoMensaje.setText(getString(R.string.pago_confirmado_mensaje,
                reserva.getCodigo(),
                DateFormatter.fechaLarga(reserva.getFechaEntrada())));
        binding.pagoFormulario.setVisibility(View.GONE);
        binding.pagoExito.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
