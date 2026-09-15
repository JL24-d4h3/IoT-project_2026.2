package org.iot.project.ui.client.taxi;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.SheetQrBinding;
import org.iot.project.models.TaxiService;
import org.iot.project.utils.DateFormatter;

/**
 * El codigo QR del cliente (§39, §69).
 *
 * <p>Es lo unico que cierra el servicio (RF-110, RT-016): el conductor lo
 * escanea y el traslado queda terminado. No hay boton de "finalizar" en ninguna
 * parte, y no es un olvido — un boton se puede pulsar desde el sofa.
 *
 * <p>Lee el servicio del ViewModel de la pantalla en vez de recibirlo copiado
 * por argumentos: el conductor puede escanear mientras la hoja esta abierta, y
 * entonces el estado cambia y la hoja tiene que enterarse. Con una copia
 * congelada seguiria enseñando un codigo que ya se uso.
 */
public class QrSheet extends BottomSheetDialogFragment {

    public static final String TAG = "qr_taxi";

    private static final String ARG_TAXI = "taxiId";

    private SheetQrBinding binding;

    public static QrSheet newInstance(@NonNull String taxiId) {
        QrSheet hoja = new QrSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_TAXI, taxiId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetQrBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        binding.qrCerrar.setOnClickListener(v -> dismiss());

        TaxiViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TaxiViewModel.class);

        String taxiId = getArguments() != null ? getArguments().getString(ARG_TAXI) : null;
        if (taxiId == null) {
            dismiss();
            return;
        }

        viewModel.getContenido().observe(getViewLifecycleOwner(), estado -> {
            TaxiService servicio = buscar(estado, taxiId);
            if (servicio == null) {
                // El servicio dejo de estar disponible: no hay nada que enseñar.
                dismiss();
                return;
            }
            if (servicio.getEstado().isFinished()) {
                // El conductor acaba de escanear. La hoja ya no sirve y dejarla
                // abierta invitaria a escanear un codigo que ya se consumio.
                dismiss();
                return;
            }
            pintar(servicio);
        });
    }

    @Nullable
    private static TaxiService buscar(@Nullable UiState<TaxiViewModel.Estado> estado,
                                      @NonNull String taxiId) {
        if (estado == null || estado.getData() == null) {
            return null;
        }
        TaxiViewModel.Estado datos = estado.getData();
        if (datos.activo != null && taxiId.equals(datos.activo.getId())) {
            return datos.activo;
        }
        for (TaxiService anterior : datos.anteriores) {
            if (taxiId.equals(anterior.getId())) {
                return anterior;
            }
        }
        return null;
    }

    private void pintar(@NonNull TaxiService servicio) {
        // El contenido del codigo es el identificador del servicio y nada mas.
        // El nombre del cliente o su telefono no hacen falta para cerrar el
        // traslado, y un QR se lee a distancia y se fotografía.
        boolean dibujado = binding.qrCodigo.setContenido(servicio.getCodigo());

        binding.qrCodigo.setVisibility(dibujado ? View.VISIBLE : View.GONE);
        binding.qrError.setVisibility(dibujado ? View.GONE : View.VISIBLE);
        binding.qrCodigoTexto.setVisibility(dibujado ? View.VISIBLE : View.GONE);
        binding.qrDatos.setVisibility(dibujado ? View.VISIBLE : View.GONE);

        if (!dibujado) {
            return;
        }

        binding.qrCodigo.setContentDescription(getString(R.string.cd_qr_codigo,
                servicio.getCodigo()));
        binding.qrCodigoTexto.setText(servicio.getCodigo());

        binding.qrFilaRecogida.bind(R.string.taxi_recogida, servicio.getOrigen(),
                R.string.perfil_sin_dato);
        binding.qrFilaDestino.bind(R.string.taxi_destino, servicio.getDestino(),
                R.string.perfil_sin_dato);
        binding.qrFilaCuando.bind(R.string.taxi_fecha,
                DateFormatter.fechaCorta(servicio.getFecha())
                        + " · " + DateFormatter.hora(servicio.getHora()),
                R.string.perfil_sin_dato);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
