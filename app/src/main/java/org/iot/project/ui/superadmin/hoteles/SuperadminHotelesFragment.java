package org.iot.project.ui.superadmin.hoteles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminHotelesBinding;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Hoteles de la plataforma (RF-007, RF-008).
 *
 * <p>Cada fila abre la ficha, que es donde se asignan administradores y donde
 * se decide la publicacion: esta pantalla solo enseña en que punto esta cada
 * hotel.
 *
 * <p>La lista llega con los que no se publican primero, que son los unicos
 * sobre los que hay algo que decidir. Ese orden lo pone el repositorio.
 */
public class SuperadminHotelesFragment extends Fragment {

    /** El identificador que viaja a la ficha. */
    static final String ARG_HOTEL_ID = "hotelId";

    /**
     * Clave con la que el alta devuelve el nombre del hotel recien registrado.
     *
     * <p>Vive aqui y no en el alta porque el aviso lo da esta pantalla: quien
     * enseña algo es quien decide como se llama lo que enseña.
     */
    static final String CLAVE_HOTEL_REGISTRADO = "hotelRegistrado";

    private FragmentSuperadminHotelesBinding binding;
    private SuperadminHotelesViewModel viewModel;
    private HotelSaAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminHotelesBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_hoteles);
        binding.saHeader.mostrarAccion(R.drawable.ic_add, R.string.cd_registrar_hotel,
                v -> abrirAlta());

        adaptador = new HotelSaAdapter(this::abrirFicha);
        binding.saHotelesLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saHotelesLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(SuperadminHotelesViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);

        // Despues de tener el ViewModel: el grupo puede anunciar el chip que ya
        // venia marcado en cuanto se registra el oyente.
        binding.saHotelesFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrar(!marcados.isEmpty()
                        && marcados.get(0) == R.id.sa_filtro_sin_publicar));

        viewModel.cargar();
        avisarDelAlta();
    }

    @Override
    public void onResume() {
        super.onResume();
        // La barra inferior guarda el estado de la seccion, asi que al volver
        // esto no se recrea: hay que pedir los hoteles otra vez para ver lo que
        // cambio mientras estabamos en la ficha.
        viewModel.refrescarEnSilencio();
    }

    private void abrirFicha(@NonNull String hotelId) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_HOTEL_ID, hotelId);
        Navigation.findNavController(requireView())
                .navigate(R.id.superadminHotelFragment, argumentos);
    }

    private void abrirAlta() {
        Navigation.findNavController(requireView())
                .navigate(R.id.superadminAltaHotelFragment);
    }

    /**
     * El aviso de que se acaba de registrar un hotel.
     *
     * <p>Lo deja escrito el alta antes de volver, y se lee una sola vez y se
     * borra: si quedara ahi, volver mas tarde a esta pantalla repetiria un aviso
     * de algo que ya paso. El aviso no dice que el hotel este listo —no lo esta:
     * nace sin publicar y sin administrador—, dice que el alta salio bien y cual
     * es el paso que sigue.
     */
    private void avisarDelAlta() {
        NavBackStackEntry propia =
                Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (propia == null) {
            return;
        }
        String nombre = propia.getSavedStateHandle().get(CLAVE_HOTEL_REGISTRADO);
        propia.getSavedStateHandle().remove(CLAVE_HOTEL_REGISTRADO);
        if (nombre != null && !nombre.isEmpty()) {
            Snackbar.make(binding.getRoot(), getString(R.string.sa_alta_hecha, nombre),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<SuperadminHotelesViewModel.FilaHotel>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saHotelesVacio.setVisibility(View.GONE);
                binding.saHotelesLista.setVisibility(View.VISIBLE);
                adaptador.submitList(estado.requireData());
                break;
            case EMPTY:
                mostrar(true, false, false);
                binding.saHotelesLista.setVisibility(View.GONE);
                binding.saHotelesVacio.setVisibility(View.VISIBLE);
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
     * El vacio dice cosas distintas segun el filtro.
     *
     * <p>Con "Sin publicar" puesto, que no haya nadie es una buena noticia y se
     * dice como tal; sin filtro significa que la plataforma no tiene ningun
     * hotel registrado, que es otra cosa completamente distinta.
     */
    private void pintarVacio() {
        boolean soloSinPublicar = viewModel.isSoloSinPublicar();
        binding.saHotelesVacio.conIcono(R.drawable.ic_service_business)
                .conTitulo(soloSinPublicar
                        ? R.string.sa_hoteles_vacio_titulo
                        : R.string.sa_hoteles_nadie_titulo)
                .conMensaje(soloSinPublicar
                        ? R.string.sa_hoteles_vacio_mensaje
                        : R.string.sa_hoteles_nadie_mensaje);
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saHotelesLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
