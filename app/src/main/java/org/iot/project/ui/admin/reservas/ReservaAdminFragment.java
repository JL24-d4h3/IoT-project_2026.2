package org.iot.project.ui.admin.reservas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentAdminReservaBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Card;
import org.iot.project.models.Charge;
import org.iot.project.models.HotelService;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.ui.components.ChargeListView;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.FormatoDeDatos;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Detalle de una reserva, vista del administrador (§44).
 *
 * <p>Es donde el hotel hace las tres cosas que puede hacer sobre una reserva:
 * registrar un consumo mientras el huésped está alojado (RF-051), cobrarle el
 * total a la tarjeta cuando ya se fue (RF-049) y cancelarla si todavía no llegó
 * (RC-013). Las tres aparecen y desaparecen según el estado, porque el
 * repositorio rechaza las que no corresponden y ofrecer un botón que va a
 * fallar es prometer algo que no se puede cumplir.
 *
 * <p>No hay botón de checkout: RF-045 se lo da al cliente, y el hotel espera a
 * que el huésped cierre la estadía desde su aplicación. Lo único que el hotel
 * necesita saber es que ya pasó, y eso lo dice el estado de la reserva.
 */
public class ReservaAdminFragment extends Fragment {

    private FragmentAdminReservaBinding binding;
    private ReservaAdminViewModel viewModel;

