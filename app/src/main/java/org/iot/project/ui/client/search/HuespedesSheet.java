package org.iot.project.ui.client.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetHuespedesBinding;
import org.iot.project.models.SearchQuery;

/**
 * Selector de huespedes (§16).
 *
 * <p>No aplica nada hasta que se pulsa "Aplicar": los tres controles se tocan
 * muchas veces seguidas mientras el usuario busca la combinacion, y aplicar en
 * cada toque lanzaria una busqueda por cada dedo que sube. El resumen se
 * actualiza en vivo para que esos toques tengan respuesta inmediata aunque la
 * busqueda no se haya lanzado.
 */
public class HuespedesSheet extends BottomSheetDialogFragment {

    public static final String TAG = "huespedes";

    private SheetHuespedesBinding binding;
    private SearchViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetHuespedesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_client, SearchViewModel.class);

        SearchQuery consulta = viewModel.getConsulta().getValue();
        if (consulta != null) {
            binding.selectorHuespedes.escribir(consulta);
        }
        refrescarResumen();

        // El resumen se rehace con cada toque, pero la busqueda no se toca.
        binding.selectorHuespedes.setOnCambioListener(this::refrescarResumen);

        binding.hojaCerrar.setOnClickListener(v -> dismiss());

        binding.hojaAplicar.setOnClickListener(v -> {
            SearchQuery actual = viewModel.getConsulta().getValue();
            if (actual != null) {
                // Se escribe sobre un borrador para no dejar la consulta a
                // medias si el usuario descarta la hoja con el gesto de bajada.
                SearchQuery borrador = actual.copy();
                binding.selectorHuespedes.leer(borrador);
                viewModel.setHuespedes(borrador.getAdultos(), borrador.getNinos(),
                        borrador.getHabitaciones());
            }
            dismiss();
        });
    }

    private void refrescarResumen() {
        binding.hojaResumen.setText(binding.selectorHuespedes.getResumen());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
