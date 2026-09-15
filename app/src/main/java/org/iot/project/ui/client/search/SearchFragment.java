package org.iot.project.ui.client.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentSearchBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.SearchQuery;
import org.iot.project.ui.common.HotelAdapter;
import org.iot.project.ui.components.LoadingSkeletonView;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Busqueda y resultados (§13 a §19).
 *
 * <p>La pantalla dibuja lo que diga el {@link SearchViewModel}, que se comparte
 * con la pantalla de destino y con las cuatro hojas de dialogo a traves del
 * grafo de navegacion. Aqui no se guarda criterio de busqueda ninguno: si esta
 * pantalla se destruyera y se volviera a crear, la busqueda seguiria siendo la
 * misma.
 *
 * <p>Tiene dos caras —formulario y resultados— y las dos estan montadas en el
 * layout; ensenar una es apagar la otra.
 */
public class SearchFragment extends Fragment {

    /** Cuantos huespedes fantasma dibuja el esqueleto de carga. */
    private static final int ESQUELETOS = 3;

    /** Etiqueta del selector de fechas en el gestor de fragmentos. */
    private static final String TAG_FECHAS = "selector_fechas";

    private FragmentSearchBinding binding;
    private SearchViewModel viewModel;
    private HotelAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_client, SearchViewModel.class);

        InsetUtils.applyTopPadding(binding.header);

        configurarLista();
        configurarFormulario();
        configurarResultados();
        observar();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Si la pantalla se giro con el calendario abierto, el selector vuelve
        // restaurado y sin oyente: los escuchas no se guardan en el estado. Sin
        // volver a engancharlo, el usuario elegiria fechas y no pasaria nada.
        Fragment restaurado = getChildFragmentManager().findFragmentByTag(TAG_FECHAS);
        if (restaurado instanceof MaterialDatePicker) {
            @SuppressWarnings("unchecked")
            MaterialDatePicker<Pair<Long, Long>> selector = (MaterialDatePicker<Pair<Long, Long>>) restaurado;
            engancharSeleccion(selector);
        }
    }

    // ------------------------------------------------------------------
    //  Montaje
    // ------------------------------------------------------------------

    private void configurarLista() {
        adaptador = new HotelAdapter(this::abrirHotel);
        binding.listResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listResults.setAdapter(adaptador);
        binding.listResults.setHasFixedSize(true);
    }

    private void configurarFormulario() {
        binding.rowDestino.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.destinoFragment));

        binding.rowFechas.setOnClickListener(v -> abrirSelectorFechas());

        binding.rowHuespedes.setOnClickListener(v ->
                new HuespedesSheet().show(getChildFragmentManager(), HuespedesSheet.TAG));

        binding.btnBuscar.setOnClickListener(v -> viewModel.buscar());
    }

    // ------------------------------------------------------------------
    //  Fechas (§15)
    // ------------------------------------------------------------------

    /**
     * Abre el calendario de rango de Material.
     *
     * <p>No hay hoja propia para las fechas, a diferencia de huespedes o
     * filtros: entrada y salida no son dos ajustes que se prueban por separado
     * sino una sola decision —cuatro noches— y el selector de rango ya la pide
     * entera, con la navegacion por meses y los dias pasados bloqueados.
     * Meterlo dentro de una hoja seria apilar un dialogo sobre otro.
     */
    private void abrirSelectorFechas() {
        construirSelectorFechas().show(getChildFragmentManager(), TAG_FECHAS);
    }

    @NonNull
    private MaterialDatePicker<Pair<Long, Long>> construirSelectorFechas() {
        CalendarConstraints restricciones = new CalendarConstraints.Builder()
                // No se puede reservar hacia atras: el propio calendario lo
                // impide en vez de dejar elegir y rechazarlo despues (§53).
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker.Builder<Pair<Long, Long>> constructor =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.titulo_fechas)
                        .setPositiveButtonText(R.string.accion_aplicar)
                        .setNegativeButtonText(R.string.accion_cancelar)
                        .setCalendarConstraints(restricciones);

        Pair<Long, Long> seleccion = seleccionActual(restricciones);
        if (seleccion != null) {
            constructor.setSelection(seleccion);
        }

        MaterialDatePicker<Pair<Long, Long>> selector = constructor.build();
        engancharSeleccion(selector);
        return selector;
    }

    /**
     * La seleccion de partida, o {@code null} si no hay ninguna que valga.
     *
     * <p>No basta con que la busqueda traiga fechas: si son anteriores a hoy,
     * el selector las rechaza y falla al abrirse. Se comprueba contra el mismo
     * validador que lleva el calendario en vez de comparar con la fecha de hoy
     * por separado, para que no puedan discrepar.
     */
    @Nullable
    private Pair<Long, Long> seleccionActual(@NonNull CalendarConstraints restricciones) {
        SearchQuery consulta = viewModel.getConsulta().getValue();
        if (consulta == null || !consulta.hasFechas()) {
            return null;
        }
        long entrada = aMillis(consulta.getFechaEntrada());
        long salida = aMillis(consulta.getFechaSalida());

        CalendarConstraints.DateValidator validador = restricciones.getDateValidator();
        if (validador != null && !validador.isValid(entrada)) {
            return null;
        }
        return new Pair<>(entrada, salida);
    }

    /**
     * Conecta el selector con la busqueda.
     *
     * <p>Se llama dos veces: al construirlo y otra vez en {@link #onStart} si
     * el selector reaparece restaurado. Los oyentes no viajan en el estado
     * guardado, asi que girar la pantalla con el calendario abierto lo dejaria
     * mudo; y {@code addOnPositiveButtonClickListener} no reemplaza al
     * anterior, solo acepta uno, de modo que hay que vaciarlo antes.
     */
    private void engancharSeleccion(@NonNull MaterialDatePicker<Pair<Long, Long>> selector) {
        selector.clearOnPositiveButtonClickListeners();
        selector.addOnPositiveButtonClickListener(seleccion -> {
            if (seleccion == null || seleccion.first == null || seleccion.second == null) {
                return;
            }
            // El selector trabaja en milisegundos UTC a medianoche: la zona
            // horaria del aparato no interviene, y convertirla con la local
            // desplazaria un dia la fecha elegida.
            viewModel.setFechas(aFecha(seleccion.first), aFecha(seleccion.second));
        });
    }

    @NonNull
    private static LocalDate aFecha(long millisUtc) {
        return Instant.ofEpochMilli(millisUtc).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static long aMillis(@NonNull LocalDate fecha) {
        return fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private void configurarResultados() {
        // Volver al formulario conserva lo escrito: editar la busqueda es
        // retocarla, no empezarla de cero.
        binding.resumenBarra.setOnClickListener(v -> viewModel.volverAlFormulario());

        binding.btnFiltros.setOnClickListener(v ->
                new FiltrosSheet().show(getChildFragmentManager(), FiltrosSheet.TAG));

        binding.btnOrden.setOnClickListener(v ->
                new OrdenSheet().show(getChildFragmentManager(), OrdenSheet.TAG));

        binding.btnVerMas.setOnClickListener(v -> viewModel.verMas());
    }

    private void observar() {
        viewModel.getEnResultados().observe(getViewLifecycleOwner(), this::pintarCara);
        viewModel.getConsulta().observe(getViewLifecycleOwner(), this::pintarFormulario);
        viewModel.getResultados().observe(getViewLifecycleOwner(), this::pintarResultados);

        viewModel.getCargandoMas().observe(getViewLifecycleOwner(), this::pintarCargandoMas);
        viewModel.getHayMas().observe(getViewLifecycleOwner(), hayMas ->
                binding.btnVerMas.setVisibility(
                        Boolean.TRUE.equals(hayMas) ? View.VISIBLE : View.GONE));
    }

    // ------------------------------------------------------------------
    //  Cara A: formulario
    // ------------------------------------------------------------------

    private void pintarFormulario(@Nullable SearchQuery consulta) {
        if (consulta == null) {
            return;
        }

        pintarDestino(consulta.getDestino());
        pintarFechas(consulta);
        binding.valorHuespedes.setText(consulta.getResumenHuespedes());

        // El boton solo se enciende cuando hay destino y fechas, y mientras no
        // lo esten se explica que falta. Un boton apagado sin motivo se lee
        // como una aplicacion rota (§53).
        boolean listo = consulta.isReadyToSearch();
        binding.btnBuscar.setEnabled(listo);
        binding.formHint.setVisibility(listo ? View.GONE : View.VISIBLE);
        if (!listo) {
            binding.formHint.setText(consulta.getDestino() == null
                    || consulta.getDestino().trim().isEmpty()
                    ? R.string.buscar_falta_destino
                    : R.string.buscar_falta_fechas);
        }

        pintarResumen(consulta);
        pintarChips(consulta);
    }

    private void pintarDestino(@Nullable String destino) {
        boolean hay = destino != null && !destino.trim().isEmpty();
        binding.valorDestino.setText(hay ? destino : getString(R.string.buscar_destino_pista));
        ponerColorSegunValor(binding.valorDestino, hay);
    }

    private void pintarFechas(@NonNull SearchQuery consulta) {
        if (!consulta.hasFechas()) {
            binding.valorEntrada.setText(R.string.buscar_fechas_pista);
            ponerColorSegunValor(binding.valorEntrada, false);
            binding.valorSalida.setText("");
            return;
        }
        LocalDate entrada = consulta.getFechaEntrada();
        LocalDate salida = consulta.getFechaSalida();
        binding.valorEntrada.setText(DateFormatter.fechaConDia(entrada));
        binding.valorSalida.setText(DateFormatter.fechaConDia(salida));
        ponerColorSegunValor(binding.valorEntrada, true);
        ponerColorSegunValor(binding.valorSalida, true);
    }

    /** Un valor sin elegir se pinta como pista; elegido, como dato. */
    private void ponerColorSegunValor(@NonNull TextView vista, boolean hayValor) {
        vista.setTextColor(requireContext().getColor(
                hayValor ? R.color.colorTextPrimary : R.color.colorTextSecondary));
    }

    // ------------------------------------------------------------------
    //  Cara B: resultados
    // ------------------------------------------------------------------

    private void pintarCara(@Nullable Boolean enResultados) {
        boolean resultados = Boolean.TRUE.equals(enResultados);
        binding.formContainer.setVisibility(resultados ? View.GONE : View.VISIBLE);
        binding.resultsContainer.setVisibility(resultados ? View.VISIBLE : View.GONE);
    }

    /** La barra compacta: lo que el usuario pidio, contado en una linea. */
    private void pintarResumen(@NonNull SearchQuery consulta) {
        StringBuilder sb = new StringBuilder();
        if (consulta.getDestino() != null && !consulta.getDestino().trim().isEmpty()) {
            sb.append(consulta.getDestino());
        } else {
            sb.append(getString(R.string.resultados_sin_destino));
        }
        if (consulta.hasFechas()) {
            sb.append(" · ").append(DateFormatter.rango(
                    consulta.getFechaEntrada(), consulta.getFechaSalida()));
        }
        sb.append(" · ").append(consulta.getResumenHuespedes());
        binding.resumenTexto.setText(sb.toString());
    }

    private void pintarChips(@NonNull SearchQuery consulta) {
        int filtros = consulta.getNumFiltrosActivos();
        binding.btnFiltros.setText(filtros == 0
                ? getString(R.string.resultados_filtros)
                : getString(R.string.resultados_filtros_activos, filtros));
        // El numero en el texto no basta para un lector de pantalla: "Filtros · 2"
        // se lee como una frase suelta, sin decir de que son los dos.
        binding.btnFiltros.setContentDescription(filtros == 0
                ? getString(R.string.resultados_filtros)
                : getString(R.string.cd_filtros_activos, filtros));

        binding.btnOrden.setText(
                getString(R.string.resultados_orden_actual, consulta.getOrden().getDisplayName()));
    }

    private void pintarCargandoMas(@Nullable Boolean cargando) {
        boolean enCurso = Boolean.TRUE.equals(cargando);
        binding.btnVerMas.setEnabled(!enCurso);
        binding.btnVerMas.setText(enCurso
                ? R.string.resultados_cargando_mas
                : R.string.resultados_ver_mas);
    }

    private void pintarResultados(@Nullable UiState<List<Hotel>> estado) {
        if (estado == null) {
            return;
        }

        // Se apagan los cuatro y se enciende el que toca: asi no queda un
        // estado anterior visible detras del nuevo (§50).
        binding.skeletonContainer.setVisibility(View.GONE);
        binding.listResults.setVisibility(View.GONE);
        binding.stateEmpty.setVisibility(View.GONE);
        binding.stateError.setVisibility(View.GONE);

        switch (estado.getStatus()) {
            case LOADING:
                mostrarEsqueleto();
                break;

            case SUCCESS:
                binding.listResults.setVisibility(View.VISIBLE);
                binding.resultsCount.setVisibility(View.VISIBLE);
                pintarCuenta(estado.requireData().size());
                adaptador.submitList(estado.getData());
                break;

            case EMPTY:
                binding.resultsCount.setVisibility(View.GONE);
                binding.stateEmpty.setVisibility(View.VISIBLE);
                pintarVacio();
                break;

            case ERROR:
                binding.resultsCount.setVisibility(View.GONE);
                binding.stateError.setVisibility(View.VISIBLE);
                binding.stateError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    /**
     * "24 alojamientos en Cusco".
     *
     * <p>Cuenta lo que hay en pantalla, no lo que hay en total: con paginacion
     * esas dos cifras no coinciden, y decir "24" cuando se ven 10 haria dudar
     * de si la lista se corto.
     */
    private void pintarCuenta(int mostrados) {
        String destino = viewModel.getConsulta().getValue() != null
                ? viewModel.getConsulta().getValue().getDestino()
                : null;
        String cuantos = getResources().getQuantityString(
                R.plurals.resultados_total, mostrados, mostrados);
        binding.resultsCount.setText(destino != null && !destino.trim().isEmpty()
                ? cuantos + " " + getString(R.string.resultados_en, destino)
                : cuantos);
    }

    /**
     * El estado vacio propone algo, y lo que propone depende de por que no hay
     * nada: si el usuario puso filtros, el camino es quitarlos; si no, es
     * cambiar la busqueda (§50).
     */
    private void pintarVacio() {
        SearchQuery consulta = viewModel.getConsulta().getValue();
        boolean conFiltros = consulta != null && consulta.getNumFiltrosActivos() > 0;

        binding.stateEmpty
                .conTitulo(R.string.resultados_vacio_titulo)
                .conMensaje(R.string.resultados_vacio);

        if (conFiltros) {
            binding.stateEmpty.conAccion(R.string.resultados_quitar_filtros,
                    v -> viewModel.limpiarFiltros());
        } else {
            binding.stateEmpty.conAccion(R.string.resultados_editar,
                    v -> viewModel.volverAlFormulario());
        }
    }

    private void mostrarEsqueleto() {
        binding.skeletonContainer.removeAllViews();
        binding.skeletonContainer.setVisibility(View.VISIBLE);

        int separacion = getResources().getDimensionPixelSize(R.dimen.space_md);
        for (int i = 0; i < ESQUELETOS; i++) {
            LoadingSkeletonView esqueleto = new LoadingSkeletonView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // El primero no lleva margen: pegado al borde superior, un margen
            // extra solo empuja la lista hacia abajo sin separar nada.
            params.topMargin = (i == 0) ? 0 : separacion;
            esqueleto.setLayoutParams(params);
            binding.skeletonContainer.addView(esqueleto);
        }
    }

    private void abrirHotel(@NonNull Hotel hotel) {
        Bundle args = new Bundle();
        args.putString("hotelId", hotel.getId());
        Navigation.findNavController(requireView()).navigate(R.id.hotelDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
