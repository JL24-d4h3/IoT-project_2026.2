package org.iot.project.ui.admin.reservas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.SessionManager;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentAdminReservaBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Card;
import org.iot.project.models.Charge;
import org.iot.project.models.HotelService;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.components.ChargeListView;
import org.iot.project.ui.components.PriceBreakdownView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.FormatoDeDatos;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Detalle de una reserva, vista del administrador (§44).
 *
 * <p>Recepción registra el ingreso de una reserva confirmada, añade consumos
 * durante la estadía, registra el cobro después del checkout y cancela antes
 * de la llegada. El ingreso tiene una acción fija y explica cuándo está
 * disponible; el resto de acciones depende del estado de la reserva.
 *
 * <p>No hay botón de checkout: RF-045 se lo da al cliente, y el hotel espera a
 * que el huésped cierre la estadía desde su aplicación. Lo único que el hotel
 * necesita saber es que ya pasó, y eso lo dice el estado de la reserva.
 */
public class ReservaAdminFragment extends Fragment {

    private FragmentAdminReservaBinding binding;
    private ReservaAdminViewModel viewModel;
    private AdminTaxiReservaViewModel taxiViewModel;
    private List<String> opcionesTaxi = new ArrayList<>();
    @Nullable private AlertDialog confirmacionIngreso;

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
        InsetUtils.applyBottomPadding(binding.adminReservaIngresoAcciones);
        binding.adminReservaHeader.setTitulo(R.string.nav_reserva_detalle);
        binding.adminReservaHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        // El identificador se guarda como campo y no se lee del Bundle cada vez:
        // al girar la pantalla el fragmento se reconstruye y el Bundle vuelve,
        // pero entre medias los observadores pueden dispararse, y una reserva
        // nula ahí sería un fallo silencioso en vez de un dato que falta.
        bookingId = getArguments() != null
                ? getArguments().getString(ReservasAdminFragment.ARG_BOOKING_ID) : null;

        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ReservaAdminViewModel.class);
        if (bookingId != null) viewModel.cargar(bookingId);
        viewModel.getReserva().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        viewModel.getOcupado().observe(getViewLifecycleOwner(), this::pintarOcupado);

        binding.adminReservaTaxiMapa.onCreate(savedInstanceState);
        taxiViewModel = new ViewModelProvider(this).get(AdminTaxiReservaViewModel.class);
        taxiViewModel.cargar(bookingId);
        taxiViewModel.getContenido().observe(getViewLifecycleOwner(), this::pintarTaxi);
        binding.adminReservaTaxiReintentar.setOnClickListener(v -> taxiViewModel.reintentar());
        binding.adminReservaTaxiSelector.setOnItemClickListener((padre, fila, posicion, id) -> {
            UiState<AdminTaxiReservaViewModel.Estado> actual = taxiViewModel.getContenido().getValue();
            if (actual != null && actual.isSuccess()
                    && posicion < actual.requireData().confirmados.size()) {
                taxiViewModel.seleccionar(actual.requireData().confirmados.get(posicion).getId());
            }
        });

        binding.adminReservaCargo.setOnClickListener(v -> abrirHojaCargo());
        binding.adminReservaIniciar.setOnClickListener(v -> confirmarIngreso());
        binding.adminReservaCobrar.setOnClickListener(v -> confirmarCobro());
        binding.adminReservaCancelar.setOnClickListener(v -> confirmarCancelacion());
        binding.adminReservaChat.setOnClickListener(v -> abrirChat());

        if (bookingId == null) {
            // Sin identificador no hay nada que pedir. Se dice y se deja el
            // botón de reintento, que vuelve a intentarlo con lo que haya.
            pintarError(getString(R.string.admin_reserva_no_encontrada));
            return;
        }
    }

    @Override public void onStart() {
        super.onStart();
        if (binding != null) binding.adminReservaTaxiMapa.onStart();
        taxiViewModel.iniciarSeguimiento();
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null) binding.adminReservaTaxiMapa.onResume();
        if (bookingId != null) {
            viewModel.cargar(bookingId);
            viewModel.iniciarSeguimiento();
        }
    }

    @Override public void onPause() {
        if (binding != null) binding.adminReservaTaxiMapa.onPause();
        super.onPause();
    }

    @Override public void onStop() {
        cerrarConfirmacionIngreso();
        viewModel.detenerSeguimiento();
        taxiViewModel.detenerSeguimiento();
        if (binding != null) binding.adminReservaTaxiMapa.onStop();
        super.onStop();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ReservaDeHotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminReservaIngresoAcciones.setVisibility(View.GONE);
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

    private void pintarTaxi(@NonNull UiState<AdminTaxiReservaViewModel.Estado> estado) {
        if (binding == null) return;
        boolean exito = estado.isSuccess();
        binding.adminReservaTaxiDetalle.setVisibility(View.GONE);
        binding.adminReservaTaxiSelectorCampo.setVisibility(View.GONE);
        binding.adminReservaTaxiSinConfirmar.setVisibility(View.GONE);
        binding.adminReservaTaxiMensaje.setVisibility(View.VISIBLE);
        binding.adminReservaTaxiReintentar.setVisibility(estado.isError() ? View.VISIBLE : View.GONE);
        if (!exito) {
            limpiarMapaTaxi();
            if (estado.isLoading()) binding.adminReservaTaxiMensaje.setText(R.string.admin_taxi_reserva_cargando);
            else if (estado.isEmpty()) binding.adminReservaTaxiMensaje.setText(R.string.admin_taxi_reserva_vacio);
            else binding.adminReservaTaxiMensaje.setText(getString(R.string.admin_taxi_reserva_error,
                    estado.getMessage() == null ? getString(R.string.estado_error_descripcion) : estado.getMessage()));
            return;
        }
        AdminTaxiReservaViewModel.Estado datos = estado.requireData();
        binding.adminReservaTaxiMensaje.setVisibility(datos.aviso == null ? View.GONE : View.VISIBLE);
        binding.adminReservaTaxiReintentar.setVisibility(datos.aviso == null ? View.GONE : View.VISIBLE);
        if (datos.aviso != null) {
            binding.adminReservaTaxiMensaje.setText(getString(R.string.admin_taxi_reserva_error, datos.aviso));
        }
        pintarSolicitudesSinConfirmar(datos.sinConfirmar);
        TaxiService servicio = datos.seleccionado;
        if (servicio == null) { limpiarMapaTaxi(); return; }
        binding.adminReservaTaxiDetalle.setVisibility(View.VISIBLE);
        pintarSelectorTaxi(datos);
        binding.adminReservaTaxiEstado.bindParaHotel(servicio);
        binding.adminReservaTaxiOrigen.bind(R.string.admin_taxi_reserva_origen,
                servicio.getOrigen(), R.string.perfil_sin_dato);
        binding.adminReservaTaxiDestino.bind(R.string.admin_taxi_reserva_destino,
                servicio.getDestino(), R.string.perfil_sin_dato);
        binding.adminReservaTaxiProgramacion.bind(R.string.admin_taxi_reserva_programacion,
                programacionTaxi(servicio), R.string.perfil_sin_dato);
        boolean hayConductor = servicio.getDriver() != null;
        binding.adminReservaTaxiConductor.setVisibility(hayConductor ? View.VISIBLE : View.GONE);
        binding.adminReservaTaxiConductorNota.setText(hayConductor
                ? servicio.getEstado().isFinished() ? R.string.admin_taxi_reserva_conductor_finalizado
                : R.string.admin_taxi_reserva_conductor_asignado
                : servicio.getEstado() == TaxiStatus.SOLICITADO ? R.string.admin_taxi_reserva_conductor_espera
                : R.string.admin_taxi_reserva_conductor_sin_datos);
        if (hayConductor) binding.adminReservaTaxiConductor.bind(servicio.getDriver());
        pintarMapaTaxi(servicio);
    }

    private void pintarSolicitudesSinConfirmar(List<TaxiService> pendientes) {
        binding.adminReservaTaxiSinConfirmar.setVisibility(pendientes.isEmpty() ? View.GONE : View.VISIBLE);
        if (pendientes.isEmpty()) return;
        int rechazadas = 0;
        for (TaxiService servicio : pendientes) {
            if ("RECHAZADO".equals(servicio.getEnvioApi())) rechazadas++;
        }
        List<String> avisos = new ArrayList<>();
        if (rechazadas < pendientes.size()) {
            int sinConfirmar = pendientes.size() - rechazadas;
            avisos.add(getResources().getQuantityString(R.plurals.admin_taxi_reserva_sin_confirmar,
                    sinConfirmar, sinConfirmar));
        }
        if (rechazadas > 0) avisos.add(getResources().getQuantityString(
                R.plurals.admin_taxi_reserva_rechazados, rechazadas, rechazadas));
        binding.adminReservaTaxiSinConfirmar.setText(String.join("\n", avisos));
    }

    private void pintarSelectorTaxi(AdminTaxiReservaViewModel.Estado datos) {
        binding.adminReservaTaxiSelectorCampo.setVisibility(datos.confirmados.size() > 1 ? View.VISIBLE : View.GONE);
        List<String> opciones = new ArrayList<>();
        String seleccion = "";
        for (TaxiService servicio : datos.confirmados) {
            String etiqueta = getString(R.string.admin_taxi_reserva_opcion,
                    programacionTaxi(servicio), servicio.getEstado().getDisplayName());
            opciones.add(etiqueta);
            if (datos.seleccionado != null && datos.seleccionado.getId().equals(servicio.getId())) seleccion = etiqueta;
        }
        if (!opciones.equals(opcionesTaxi) || binding.adminReservaTaxiSelector.getAdapter() == null) {
            opcionesTaxi = opciones;
            binding.adminReservaTaxiSelector.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, opciones));
        }
        if (!seleccion.contentEquals(binding.adminReservaTaxiSelector.getText())) {
            binding.adminReservaTaxiSelector.setText(seleccion, false);
        }
    }

    private String programacionTaxi(TaxiService servicio) {
        if (servicio.getFecha() == null) return getString(R.string.admin_taxi_reserva_sin_programacion);
        return servicio.getHora() == null ? DateFormatter.fechaConDia(servicio.getFecha())
                : getString(R.string.admin_taxi_reserva_fecha_hora,
                DateFormatter.fechaConDia(servicio.getFecha()), DateFormatter.hora(servicio.getHora()));
    }

    private void pintarMapaTaxi(TaxiService servicio) {
        boolean finalizado = servicio.getEstado().isFinished();
        LocalDateTime reporte = servicio.getUltimaActualizacionUbicacion();
        boolean hayPosicion = reporte != null && coordenadasValidas(servicio.getLatConductor(), servicio.getLngConductor());
        boolean hayRecojo = servicio.hasRecojo() && coordenadasValidas(servicio.getLatRecojo(), servicio.getLngRecojo());
        // TrackingMapView conserva los marcadores si sus coordenadas no cambiaron.
        binding.adminReservaTaxiMapa.setRecojo(hayRecojo ? servicio.getLatRecojo() : Double.NaN,
                hayRecojo ? servicio.getLngRecojo() : Double.NaN);
        if (hayPosicion) binding.adminReservaTaxiMapa.setConductor(servicio.getLatConductor(), servicio.getLngConductor());
        else binding.adminReservaTaxiMapa.limpiarConductor();
        binding.adminReservaTaxiMapa.setVisibility(hayPosicion || hayRecojo ? View.VISIBLE : View.GONE);
        binding.adminReservaTaxiMapaTitulo.setText(finalizado ? R.string.admin_taxi_reserva_ubicacion_final
                : servicio.getEstado() == TaxiStatus.EN_TRASLADO ? R.string.admin_taxi_reserva_ubicacion_traslado
                : R.string.admin_taxi_reserva_ubicacion_recojo);
        binding.adminReservaTaxiUbicacionAviso.setVisibility(View.GONE);
        if (!hayPosicion) {
            binding.adminReservaTaxiActualizado.setText(finalizado
                    ? R.string.admin_taxi_reserva_ubicacion_final_sin_reporte : R.string.admin_taxi_reserva_ubicacion_sin_reporte);
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        binding.adminReservaTaxiActualizado.setText(finalizado
                ? getString(R.string.admin_taxi_reserva_ubicacion_historica,
                DateFormatter.fechaCorta(reporte.toLocalDate()), DateFormatter.horaDe(reporte))
                : getString(R.string.admin_taxi_reserva_ubicacion_actualizada,
                DateFormatter.fechaCorta(reporte.toLocalDate()), DateFormatter.horaDe(reporte),
                DateFormatter.relativo(reporte, ahora)));
        if (!finalizado && reporte.isAfter(ahora.plusMinutes(1))) {
            binding.adminReservaTaxiUbicacionAviso.setText(R.string.admin_taxi_reserva_ubicacion_futura);
            binding.adminReservaTaxiUbicacionAviso.setVisibility(View.VISIBLE);
        } else if (!finalizado && servicio.ubicacionDesactualizada(ahora, 300L)) {
            binding.adminReservaTaxiUbicacionAviso.setText(R.string.admin_taxi_reserva_ubicacion_vieja);
            binding.adminReservaTaxiUbicacionAviso.setVisibility(View.VISIBLE);
        }
    }

    private boolean coordenadasValidas(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat >= -90d && lat <= 90d && lng >= -180d && lng <= 180d;
    }

    private void limpiarMapaTaxi() {
        binding.adminReservaTaxiMapa.limpiarConductor();
        binding.adminReservaTaxiMapa.setRecojo(Double.NaN, Double.NaN);
        binding.adminReservaTaxiMapa.setVisibility(View.GONE);
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
            binding.adminReservaTarjetaNota.setText(reserva.isCobrado()
                    ? R.string.admin_firestore_cobro_registrado
                    : R.string.admin_firestore_cobro_recepcion_nota);
            return;
        }
        binding.adminReservaFilaTarjeta.setVisibility(View.VISIBLE);
        // Marca y últimos cuatro dígitos, nunca el número completo ni el código
        // de seguridad: no se guardan (RC-011, RT-038).
        binding.adminReservaFilaTarjeta.bind(R.string.admin_reserva_fila_tarjeta,
                describirTarjeta(tarjeta), R.string.perfil_sin_dato);
        binding.adminReservaTarjetaNota.setText(reserva.isCobrado()
                ? R.string.admin_firestore_cobro_registrado
                : R.string.admin_firestore_cobro_recepcion_nota);
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
     * <p>El ingreso sigue visible antes de la llegada, con la explicación de
     * pago o fechas cuando está deshabilitado. Las demás acciones se ofrecen
     * únicamente en los estados que admite el repositorio.
     */
    private void pintarAcciones(@NonNull Booking reserva) {
        BookingStatus estado = reserva.getEstado();

        // Cobros adicionales: solo con el huésped dentro (RF-051). El cargo va
        // asociado a una estadía, y antes de que el huésped llegue no hay
        // estadía a la que asociarlo.
        binding.adminReservaCargo.setVisibility(
                estado == BookingStatus.ACTIVA ? View.VISIBLE : View.GONE);

        LocalDate hoy = LocalDate.now();
        boolean esperando = estado == BookingStatus.PENDIENTE || estado == BookingStatus.CONFIRMADA;
        binding.adminReservaIngresoAcciones.setVisibility(esperando ? View.VISIBLE : View.GONE);
        if (esperando) {
            binding.adminReservaIngresoNota.setText(estado == BookingStatus.PENDIENTE
                    ? R.string.admin_firestore_ingreso_pendiente
                    : hoy.isBefore(reserva.getFechaEntrada())
                    ? R.string.admin_firestore_ingreso_anticipado
                    : !hoy.isBefore(reserva.getFechaSalida())
                    ? R.string.admin_firestore_ingreso_vencido
                    : R.string.admin_firestore_ingreso_listo);
        }

        // El registro de recepción es simulado y no requiere una tarjeta almacenada.
        boolean puedeCobrar = estado == BookingStatus.FINALIZADA
                && !reserva.isCobrado();
        binding.adminReservaCobrar.setVisibility(puedeCobrar ? View.VISIBLE : View.GONE);
        if (puedeCobrar) {
            binding.adminReservaCobrar.setText(getString(R.string.admin_firestore_cobrar,
                    PriceFormatter.format(reserva.getTotal())));
        }

        // Escribir al huésped: solo mientras la conversación tenga sentido
        // (RF-065). El modelo es quien decide cuándo, no la pantalla.
        binding.adminReservaChat.setVisibility(reserva.permiteChat() ? View.VISIBLE : View.GONE);

        // Cancelar: solo si el estado lo permite (RC-013), que es exactamente lo
        // que comprueba el repositorio antes de cancelar.
        binding.adminReservaCancelar.setVisibility(
                estado.allowsCancellation() ? View.VISIBLE : View.GONE);
        pintarOcupado(viewModel.estaOcupado());
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminReservaIngresoAcciones.setVisibility(View.GONE);
        cerrarConfirmacionIngreso();
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
            if (reserva != null) {
                avisar(getString(reserva.getEstado() == BookingStatus.CANCELADA
                        ? R.string.admin_reserva_cancelada
                        : reserva.getEstado() == BookingStatus.ACTIVA
                        ? R.string.admin_firestore_ingreso_hecho
                        : R.string.admin_firestore_cobro_hecho));
            }
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
        if (viewModel.estaOcupado() || getChildFragmentManager().findFragmentByTag(CargoSheet.TAG) != null) return;
        CargoSheet.para(bookingId).show(getChildFragmentManager(), CargoSheet.TAG);
    }

    private void pintarOcupado(@Nullable Boolean guardando) {
        boolean libre = !Boolean.TRUE.equals(guardando);
        binding.adminReservaIniciar.setEnabled(libre && puedeRegistrarIngreso());
        binding.adminReservaIniciar.setText(libre
                ? R.string.admin_firestore_ingreso : R.string.admin_firestore_guardando);
        binding.adminReservaCargo.setEnabled(libre);
        binding.adminReservaCobrar.setEnabled(libre);
        binding.adminReservaCancelar.setEnabled(libre);
        binding.adminReservaChat.setEnabled(libre);
        binding.adminReservaOperacion.setVisibility(libre ? View.GONE : View.VISIBLE);
    }

    private void abrirChat() {
        UiState<ReservaDeHotel> actual = viewModel.getReserva().getValue();
        if (viewModel.estaOcupado() || actual == null || !actual.isSuccess()
                || !actual.requireData().getReserva().permiteChat() || bookingId == null) return;
        Bundle argumentos = new Bundle();
        argumentos.putString(ReservasAdminFragment.ARG_BOOKING_ID, bookingId);
        Navigation.findNavController(requireView()).navigate(R.id.adminChatFragment, argumentos);
    }

    private void confirmarIngreso() {
        if (viewModel.estaOcupado() || !puedeRegistrarIngreso() || confirmacionIngreso != null) return;
        final String reservaConfirmada = bookingId;
        final String cuenta = SessionManager.getIdentidadId();
        final String hotel = SessionManager.getHotelAdministrado();
        confirmacionIngreso = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_firestore_ingreso_titulo)
                .setMessage(R.string.admin_firestore_ingreso_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.admin_firestore_ingreso,
                        (dialogo, cual) -> {
                            if (Objects.equals(reservaConfirmada, bookingId)
                                    && Objects.equals(cuenta, SessionManager.getIdentidadId())
                                    && Objects.equals(hotel, SessionManager.getHotelAdministrado())
                                    && puedeRegistrarIngreso()) {
                                viewModel.iniciarEstadia();
                            }
                        })
                .create();
        confirmacionIngreso.setOnDismissListener(dialogo -> confirmacionIngreso = null);
        confirmacionIngreso.show();
    }

    private boolean puedeRegistrarIngreso() {
        UiState<ReservaDeHotel> actual = viewModel.getReserva().getValue();
        if (bookingId == null || actual == null || !actual.isSuccess()) return false;
        Booking reserva = actual.requireData().getReserva();
        LocalDate hoy = LocalDate.now();
        return bookingId.equals(reserva.getId()) && reserva.getEstado() == BookingStatus.CONFIRMADA
                && !hoy.isBefore(reserva.getFechaEntrada()) && hoy.isBefore(reserva.getFechaSalida());
    }

    private void cerrarConfirmacionIngreso() {
        if (confirmacionIngreso != null) {
            confirmacionIngreso.dismiss();
            confirmacionIngreso = null;
        }
    }

    private void confirmarCobro() {
        UiState<ReservaDeHotel> estado = viewModel.getReserva().getValue();
        ReservaDeHotel detalle = estado == null ? null : estado.getData();
        if (viewModel.estaOcupado() || detalle == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.admin_firestore_cobro_titulo,
                        PriceFormatter.format(detalle.getReserva().getTotal())))
                .setMessage(R.string.admin_firestore_cobro_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.admin_firestore_cobro_confirmar,
                        (dialogo, cual) -> viewModel.cobrar())
                .show();
    }

    private void confirmarCancelacion() {
        if (viewModel.estaOcupado()) return;
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
        cerrarConfirmacionIngreso();
        if (binding != null) binding.adminReservaTaxiMapa.onDestroy();
        opcionesTaxi = new ArrayList<>();
        super.onDestroyView();
        binding = null;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle salida) {
        if (binding != null) binding.adminReservaTaxiMapa.onSaveInstanceState(salida);
        super.onSaveInstanceState(salida);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (binding != null) binding.adminReservaTaxiMapa.onLowMemory();
    }
}
