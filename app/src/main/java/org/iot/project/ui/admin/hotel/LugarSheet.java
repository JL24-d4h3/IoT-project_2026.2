package org.iot.project.ui.admin.hotel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetLugarBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.NearbyPlace;

/**
 * Registrar un lugar de interés cercano (§42, RF-011).
 *
 * <p>No hay hoja de edición porque no hay nada que editar: RF-011 solo pide
 * registrarlos, y el repositorio no tiene operación para quitarlos. Una hoja
 * que solo sabe agregar no necesita recibir nada, y por eso {@link #nueva()} no
 * lleva argumentos.
 *
 * <p>Quien registra es el ViewModel de la pantalla, y el resultado llega por un
 * canal que solo lee esta hoja. Al salir bien se cierra sin avisar: un Snackbar
 * dentro de una hoja que se está cerrando desaparece con ella, y la
 * confirmación es la lista de detrás, que ya tiene el lugar nuevo.
 */
public class LugarSheet extends BottomSheetDialogFragment {

    public static final String TAG = "lugar";

    private SheetLugarBinding binding;
    private HotelDatosViewModel viewModel;

    /** La hoja, vacía. */
    @NonNull
    public static LugarSheet nueva() {
        return new LugarSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetLugarBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, HotelDatosViewModel.class);

        binding.lugarCerrar.setOnClickListener(v -> {
            if (!viewModel.estaEscribiendo()) {
                viewModel.limpiarLugar();
                dismiss();
            }
        });
        binding.lugarGuardar.setOnClickListener(v -> guardar());

        viewModel.getLugar().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOcupado().observe(getViewLifecycleOwner(), ocupado -> pintarEspera());
    }

    @Override
    public void onStart() {
        super.onStart();
        pintarEspera();
    }

    // ------------------------------------------------------------------ Guardar

    /**
     * Valida y registra el lugar.
     *
     * <p>Se leen los tres campos antes de decidir, para que los errores salgan
     * de una vez en vez de uno por intento. Lo que se comprueba aquí es lo que
     * se puede comprobar mirando un campo; que el nombre no sea solo espacios y
     * que la distancia sea de verdad mayor que cero lo decide el repositorio,
     * que es donde vive la regla.
     */
    private void guardar() {
        if (!viewModel.puedeModificar()) return;
        binding.lugarNombreCampo.setError(null);
        binding.lugarTipoCampo.setError(null);
        binding.lugarDistanciaCampo.setError(null);

        String nombre = requerido(binding.lugarNombre, binding.lugarNombreCampo,
                R.string.admin_hotel_lugar_nombre_obligatorio);
        String tipo = requerido(binding.lugarTipo, binding.lugarTipoCampo,
                R.string.admin_hotel_lugar_tipo_obligatorio);
        Double distancia = distancia();

        if (nombre == null || tipo == null || distancia == null) {
            return;
        }
        viewModel.agregarLugar(new NearbyPlace(nombre, tipo, distancia));
    }

    /**
     * La distancia, obligatoria y mayor que cero.
     *
     * <p>La coma se acepta como separador decimal porque es lo que ofrece el
     * teclado en español, y rechazarla obligaría a escribir "1.2" con un
     * teclado que no tiene punto.
     */
    @Nullable
    private Double distancia() {
        CharSequence contenido = binding.lugarDistancia.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            binding.lugarDistanciaCampo.setError(
                    getString(R.string.admin_hotel_lugar_distancia_obligatoria));
            return null;
        }
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (!Double.isFinite(numero) || numero <= 0d) {
                binding.lugarDistanciaCampo.setError(
                        getString(R.string.admin_hotel_lugar_distancia_cero));
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            binding.lugarDistanciaCampo.setError(
                    getString(R.string.admin_hotel_lugar_distancia_invalida));
            return null;
        }
    }

    /** El texto de un campo obligatorio, o {@code null} si está vacío. */
    @Nullable
    private String requerido(@NonNull EditText campo, @NonNull TextInputLayout marco,
                             int errorVacio) {
        CharSequence contenido = campo.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            marco.setError(getString(errorVacio));
            return null;
        }
        return valor;
    }

    // ------------------------------------------------------------------ Estado

    private void pintar(@Nullable UiState<Hotel> estado) {
        pintarEspera();
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                break;
            case SUCCESS:
                // Sin aviso aquí: la hoja se cierra y el Snackbar se iría con
                // ella. El lugar aparece en la lista de detrás.
                dismiss();
                viewModel.limpiarLugar();
                break;
            case EMPTY:
            case ERROR:
            default:
                avisar(estado.getMessage());
                viewModel.limpiarLugar();
                break;
        }
    }

    private void pintarEspera() {
        if (binding == null || viewModel == null) return;
        boolean bloqueado = Boolean.TRUE.equals(viewModel.getOcupado().getValue());
        setCancelable(!bloqueado);
        binding.lugarCerrar.setEnabled(!bloqueado);
        binding.lugarGuardar.setEnabled(viewModel.puedeModificar());
        binding.lugarNombreCampo.setEnabled(!bloqueado);
        binding.lugarTipoCampo.setEnabled(!bloqueado);
        binding.lugarDistanciaCampo.setEnabled(!bloqueado);
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialogo = (BottomSheetDialog) getDialog();
            dialogo.setCanceledOnTouchOutside(!bloqueado);
            dialogo.getBehavior().setDraggable(!bloqueado);
        }
    }

    private void avisar(@Nullable String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) {
            return;
        }
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
