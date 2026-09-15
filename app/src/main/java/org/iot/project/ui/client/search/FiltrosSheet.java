package org.iot.project.ui.client.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetFiltrosBinding;
import org.iot.project.models.SearchQuery;
import org.iot.project.models.Service;
import org.iot.project.models.TipoHabitacion;
import org.iot.project.ui.components.FilterChipView;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filtros de la busqueda (§18): precio, calificacion, tipo de habitacion y
 * servicios.
 *
 * <p>Edita una copia de la consulta y solo la vuelca al pulsar "Ver
 * resultados". Sin copia, cerrar la hoja deslizando el dedo dejaria aplicados
 * los filtros que el usuario estaba probando — que es justo lo contrario de lo
 * que significa descartar una hoja.
 *
 * <p>Los servicios se piden al catalogo global (regla 21) y no a los servicios
 * del hotel: el filtro pregunta "que tiene el alojamiento", no "que vende
 * este". Es tambien lo que hace que el chip signifique lo mismo en todas las
 * busquedas.
 */
public class FiltrosSheet extends BottomSheetDialogFragment {

    public static final String TAG = "filtros";

    /** Un paso de 50 soles; por encima de 2000 el deslizador se vuelve inutil. */
    private static final float PASO_PRECIO_CORTO = 50f;
    private static final float PASO_PRECIO_LARGO = 100f;
    private static final double UMBRAL_PASO_LARGO = 2000d;

    private static final float RATING_MAXIMO = 10f;

    private SheetFiltrosBinding binding;
    private SearchViewModel viewModel;

    /** Borrador: lo que el usuario esta probando, todavia sin aplicar. */
    private SearchQuery borrador;

    /** Que tipo de habitacion representa cada chip, por identificador. */
    private final Map<Integer, TipoHabitacion> tipoPorChip = new LinkedHashMap<>();

    /**
     * Los servicios del catalogo, en el mismo orden en que se añadieron sus
     * chips. Es lo que permite volver del chip al servicio al rellenar la hoja
     * sin depender de que los indices de un {@code ChipGroup} y del catalogo
     * sigan coincidiendo.
     */
    private final List<Service> serviciosDelCatalogo = new ArrayList<>();

