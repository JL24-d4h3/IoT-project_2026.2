package org.iot.project.ui.superadmin.bitacora;

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

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminBitacoraBinding;
import org.iot.project.models.LogEntry;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Auditoria de la plataforma (RF-118 a RF-120).
 *
 * <p>La lista entera de movimientos, del mas reciente al mas antiguo, con dos
 * mandos: el texto —que busca a la vez en el detalle, el autor y el tipo de
 * evento— y el chip de "Solo cambios", que deja los movimientos que alteraron
 * algo del sistema.
 *
 * <p>Es la pantalla donde el superadministrador responde "quien hizo esto". Por
 * eso el detalle va en su propio renglon y no se recorta: un movimiento
 * recortado a media frase es justo el que hacia falta leer.
 */
public class SuperadminBitacoraFragment extends Fragment {

    private FragmentSuperadminBitacoraBinding binding;
    private SuperadminBitacoraViewModel viewModel;
    private BitacoraAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminBitacoraBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_auditoria);

        // La bitacora no esta en la barra: se llega a ella desde la portada o
        // desde el perfil, asi que lleva flecha de vuelta, como toda pantalla
        // del panel que no es una seccion.
        binding.saHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptador = new BitacoraAdapter();
        binding.saBitacoraLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saBitacoraLista.setAdapter(adaptador);

        binding.saBitacoraBusqueda.setPista(R.string.sa_bitacora_buscar);

        viewModel = new ViewModelProvider(this).get(SuperadminBitacoraViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);

        // Despues de tener el ViewModel: el campo avisa de cada tecla y el
        // filtro es inmediato porque la lista entera ya esta en memoria.
        binding.saBitacoraBusqueda.setOnQueryChangeListener(viewModel::buscar);
        binding.saBitacoraFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrarSoloCambios(!marcados.isEmpty()
                        && marcados.get(0) == R.id.sa_bitacora_solo_cambios));

        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // La barra inferior guarda el estado de la seccion, asi que al volver
        // esto no se recrea: hay que pedir la bitacora otra vez para ver lo que
        // ocurrio mientras estabamos en otra pantalla. Sin esqueleto, porque la
        // lista ya esta pintada.
        viewModel.refrescarEnSilencio();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<LogEntry>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saBitacoraVacio.setVisibility(View.GONE);
                binding.saBitacoraLista.setVisibility(View.VISIBLE);
                adaptador.submitList(estado.requireData());
                break;
            case EMPTY:
                mostrar(true, false, false);
                binding.saBitacoraLista.setVisibility(View.GONE);
                binding.saBitacoraVacio.setVisibility(View.VISIBLE);
                pintarVacio();
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    /**
     * El vacio dice cosas distintas segun haya un filtro puesto.
     *
     * <p>Sin filtro significa que la plataforma no ha registrado nada todavia,
     * que es un estado del sistema y no del formulario; con filtro, que no hay
     * nada que encaje, y ahi si hay algo que probar.
     */
    private void pintarVacio() {
        boolean filtrando = viewModel.isFiltrando();
        binding.saBitacoraVacio.conIcono(R.drawable.ic_time)
                .conTitulo(filtrando
                        ? R.string.sa_bitacora_vacio_titulo
                        : R.string.sa_bitacora_nadie_titulo)
                .conMensaje(filtrando
                        ? R.string.sa_bitacora_vacio_mensaje
                        : R.string.sa_bitacora_nadie_mensaje);
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saBitacoraLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
