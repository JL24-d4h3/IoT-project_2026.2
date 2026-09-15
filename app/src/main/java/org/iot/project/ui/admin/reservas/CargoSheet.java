package org.iot.project.ui.admin.reservas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetCargoBinding;
import org.iot.project.models.Booking;
import org.iot.project.models.Charge;

/**
 * Cobro adicional sobre una estadía en curso (RF-051, RF-052).
 *
 * <p>Monto, motivo y observación: RF-052 exige los tres, y el modelo
 * {@link Charge} los comprueba también en su constructor. La comprobación está
 * duplicada a propósito —aquí para que el error salga en el campo que lo causó,
 * allí porque un modelo que se puede construir mal acaba construido mal desde
 * cualquier sitio.
 *
 * <p>Quien registra el cobro es el ViewModel del detalle —la hoja no tiene
 * repositorio propio—, y el resultado llega por un canal aparte del que usa la
 * pantalla para cancelar y cobrar. Aquí solo se cierra cuando sale bien, sin
 * avisar: un Snackbar dentro de una hoja que se está cerrando desaparece con
 * ella, y la confirmación es el total actualizado que queda detrás.
 */
public class CargoSheet extends BottomSheetDialogFragment {

    public static final String TAG = "cargo";

    private SheetCargoBinding binding;
    private ReservaAdminViewModel viewModel;

    /** La hoja, para la reserva indicada. */
    @NonNull
    public static CargoSheet para(@Nullable String bookingId) {
        CargoSheet hoja = new CargoSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ReservasAdminFragment.ARG_BOOKING_ID, bookingId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetCargoBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el del grafo, el mismo que usa la pantalla del
        // detalle: es la que tiene los totales en pantalla y la que tiene que
        // recargarlos cuando este cobro entre.
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ReservaAdminViewModel.class);

        binding.cargoCerrar.setOnClickListener(v -> dismiss());
        binding.cargoGuardar.setOnClickListener(v -> guardar());

        // Se limpia antes de observar: LiveData entrega su último valor al
        // registrarse, y sin esto una hoja recién abierta vería el éxito del
        // cobro anterior y se cerraría sola.
        viewModel.limpiarCargo();
        viewModel.getCargo().observe(getViewLifecycleOwner(), this::pintar);
    }

    // ------------------------------------------------------------------ Guardar

    /**
     * Valida y registra el cobro.
     *
     * <p>Se leen los tres campos antes de decidir, para que los errores salgan
     * todos de una vez en vez de uno por intento.
     */
    private void guardar() {
        binding.cargoMontoCampo.setError(null);
        binding.cargoMotivoCampo.setError(null);
        binding.cargoObservacionCampo.setError(null);

        Double monto = monto();
        String motivo = texto(binding.cargoMotivoCampo, binding.cargoMotivo,
                R.string.cargo_motivo_obligatorio);
        String observacion = texto(binding.cargoObservacionCampo, binding.cargoObservacion,
                R.string.cargo_observacion_obligatoria);

        if (monto == null || motivo == null || observacion == null) {
            return;
        }
        viewModel.agregarCargo(new Charge(monto, motivo, observacion));
    }

    /**
     * El monto, obligatorio y mayor que cero.
     *
     * <p>La coma se acepta como separador decimal: es lo que ofrece el teclado
     * en español, y rechazarla obligaría a escribir "45.50" con un teclado que
     * no tiene punto.
     */
    @Nullable
    private Double monto() {
        CharSequence contenido = binding.cargoMonto.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            binding.cargoMontoCampo.setError(getString(R.string.cargo_monto_obligatorio));
            return null;
        }
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (numero <= 0d) {
                binding.cargoMontoCampo.setError(getString(R.string.cargo_monto_cero));
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            binding.cargoMontoCampo.setError(getString(R.string.cargo_monto_invalido));
            return null;
        }
    }

    /** El texto de un campo obligatorio, o {@code null} si está vacío. */
    @Nullable
    private String texto(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                         @StringRes int errorVacio) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            campo.setError(getString(errorVacio));
            return null;
        }
        return valor;
    }

    // ------------------------------------------------------------------ Estado

    private void pintar(@Nullable UiState<Booking> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.cargoGuardar.setEnabled(false);
                break;
            case SUCCESS:
                binding.cargoGuardar.setEnabled(true);
                // Sin aviso aquí: la hoja se cierra y el Snackbar se iría con
                // ella. La pantalla de detrás avisa y recarga los totales.
                dismiss();
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.cargoGuardar.setEnabled(true);
                avisar(estado.getMessage());
                break;
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
