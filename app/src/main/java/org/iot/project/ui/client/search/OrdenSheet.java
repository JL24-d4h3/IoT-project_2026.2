package org.iot.project.ui.client.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetOrdenBinding;
import org.iot.project.models.OrdenBusqueda;
import org.iot.project.models.SearchQuery;

/**
 * Orden de los resultados (§19).
 *
 * <p>Sin boton "Aplicar": elegir un orden <em>es</em> la decision. A diferencia
 * de los filtros, donde se prueban varias combinaciones antes de dar por buena
 * una, aqui no hay nada que probar — se toca y se ve.
 *
 * <p>Cada opcion lleva su explicacion porque "Recomendados" y "Mejor valorados"
 * se leen igual hasta que se sabe que el primero pesa la nota por el numero de
 * opiniones.
 */
public class OrdenSheet extends BottomSheetDialogFragment {

    public static final String TAG = "orden";

    private SheetOrdenBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetOrdenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SearchViewModel viewModel = ViewModelGrafo.de(this, R.id.nav_client, SearchViewModel.class);

        SearchQuery consulta = viewModel.getConsulta().getValue();
        OrdenBusqueda actual = consulta != null ? consulta.getOrden() : OrdenBusqueda.RECOMENDADOS;

        for (OrdenBusqueda orden : OrdenBusqueda.values()) {
            binding.ordenOpciones.addView(
                    crearFila(orden, orden == actual, viewModel));
        }

        binding.hojaCerrar.setOnClickListener(v -> dismiss());
    }

    /**
     * Una opcion de la lista.
     *
     * <p>La fila se construye en codigo y no inflando {@code item_orden}: la
     * lista de ordenes vive en el modelo, y escribir cinco filas en el XML
     * obligaria a tocar el diseño cada vez que se añada una forma de ordenar.
     */
    private View crearFila(@NonNull OrdenBusqueda orden, boolean elegido,
                           @NonNull SearchViewModel viewModel) {
        View fila = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_orden, binding.ordenOpciones, false);

        TextView nombre = fila.findViewById(R.id.orden_nombre);
        TextView nota = fila.findViewById(R.id.orden_nota);
        ImageView marca = fila.findViewById(R.id.orden_marca);

        nombre.setText(orden.getDisplayName());
        nota.setText(notaDe(orden));

        // La marca distingue el orden vigente; el hueco se reserva siempre
        // (INVISIBLE y no GONE) para que ninguna fila cambie de ancho al
        // cambiar de orden.
        marca.setVisibility(elegido ? View.VISIBLE : View.INVISIBLE);

        // El estado "elegido" viaja en la seleccion de la fila, que es lo que
        // leen los lectores de pantalla: no hace falta describir la fila a
        // mano, y hacerlo taparia el nombre y la explicacion, que ya son texto.
        fila.setSelected(elegido);

        fila.setOnClickListener(v -> {
            if (!elegido) {
                viewModel.setOrden(orden);
            }
            dismiss();
        });
        return fila;
    }

    /**
     * La explicacion de cada forma de ordenar.
     *
     * <p>Es texto de pantalla, asi que vive en recursos y no en el enum: el
     * modelo guarda como se llama cada orden, no como se explica.
     */
    @StringRes
    private static int notaDe(@NonNull OrdenBusqueda orden) {
        switch (orden) {
            case PRECIO_MENOR:
                return R.string.orden_precio_menor_nota;
            case PRECIO_MAYOR:
                return R.string.orden_precio_mayor_nota;
            case MEJOR_RATING:
                return R.string.orden_mejor_rating_nota;
            case MAS_POPULARES:
                return R.string.orden_mas_populares_nota;
            case RECOMENDADOS:
            default:
                return R.string.orden_recomendados_nota;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