    /** Identificador de la reserva abierta, tal como llegó en los argumentos. */
    @Nullable
    private String bookingId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminReservaBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminReservaHeader);
        binding.adminReservaHeader.setTitulo(R.string.nav_reserva_detalle);
        binding.adminReservaHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        // El identificador se guarda como campo y no se lee del Bundle cada vez:
        // al girar la pantalla el fragmento se reconstruye y el Bundle vuelve,
        // pero entre medias los observadores pueden dispararse, y una reserva
        // nula ahí sería un fallo silencioso en vez de un dato que falta.
        bookingId = getArguments() != null
                ? getArguments().getString(ReservasAdminFragment.ARG_BOOKING_ID) : null;

        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ReservaAdminViewModel.class);
        viewModel.getReserva().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);

        binding.adminReservaCargo.setOnClickListener(v -> abrirHojaCargo());
        binding.adminReservaChat.setOnClickListener(v -> abrirChat());
        binding.adminReservaCancelar.setOnClickListener(v -> confirmarCancelacion());

        if (bookingId == null) {
            // Sin identificador no hay nada que pedir. Se dice y se deja el
            // botón de reintento, que vuelve a intentarlo con lo que haya.
            pintarError(getString(R.string.admin_reserva_no_encontrada));
            return;
        }
        viewModel.cargar(bookingId);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ReservaDeHotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminReservaEsqueleto.setVisibility(View.VISIBLE);
                binding.adminReservaContenido.setVisibility(View.GONE);
                binding.adminReservaError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                ReservaDeHotel detalle = estado.getData();
                if (detalle == null) {
                    // Un éxito sin datos no es un éxito: se trata como error
                    // para no dejar la pantalla en blanco sin explicación.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminReservaEsqueleto.setVisibility(View.GONE);
                binding.adminReservaError.setVisibility(View.GONE);
                binding.adminReservaContenido.setVisibility(View.VISIBLE);
                mostrar(detalle);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void mostrar(@NonNull ReservaDeHotel detalle) {
        Booking reserva = detalle.getReserva();

        // El código en el subtítulo del encabezado: es lo que el huésped dice
        // por teléfono y lo que el administrador busca en la lista.
        binding.adminReservaHeader.setSubtitulo(reserva.getCodigo());
        binding.adminReservaHuesped.setText(detalle.getClienteNombre(
                getString(R.string.reserva_huesped_no_disponible)));
        binding.adminReservaEstado.setEstado(reserva.getEstado());

        pintarHuesped(detalle);
        pintarEstadia(detalle);
        pintarConsumos(reserva);
        pintarDesglose(reserva);
        pintarAcciones(reserva);
    }

    private void pintarHuesped(@NonNull ReservaDeHotel detalle) {
        // El correo y el documento son con lo que el administrador identifica a
        // quien tiene delante en recepción: sin ellos, el bloque del huésped
        // solo diría un nombre, que es el dato que menos ayuda a comprobarlo.
        binding.adminReservaFilaContacto.bind(R.string.admin_reserva_fila_contacto,
                detalle.getCliente() != null ? detalle.getCliente().getEmail() : null,
                R.string.perfil_sin_dato);
        binding.adminReservaFilaDocumento.bind(R.string.admin_reserva_fila_documento,
                FormatoDeDatos.documento(detalle.getCliente()),
                R.string.perfil_sin_dato);
    }

    private void pintarEstadia(@NonNull ReservaDeHotel detalle) {
        Booking reserva = detalle.getReserva();
        binding.adminReservaFilaHabitacion.bind(R.string.admin_reserva_fila_habitacion,
                describirHabitacion(detalle), R.string.reserva_hotel_desconocido);
        binding.adminReservaFilaEntrada.bind(R.string.admin_reserva_fila_entrada,
                DateFormatter.fechaConDia(reserva.getFechaEntrada()), R.string.perfil_sin_dato);
        binding.adminReservaFilaSalida.bind(R.string.admin_reserva_fila_salida,
                DateFormatter.fechaConDia(reserva.getFechaSalida()), R.string.perfil_sin_dato);
        // El número a secas y no "2 huéspedes": la etiqueta de la fila ya dice
        // de qué es la cifra, y repetirlo la alarga sin añadir nada.
        binding.adminReservaFilaHuespedes.bind(R.string.admin_reserva_fila_huespedes,
                String.valueOf(reserva.getNumHuespedes()), R.string.perfil_sin_dato);
    }

    /** "601 · Doble clásica"; solo el número si el tipo ya no se puede leer. */
    @NonNull
    private String describirHabitacion(@NonNull ReservaDeHotel detalle) {
        return detalle.getHabitacionTipo().isEmpty()
                ? detalle.getHabitacionNumero()
                : getString(R.string.admin_reserva_linea_tipo,
                        detalle.getHabitacionNumero(), detalle.getHabitacionTipo());
    }

    private void pintarConsumos(@NonNull Booking reserva) {
        binding.adminReservaServicios.setText(nombresDeServicios(reserva));

        List<Charge> cargos = reserva.getCargos();
        boolean hayCargos = ChargeListView.hayCargos(cargos);
        binding.adminReservaCargos.setVisibility(hayCargos ? View.VISIBLE : View.GONE);
        binding.adminReservaSinCargos.setVisibility(hayCargos ? View.GONE : View.VISIBLE);
        binding.adminReservaCargos.setCargos(cargos);
    }

    /** Los servicios adicionales de la reserva, en una línea. */
    @NonNull
    private String nombresDeServicios(@NonNull Booking reserva) {
        List<HotelService> servicios = reserva.getServiciosAdicionales();
        if (servicios.isEmpty()) {
            return getString(R.string.admin_reserva_sin_servicios);
        }
        List<String> nombres = new ArrayList<>();
        for (HotelService servicio : servicios) {
            // El nombre vive en el catálogo y la reserva solo guarda a qué
            // servicio apunta (regla 8), así que hay que resolverlo. Si ya no
            // está, se dice que no está en vez de dejar un hueco.
            nombres.add(FormatoDeDatos.nombreServicio(servicio.getServiceId(),
                    getString(R.string.admin_reserva_servicio_retirado)));
        }
        return String.join(getString(R.string.lista_separador), nombres);
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

        binding.adminReservaDesglose.setLineas(lineas);
        binding.adminReservaDesglose.setTotal(getString(R.string.reserva_total), reserva.getTotal());

        pintarTarjeta(reserva);
    }

    private void pintarTarjeta(@NonNull Booking reserva) {
        Card tarjeta = reserva.getTarjeta();
        if (tarjeta == null) {
            // Sin tarjeta no se enseña la fila: el aviso que la sustituye ya
            // explica dónde se cobra, y una fila vacía debajo solo estorbaría.
            binding.adminReservaFilaTarjeta.setVisibility(View.GONE);
            binding.adminReservaTarjetaNota.setText(R.string.admin_reserva_sin_tarjeta);
            return;
        }
        binding.adminReservaFilaTarjeta.setVisibility(View.VISIBLE);
        // Marca y últimos cuatro dígitos, nunca el número completo ni el código
        // de seguridad: no se guardan (RC-011, RT-038).
        binding.adminReservaFilaTarjeta.bind(R.string.admin_reserva_fila_tarjeta,
                describirTarjeta(tarjeta), R.string.perfil_sin_dato);
        binding.adminReservaTarjetaNota.setText(reserva.isCobrado()
                ? R.string.admin_reserva_cobrado : R.string.admin_reserva_cobro_simulado);
    }

    /**
     * "Visa •••• 4242 · vence 08/27".
     *
     * <p>El vencimiento se omite si el registro no lo trae, en vez de dejar un
     * "vence null" que es lo que sale al concatenar sin mirar.
     */
    @NonNull
    private CharSequence describirTarjeta(@NonNull Card tarjeta) {
        String expiracion = tarjeta.getExpiracion();
        return expiracion == null || expiracion.isEmpty()
                ? tarjeta.getDisplayName()
                : getString(R.string.pago_metodo_tarjeta,
                        tarjeta.getDisplayName(), expiracion);
    }

    /**
     * Enseña las acciones que el estado de la reserva permite.
     *
     * <p>La regla es la misma en las cuatro: un botón solo se muestra si tiene
     * algo que hacer. Cancelar una estadía en curso o cobrar antes del checkout
     * no son operaciones que el repositorio vaya a aceptar, y un botón que
     * siempre falla enseña a desconfiar de los botones.
     */
    private void pintarAcciones(@NonNull Booking reserva) {
        BookingStatus estado = reserva.getEstado();

        // Cobros adicionales: solo con el huésped dentro (RF-051). El cargo va
        // asociado a una estadía, y antes de que el huésped llegue no hay
        // estadía a la que asociarlo.
        binding.adminReservaCargo.setVisibility(
                estado == BookingStatus.ACTIVA ? View.VISIBLE : View.GONE);

        // El cobro del total, solo después del checkout y con tarjeta (RF-049).
        boolean puedeCobrar = estado == BookingStatus.FINALIZADA
                && reserva.getTarjeta() != null && !reserva.isCobrado();
        binding.adminReservaCobrar.setVisibility(puedeCobrar ? View.VISIBLE : View.GONE);
        if (puedeCobrar) {
            binding.adminReservaCobrar.setText(getString(R.string.admin_reserva_cobrar,
                    PriceFormatter.format(reserva.getTotal())));
        }

        // Escribir al huésped: solo mientras la conversación tenga sentido
        // (RF-065). El modelo es quien decide cuándo, no la pantalla.
        binding.adminReservaChat.setVisibility(reserva.permiteChat() ? View.VISIBLE : View.GONE);

        // Cancelar: solo si el estado lo permite (RC-013), que es exactamente lo
        // que comprueba el repositorio antes de cancelar.
        binding.adminReservaCancelar.setVisibility(
                estado.allowsCancellation() ? View.VISIBLE : View.GONE);
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminReservaEsqueleto.setVisibility(View.GONE);
        binding.adminReservaContenido.setVisibility(View.GONE);
        binding.adminReservaError.setVisibility(View.VISIBLE);
        binding.adminReservaError.conReintento(mensaje, v -> {
            if (bookingId != null) {
                viewModel.recargar();
            }
        });
    }

    // ------------------------------------------------------------------ Operaciones

    private void pintarOperacion(@Nullable UiState<Booking> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        if (estado.isSuccess()) {
            Booking reserva = estado.getData();
            // El mensaje se elige por el estado en que quedó la reserva y no por
            // recordar qué botón se pulsó: si se canceló, está cancelada, y eso
            // no depende de que esta pantalla se acuerde de lo que hizo.
            avisar(reserva != null && reserva.getEstado() == BookingStatus.CANCELADA
                    ? getString(R.string.admin_reserva_cancelada)
                    : getString(R.string.admin_reserva_cobrado_hecho));
        } else if (estado.isError()) {
            avisar(estado.getMessage() != null
                    ? estado.getMessage() : getString(R.string.estado_error_descripcion));
        }
        viewModel.limpiarOperacion();
    }

    private void avisar(@NonNull String mensaje) {
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    private void abrirHojaCargo() {
        CargoSheet.para(bookingId).show(getChildFragmentManager(), CargoSheet.TAG);
    }

    private void abrirChat() {
        Bundle argumentos = new Bundle();
        argumentos.putString(ReservasAdminFragment.ARG_BOOKING_ID, bookingId);
        Navigation.findNavController(requireView())
                .navigate(R.id.adminChatFragment, argumentos);
    }

    private void confirmarCancelacion() {
        UiState<ReservaDeHotel> estado = viewModel.getReserva().getValue();
        ReservaDeHotel detalle = estado != null ? estado.getData() : null;
        if (detalle == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.admin_reserva_cancelar_titulo,
                        detalle.getReserva().getCodigo()))
                .setMessage(R.string.admin_reserva_cancelar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.admin_reserva_cancelar_confirmar,
                        (dialogo, cual) -> viewModel.cancelar())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
