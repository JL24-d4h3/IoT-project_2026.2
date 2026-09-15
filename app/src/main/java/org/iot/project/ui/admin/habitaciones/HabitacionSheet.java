package org.iot.project.ui.admin.habitaciones;

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
import org.iot.project.databinding.SheetHabitacionBinding;
import org.iot.project.models.Room;

/**
 * Registrar una habitación nueva o editar la que ya existe (§42, RF-014 a
 * RF-018).
 *
 * <p>Los dos modos comparten formulario porque son el mismo: lo único que
 * cambia es el título y que al editar los campos vienen rellenos. Separarlos en
 * dos hojas duplicaría siete campos para que una de ellas empezara en blanco.
 *
 * <p>La hoja <b>construye una habitación nueva</b> al guardar en vez de escribir
 * sobre la que recibió. El repositorio entrega las habitaciones del hotel
 * vivas, no copias: escribir sobre ellas cambiaría el inventario sin pasar por
 * la validación ni por la bitácora, y con solo abrir la hoja y cerrarla sin
 * guardar ya habría quedado tocado lo que el administrador escribiera.
 */
public class HabitacionSheet extends BottomSheetDialogFragment {

    public static final String TAG = "habitacion";

    private static final String ARG_ROOM_ID = "roomId";

    /** Con cuántos adultos empieza una habitación nueva. */
    private static final int DEFAULT_ADULTOS = 2;

    private SheetHabitacionBinding binding;
    private HabitacionesViewModel viewModel;

    /** La habitación que se está editando, o {@code null} si es nueva. */
    @Nullable
    private Room enEdicion;

