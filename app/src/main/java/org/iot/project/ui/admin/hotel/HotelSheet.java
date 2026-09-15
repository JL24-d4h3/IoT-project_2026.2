package org.iot.project.ui.admin.hotel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetHotelBinding;
import org.iot.project.models.Hotel;

import java.util.Locale;

/**
 * Formulario de los datos del hotel (§42, RF-010).
 *
 * <p>Recibe lo que hay que editar por argumentos y no busca el hotel en el
 * ViewModel: los cinco datos son cadenas y números, que sí caben en un
 * paquete, y así la hoja se puede abrir con lo que hay en pantalla sin
 * depender de que el hotel esté cargado en un sitio concreto.
 *
 * <p>Quien guarda es el ViewModel de la pantalla —esta hoja no tiene
 * repositorio propio— y el resultado llega por un canal que solo lee ella. Al
 * salir bien se cierra sin avisar: un Snackbar dentro de una hoja que se está
 * cerrando desaparece con ella, y la confirmación es la pantalla de detrás,
 * que ya se repintó con los datos nuevos.
 */
public class HotelSheet extends BottomSheetDialogFragment {

    public static final String TAG = "hotel";

    private static final String ARG_NOMBRE = "nombre";
    private static final String ARG_DESCRIPCION = "descripcion";
    private static final String ARG_DIRECCION = "direccion";
    private static final String ARG_LATITUD = "latitud";
    private static final String ARG_LONGITUD = "longitud";

    private SheetHotelBinding binding;
    private HotelDatosViewModel viewModel;

    /** La hoja, con los datos que hay que editar. */
    @NonNull
    public static HotelSheet para(@NonNull Hotel hotel) {
        HotelSheet hoja = new HotelSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_NOMBRE, hotel.getNombre());
        argumentos.putString(ARG_DESCRIPCION, hotel.getDescripcion());
        argumentos.putString(ARG_DIRECCION, hotel.getDireccion());
        argumentos.putDouble(ARG_LATITUD, hotel.getLatitud());
        argumentos.putDouble(ARG_LONGITUD, hotel.getLongitud());
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetHotelBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, HotelDatosViewModel.class);

        binding.hotelCerrar.setOnClickListener(v -> dismiss());
        binding.hotelGuardar.setOnClickListener(v -> guardar());

        rellenar();

        // Se limpia antes de observar: LiveData entrega su último valor al
        // registrarse, y sin esto una hoja recién abierta vería el éxito del
        // guardado anterior y se cerraría sola.
        viewModel.limpiarGuardado();
        viewModel.getGuardado().observe(getViewLifecycleOwner(), this::pintar);
    }

    /** Pone en los campos lo que el hotel tiene ahora. */
    private void rellenar() {
        Bundle argumentos = getArguments();
        if (argumentos == null) {
            return;
        }
        binding.hotelNombre.setText(argumentos.getString(ARG_NOMBRE, ""));
        binding.hotelDescripcion.setText(argumentos.getString(ARG_DESCRIPCION, ""));
        binding.hotelDireccion.setText(argumentos.getString(ARG_DIRECCION, ""));

        // Las coordenadas se escriben con cuatro decimales, que es lo que la
        // pantalla enseña: si aquí salieran los quince que guarda un double, el
        // administrador que solo venía a cambiar el nombre vería las
        // coordenadas cambiar de aspecto sin haberlas tocado.
        binding.hotelLatitud.setText(numero(argumentos.getDouble(ARG_LATITUD)));
        binding.hotelLongitud.setText(numero(argumentos.getDouble(ARG_LONGITUD)));
    }

    @NonNull
    private static String numero(double valor) {
        return String.format(Locale.getDefault(), "%.4f", valor);
    }

    // ------------------------------------------------------------------ Guardar

    /**
     * Valida y guarda.
     *
     * <p>Se leen los campos todos antes de decidir, para que los errores salgan
     * de una vez en vez de uno por intento. Lo que se comprueba aquí es lo que
     * se puede comprobar mirando un campo: que no esté vacío y que sea un
     * número. Que el nombre y la dirección sean obligatorios de verdad, y que
     * las coordenadas caigan dentro del mundo, lo decide el repositorio.
     */
    private void guardar() {
        binding.hotelNombreCampo.setError(null);
        binding.hotelDireccionCampo.setError(null);
        binding.hotelLatitudCampo.setError(null);
        binding.hotelLongitudCampo.setError(null);

        String nombre = requerido(binding.hotelNombre, binding.hotelNombreCampo,
                R.string.admin_hotel_nombre_obligatorio);
        String direccion = requerido(binding.hotelDireccion, binding.hotelDireccionCampo,
                R.string.admin_hotel_direccion_obligatoria);
        Double latitud = coordenada(binding.hotelLatitud, binding.hotelLatitudCampo, -90d, 90d);
        Double longitud = coordenada(binding.hotelLongitud, binding.hotelLongitudCampo, -180d, 180d);

        if (nombre == null || direccion == null || latitud == null || longitud == null) {
            return;
        }
        CharSequence descripcion = binding.hotelDescripcion.getText();
        viewModel.guardarDatos(nombre,
                descripcion != null ? descripcion.toString().trim() : "",
                direccion, latitud, longitud);
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

    /**
     * Una coordenada, obligatoria y dentro de su rango.
     *
     * <p>El rango se comprueba también en el repositorio, que es donde vive la
     * regla; aquí está para que el error salga en el campo que lo causó en vez
     * de en un aviso al pie de la hoja. La coma se acepta como separador
     * decimal porque es lo que ofrece el teclado en español.
     */
    @Nullable
    private Double coordenada(@NonNull EditText campo, @NonNull TextInputLayout marco,
                              double minimo, double maximo) {
        CharSequence contenido = campo.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            marco.setError(getString(R.string.admin_hotel_ubicacion_obligatoria));
            return null;
        }
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (numero < minimo || numero > maximo) {
                marco.setError(getString(R.string.admin_hotel_coordenada_rango));
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            marco.setError(getString(R.string.admin_hotel_coordenada_invalida));
            return null;
        }
    }

    // ------------------------------------------------------------------ Estado

    private void pintar(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.hotelGuardar.setEnabled(false);
                break;
            case SUCCESS:
                binding.hotelGuardar.setEnabled(true);
                // Sin aviso aquí: la hoja se cierra y el Snackbar se iría con
                // ella. La pantalla de detrás avisa y ya está repintada.
                dismiss();
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.hotelGuardar.setEnabled(true);
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
