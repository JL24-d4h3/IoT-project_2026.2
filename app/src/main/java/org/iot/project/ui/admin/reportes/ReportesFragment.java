package org.iot.project.ui.admin.reportes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminReportesBinding;
import org.iot.project.models.Periodicidad;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

/**
 * Reportes del hotel (§45, RF-055 a RF-061).
 *
 * <p>Pantalla de consulta, sin ninguna accion: se entra a mirar como va el
 * negocio y se sale. Lo unico que se toca es el selector de periodo, que no
 * modifica nada, solo vuelve a calcular el reporte con otra granularidad.
 *
 * <p>El boton marcado arranca del ViewModel y no del XML, porque es el
 * ViewModel el que sobrevive a un giro de pantalla: dibujar el selector con su
 * propio valor por defecto dejaria el boton diciendo "Mensual" sobre un reporte
 * calculado por dia.
 */
public class ReportesFragment extends Fragment {

    private FragmentAdminReportesBinding binding;
    private ReportesViewModel viewModel;
    private PeriodoAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminReportesBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminReportesHeader);

        binding.adminReportesHeader.setTitulo(R.string.nav_reportes);
        // Vuelve aunque la barra inferior siga visible: esta pantalla no es una
        // de sus secciones, y sin flecha la única salida sería adivinar qué
        // pestaña lleva de vuelta al panel.
        binding.adminReportesHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptador = new PeriodoAdapter();
        binding.adminReportesPeriodos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.adminReportesPeriodos.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(ReportesViewModel.class);

        // Primero se marca el botón que toca y solo después se escucha: al revés,
        // marcar el botón contaría como una elección del usuario y la pantalla
        // pediría el reporte dos veces al abrirse.
        marcarPeriodicidad(viewModel.getPeriodicidad());
        binding.adminReportesSelector.addOnButtonCheckedListener(this::alElegirPeriodo);

        viewModel.getReporte().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    // ------------------------------------------------------------- Selector

    private void alElegirPeriodo(@NonNull MaterialButtonToggleGroup grupo, int marcado,
                                 boolean seleccionado) {
        // El oyente avisa también al desmarcar el botón anterior; solo interesa
        // el que queda marcado.
        if (!seleccionado) {
            return;
        }
        viewModel.cambiarPeriodicidad(periodicidadDe(marcado));
    }

    private void marcarPeriodicidad(@NonNull Periodicidad periodicidad) {
        binding.adminReportesSelector.check(idDe(periodicidad));
    }

    /**
     * Las dos direcciones de la misma tabla: qué botón es cada granularidad.
     *
     * <p>Están juntas a propósito. Separadas, un botón nuevo en el XML o un
     * valor nuevo en el enum se añadiría en un sitio y no en el otro, y el
     * selector quedaría mandando un periodo que el reporte no entiende.
     */
    @NonNull
    private static Periodicidad periodicidadDe(int boton) {
        if (boton == R.id.admin_reportes_diario) {
            return Periodicidad.DIA;
        }
        if (boton == R.id.admin_reportes_anual) {
            return Periodicidad.ANIO;
        }
        return Periodicidad.MES;
    }

    private static int idDe(@NonNull Periodicidad periodicidad) {
        switch (periodicidad) {
            case DIA:
                return R.id.admin_reportes_diario;
            case ANIO:
                return R.id.admin_reportes_anual;
            case MES:
            default:
                return R.id.admin_reportes_mensual;
        }
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ReportesViewModel.Reporte> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, false, true, false);
                break;
            case SUCCESS:
                ReportesViewModel.Reporte reporte = estado.getData();
                if (reporte == null) {
                    // Un éxito sin datos no es un éxito: se trata como error
                    // para no pintar una pantalla vacía sin explicación.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                mostrar(true, false, false, false);
                llenar(reporte);
                break;
            case EMPTY:
                // El selector sigue visible: un periodo sin reservas no puede
                // llevarse consigo la forma de elegir otro que sí las tenga.
                mostrar(false, true, false, false);
                binding.adminReportesVacio.conIcono(R.drawable.ic_calendar);
                binding.adminReportesVacio.conTitulo(R.string.admin_reportes_vacio_titulo);
                binding.adminReportesVacio.conMensaje(R.string.admin_reportes_vacio);
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean vacio, boolean esqueleto, boolean error) {
        binding.adminReportesContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.adminReportesVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
        binding.adminReportesEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.adminReportesError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void llenar(@NonNull ReportesViewModel.Reporte reporte) {
        binding.adminReportesVentas.setText(PriceFormatter.format(reporte.ventas));
        binding.adminReportesResumen.setText(getString(R.string.admin_reportes_linea,
                getResources().getQuantityString(R.plurals.admin_reportes_reservas,
                        reporte.reservas, reporte.reservas),
                getResources().getQuantityString(R.plurals.admin_reportes_noches,
                        (int) reporte.noches, reporte.noches)));

        adaptador.submitList(reporte.periodos);

        // Un hotel puede facturar y no haber cobrado ningún servicio adicional.
        // Eso no deja la sección en blanco: deja una frase que lo dice, porque
        // un título sin nada debajo se lee como si algo hubiera fallado.
        boolean hayIngresos = reporte.hayIngresos();
        binding.adminReportesServiciosNota.setVisibility(hayIngresos ? View.VISIBLE : View.GONE);
        binding.adminReportesServicios.setVisibility(hayIngresos ? View.VISIBLE : View.GONE);
        binding.adminReportesServiciosVacio.setVisibility(hayIngresos ? View.GONE : View.VISIBLE);
        binding.adminReportesServicios.setIngresos(hayIngresos ? reporte.ingresos : null);
    }

    private void pintarError(@Nullable String mensaje) {
        mostrar(false, false, false, true);
        binding.adminReportesError.conReintento(mensaje, v -> viewModel.reintentar());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.adminReportesPeriodos.setAdapter(null);
        binding = null;
    }
}
