package org.iot.project.ui.admin.servicios;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetServicioAdminBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;
import org.iot.project.ui.components.FilterChipView;

import java.util.ArrayList;
import java.util.List;

/**
 * Agregar o editar un servicio del hotel (§45).
 *
 * <p>Tiene dos modos y la diferencia no es cosmética: al <b>agregar</b> hay que
 * elegir cuál del catálogo, y al <b>editar</b> ya está elegido y no se puede
 * cambiar —el repositorio asocia por identificador de servicio, así que cambiar
 * de servicio es quitar uno y poner otro—. Por eso en modo edición el catálogo
 * no se enseña: ofrecer una lista que no se puede usar es peor que no ofrecerla.
 */
public class ServicioSheet extends BottomSheetDialogFragment {

    public static final String TAG = "servicio";

    private static final String ARG_SERVICE_ID = "serviceId";

    private SheetServicioAdminBinding binding;
    private ServiciosViewModel viewModel;

    /** El servicio que se está editando, o {@code null} si se está agregando. */
    @Nullable
    private Service enEdicion;

    /** Si se está rellenando el formulario, no el usuario. Ver {@link #escribir}. */
    private boolean rellenando = false;

    /** El servicio elegido en el catálogo mientras se agrega. */
    @Nullable
    private Service elegido;

    /** El identificador del chip "incluido", para distinguirlo del otro. */
    private int idChipIncluido;

