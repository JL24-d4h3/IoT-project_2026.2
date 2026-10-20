package org.iot.project.ui.client.taxi;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.ResultCallback;
import org.iot.project.core.UiState;
import org.iot.project.databinding.SheetValorarTaxiBinding;
import org.iot.project.models.TaxiService;

/**
 * Valoracion de un traslado terminado (RF-105).
 *
 * <p>Es una hoja y no una pantalla porque la valoracion del taxi tiene un solo
 * dato. La escala es la misma de §10 regla 9 —de 1 a 10— y por eso el numero va
 * siempre a la vista junto al deslizador.
 *
 * <p>Se valora una sola vez: el servicio terminado y sin valorar es el unico
 * que llega hasta aqui, porque es el unico que enseña el boton. Si aun asi
 * llegara repetido —dos toques seguidos—, el repositorio lo rechaza y el
 * mensaje se enseña en la propia hoja en vez de cerrarla.
 */
public class ValorarTaxiSheet extends BottomSheetDialogFragment {

    public static final String TAG = "valorar_taxi";

    private static final String ARG_TAXI = "taxiId";

    private SheetValorarTaxiBinding binding;
    private ValoracionViewModel formulario;

    /** Conserva el envío sin retener vistas de la hoja. */
    public static class ValoracionViewModel extends ViewModel {
        final SavedStateHandle guardado;
        final MutableLiveData<UiState<TaxiService>> envio = new MutableLiveData<>();
        boolean enviando;

        public ValoracionViewModel(@NonNull SavedStateHandle guardado) { this.guardado = guardado; }

        float getNota() {
            Float nota = guardado.get("notaTaxi");
            return nota == null ? 10f : nota;
        }

        void enviar(TaxiViewModel taxis, String taxiId) {
            if (enviando) return;
            enviando = true;
            envio.setValue(UiState.loading());
            taxis.valorar(taxiId, getNota(), new ResultCallback<TaxiService>() {
                @Override public void onExito(@NonNull TaxiService dato) {
                    enviando = false;
                    envio.setValue(UiState.success(dato));
                }
                @Override public void onError(@NonNull String mensaje) {
                    enviando = false;
                    envio.setValue(UiState.error(mensaje));
                }
            });
        }
    }

    public static ValorarTaxiSheet newInstance(@NonNull String taxiId) {
        ValorarTaxiSheet hoja = new ValorarTaxiSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_TAXI, taxiId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetValorarTaxiBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el de la pantalla que abrio la hoja: la valoracion
        // cambia el estado del servicio, y ese estado lo pinta el historial.
        TaxiViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TaxiViewModel.class);
        formulario = new ViewModelProvider(this).get(ValoracionViewModel.class);

        binding.valorarTaxiCerrar.setOnClickListener(v -> dismiss());

        binding.valorarTaxiSlider.setValue(formulario.getNota());
        binding.valorarTaxiSlider.addOnChangeListener((deslizador, valor, delUsuario) -> {
            formulario.guardado.set("notaTaxi", valor);
            binding.valorarTaxiValor.setValorEntero(Math.round(valor));
        });
        binding.valorarTaxiValor.setValorEntero(Math.round(binding.valorarTaxiSlider.getValue()));

        binding.valorarTaxiEnviar.setOnClickListener(v -> enviar(viewModel));
        formulario.envio.observe(getViewLifecycleOwner(), estado -> {
            binding.valorarTaxiEnviar.setEnabled(!estado.isLoading());
            binding.valorarTaxiSlider.setEnabled(!estado.isLoading());
            binding.valorarTaxiCerrar.setEnabled(!estado.isLoading());
            setCancelable(!estado.isLoading());
            binding.valorarTaxiError.setVisibility(estado.isError() ? View.VISIBLE : View.GONE);
            if (estado.isError()) binding.valorarTaxiError.setText(estado.getMessage());
            if (estado.isSuccess()) dismiss();
        });
    }

    private void enviar(@NonNull TaxiViewModel viewModel) {
        String taxiId = getArguments() != null ? getArguments().getString(ARG_TAXI) : null;
        if (taxiId == null) {
            dismiss();
            return;
        }

        formulario.enviar(viewModel, taxiId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