    /**
     * La hoja, en uno de sus dos modos.
     *
     * @param habitacion la habitación a editar, o {@code null} para registrar una
     */
    @NonNull
    public static HabitacionSheet para(@Nullable Room habitacion) {
        HabitacionSheet hoja = new HabitacionSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_ROOM_ID, habitacion != null ? habitacion.getId() : null);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetHabitacionBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el del grafo: la hoja no tiene lista propia, y lo que
        // tiene que recargarse al guardar es la de la pantalla que la abrió.
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, HabitacionesViewModel.class);

        binding.habitacionCerrar.setOnClickListener(v -> dismiss());
        binding.habitacionGuardar.setOnClickListener(v -> guardar());

        prepararModo();

        // Se limpia antes de observar. LiveData entrega su último valor al
        // registrarse, así que sin esto una hoja recién abierta vería el éxito
        // del guardado anterior y se cerraría sola.
        viewModel.limpiarGuardado();
        viewModel.getGuardado().observe(getViewLifecycleOwner(), this::pintarGuardado);
    }

    // ------------------------------------------------------------------ Modos

    private void prepararModo() {
        Bundle argumentos = getArguments();
        String roomId = argumentos != null ? argumentos.getString(ARG_ROOM_ID) : null;
        enEdicion = roomId == null ? null : viewModel.buscarHabitacion(roomId);

        if (enEdicion == null) {
            // También cuando se pedía editar y la habitación ya no está en la
            // lista: se abre en blanco en vez de cerrarse sin decir nada. Es
            // preferible que el administrador vuelva a escribir a que toque un
            // lápiz y no ocurra nada.
            binding.habitacionTitulo.setText(R.string.admin_habitacion_nueva);
            binding.habitacionAdultos.setText(String.valueOf(DEFAULT_ADULTOS));
            return;
        }
        binding.habitacionTitulo.setText(
                getString(R.string.admin_habitacion_editar, enEdicion.getNumero()));
        escribir(enEdicion);
    }

    /** Vuelca la habitación en los controles. */
    private void escribir(@NonNull Room habitacion) {
        binding.habitacionNumero.setText(habitacion.getNumero());
        binding.habitacionTipo.setText(habitacion.getTipo());
        binding.habitacionAdultos.setText(String.valueOf(habitacion.getCapacidadAdultos()));
        binding.habitacionNinos.setText(habitacion.getCapacidadNinos() > 0
                ? String.valueOf(habitacion.getCapacidadNinos()) : "");
        binding.habitacionArea.setText(habitacion.getAreaM2() > 0d
                ? String.valueOf(habitacion.getAreaM2()) : "");
        // El piso se deja en blanco cuando es cero —planta baja— porque un "0"
        // ahí se lee como un dato, y en blanco se lee como que no aplica.
        binding.habitacionPiso.setText(habitacion.getPiso() > 0
                ? String.valueOf(habitacion.getPiso()) : "");
        binding.habitacionPrecio.setText(String.valueOf(habitacion.getPrecioNoche()));
    }

    // ------------------------------------------------------------------ Guardar

    /**
     * Valida y guarda.
     *
     * <p>Se leen todos los campos antes de decidir, en vez de parar en el
     * primero que falle: los siete están a la vista y marcarlos todos de una vez
     * evita el ir y venir de guardar, corregir, guardar.
     */
    private void guardar() {
        limpiarErrores();

        String numero = texto(binding.habitacionNumeroCampo, binding.habitacionNumero,
                R.string.admin_habitacion_numero_obligatorio);
        String tipo = texto(binding.habitacionTipoCampo, binding.habitacionTipo,
                R.string.admin_habitacion_tipo_obligatorio);
        Integer adultos = entero(binding.habitacionAdultosCampo, binding.habitacionAdultos,
                1, 1, R.string.admin_habitacion_adultos_invalido);
        Integer ninos = entero(binding.habitacionNinosCampo, binding.habitacionNinos,
                0, 0, R.string.admin_habitacion_ninos_invalido);
        Integer piso = entero(binding.habitacionPisoCampo, binding.habitacionPiso,
                0, 0, R.string.admin_habitacion_piso_invalido);
        Double area = decimal(binding.habitacionAreaCampo, binding.habitacionArea,
                R.string.admin_habitacion_area_obligatoria,
                R.string.admin_habitacion_area_cero,
                R.string.admin_habitacion_area_invalida);
        Double precio = decimal(binding.habitacionPrecioCampo, binding.habitacionPrecio,
                R.string.admin_habitacion_precio_obligatorio,
                R.string.admin_habitacion_precio_cero,
                R.string.admin_habitacion_precio_invalido);

        if (numero == null || tipo == null || adultos == null || ninos == null
                || piso == null || area == null || precio == null) {
            return;
        }
        if (viewModel.getHotelId() == null) {
            // Sin hotel no hay dónde registrarla. No debería llegar aquí —la
            // lista tampoco habría cargado—, pero el aviso dice por qué en vez
            // de dejar el botón sin efecto.
            avisar(getString(R.string.admin_habitacion_sin_hotel));
            return;
        }

        Room borrador = new Room(
                enEdicion != null ? enEdicion.getId() : null,
                viewModel.getHotelId(), tipo, precio);
        borrador.withCapacidad(adultos, ninos)
                .withArea(area)
                .withUbicacion(piso, numero);
        viewModel.guardarHabitacion(borrador);
    }

    private void limpiarErrores() {
        binding.habitacionNumeroCampo.setError(null);
        binding.habitacionTipoCampo.setError(null);
        binding.habitacionAdultosCampo.setError(null);
        binding.habitacionNinosCampo.setError(null);
        binding.habitacionAreaCampo.setError(null);
        binding.habitacionPisoCampo.setError(null);
        binding.habitacionPrecioCampo.setError(null);
    }

    // ------------------------------------------------------------------ Lectura de campos

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

    /**
     * Un entero. Vacío vale {@code porDefecto}; por debajo de {@code minimo}, no
     * vale.
     *
     * <p>Los niños y el piso son opcionales y su vacío significa cero —ninguno,
     * planta baja—, así que el campo en blanco no es un error.
     */
    @Nullable
    private Integer entero(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                           int porDefecto, int minimo, @StringRes int errorInvalido) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            return porDefecto;
        }
        try {
            int numero = Integer.parseInt(valor);
            if (numero < minimo) {
                campo.setError(getString(errorInvalido));
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            campo.setError(getString(errorInvalido));
            return null;
        }
    }

    /**
     * Un decimal obligatorio y mayor que cero.
     *
     * <p>La coma se acepta como separador decimal: es lo que sale del teclado en
     * español, y rechazarla obligaría a escribir "32.5" con un teclado que
     * ofrece coma. Se valida aquí además de en el repositorio porque el error de
     * un formulario tiene que salir en el campo que lo causó; el repositorio lo
     * vuelve a comprobar de todas formas, que una pantalla se puede saltar.
     */
    @Nullable
    private Double decimal(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                           @StringRes int errorVacio, @StringRes int errorCero,
                           @StringRes int errorTexto) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            campo.setError(getString(errorVacio));
            return null;
        }
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (numero <= 0d) {
                campo.setError(getString(errorCero));
                return null;
            }
            return numero;
        } catch (NumberFormatException e) {
            campo.setError(getString(errorTexto));
            return null;
        }
    }

    // ------------------------------------------------------------------ Estado

    private void pintarGuardado(@Nullable UiState<Room> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.habitacionGuardar.setEnabled(false);
                break;
            case SUCCESS:
                binding.habitacionGuardar.setEnabled(true);
                // No se avisa aquí: la hoja se cierra y el aviso quedaría
                // detrás. La confirmación es la habitación en la lista.
                dismiss();
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.habitacionGuardar.setEnabled(true);
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
