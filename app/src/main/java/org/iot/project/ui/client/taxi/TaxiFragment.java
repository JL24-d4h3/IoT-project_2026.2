package org.iot.project.ui.client.taxi;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentTaxiBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Hotel;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.components.BookingSummaryView;
import org.iot.project.ui.components.TaxiRequestCardView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Taxi al aeropuerto (§35 a §39). Prioridad 7 de §79.
 *
 * <p>La pantalla tiene dos caras. La primera es el estado: que servicio tiene
 * en curso, donde esta el conductor, y que traslados hizo antes. La segunda es
 * el formulario, que se abre encima y solo cuando se puede pedir algo.
 *
 * <p>El borrador conserva reserva, referencias y horario al recrear la vista.
 */
public class TaxiFragment extends Fragment {

    private static final String TAG_FECHA = "selector_fecha_taxi";
    private static final String TAG_HORA = "selector_hora_taxi";

    /** RF-105: se valora una sola vez y el resultado se ve en el historial. */
    private static final String TAG_VALORAR = "valorar_taxi";

    /**
     * Pasado este tiempo sin reportar, la ubicacion del conductor ya no sirve
     * para decidir si conviene bajar a la calle (RC-027).
     */
    private static final long SEGUNDOS_UBICACION_FRESCA = 300L;

    /**
     * Tope de pasajeros de un traslado.
     *
     * <p>No lo impone el repositorio, lo impone el vehiculo: la flota son
     * coches y furgonetas, y un grupo mas grande necesita dos unidades. Dejar
     * el selector sin tope permitiria pedir veinte plazas y no habria coche.
     */
    private static final int MAX_PASAJEROS = 6;

    private FragmentTaxiBinding binding;
    private TaxiViewModel viewModel;

    /** Reservas pintadas en el formulario, para poder marcar la elegida. */
    private final List<BookingSummaryView> tarjetasReserva = new ArrayList<>();

    @Nullable
    private Booking reservaElegida;
    @Nullable
    private LocalDate fecha;
    @Nullable
    private LocalTime hora;

