package org.iot.project.ui.driver.qr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.ResultCallback;
import org.iot.project.databinding.SheetValidarCodigoBinding;
import org.iot.project.models.TaxiService;
import org.iot.project.ui.driver.home.DriverHomeViewModel;

/**
 * Cierre del traslado con el codigo que dicta el cliente (RF-102 a RF-104,
 * RF-110).
 *
 * <p>Es la unica puerta a FINALIZADO: el conductor no tiene un boton de
 * "terminar" en la portada, y aunque lo tuviera
 * {@code TaxiService.avanzarPorConductor} lo rechazaria.
 *
 * <p>Igual que {@code ValorarTaxiSheet}, la hoja no habla con el repositorio:
 * le pide el ViewModel a la pantalla que la abrio. Asi los mocks siguen fuera
 * de las vistas (§49) y el refresco de la portada —que tiene que volver a la
 * cara libre— ocurre en un solo sitio.
 */
public class ValidarCodigoSheet extends BottomSheetDialogFragment {

    public static final String TAG = "validar_codigo";

    private static final String ARG_TAXI = "taxiId";

    private SheetValidarCodigoBinding binding;
    private DriverHomeViewModel viewModel;

    public static ValidarCodigoSheet newInstance(@NonNull String taxiId) {
        ValidarCodigoSheet hoja = new ValidarCodigoSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_TAXI, taxiId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetValidarCodigoBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el de la portada: cerrar el servicio cambia su estado,
        // y ese estado lo pinta ella.
        viewModel = new ViewModelProvider(requireParentFragment())
                .get(DriverHomeViewModel.class);

        binding.validarCerrar.setOnClickListener(v -> dismiss());
        binding.validarConfirmar.setOnClickListener(v -> confirmar());
    }

    private void confirmar() {
        String taxiId = getArguments() != null ? getArguments().getString(ARG_TAXI) : null;
        if (taxiId == null) {
            dismiss();
            return;
        }

        CharSequence texto = binding.validarCodigo.getText();
        String codigo = texto == null ? "" : texto.toString().trim();
        if (codigo.isEmpty()) {
            binding.validarCampo.setError(getString(R.string.driver_codigo_campo));
            return;
        }
        binding.validarCampo.setError(null);

        binding.validarConfirmar.setEnabled(false);
        binding.validarError.setVisibility(View.GONE);

        viewModel.validarCodigo(taxiId, codigo, new ResultCallback<TaxiService>() {
            @Override
            public void onExito(@NonNull TaxiService dato) {
                // El aviso es un Toast y no el Snackbar del ViewModel porque la
                // hoja se cierra en este mismo instante: el Snackbar vive en la
                // vista de la portada y apareceria a la espalda de la lista.
                Toast.makeText(requireContext(), R.string.driver_servicio_cerrado,
                        Toast.LENGTH_LONG).show();
                dismiss();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                // RF-103: un codigo que no es el de este servicio no cambia
                // nada. La hoja se queda abierta para poder reintentar sin
                // salir y volver a entrar.
                binding.validarConfirmar.setEnabled(true);
                binding.validarError.setText(mensaje);
                binding.validarError.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