    /**
     * La hoja, en uno de sus dos modos.
     *
     * @param servicio el servicio a editar, o {@code null} para agregar uno
     */
    @NonNull
    public static ServicioSheet para(@Nullable Service servicio) {
        ServicioSheet hoja = new ServicioSheet();
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_SERVICE_ID, servicio != null ? servicio.getServiceId() : null);
        hoja.setArguments(argumentos);
        return hoja;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetServicioAdminBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el del grafo: la hoja no tiene hotel propio y lo que
        // cambia al guardar es el de la pantalla que la abrió.
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ServiciosViewModel.class);

        binding.servicioCerrar.setOnClickListener(v -> dismiss());
        binding.servicioGuardar.setOnClickListener(v -> guardar());
        binding.servicioQuitar.setOnClickListener(v -> confirmarQuitar());

        configurarForma();
        prepararModo();

        // Se limpia antes de observar. LiveData entrega su ultimo valor al
        // registrarse, asi que sin esto una hoja recien abierta veria el exito
        // de la operacion anterior y se cerraria sola.
        viewModel.limpiarOperacion();
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
    }

    // ------------------------------------------------------------------ Modos

    private void prepararModo() {
        Bundle argumentos = getArguments();
        String serviceId = argumentos != null ? argumentos.getString(ARG_SERVICE_ID) : null;
        enEdicion = serviceId == null ? null : buscarEnCatalogo(serviceId);

        if (enEdicion == null) {
            abrirComoAgregar();
        } else {
            abrirComoEditar(enEdicion);
        }
    }

    private void abrirComoAgregar() {
        binding.servicioTitulo.setText(R.string.admin_servicios_agregar);
        binding.servicioCatalogoBloque.setVisibility(View.VISIBLE);
        binding.servicioQuitar.setVisibility(View.GONE);

        List<Service> libres = viewModel.catalogoDisponible(hotelActual());
        for (Service servicio : libres) {
            binding.servicioCatalogo.addView(chipDeCatalogo(servicio));
        }
        // Al agregar se empieza por "incluido": es lo que un hotel hace con un
        // servicio que acaba de sumar, y evita inventar un precio para poder
        // guardar. Cobrarlo es la decisión, y se toma después.
        escribir(true, 0d);
    }

    private void abrirComoEditar(@NonNull Service servicio) {
        binding.servicioTitulo.setText(servicio.getName());
        // El catálogo no se enseña: el servicio ya está elegido y el
        // repositorio asocia por identificador, así que "cambiar de servicio"
        // no es una operación que exista.
        binding.servicioCatalogoBloque.setVisibility(View.GONE);
        binding.servicioQuitar.setVisibility(View.VISIBLE);

        HotelService asignado = asignacionDe(servicio.getServiceId());
        escribir(asignado == null || asignado.isIncluded(),
                asignado != null ? asignado.getPrice() : 0d);
    }

    // ------------------------------------------------------------------ Forma

    private void configurarForma() {
        agregarChipForma(true, getString(R.string.admin_servicio_incluido));
        agregarChipForma(false, getString(R.string.admin_servicio_adicional));

        binding.servicioForma.setOnCheckedStateChangeListener((grupo, marcados) -> {
            if (rellenando || marcados.isEmpty()) {
                return;
            }
            // El identificador del chip dice cuál es: los chips se crean en
            // este orden y el grupo es de selección única y obligatoria, así
            // que basta con saber si el marcado es el primero.
            boolean incluido = marcados.get(0) == idChipIncluido;
            mostrarPrecio(!incluido);
        });
    }

    private void agregarChipForma(boolean incluido, @NonNull String etiqueta) {
        FilterChipView chip = nuevoChip(etiqueta);
        chip.setId(View.generateViewId());
        if (incluido) {
            idChipIncluido = chip.getId();
        }
        binding.servicioForma.addView(chip);
    }

    private void mostrarPrecio(boolean visible) {
        binding.servicioPrecioCampo.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            binding.servicioPrecioCampo.setError(null);
        }
    }

    /**
     * Vuelca el estado en los controles.
     *
     * <p>Mientras dura, {@code rellenando} está encendido: marcar un chip
     * dispara su oyente, y sin la bandera rellenar el formulario se confundiría
     * con el usuario eligiendo.
     */
    private void escribir(boolean incluido, double precio) {
        rellenando = true;
        try {
            for (int i = 0; i < binding.servicioForma.getChildCount(); i++) {
                FilterChipView chip = (FilterChipView) binding.servicioForma.getChildAt(i);
                chip.setChecked((chip.getId() == idChipIncluido) == incluido);
            }
            binding.servicioPrecio.setText(precio > 0d
                    ? String.valueOf(precio) : "");
            mostrarPrecio(!incluido);
        } finally {
            rellenando = false;
        }
    }

    // ------------------------------------------------------------------ Guardar

    private void guardar() {
        Service servicio = enEdicion != null ? enEdicion : elegido;
        if (servicio == null) {
            avisar(getString(R.string.admin_servicio_falta_elegir));
            return;
        }

        boolean incluido = esIncluido();
        double precio = 0d;
        if (!incluido) {
            Double leido = leerPrecio();
            if (leido == null) {
                return;
            }
            precio = leido;
        }

        binding.servicioPrecioCampo.setError(null);
        viewModel.asignar(servicio.getServiceId(), incluido, precio);
    }

    private boolean esIncluido() {
        for (int i = 0; i < binding.servicioForma.getChildCount(); i++) {
            FilterChipView chip = (FilterChipView) binding.servicioForma.getChildAt(i);
            if (chip.isChecked()) {
                return chip.getId() == idChipIncluido;
            }
        }
        // El grupo es de selección obligatoria, así que esto no debería pasar;
        // si pasara, "incluido" es la opción que no exige inventar un precio.
        return true;
    }

    /**
     * El precio escrito, o {@code null} si no sirve.
     *
     * <p>Se valida aquí además de en el repositorio porque el error de un
     * formulario tiene que salir en el campo que lo causó, no en un aviso
     * genérico. El repositorio lo vuelve a comprobar de todas formas: una
     * comprobación en la pantalla se puede saltar, y la regla de que un
     * adicional se cobra es del negocio, no del formulario.
     */
    @Nullable
    private Double leerPrecio() {
        String texto = binding.servicioPrecio.getText() != null
                ? binding.servicioPrecio.getText().toString().trim() : "";
        if (texto.isEmpty()) {
            binding.servicioPrecioCampo.setError(
                    getString(R.string.admin_servicio_precio_obligatorio));
            return null;
        }
        try {
            double valor = Double.parseDouble(texto.replace(',', '.'));
            if (valor <= 0d) {
                binding.servicioPrecioCampo.setError(
                        getString(R.string.admin_servicio_precio_cero));
                return null;
            }
            return valor;
        } catch (NumberFormatException e) {
            binding.servicioPrecioCampo.setError(
                    getString(R.string.admin_servicio_precio_invalido));
            return null;
        }
    }

    private void confirmarQuitar() {
        if (enEdicion == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.admin_servicio_quitar_titulo, enEdicion.getName()))
                .setMessage(R.string.admin_servicio_quitar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.admin_servicio_quitar,
                        (dialogo, cual) -> viewModel.quitar(enEdicion.getServiceId()))
                .show();
    }

    // ------------------------------------------------------------------ Estado

    private void pintarOperacion(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.servicioGuardar.setEnabled(false);
                break;
            case SUCCESS:
                binding.servicioGuardar.setEnabled(true);
                dismiss();
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.servicioGuardar.setEnabled(true);
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

    // ------------------------------------------------------------------ Auxiliares

    @NonNull
    private FilterChipView chipDeCatalogo(@NonNull Service servicio) {
        FilterChipView chip = nuevoChip(servicio.getName());
        chip.setId(View.generateViewId());
        chip.bind(servicio);
        chip.setOnCheckedChangeListener((boton, marcado) -> {
            if (marcado) {
                elegido = servicio;
            } else if (elegido == servicio) {
                elegido = null;
            }
        });
        return chip;
    }

    /**
     * Un chip nuevo, con la separación que {@code ChipGroup} no pone.
     *
     * <p>El grupo coloca los chips pegados unos a otros: sin margen se leen
     * como una sola palabra.
     */
    @NonNull
    private FilterChipView nuevoChip(@NonNull String etiqueta) {
        FilterChipView chip = new FilterChipView(requireContext());
        chip.setText(etiqueta);

        ChipGroup.LayoutParams parametros = new ChipGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int separacion = getResources().getDimensionPixelSize(R.dimen.space_sm);
        parametros.setMarginEnd(separacion);
        parametros.bottomMargin = separacion;
        chip.setLayoutParams(parametros);
        return chip;
    }

    @Nullable
    private Hotel hotelActual() {
        UiState<Hotel> estado = viewModel.getHotel().getValue();
        return estado != null ? estado.getData() : null;
    }

    @Nullable
    private HotelService asignacionDe(@NonNull String serviceId) {
        Hotel hotel = hotelActual();
        if (hotel == null) {
            return null;
        }
        for (HotelService asignado : hotel.getServicios()) {
            if (asignado.getServiceId().equals(serviceId)) {
                return asignado;
            }
        }
        return null;
    }

    @Nullable
    private Service buscarEnCatalogo(@NonNull String serviceId) {
        for (Service servicio : viewModel.getCatalogo()) {
            if (servicio.getServiceId().equals(serviceId)) {
                return servicio;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
