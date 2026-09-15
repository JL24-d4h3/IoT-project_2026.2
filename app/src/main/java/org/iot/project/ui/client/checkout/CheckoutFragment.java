package org.iot.project.ui.client.checkout;

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

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentCheckoutBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Hotel;
import org.iot.project.ui.components.ChargeListView;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Checkout de la estadía (RF-056).
 *
 * <p>Cerrar la estadía es irreversible y además corta el chat (RF-065), así que
 * se pregunta antes. La pantalla enseña primero lo que se va a cerrar —los
 * consumos y el total— porque es lo último que el huésped ve de su estadía.
 */
public class CheckoutFragment extends Fragment {

    private FragmentCheckoutBinding binding;
    private CheckoutViewModel viewModel;

    /**
     * La estadía ya se cerró.
     *
     * <p>Cerrar publica dos cosas: el resultado de la operación y la reserva ya
     * finalizada. El segundo aviso vuelve a pasar por {@link #pintar}, que
     * enseñaría otra vez el formulario encima del panel de confirmación. Con
     * esto, una vez cerrada, el formulario no vuelve.
     */
    private boolean cerrada;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCheckoutBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        InsetUtils.applyBottomPadding(binding.checkoutContenido);

        binding.header.setTitulo(R.string.titulo_checkout);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());
        binding.checkoutConfirmar.setOnClickListener(v -> confirmar());
        binding.checkoutVolver.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        viewModel = new ViewModelProvider(this).get(CheckoutViewModel.class);
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
        if (cerrada) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.checkoutContenido.setVisibility(View.GONE);
                binding.checkoutError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.checkoutError.setVisibility(View.GONE);
                binding.checkoutContenido.setVisibility(View.VISIBLE);
                mostrar(estado.requireData());
                break;
            default:
                binding.checkoutContenido.setVisibility(View.GONE);
                binding.checkoutError.setVisibility(View.VISIBLE);
                binding.checkoutError.conReintento(estado.getMessage(),
                        v -> viewModel.recargar());
                break;
        }
    }

    private void mostrar(@NonNull Booking datos) {
        binding.checkoutReserva.bind(datos);

        boolean hayCargos = ChargeListView.hayCargos(datos.getCargos());
        binding.checkoutCargos.setVisibility(hayCargos ? View.VISIBLE : View.GONE);
        binding.checkoutSinCargos.setVisibility(hayCargos ? View.GONE : View.VISIBLE);
        binding.checkoutCargos.setCargos(datos.getCargos());

        pintarDesglose(datos);
    }

    private void pintarDesglose(@NonNull Booking datos) {
        List<PriceBreakdownView.Linea> lineas = new ArrayList<>();
        String precioNoche = PriceFormatter.format(datos.getPrecioNoche());
        lineas.add(new PriceBreakdownView.Linea(
                datos.getNumNoches() == 1
                        ? getString(R.string.detalle_linea_alojamiento_una, precioNoche)
                        : getString(R.string.detalle_linea_alojamiento,
                        datos.getNumNoches(), precioNoche),
                datos.getSubtotalAlojamiento()));
        lineas.add(new PriceBreakdownView.Linea(
                getString(R.string.detalle_linea_servicios),
                datos.getSubtotalServiciosAdicionales()));
        lineas.add(new PriceBreakdownView.Linea(
                getString(R.string.detalle_linea_cargos),
                datos.getSubtotalCargos()));

        binding.checkoutDesglose.setLineas(lineas);
        binding.checkoutDesglose.setTotal(getString(R.string.reserva_total), datos.getTotal());
    }

    // --------------------------------------------------------------- Confirmar

    private void confirmar() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.titulo_checkout)
                .setMessage(R.string.checkout_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.checkout_confirmar,
                        (dialogo, cual) -> {
                            binding.checkoutConfirmar.setEnabled(false);
                            viewModel.finalizar();
                        })
                .show();
    }

    private void pintarOperacion(@Nullable UiState<Booking> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        binding.checkoutConfirmar.setEnabled(true);

        if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.limpiarOperacion();
            return;
        }

        Booking finalizada = estado.requireData();
        cerrada = true;
        Hotel hotel = ServiceLocator.hoteles().hotel(finalizada.getHotelId());
        binding.checkoutExitoMensaje.setText(getString(R.string.checkout_listo_mensaje,
                hotel != null ? hotel.getNombre() : getString(R.string.reserva_hotel_desconocido)));

        // Valorar solo si todavía no lo hizo: la reserva acaba de cerrarse, así
        // que lo normal es que no. Si ya estaba valorada, el botón sobra.
        binding.checkoutValorar.setVisibility(
                finalizada.getValoracion() == null ? View.VISIBLE : View.GONE);
        binding.checkoutValorar.setOnClickListener(v -> {
            Bundle argumentos = new Bundle();
            argumentos.putString("bookingId", finalizada.getId());
            Navigation.findNavController(v).navigate(R.id.reviewFragment, argumentos);
        });

        binding.checkoutContenido.setVisibility(View.GONE);
        binding.checkoutExito.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
