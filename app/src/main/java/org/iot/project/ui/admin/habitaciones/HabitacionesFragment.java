package org.iot.project.ui.admin.habitaciones;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentAdminHabitacionesBinding;
import org.iot.project.models.Room;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Gestión de habitaciones del hotel (§42, RF-014 a RF-018).
 *
 * <p>La pantalla es una lista y un botón de agregar, y esa es toda su
 * responsabilidad. La disponibilidad se cambia desde la propia fila porque es
 * la operación del día a día —una avería, una reforma— y el resto de los datos
 * se editan en una hoja, que es donde un formulario de siete campos cabe.
 */
public class HabitacionesFragment extends Fragment {

    private FragmentAdminHabitacionesBinding binding;
    private HabitacionesViewModel viewModel;
    private HabitacionAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminHabitacionesBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminHabitacionesHeader);

        binding.adminHabitacionesHeader.setTitulo(R.string.nav_habitaciones);
        binding.adminHabitacionesHeader.mostrarAccion(
                R.drawable.ic_add, R.string.cd_agregar_habitacion,
                v -> abrirHoja(null));

        adaptador = new HabitacionAdapter();
        adaptador.setAlEditar(this::abrirHoja);
        adaptador.setAlCambiarDisponibilidad(this::cambiarDisponibilidad);

        binding.adminHabitacionesLista.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.adminHabitacionesLista.setAdapter(adaptador);

        // El ViewModel es el del grafo y no el del fragmento: la hoja de edición
        // vive en otro fragmento y tiene que ver la misma lista para encontrar
        // la habitación que edita y para que el guardado recargue esta pantalla.
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, HabitacionesViewModel.class);
        viewModel.getHabitaciones().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<Room>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminHabitacionesEsqueleto.setVisibility(View.VISIBLE);
                binding.adminHabitacionesLista.setVisibility(View.GONE);
                binding.adminHabitacionesVacio.setVisibility(View.GONE);
                binding.adminHabitacionesError.setVisibility(View.GONE);
                binding.adminHabitacionesHeader.setSubtitulo(null);
                break;
            case SUCCESS:
                List<Room> habitaciones = estado.getData();
                if (habitaciones == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminHabitacionesEsqueleto.setVisibility(View.GONE);
                binding.adminHabitacionesVacio.setVisibility(View.GONE);
                binding.adminHabitacionesError.setVisibility(View.GONE);
                binding.adminHabitacionesLista.setVisibility(View.VISIBLE);
                binding.adminHabitacionesHeader.setSubtitulo(resumen(habitaciones));
                adaptador.submitList(habitaciones);
                break;
            case EMPTY:
                pintarVacio();
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    /**
     * "12 habitaciones · 9 a la venta".
     *
     * <p>El segundo dato es el que el administrador viene a comprobar de un
     * vistazo: si retiró alguna de la venta y cuántas le quedan. Se omite cuando
     * están todas, porque entonces repetiría al primero.
     */
    @NonNull
    private CharSequence resumen(@NonNull List<Room> habitaciones) {
        int total = habitaciones.size();
        int aLaVenta = 0;
        for (Room habitacion : habitaciones) {
            if (habitacion.isDisponible()) {
                aLaVenta++;
            }
        }
        String cuenta = getResources().getQuantityString(
                R.plurals.admin_habitaciones_total, total, total);
        if (aLaVenta == total) {
            return cuenta;
        }
        return getString(R.string.admin_habitaciones_resumen, cuenta,
                getResources().getQuantityString(
                        R.plurals.admin_habitaciones_a_la_venta, aLaVenta, aLaVenta));
    }

    /**
     * El hotel todavía no tiene ninguna habitación registrada.
     *
     * <p>El estado vacío lleva la acción de agregar y no solo el aviso: es la
     * única cosa que se puede hacer aquí, y obligar a buscarla en la cabecera
     * sería esconderla justo cuando hace falta.
     */
    private void pintarVacio() {
        binding.adminHabitacionesEsqueleto.setVisibility(View.GONE);
        binding.adminHabitacionesLista.setVisibility(View.GONE);
        binding.adminHabitacionesError.setVisibility(View.GONE);
        binding.adminHabitacionesVacio.setVisibility(View.VISIBLE);
        binding.adminHabitacionesVacio.conIcono(R.drawable.ic_service_business);
        binding.adminHabitacionesVacio.conTitulo(R.string.admin_habitaciones_vacio_titulo);
        binding.adminHabitacionesVacio.conMensaje(R.string.admin_habitaciones_vacio);
        binding.adminHabitacionesVacio.conAccion(
                R.string.admin_habitaciones_agregar, v -> abrirHoja(null));
        binding.adminHabitacionesHeader.setSubtitulo(null);
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminHabitacionesEsqueleto.setVisibility(View.GONE);
        binding.adminHabitacionesLista.setVisibility(View.GONE);
        binding.adminHabitacionesVacio.setVisibility(View.GONE);
        binding.adminHabitacionesError.setVisibility(View.VISIBLE);
        binding.adminHabitacionesError.conReintento(mensaje, v -> viewModel.recargar());
        binding.adminHabitacionesHeader.setSubtitulo(null);
    }

    /**
     * El resultado de mover un interruptor.
     *
     * <p>No hay nada que pintar cuando sale bien: la lista se recarga sola y el
     * interruptor se queda como el administrador lo dejó. El aviso aparece solo
     * al fallar, que es cuando la fila vuelve a su sitio y hace falta decir por
     * qué.
     */
    private void pintarOperacion(@Nullable UiState<Room> estado) {
        if (estado == null || !estado.isError()) {
            return;
        }
        // Al fallar, el modelo no cambió pero el interruptor sí se movió: hay
        // que repintar la fila para devolverlo a donde estaba. Se pide un
        // repintado entero y no de una fila concreta porque la posición ya no es
        // de fiar —la lista pudo reordenarse— y porque esto solo ocurre cuando
        // algo ha ido mal, no en el uso normal.
        adaptador.notifyDataSetChanged();
        Snackbar.make(binding.getRoot(),
                estado.getMessage() != null
                        ? estado.getMessage() : getString(R.string.estado_error_descripcion),
                Snackbar.LENGTH_LONG).show();
        viewModel.limpiarOperacion();
    }

    // ------------------------------------------------------------------ Acciones

    private void cambiarDisponibilidad(@NonNull Room habitacion, boolean disponible) {
        viewModel.cambiarDisponibilidad(habitacion.getId(), disponible);
    }

    private void abrirHoja(@Nullable Room habitacion) {
        HabitacionSheet hoja = HabitacionSheet.para(habitacion);
        hoja.show(getChildFragmentManager(), HabitacionSheet.TAG);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
