package org.iot.project.ui.admin.servicios;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.ChipGroup;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentAdminServiciosBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;
import org.iot.project.ui.components.FilterChipView;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de servicios del hotel (§45, RF-019 a RF-022).
 *
 * <p>§45 pide que esta vista sea "especialmente buena" y da el boceto: el
 * catálogo en chips, partido en incluidos y adicionales, y "Agregar servicio"
 * al final. Se sigue tal cual, y lo que se añade es lo que el boceto no dice:
 * qué hacer cuando no hay nada en un grupo, y qué hacer cuando ya no queda nada
 * por agregar.
 *
 * <p>El chip se toca para editarlo. Es la decisión de diseño de la pantalla:
 * como el catálogo es corto y cabe entero, no hay una pantalla de "detalle del
 * servicio" a la que entrar — se ajusta ahí mismo, en la hoja.
 */
public class ServiciosFragment extends Fragment {

    private FragmentAdminServiciosBinding binding;
    private ServiciosViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminServiciosBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminServiciosHeader);

        binding.adminServiciosHeader.setTitulo(R.string.nav_servicios);
        binding.adminServiciosAgregar.setOnClickListener(v -> abrirHoja(null));

        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, ServiciosViewModel.class);
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<Hotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminServiciosEsqueleto.setVisibility(View.VISIBLE);
                binding.adminServiciosContenido.setVisibility(View.GONE);
                binding.adminServiciosError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                Hotel hotel = estado.getData();
                if (hotel == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminServiciosEsqueleto.setVisibility(View.GONE);
                binding.adminServiciosError.setVisibility(View.GONE);
                binding.adminServiciosContenido.setVisibility(View.VISIBLE);
                pintarServicios(hotel);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    /**
     * No hay estado vacío de pantalla completa.
     *
     * <p>Que el hotel no ofrezca ningún servicio no es una pantalla sin
     * contenido: es un hotel al que le falta configurarlo, y lo que hay que
     * enseñar es precisamente el catálogo para que lo haga. Un estado vacío
     * aquí taparía la única acción útil.
     */
    private void pintarError(@Nullable String mensaje) {
        binding.adminServiciosEsqueleto.setVisibility(View.GONE);
        binding.adminServiciosContenido.setVisibility(View.GONE);
        binding.adminServiciosError.setVisibility(View.VISIBLE);
        binding.adminServiciosError.conReintento(mensaje, v -> viewModel.recargar());
    }

    private void pintarServicios(@NonNull Hotel hotel) {
        List<HotelService> incluidos = new ArrayList<>();
        List<HotelService> adicionales = new ArrayList<>();
        for (HotelService asignado : hotel.getServicios()) {
            if (asignado.isIncluded()) {
                incluidos.add(asignado);
            } else {
                adicionales.add(asignado);
            }
        }

        llenarGrupo(binding.adminServiciosIncluidos, incluidos);
        llenarGrupo(binding.adminServiciosAdicionales, adicionales);

        // Un grupo sin chips se lee como un fallo de carga; la frase dice que
        // es una decisión que todavía no se ha tomado.
        binding.adminServiciosIncluidosVacio.setVisibility(
                incluidos.isEmpty() ? View.VISIBLE : View.GONE);
        binding.adminServiciosAdicionalesVacio.setVisibility(
                adicionales.isEmpty() ? View.VISIBLE : View.GONE);

        pintarAgregar(hotel);
    }

    /**
     * El botón de agregar, o la razón de que no esté.
     *
     * <p>Si el hotel ya ofrece todo el catálogo, el botón no tiene a dónde
     * llevar: abriría una lista vacía. En su lugar se dice que no queda nada,
     * que es la información que el administrador venía a buscar.
     */
    private void pintarAgregar(@NonNull Hotel hotel) {
        boolean quedanServicios = !viewModel.catalogoDisponible(hotel).isEmpty();
        binding.adminServiciosAgregar.setVisibility(quedanServicios ? View.VISIBLE : View.GONE);
        binding.adminServiciosCompleto.setVisibility(quedanServicios ? View.GONE : View.VISIBLE);
    }

    private void llenarGrupo(@NonNull ChipGroup grupo, @NonNull List<HotelService> asignados) {
        grupo.removeAllViews();
        for (HotelService asignado : asignados) {
            Service catalogo = buscarEnCatalogo(asignado.getServiceId());
            if (catalogo == null) {
                // Un servicio que no está en el catálogo no debería llegar
                // aquí: el repositorio lo rechaza al asignarlo (regla 7). Si
                // llegara, se salta en vez de pintar una fila sin nombre.
                continue;
            }
            grupo.addView(chipDe(catalogo, asignado));
        }
    }

    @NonNull
    private FilterChipView chipDe(@NonNull Service catalogo, @NonNull HotelService asignado) {
        FilterChipView chip = new FilterChipView(requireContext());
        chip.bind(catalogo);
        // El chip no se marca: aquí no filtra nada, abre el editor del
        // servicio. Dejarlo marcable haría que al tocarlo quedara con un
        // visto bueno que no significa nada y que no se puede deshacer.
        chip.setCheckable(false);
        chip.setClickable(true);
        // El incluido no lleva precio —no se cobra— y el adicional lo lleva en
        // el propio chip: es la única diferencia entre los dos grupos, así que
        // tiene que caber en el chip y no en una nota al pie.
        chip.setText(asignado.isIncluded()
                ? catalogo.getName()
                : getString(R.string.admin_servicio_chip_adicional, catalogo.getName(),
                        PriceFormatter.format(asignado.getPrice())));
        chip.setId(View.generateViewId());

        ChipGroup.LayoutParams parametros = new ChipGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int separacion = getResources().getDimensionPixelSize(R.dimen.space_sm);
        parametros.setMarginEnd(separacion);
        parametros.bottomMargin = separacion;
        chip.setLayoutParams(parametros);

        // Se toca para editarlo: la hoja abre con este servicio ya elegido.
        chip.setOnClickListener(v -> abrirHoja(catalogo));
        return chip;
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

    private void abrirHoja(@Nullable Service servicio) {
        ServicioSheet hoja = ServicioSheet.para(servicio);
        hoja.show(getChildFragmentManager(), ServicioSheet.TAG);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
