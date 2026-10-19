package org.iot.project.ui.admin.habitaciones;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetHabitacionBinding;
import org.iot.project.models.Room;

import java.util.UUID;

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
    private static final String ARG_HOTEL_ID = "hotelId";
    private static final String CLAVE_NUEVO_ID = "nuevaHabitacionId";

    /** Con cuántos adultos empieza una habitación nueva. */
    private static final int DEFAULT_ADULTOS = 2;

    private SheetHabitacionBinding binding;
    private HabitacionesViewModel viewModel;
    private String nuevoId = UUID.randomUUID().toString();
    private String roomId;
    private String hotelId;
    private boolean preparada;
    private boolean restaurando;

    /** La habitación que se está editando, o {@code null} si es nueva. */
    @Nullable
    private Room enEdicion;

    /**
     * La hoja, en uno de sus dos modos.
     *
     * @param habitacion la habitación a editar, o {@code null} para registrar una
     */
    @NonNull
    public static HabitacionSheet para(@Nullable Room habitacion, @NonNull String hotelId) {
        HabitacionSheet hoja = new HabitacionSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_ROOM_ID, habitacion != null ? habitacion.getId() : null);
        argumentos.putString(ARG_HOTEL_ID, hotelId);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) nuevoId = savedInstanceState.getString(CLAVE_NUEVO_ID, nuevoId);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(CLAVE_NUEVO_ID, nuevoId);
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

        binding.habitacionCerrar.setOnClickListener(v -> {
            if (!viewModel.estaGuardando()) {
                viewModel.limpiarGuardado();
                dismiss();
            }
        });
        binding.habitacionGuardar.setOnClickListener(v -> guardar());

        restaurando = savedInstanceState != null;
        preparada = false;
        prepararModo();

        viewModel.getHabitaciones().observe(getViewLifecycleOwner(), estado -> {
            if (roomId != null && estado != null) {
                if (estado.isSuccess() || estado.isEmpty()) prepararEdicion();
                else if (estado.isError()) avisar(estado.getMessage());
            }
            pintarEspera();
        });
        viewModel.getGuardado().observe(getViewLifecycleOwner(), this::pintarGuardado);
        viewModel.getOcupado().observe(getViewLifecycleOwner(), ocupado -> pintarEspera());
        viewModel.cargar();
    }

    @Override
    public void onStart() {
        super.onStart();
        pintarEspera();
    }

    // ------------------------------------------------------------------ Modos

    private void prepararModo() {
        Bundle argumentos = getArguments();
        roomId = argumentos != null ? argumentos.getString(ARG_ROOM_ID) : null;
        hotelId = argumentos != null ? argumentos.getString(ARG_HOTEL_ID) : null;
        enEdicion = null;

        if (roomId == null) {
            binding.habitacionTitulo.setText(R.string.admin_habitacion_nueva);
            if (!restaurando) {
                binding.habitacionAdultos.setText(String.valueOf(DEFAULT_ADULTOS));
            }
            preparada = true;
            return;
        }
        binding.habitacionTitulo.setText(R.string.admin_habitacion_editar_titulo);
    }

    /** Una edición restaurada espera su habitación; nunca se convierte en un alta. */
    private void prepararEdicion() {
        enEdicion = viewModel.buscarHabitacion(roomId);
        if (enEdicion == null) {
            preparada = false;
            avisar(getString(R.string.admin_habitacion_no_encontrada));
            return;
        }
        binding.habitacionTitulo.setText(
                getString(R.string.admin_habitacion_editar, enEdicion.getNumero()));
        if (!preparada && !restaurando) escribir(enEdicion);
        preparada = true;
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
        binding.habitacionPiso.setText(habitacion.getPiso() != 0
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
        if (!preparada || !hotelVigente() || !viewModel.puedeGuardar()
                || (roomId == null && !viewModel.puedeCrear())) return;
        limpiarErrores();

        String numero = texto(binding.habitacionNumeroCampo, binding.habitacionNumero,
                R.string.admin_habitacion_numero_obligatorio);
        String tipo = texto(binding.habitacionTipoCampo, binding.habitacionTipo,
                R.string.admin_habitacion_tipo_obligatorio);
        Integer adultos = entero(binding.habitacionAdultosCampo, binding.habitacionAdultos,
                null, 1, 20, R.string.admin_habitacion_adultos_rango);
        Integer ninos = entero(binding.habitacionNinosCampo, binding.habitacionNinos,
                0, 0, 20, R.string.admin_habitacion_ninos_rango);
        Integer piso = entero(binding.habitacionPisoCampo, binding.habitacionPiso,
                0, -10, 200, R.string.admin_habitacion_piso_rango);
        Double area = decimal(binding.habitacionAreaCampo, binding.habitacionArea,
                10000d, R.string.admin_habitacion_area_rango,
                R.string.admin_habitacion_area_obligatoria,
                R.string.admin_habitacion_area_cero,
                R.string.admin_habitacion_area_invalida);
        Double precio = decimal(binding.habitacionPrecioCampo, binding.habitacionPrecio,
                1000000d, R.string.admin_habitacion_precio_rango,
                R.string.admin_habitacion_precio_obligatorio,
                R.string.admin_habitacion_precio_cero,
                R.string.admin_habitacion_precio_invalido);

        if (numero == null || tipo == null || adultos == null || ninos == null
                || piso == null || area == null || precio == null) {
            return;
        }
        Room borrador = new Room(
                enEdicion != null ? enEdicion.getId() : nuevoId,
                hotelId, tipo, precio);
        borrador.withCapacidad(adultos, ninos)
                .withArea(area)
                .withUbicacion(piso, numero);
        viewModel.guardarHabitacion(borrador);
    }

    private void limpiarErrores() {
        binding.habitacionError.setVisibility(View.GONE);
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
     * Un entero dentro del rango permitido. Solo niños y piso admiten vacío.
     *
     * <p>Los niños y el piso son opcionales y su vacío significa cero —ninguno,
     * planta baja—, así que el campo en blanco no es un error.
     */
    @Nullable
    private Integer entero(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                           @Nullable Integer porDefecto, int minimo, int maximo,
                           @StringRes int errorInvalido) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            if (porDefecto == null) campo.setError(getString(errorInvalido));
            return porDefecto;
        }
        try {
            int numero = Integer.parseInt(valor);
            if (numero < minimo || numero > maximo) {
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
                           double maximo, @StringRes int errorMaximo,
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
            if (!Double.isFinite(numero) || numero <= 0d) {
                campo.setError(getString(errorCero));
                return null;
            }
            if (numero > maximo) {
                campo.setError(getString(errorMaximo));
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
        pintarEspera();
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.habitacionError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                dismiss();
                viewModel.limpiarGuardado();
                break;
            case EMPTY:
            case ERROR:
            default:
                avisar(estado.getMessage());
                viewModel.limpiarGuardado();
                break;
        }
    }

    private void pintarEspera() {
        if (binding == null || viewModel == null) return;
        boolean bloqueado = Boolean.TRUE.equals(viewModel.getOcupado().getValue());
        boolean guardando = viewModel.estaGuardando();
        setCancelable(!guardando);
        binding.habitacionCerrar.setEnabled(!guardando);
        binding.habitacionGuardar.setEnabled(preparada && hotelVigente() && (roomId == null
                ? viewModel.puedeCrear() : viewModel.puedeGuardar()));
        binding.habitacionGuardar.setText(guardando ? R.string.admin_habitacion_guardando
                : roomId == null ? R.string.admin_habitaciones_crear : R.string.accion_guardar);
        boolean editable = preparada && hotelVigente() && !bloqueado;
        binding.habitacionNumeroCampo.setEnabled(editable);
        binding.habitacionTipoCampo.setEnabled(editable);
        binding.habitacionAdultosCampo.setEnabled(editable);
        binding.habitacionNinosCampo.setEnabled(editable);
        binding.habitacionAreaCampo.setEnabled(editable);
        binding.habitacionPisoCampo.setEnabled(editable);
        binding.habitacionPrecioCampo.setEnabled(editable);
        if (roomId == null && viewModel.puedeGuardar() && !viewModel.puedeCrear()) {
            avisar(getString(R.string.admin_habitaciones_limite));
        }
        if (!hotelVigente()) avisar(getString(R.string.admin_habitacion_asignacion_cambio));
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialogo = (BottomSheetDialog) getDialog();
            dialogo.setCanceledOnTouchOutside(!guardando);
            dialogo.getBehavior().setDraggable(!guardando);
        }
    }

    private boolean hotelVigente() {
        return hotelId != null && hotelId.equals(viewModel.getHotelId());
    }

    private void avisar(@Nullable String mensaje) {
        if (binding == null || mensaje == null || mensaje.isEmpty()) {
            return;
        }
        binding.habitacionError.setText(mensaje);
        binding.habitacionError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
