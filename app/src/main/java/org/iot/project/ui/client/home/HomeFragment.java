package org.iot.project.ui.client.home;

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
import org.iot.project.databinding.FragmentHomeBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.User;
import org.iot.project.ui.common.HotelAdapter;
import org.iot.project.ui.components.LoadingSkeletonView;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Pantalla de inicio del cliente.
 *
 * <p>No decide nada por su cuenta: observa el {@link HomeViewModel} y dibuja el
 * estado que reciba. Los cuatro estados de §50 —carga, contenido, vacío y
 * error— están contemplados, y el de error trae su botón de reintento.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private HotelAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // La barra de estado se pinta del color de la cabecera: sin esto, el
        // contenido quedaría por debajo del reloj y los iconos del sistema.
        InsetUtils.applyTopPadding(binding.header);

        configurarLista();
        configurarNavegacion();
        observar();

        // Solo se carga la primera vez. Al girar la pantalla, el ViewModel
        // conserva los datos y no se vuelve a pedir.
        if (viewModel.getRecomendados().getValue() == null) {
            viewModel.cargar();
        }
    }

    private void configurarLista() {
        adaptador = new HotelAdapter(this::abrirHotel);
        binding.listHotels.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listHotels.setAdapter(adaptador);
        binding.listHotels.setHasFixedSize(true);
    }

    private void configurarNavegacion() {
        // Inicio no busca: manda a la pantalla de búsqueda, que es la que
        // tiene los filtros y las fechas.
        binding.searchField.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.searchFragment));

        binding.actionNotifications.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.notificationsFragment));

        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.cargar());
    }

    private void abrirHotel(@NonNull Hotel hotel) {
        Bundle args = new Bundle();
        args.putString("hotelId", hotel.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.hotelDetailFragment, args);
    }

    private void observar() {
        viewModel.getRecomendados().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getUsuario().observe(getViewLifecycleOwner(), this::pintarSaludo);
    }

    private void pintarSaludo(@Nullable User usuario) {
        if (usuario == null) {
            binding.greeting.setText(R.string.inicio_saludo_generico);
            return;
        }
        // Solo el nombre de pila: el saludo es un aparte, no una ficha.
        binding.greeting.setText(getString(R.string.inicio_saludo, usuario.getNombres()));
    }

    private void pintar(@NonNull UiState<List<Hotel>> estado) {
        binding.swipeRefresh.setRefreshing(false);

        // Se ocultan los cuatro y se enciende el que toca. Así no queda un
        // estado anterior visible detrás del nuevo.
        binding.skeletonContainer.setVisibility(View.GONE);
        binding.listHotels.setVisibility(View.GONE);
        binding.stateEmpty.setVisibility(View.GONE);
        binding.stateError.setVisibility(View.GONE);

        switch (estado.getStatus()) {
            case LOADING:
                mostrarEsqueleto();
                break;
            case SUCCESS:
                binding.listHotels.setVisibility(View.VISIBLE);
                adaptador.submitList(estado.getData());
                break;
            case EMPTY:
                binding.stateEmpty.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                binding.stateError.setVisibility(View.VISIBLE);
                binding.stateError.conReintento(estado.getMessage(), v -> viewModel.cargar());
                break;
        }
    }

    /**
     * Dibuja tres esqueletos, no uno: con uno solo la pantalla parecería rota,
     * y con más de tres se llena de ruido antes de tener datos.
     */
    private void mostrarEsqueleto() {
        binding.skeletonContainer.removeAllViews();
        binding.skeletonContainer.setVisibility(View.VISIBLE);
        for (int i = 0; i < 3; i++) {
            LoadingSkeletonView esqueleto = new LoadingSkeletonView(requireContext());
            binding.skeletonContainer.addView(esqueleto);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