    private int pasajeros = 1;
    private boolean pintandoBorrador;
    @Nullable private String enfoquePendiente;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTaxiBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.taxi_titulo);
        binding.header.setSubtitulo(getString(R.string.taxi_subtitulo));

        viewModel = new ViewModelProvider(this).get(TaxiViewModel.class);
        viewModel.cargar();
        enfoquePendiente = getArguments() == null ? null : getArguments().getString("taxiId");
        if (enfoquePendiente != null && !enfoquePendiente.isEmpty()) {
            viewModel.enfocarServicio(enfoquePendiente);
            viewModel.setFormularioAbierto(false);
        }

        binding.taxiPedir.setOnClickListener(v -> abrirFormulario());
        binding.taxiIrReservas.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.bookingsFragment));
        binding.taxiQr.setOnClickListener(v -> mostrarQr());
        binding.taxiConfirmar.setOnClickListener(v -> confirmar());
        binding.taxiCancelar.setOnClickListener(v -> cerrarFormulario());
        binding.taxiFormReintentar.setOnClickListener(v -> viewModel.refrescarEnSilencio());
        binding.taxiFilaFecha.setOnClickListener(v -> elegirFecha());
        binding.taxiFilaHora.setOnClickListener(v -> elegirHora());

        binding.taxiPasajeros.configurar(R.string.taxi_pasajeros,
                getString(R.string.taxi_pasajeros_nota), 1, MAX_PASAJEROS);
        binding.taxiPasajeros.setOnValorCambiadoListener(valor -> {
            if (viewModel.isEnviando()) {
                binding.taxiPasajeros.setValor(pasajeros);
                return;
            }
            pasajeros = valor;
            guardarBorrador();
        });
        pasajeros = binding.taxiPasajeros.getValor();

        restaurarBorrador();
        binding.taxiRecogida.addTextChangedListener(observarTexto());
        binding.taxiDestino.addTextChangedListener(observarTexto());
        binding.taxiIdaVueltaSwitch.setOnCheckedChangeListener((boton, marcado) -> guardarBorrador());
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getReservas().observe(getViewLifecycleOwner(), this::pintarReservas);
        viewModel.getHotelElegido().observe(getViewLifecycleOwner(), this::pintarHotel);
        viewModel.getSolicitud().observe(getViewLifecycleOwner(), this::pintarSolicitud);
        binding.taxiFormulario.setVisibility(viewModel.isFormularioAbierto() ? View.VISIBLE : View.GONE);
    }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.iniciarSeguimiento();
    }

    @Override public void onStop() {
        guardarBorrador();
        if (viewModel != null) viewModel.detenerSeguimiento();
        super.onStop();
    }

    // ------------------------------------------------------------------
    //  Estado
    // ------------------------------------------------------------------

    private void pintar(@NonNull UiState<TaxiViewModel.Estado> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.taxiEsqueleto.setVisibility(View.VISIBLE);
                binding.taxiContenido.setVisibility(View.GONE);
                binding.taxiError.setVisibility(View.GONE);
                break;
            case ERROR:
                binding.taxiEsqueleto.setVisibility(View.GONE);
                binding.taxiContenido.setVisibility(View.GONE);
                binding.taxiError.setVisibility(View.VISIBLE);
                binding.taxiError.conReintento(estado.getMessage(),
                        v -> viewModel.reintentar());
                break;
            case SUCCESS:
            case EMPTY:
                binding.taxiEsqueleto.setVisibility(View.GONE);
                binding.taxiError.setVisibility(View.GONE);
                binding.taxiContenido.setVisibility(View.VISIBLE);
                TaxiViewModel.Estado datos = estado.getData();
                pintarBloques(datos != null ? datos : new TaxiViewModel.Estado(null,
                        new ArrayList<>()));
                break;
        }
    }

    private void pintarBloques(@NonNull TaxiViewModel.Estado estado) {
        boolean hayActivo = estado.tieneActivo();
        binding.taxiBloqueActivo.setVisibility(hayActivo ? View.VISIBLE : View.GONE);
        binding.taxiBloqueLibre.setVisibility(!hayActivo || !reservasDisponibles().isEmpty()
                ? View.VISIBLE : View.GONE);

        if (hayActivo) {
            pintarActivo(estado.activo);
            if (estado.activo.getId().equals(enfoquePendiente)) {
                binding.taxiContenido.post(() -> {
                    if (binding != null) binding.taxiContenido.smoothScrollTo(0, 0);
                });
                enfoquePendiente = null;
            }
        }
        ajustarBloqueLibre();

        pintarHistorial(estado.anteriores);
    }

    /**
     * El bloque sin servicio tiene dos versiones y no una.
     *
     * <p>Sin ninguna reserva no se puede pedir un traslado, asi que ofrecer el
     * boton seria llevar al cliente a un formulario que le va a decir que no.
     * Lo que hace falta ahi es explicarle de donde sale el traslado y mandarlo
     * a sus reservas.
     *
     * <p>Solo rellena el contenido del bloque; cual de los dos bloques se ve lo
     * decide {@link #pintarBloques}, que es quien sabe si hay servicio activo.
     */
    private void ajustarBloqueLibre() {
        List<Booking> disponibles = reservasDisponibles();
        boolean hayReservas = !disponibles.isEmpty();

        binding.taxiLibreTitulo.setText(hayReservas
                ? R.string.taxi_sin_pasajeros_titulo : R.string.taxi_sin_reserva_titulo);
        binding.taxiLibreMensaje.setText(hayReservas
                ? R.string.taxi_sin_pasajeros : R.string.taxi_sin_reserva);
        binding.taxiPedir.setVisibility(hayReservas ? View.VISIBLE : View.GONE);
        binding.taxiIrReservas.setVisibility(hayReservas ? View.GONE : View.VISIBLE);
        UiState<TaxiViewModel.Estado> actual = viewModel.getContenido().getValue();
        boolean hayActivo = actual != null && actual.getData() != null
                && actual.getData().tieneActivo();
        binding.taxiBloqueLibre.setVisibility(!hayActivo || hayReservas ? View.VISIBLE : View.GONE);
    }

    private void pintarActivo(@NonNull TaxiService servicio) {
        binding.taxiEstado.bind(servicio);

        // RF-101: el QR solo cuando el conductor ya llego a recoger.
        binding.taxiQr.setVisibility(servicio.debeMostrarQr() ? View.VISIBLE : View.GONE);

        pintarConductor(servicio);
        pintarMapa(servicio);
        pintarViaje(servicio);
    }

    private void pintarConductor(@NonNull TaxiService servicio) {
        boolean hay = servicio.tieneConductorAsignado();
        binding.taxiBloqueConductor.setVisibility(hay ? View.VISIBLE : View.GONE);
        if (hay) {
            binding.taxiConductor.bind(servicio.getDriver());
            binding.taxiVehiculo.bind(servicio.getDriver() != null
                    ? servicio.getDriver().getVehiculo() : null);
        }
    }

    /**
     * Seguimiento del conductor (RF-099).
     *
     * <p>Se apaga a partir de EN TRASLADO: una vez dentro del taxi, un plano
     * que enfrenta al coche con el punto de recojo ya no dice nada util. Y sin
     * punto de recojo no se enciende nunca, porque el plano se construye desde
     * ahi y quedaria vacio.
     */
    private void pintarMapa(@NonNull TaxiService servicio) {
        TaxiStatus estado = servicio.getEstado();
        boolean yaArranco = estado == TaxiStatus.EN_TRASLADO || estado.isFinished();
        boolean mostrar = servicio.hasRecojo() && !yaArranco;
        binding.taxiTarjetaMapa.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        if (!mostrar) {
            return;
        }

        binding.taxiMapa.limpiarConductor();
        binding.taxiMapa.setRecojo(servicio.getLatRecojo(), servicio.getLngRecojo());

        LocalDateTime reporte = servicio.getUltimaActualizacionUbicacion();
        if (reporte == null) {
            // Todavia nadie reporto posicion: se enseña el punto de recojo y se
            // dice que falta, en vez de inventar un coche en el mapa.
            binding.taxiMapaDistancia.setText(R.string.taxi_ubicacion_esperando);
            binding.taxiMapaActualizado.setVisibility(View.GONE);
            binding.taxiMapaAviso.setVisibility(View.GONE);
            return;
        }

        binding.taxiMapa.setConductor(servicio.getLatConductor(), servicio.getLngConductor());

        double metros = binding.taxiMapa.getDistanciaM();
        binding.taxiMapaDistancia.setText(metros < 120d
                ? getString(R.string.taxi_distancia_cerca)
                : getString(R.string.taxi_distancia, distanciaLegible(metros)));

        binding.taxiMapaActualizado.setVisibility(View.VISIBLE);
        binding.taxiMapaActualizado.setText(getString(R.string.taxi_ubicacion_actualizada,
                DateFormatter.relativo(reporte, LocalDateTime.now())));

        // RC-027: si el dato es viejo hay que decirlo, no presentarlo como si
        // fuera de ahora mismo.
        boolean vieja = servicio.ubicacionDesactualizada(LocalDateTime.now(),
                SEGUNDOS_UBICACION_FRESCA);
        binding.taxiMapaAviso.setVisibility(vieja ? View.VISIBLE : View.GONE);
    }

    /** Metros o kilometros, segun lo que se lea mejor: "850 m", "1,2 km". */
    @NonNull
    private String distanciaLegible(double metros) {
        if (metros < 1000d) {
            return Math.round(metros / 10d) * 10 + " m";
        }
        return String.format(Locale.getDefault(), "%.1f", metros / 1000d)
                .replace('.', ',') + " km";
    }

    private void pintarViaje(@NonNull TaxiService servicio) {
        binding.taxiFilaRecojo.bind(R.string.taxi_recogida, servicio.getOrigen(),
                R.string.perfil_sin_dato);
        binding.taxiFilaDestino.bind(R.string.taxi_destino, servicio.getDestino(),
                R.string.perfil_sin_dato);
        binding.taxiFilaCuando.bind(R.string.taxi_fecha,
                DateFormatter.fechaCorta(servicio.getFecha())
                        + " · " + DateFormatter.hora(servicio.getHora()),
                R.string.perfil_sin_dato);
        binding.taxiFilaPasajeros.bind(R.string.taxi_pasajeros,
                getResources().getQuantityString(R.plurals.taxi_pasajeros,
                        servicio.getNumPasajeros(), servicio.getNumPasajeros()),
                R.string.perfil_sin_dato);
        binding.taxiFilaPrecio.bind(R.string.taxi_precio,
                servicio.isGratuito() ? getString(R.string.taxi_gratuito)
                        : PriceFormatter.format(servicio.getPrecio()),
                R.string.perfil_sin_dato);

        binding.taxiIdaVuelta.setVisibility(servicio.isIdaYVuelta()
                ? View.VISIBLE : View.GONE);
        binding.taxiIncluido.setVisibility(servicio.isGratuito()
                ? View.VISIBLE : View.GONE);
    }

    private void pintarHistorial(@NonNull List<TaxiService> servicios) {
        binding.taxiSeccionHistorial.setVisibility(servicios.isEmpty()
                ? View.GONE : View.VISIBLE);
        binding.taxiHistorial.removeAllViews();

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (TaxiService servicio : servicios) {
            TaxiRequestCardView tarjeta = (TaxiRequestCardView) inflador.inflate(
                    R.layout.item_taxi_historial, binding.taxiHistorial, false);
            LinearLayout.LayoutParams parametros = (LinearLayout.LayoutParams)
                    tarjeta.getLayoutParams();
            if (binding.taxiHistorial.getChildCount() > 0) {
                parametros.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.space_sm);
            }
            tarjeta.bind(servicio);
            tarjeta.setOnValorar(v -> abrirValoracion(servicio.getId()));
            tarjeta.setOnAceptar(null);
            tarjeta.setOnClickListener(v -> {
                if (servicio.getEstado().isActive()) {
                    viewModel.enfocarServicio(servicio.getId());
                    binding.taxiContenido.smoothScrollTo(0, 0);
                }
            });
            binding.taxiHistorial.addView(tarjeta);
            if (servicio.getId().equals(enfoquePendiente)) {
                enfoquePendiente = null;
                tarjeta.post(() -> {
                    if (binding != null) binding.taxiContenido.smoothScrollTo(0,
                            binding.taxiSeccionHistorial.getTop() + binding.taxiHistorial.getTop()
                                    + tarjeta.getTop());
                });
            }
        }
    }

    private void abrirValoracion(@NonNull String taxiId) {
        if (getChildFragmentManager().findFragmentByTag(TAG_VALORAR) != null) return;
        ValorarTaxiSheet.newInstance(taxiId).show(getChildFragmentManager(), TAG_VALORAR);
    }

    // ------------------------------------------------------------------
    //  Formulario
    // ------------------------------------------------------------------

    private void abrirFormulario() {
        viewModel.consumirSolicitud();
        viewModel.setFormularioAbierto(true);
        binding.taxiFormulario.setVisibility(View.VISIBLE);
        binding.taxiFormError.setVisibility(View.GONE);

        // Si solo hay una reserva posible, elegirla ya: obligar a pulsar la
        // unica opcion que existe es un paso que no decide nada.
        List<Booking> disponibles = reservasDisponibles();
        if (reservaElegida == null && disponibles.size() == 1) {
            elegirReserva(disponibles.get(0));
        }
    }

    private void cerrarFormulario() {
        if (viewModel.isEnviando()) return;
        guardarBorrador();
        viewModel.setFormularioAbierto(false);
        binding.taxiFormulario.setVisibility(View.GONE);
    }

    private void pintarReservas(@NonNull UiState<List<Booking>> estado) {
        binding.taxiReservas.removeAllViews();
        tarjetasReserva.clear();

        List<Booking> disponibles = reservasDisponibles();
        boolean hay = !disponibles.isEmpty();
        binding.taxiFormSinReserva.setVisibility(hay ? View.GONE : View.VISIBLE);
        binding.taxiFormSinReserva.setText(estado.isError() ? estado.getMessage()
                : getString(estado.isLoading() ? R.string.taxi_real_cargando_reservas
                : R.string.taxi_real_reserva_requerida));
        binding.taxiFormReintentar.setVisibility(estado.isError() ? View.VISIBLE : View.GONE);
        binding.taxiReservas.setVisibility(hay ? View.VISIBLE : View.GONE);

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (Booking reserva : disponibles) {
            BookingSummaryView tarjeta = (BookingSummaryView) inflador.inflate(
                    R.layout.item_reserva_elegible, binding.taxiReservas, false);
            LinearLayout.LayoutParams parametros = (LinearLayout.LayoutParams)
                    tarjeta.getLayoutParams();
            if (binding.taxiReservas.getChildCount() > 0) {
                parametros.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.space_sm);
            }
            tarjeta.setTag(reserva.getId());
            tarjeta.bind(reserva);
            tarjeta.setOnClickListener(v -> elegirReserva(reserva));
            binding.taxiReservas.addView(tarjeta);
            tarjetasReserva.add(tarjeta);
        }

        // Si la reserva elegida ya no esta en la lista —se cancelo desde otra
        // pantalla— la seleccion se cae con ella. Se compara por identificador
        // y no por instancia: la lista se reconstruye en cada refresco y dar por
        // hecho que trae los mismos objetos es apostar a como esta implementado
        // el repositorio.
        reservaElegida = viewModel.getReservaElegida();
        marcarReservaElegida();
        configurarPasajeros();

        // El bloque sin servicio depende de esta misma lista.
        ajustarBloqueLibre();
    }

    @NonNull
    private List<Booking> reservasDisponibles() {
        UiState<List<Booking>> estado = viewModel.getReservas().getValue();
        if (estado == null || estado.getData() == null) {
            return new ArrayList<>();
        }
        return estado.getData();
    }

    private void elegirReserva(@NonNull Booking reserva) {
        if (viewModel.isEnviando()) return;
        guardarBorrador();
        viewModel.elegirReserva(reserva);
        reservaElegida = viewModel.getReservaElegida();
        restaurarBorrador();
        marcarReservaElegida();
        configurarPasajeros();
    }

    private void marcarReservaElegida() {
        for (BookingSummaryView tarjeta : tarjetasReserva) {
            tarjeta.setElegida(tarjeta.getTag() != null
                    && tarjeta.getTag().equals(reservaElegida != null
                            ? reservaElegida.getId() : null));
        }
    }

    /**
     * La direccion de recojo la propone el hotel de la reserva.
     *
     * <p>La referencia es editable; el punto geográfico sigue siendo el hotel.
     */
    private void pintarHotel(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            binding.taxiFormPrecio.setText(R.string.taxi_real_precio_reserva);
            return;
        }
        switch (estado.getStatus()) {
            case SUCCESS:
                binding.taxiCampoRecogida.setError(null);
                binding.taxiFormPrecio.setText(viewModel.esGratuito()
                        ? getString(R.string.taxi_real_precio_gratis)
                        : getString(R.string.taxi_real_precio_fijo,
                        PriceFormatter.format(TaxiService.TARIFA_AEROPUERTO)));
                reservaElegida = viewModel.getReservaElegida();
                restaurarBorrador();
                configurarPasajeros();
                break;
            case ERROR:
                // Sin hotel no hay coordenadas, y sin coordenadas el repositorio
                // rechaza la solicitud: mejor decirlo ahora que al confirmar.
                binding.taxiCampoRecogida.setError(estado.getMessage());
                binding.taxiFormPrecio.setText(R.string.taxi_real_precio_reserva);
                break;
            default:
                binding.taxiFormPrecio.setText(R.string.taxi_real_precio_cargando);
                break;
        }
    }

    private void pintarFechaHora() {
        binding.taxiValorFecha.setText(fecha != null
                ? DateFormatter.fechaCorta(fecha) : getString(R.string.taxi_fecha));
        binding.taxiValorHora.setText(DateFormatter.hora(hora));
        binding.taxiValorFecha.setTextColor(colorDe(fecha != null));
        binding.taxiValorHora.setTextColor(colorDe(hora != null));
    }

    private int colorDe(boolean hayValor) {
        return requireContext().getColor(hayValor
                ? R.color.colorTextPrimary : R.color.colorTextSecondary);
    }

    private void elegirFecha() {
        if (viewModel.isEnviando()) return;
        if (reservaElegida != null && reservaElegida.getFechaSalida().isBefore(LocalDate.now())) {
            avisar(getString(R.string.taxi_real_reserva_requerida));
            return;
        }
        LocalDate minimo = LocalDate.now();
        if (reservaElegida != null && reservaElegida.getFechaEntrada().isAfter(minimo)) {
            minimo = reservaElegida.getFechaEntrada();
        }
        final long desde = minimo.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        final long hasta = (reservaElegida != null ? reservaElegida.getFechaSalida()
                : minimo.plusYears(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        CalendarConstraints restricciones = new CalendarConstraints.Builder()
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(desde), DateValidatorPointBackward.before(hasta))))
                .setStart(desde)
                .setEnd(hasta)
                .build();
        MaterialDatePicker.Builder<Long> constructor = MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(R.string.taxi_fecha)
                .setPositiveButtonText(R.string.accion_aplicar)
                .setNegativeButtonText(R.string.accion_cancelar)
                .setCalendarConstraints(restricciones);
        if (fecha != null && !fecha.isBefore(minimo)
                && (reservaElegida == null || !fecha.isAfter(reservaElegida.getFechaSalida()))) {
            constructor.setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        MaterialDatePicker<Long> selector = constructor.build();
        selector.addOnPositiveButtonClickListener(millis -> {
            if (millis != null) {
                fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
                pintarFechaHora();
                guardarBorrador();
            }
        });
        selector.show(getChildFragmentManager(), TAG_FECHA);
    }

    private void elegirHora() {
        if (viewModel.isEnviando()) return;
        MaterialTimePicker selector = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText(R.string.taxi_hora)
                .setPositiveButtonText(R.string.accion_aplicar)
                .setNegativeButtonText(R.string.accion_cancelar)
                .setHour(hora != null ? hora.getHour() : 9)
                .setMinute(hora != null ? hora.getMinute() : 0)
                .build();
        selector.addOnPositiveButtonClickListener(v -> {
            hora = LocalTime.of(selector.getHour(), selector.getMinute());
            pintarFechaHora();
            guardarBorrador();
        });
        selector.show(getChildFragmentManager(), TAG_HORA);
    }

    private void confirmar() {
        if (viewModel.isEnviando()) return;
        CharSequence recogida = binding.taxiRecogida.getText();
        CharSequence destino = binding.taxiDestino.getText();

        if (reservaElegida == null) {
            avisar(getString(R.string.taxi_falta_reserva));
            return;
        }
        if (!viewModel.puedeSolicitar()) {
            // La reserva esta elegida pero su hotel aun no llego, o fallo.
            avisar(getString(R.string.taxi_falta_ubicacion));
            return;
        }
        if (recogida == null || recogida.toString().trim().isEmpty()
                || destino == null || destino.toString().trim().isEmpty()) {
            avisar(getString(R.string.taxi_faltan_direcciones));
            return;
        }
        if (fecha == null || hora == null) {
            avisar(getString(R.string.taxi_falta_horario));
            return;
        }

        binding.taxiFormError.setVisibility(View.GONE);

        viewModel.solicitar(recogida.toString(), destino.toString(), fecha, hora,
                pasajeros, binding.taxiIdaVueltaSwitch.isChecked());
    }

    private void pintarSolicitud(@Nullable UiState<TaxiService> estado) {
        boolean cargando = estado != null && estado.isLoading();
        binding.taxiConfirmar.setEnabled(!cargando);
        binding.taxiCancelar.setEnabled(!cargando);
        binding.taxiRecogida.setEnabled(!cargando);
        binding.taxiDestino.setEnabled(!cargando);
        binding.taxiIdaVueltaSwitch.setEnabled(!cargando);
        binding.taxiConfirmar.setText(cargando ? R.string.taxi_real_guardando : R.string.taxi_confirmar);
        if (estado == null) return;
        if (estado.isError()) avisar(estado.getMessage() != null ? estado.getMessage() : "No se pudo guardar el traslado.");
        if (estado.isSuccess()) {
            boolean estabaAbierto = binding.taxiFormulario.getVisibility() == View.VISIBLE;
            binding.taxiFormulario.setVisibility(View.GONE);
            if (estabaAbierto) Snackbar.make(binding.getRoot(), R.string.taxi_real_solicitud_enviada,
                    Snackbar.LENGTH_LONG).show();
            viewModel.consumirSolicitud();
        }
    }

    private void avisar(@NonNull String mensaje) {
        binding.taxiFormError.setText(mensaje);
        binding.taxiFormError.setVisibility(View.VISIBLE);
    }

    private void mostrarQr() {
        if (getChildFragmentManager().findFragmentByTag(QrSheet.TAG) != null) return;
        UiState<TaxiViewModel.Estado> estado = viewModel.getContenido().getValue();
        if (estado == null || estado.getData() == null || estado.getData().activo == null) {
            return;
        }
        QrSheet.newInstance(estado.getData().activo.getId())
                .show(getChildFragmentManager(), QrSheet.TAG);
    }

    // ------------------------------------------------------------------
    //  Estado guardado
    // ------------------------------------------------------------------

    @Override
    public void onSaveInstanceState(@NonNull Bundle salida) {
        guardarBorrador();
        super.onSaveInstanceState(salida);
    }

    private void restaurarBorrador() {
        pintandoBorrador = true;
        pasajeros = viewModel.getPasajeros();
        fecha = viewModel.getFecha();
        hora = viewModel.getHora();
        binding.taxiRecogida.setText(viewModel.getOrigen());
        binding.taxiDestino.setText(viewModel.getDestino());
        binding.taxiPasajeros.setValor(pasajeros);
        binding.taxiIdaVueltaSwitch.setChecked(viewModel.isIdaYVuelta());
        pintarFechaHora();
        pintandoBorrador = false;
    }

    private void configurarPasajeros() {
        int maximo = reservaElegida == null ? MAX_PASAJEROS
                : Math.max(1, Math.min(MAX_PASAJEROS, reservaElegida.getNumHuespedes()));
        binding.taxiPasajeros.configurar(R.string.taxi_pasajeros,
                getString(R.string.taxi_real_pasajeros_limite, maximo), 1, maximo);
        pasajeros = Math.min(pasajeros, maximo);
        binding.taxiPasajeros.setValor(pasajeros);
    }

    private TextWatcher observarTexto() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable texto) { guardarBorrador(); }
        };
    }

    private void guardarBorrador() {
        if (pintandoBorrador || binding == null || viewModel == null || fecha == null || hora == null) return;
        viewModel.guardarFormulario(binding.taxiRecogida.getText() == null ? ""
                        : binding.taxiRecogida.getText().toString(),
                binding.taxiDestino.getText() == null ? "" : binding.taxiDestino.getText().toString(),
                fecha, hora, pasajeros, binding.taxiIdaVueltaSwitch.isChecked());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tarjetasReserva.clear();
        binding = null;
    }
}