    /** Si se esta rellenando la hoja desde el borrador, no desde el usuario. */
    private boolean rellenando = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetFiltrosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_client, SearchViewModel.class);

        SearchQuery actual = viewModel.getConsulta().getValue();
        borrador = actual != null ? actual.copy() : new SearchQuery();

        configurarPrecio();
        configurarRating();
        configurarTipos();
        configurarServicios();
        escribirBorrador();

        binding.hojaCerrar.setOnClickListener(v -> dismiss());
        binding.hojaLimpiar.setOnClickListener(v -> {
            borrador.limpiarFiltros();
            escribirBorrador();
        });
        binding.hojaAplicar.setOnClickListener(v -> {
            viewModel.aplicarFiltros(borrador);
            dismiss();
        });
    }

    // ------------------------------------------------------------------
    //  Controles
    // ------------------------------------------------------------------

    private void configurarPrecio() {
        float techo = (float) ServiceLocator.hoteles().precioMaximo();
        float paso = techo > UMBRAL_PASO_LARGO ? PASO_PRECIO_LARGO : PASO_PRECIO_CORTO;

        binding.precioSlider.setValueFrom(0f);
        binding.precioSlider.setValueTo(techo);
        binding.precioSlider.setStepSize(paso);

        binding.precioSlider.addOnChangeListener((slider, valor, delUsuario) ->
                aplicarPrecio(valor, techo));
    }

    /**
     * El extremo superior del deslizador significa "cualquier precio", no
     * "hasta el mas caro del catalogo".
     *
     * <p>Si no, el filtro saldria contado como activo sin excluir nada, y el
     * usuario veria "Filtros · 1" sin haber filtrado.
     */
    private void aplicarPrecio(float valor, float techo) {
        borrador.setRangoPrecio(0d, valor >= techo ? 0d : valor);
        binding.precioValor.setText(valor <= 0f || valor >= techo
                ? getString(R.string.filtros_precio_todos)
                : getString(R.string.filtros_precio_valor, PriceFormatter.format(valor)));
    }

    private void configurarRating() {
        binding.ratingSlider.setValueFrom(0f);
        binding.ratingSlider.setValueTo(RATING_MAXIMO);
        binding.ratingSlider.setStepSize(1f);

        binding.ratingSlider.addOnChangeListener((slider, valor, delUsuario) -> {
            borrador.setRatingMinimo(valor);
            binding.ratingValor.setText(valor <= 0f
                    ? getString(R.string.filtros_rating_todos)
                    : getString(R.string.filtros_rating_valor, (int) valor));
        });
    }

    private void configurarTipos() {
        // "Cualquier tipo" se ofrece como chip en lugar de dejar el grupo vacio:
        // un grupo de chips sin ninguno marcado parece una pregunta a medias.
        agregarChipTipo(null, getString(R.string.filtros_habitacion_todas));
        for (TipoHabitacion tipo : TipoHabitacion.values()) {
            agregarChipTipo(tipo, tipo.getDisplayName());
        }

        binding.grupoHabitacion.setOnCheckedStateChangeListener((grupo, marcados) -> {
            if (rellenando) {
                return;
            }
            // El grupo es de seleccion unica y no obliga a marcar: sin ningun
            // chip marcado no se filtra por tipo.
            borrador.setTipoHabitacion(marcados.isEmpty()
                    ? null
                    : tipoPorChip.get(marcados.get(0)));
        });
    }

    private void agregarChipTipo(@Nullable TipoHabitacion tipo, @NonNull String etiqueta) {
        FilterChipView chip = nuevoChip(etiqueta);
        chip.setId(View.generateViewId());
        tipoPorChip.put(chip.getId(), tipo);
        binding.grupoHabitacion.addView(chip);
    }

    private void configurarServicios() {
        for (Service servicio : ServiceLocator.hoteles().catalogoServicios()) {
            FilterChipView chip = nuevoChip(servicio.getName());
            chip.setId(View.generateViewId());
            chip.bind(servicio);
            serviciosDelCatalogo.add(servicio);

            chip.setOnCheckedChangeListener((boton, marcado) -> {
                if (rellenando) {
                    return;
                }
                // El chip y el borrador pueden discrepar cuando el segundo se
                // acaba de limpiar: se compara antes de tocar nada para no
                // invertir el conjunto dos veces por un solo toque.
                if (marcado != borrador.getServiciosSeleccionados()
                        .contains(servicio.getServiceId())) {
                    borrador.toggleServicio(servicio.getServiceId());
                }
            });
            binding.grupoServicios.addView(chip);
        }
    }

    /**
     * Un chip de filtro nuevo, con la separacion que ChipGroup no pone.
     *
     * <p>{@code ChipGroup} coloca los chips pegados unos a otros: no reparte
     * margenes. Sin esto, los chips de servicios se leen como una sola palabra.
     */
    @NonNull
    private FilterChipView nuevoChip(@NonNull String etiqueta) {
        FilterChipView chip = new FilterChipView(requireContext());
        chip.setText(etiqueta);

        ChipGroup.LayoutParams params = new ChipGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int separacion = getResources().getDimensionPixelSize(R.dimen.space_sm);
        params.setMarginEnd(separacion);
        params.bottomMargin = separacion;
        chip.setLayoutParams(params);
        return chip;
    }

    // ------------------------------------------------------------------
    //  Borrador -> controles
    // ------------------------------------------------------------------

    /**
     * Vuelca el borrador en los controles.
     *
     * <p>Mientras dura, {@code rellenando} esta encendido: mover un control
     * dispara su oyente, y sin la bandera rellenar la hoja se confundiria con
     * el usuario cambiando algo — el deslizador de precio se recortaria a si
     * mismo al fijarle un valor.
     */
    private void escribirBorrador() {
        rellenando = true;
        try {
            float techo = (float) ServiceLocator.hoteles().precioMaximo();
            float precio = borrador.getPrecioMax() > 0
                    ? (float) borrador.getPrecioMax()
                    : techo;
            binding.precioSlider.setValue(precio);
            aplicarPrecio(precio, techo);

            binding.ratingSlider.setValue(borrador.getRatingMinimo());
            binding.ratingValor.setText(borrador.getRatingMinimo() <= 0f
                    ? getString(R.string.filtros_rating_todos)
                    : getString(R.string.filtros_rating_valor,
                            (int) borrador.getRatingMinimo()));

            marcarTipo(borrador.getTipoHabitacion());
            marcarServicios(borrador.getServiciosSeleccionados());
        } finally {
            rellenando = false;
        }
    }

    /**
     * Marca el chip del tipo elegido; con {@code tipo} nulo, el de "cualquier
     * tipo".
     *
     * <p>Se marca uno siempre, tambien cuando no se filtra: un grupo de chips
     * con ninguno marcado parece una pregunta a medio contestar, y aqui "sin
     * filtrar" si es una respuesta.
     */
    private void marcarTipo(@Nullable TipoHabitacion tipo) {
        for (int i = 0; i < binding.grupoHabitacion.getChildCount(); i++) {
            FilterChipView chip = (FilterChipView) binding.grupoHabitacion.getChildAt(i);
            chip.setChecked(tipoPorChip.get(chip.getId()) == tipo);
        }
    }

    private void marcarServicios(@NonNull Set<String> seleccionados) {
        for (int i = 0; i < binding.grupoServicios.getChildCount(); i++) {
            FilterChipView chip = (FilterChipView) binding.grupoServicios.getChildAt(i);
            chip.setChecked(seleccionados.contains(serviciosDelCatalogo.get(i).getServiceId()));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
