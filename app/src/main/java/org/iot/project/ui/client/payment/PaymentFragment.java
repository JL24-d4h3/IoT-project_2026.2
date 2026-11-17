package org.iot.project.ui.client.payment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
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

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago de una reserva (RF-045).
 *
 * <p>El cobro es simulado. El usuario elige una tarjeta guardada, escribe los
 * datos de una tarjeta nueva o paga en recepción. Los datos de la tarjeta
 * nueva solo se validan en pantalla (formato, Luhn, vencimiento y CVV) y se
 * descartan: del número únicamente se conserva la marca y los últimos cuatro
 * dígitos (RC-011), y el CVV nunca se guarda.
 */
public class PaymentFragment extends Fragment {

    /** Etiqueta interna de la opción "tarjeta nueva"; no se muestra ni se guarda como método. */
    private static final String TAG_TARJETA_NUEVA = "__tarjeta_nueva__";

    private FragmentPaymentBinding binding;
    private PaymentViewModel viewModel;

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
        binding.pagoVerReserva.setOnClickListener(v -> abrirDetalle());
        prepararCamposTarjeta();

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

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.recargar();
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
                hotel != null ? hotel.getNombre()
                        : !reserva.getHotelNombre().isEmpty() ? reserva.getHotelNombre()
                        : getString(R.string.reserva_hotel_desconocido),
                reserva.getCodigo()));

        pintarDesglose(reserva);
        pintarMetodos(datos.tarjetas);

        binding.pagoConfirmar.setText(getString(R.string.pago_confirmar,
                PriceFormatter.format(reserva.getTotal())));
        actualizarEspera();
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
        String guardado = viewModel.getMetodoElegido();
        grupo.setOnCheckedChangeListener(null);
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

        MaterialRadioButton nueva = (MaterialRadioButton) inflador.inflate(
                R.layout.item_payment_method, grupo, false);
        nueva.setId(View.generateViewId());
        nueva.setText(R.string.pago_metodo_tarjeta_nueva);
        nueva.setTag(TAG_TARJETA_NUEVA);
        grupo.addView(nueva);

        MaterialRadioButton recepcion = (MaterialRadioButton) inflador.inflate(
                R.layout.item_payment_method, grupo, false);
        recepcion.setId(View.generateViewId());
        recepcion.setText(R.string.pago_metodo_recepcion);
        recepcion.setTag(getString(R.string.pago_metodo_recepcion));
        grupo.addView(recepcion);

        int seleccion = recepcion.getId();
        boolean encontrado = false;
        for (int i = 0; i < grupo.getChildCount(); i++) {
            View opcion = grupo.getChildAt(i);
            if (guardado != null && guardado.equals(opcion.getTag())) {
                seleccion = opcion.getId();
                encontrado = true;
            }
        }
        // Tras un intento con tarjeta nueva el método guardado es "Visa ****4242",
        // que no coincide con ninguna opción: se vuelve a la tarjeta nueva y no a recepción.
        if (!encontrado && guardado != null && guardado.contains("****")) {
            seleccion = nueva.getId();
        }
        grupo.setOnCheckedChangeListener((g, id) -> {
            View elegida = g.findViewById(id);
            if (elegida != null && elegida.getTag() instanceof String) {
                String etiqueta = (String) elegida.getTag();
                viewModel.setMetodoElegido(etiqueta);
                binding.pagoTarjetaNueva.setVisibility(
                        TAG_TARJETA_NUEVA.equals(etiqueta) ? View.VISIBLE : View.GONE);
            }
        });
        grupo.check(seleccion);
    }

    private void pagar() {
        if (!viewModel.puedePagar()) return;
        RadioGroup grupo = binding.pagoMetodos;
        if (grupo.getCheckedRadioButtonId() == View.NO_ID) {
            return;
        }
        View elegido = grupo.findViewById(grupo.getCheckedRadioButtonId());
        Object etiqueta = elegido != null ? elegido.getTag() : null;
        if (!(etiqueta instanceof String)) {
            return;
        }
        String metodo = (String) etiqueta;
        if (TAG_TARJETA_NUEVA.equals(metodo)) {
            metodo = validarTarjetaNueva();
            if (metodo == null) return;
        }
        viewModel.pagar(metodo);
    }

    /**
     * Valida lo tecleado y devuelve el nombre del método ("Visa ****4242"), o
     * null si hay errores, que quedan marcados en cada campo.
     */
    @Nullable
    private String validarTarjetaNueva() {
        String numero = String.valueOf(binding.pagoTarjetaNumero.getText());
        String vence = String.valueOf(binding.pagoTarjetaVencimiento.getText());
        String cvv = String.valueOf(binding.pagoTarjetaCvv.getText());

        boolean numeroOk = TarjetaSimulada.numeroValido(numero);
        boolean venceOk = TarjetaSimulada.vencimientoValido(vence, YearMonth.now());
        boolean cvvOk = TarjetaSimulada.cvvValido(cvv, numero);

        binding.pagoTarjetaNumeroLayout.setError(
                numeroOk ? null : getString(R.string.pago_tarjeta_error_numero));
        binding.pagoTarjetaVencimientoLayout.setError(
                venceOk ? null : getString(R.string.pago_tarjeta_error_vencimiento));
        binding.pagoTarjetaCvvLayout.setError(
                cvvOk ? null : getString(R.string.pago_tarjeta_error_cvv));
        if (!(numeroOk && venceOk && cvvOk)) return null;
        return TarjetaSimulada.metodo(numero);
    }

    private void prepararCamposTarjeta() {
        binding.pagoTarjetaNumero.addTextChangedListener(new TextWatcher() {
            private boolean editando;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (editando) return;
                editando = true;
                String digitos = TarjetaSimulada.soloDigitos(e.toString());
                if (digitos.length() > 19) digitos = digitos.substring(0, 19);
                String formato = digitos.replaceAll("(.{4})(?=.)", "$1 ");
                if (!formato.equals(e.toString())) e.replace(0, e.length(), formato);
                binding.pagoTarjetaNumeroLayout.setError(null);
                editando = false;
            }
        });
        binding.pagoTarjetaVencimiento.addTextChangedListener(new TextWatcher() {
            private boolean editando;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (editando) return;
                editando = true;
                String digitos = TarjetaSimulada.soloDigitos(e.toString());
                if (digitos.length() > 4) digitos = digitos.substring(0, 4);
                String formato = digitos.length() > 2
                        ? digitos.substring(0, 2) + "/" + digitos.substring(2) : digitos;
                if (!formato.equals(e.toString())) e.replace(0, e.length(), formato);
                binding.pagoTarjetaVencimientoLayout.setError(null);
                editando = false;
            }
        });
        binding.pagoTarjetaCvv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                binding.pagoTarjetaCvvLayout.setError(null);
            }
        });
    }

    /** RC-011: tras pagar no se conserva nada de lo tecleado. */
    private void limpiarTarjeta() {
        binding.pagoTarjetaNumero.setText("");
        binding.pagoTarjetaVencimiento.setText("");
        binding.pagoTarjetaCvv.setText("");
    }

    private void pintarPago(@Nullable UiState<Booking> estado) {
        actualizarEspera();
        if (estado == null || estado.isLoading()) {
            return;
        }

        if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.limpiarPago();
            return;
        }

        Booking reserva = estado.requireData();
        binding.pagoExitoMensaje.setText(getString(R.string.pago_firebase_confirmado_mensaje,
                reserva.getCodigoCorto(),
                DateFormatter.fechaLarga(reserva.getFechaEntrada())));
        limpiarTarjeta();
        binding.pagoFormulario.setVisibility(View.GONE);
        binding.pagoError.setVisibility(View.GONE);
        binding.pagoExito.setVisibility(View.VISIBLE);
    }

    private void actualizarEspera() {
        boolean ocupado = viewModel.estaPagando();
        binding.pagoConfirmar.setEnabled(viewModel.puedePagar());
        binding.pagoConfirmar.setText(ocupado
                ? getString(R.string.pago_firebase_guardando)
                : getString(R.string.pago_confirmar, PriceFormatter.format(viewModel.getTotal())));
        for (int i = 0; i < binding.pagoMetodos.getChildCount(); i++) {
            binding.pagoMetodos.getChildAt(i).setEnabled(!ocupado);
        }
    }

    private void abrirDetalle() {
        String id = getArguments() != null ? getArguments().getString("bookingId") : null;
        if (id == null) return;
        NavController navegador = Navigation.findNavController(requireView());
        if (navegador.popBackStack(R.id.bookingDetailFragment, false)) return;
        Bundle argumentos = new Bundle();
        argumentos.putString("bookingId", id);
        navegador.navigate(R.id.bookingDetailFragment, argumentos,
                new NavOptions.Builder().setPopUpTo(R.id.paymentFragment, true).build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
