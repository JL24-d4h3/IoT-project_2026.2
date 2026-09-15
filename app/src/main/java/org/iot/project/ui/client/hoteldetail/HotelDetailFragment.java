package org.iot.project.ui.client.hoteldetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentHotelDetailBinding;
import org.iot.project.databinding.ItemNearbyPlaceBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Review;
import org.iot.project.models.Room;
import org.iot.project.models.Service;
import org.iot.project.ui.common.ReviewAdapter;
import org.iot.project.ui.common.RoomAdapter;
import org.iot.project.ui.components.ServiceChipView;
import org.iot.project.utils.InsetUtils;

import java.util.List;
import java.util.Locale;

/**
 * Ficha de un alojamiento: fotos, servicios, habitaciones y opiniones.
 *
 * <p>Es la pantalla donde se decide. Por eso cada sección se pinta en cuanto
 * llega, sin esperar a las demás: si las opiniones tardan o fallan, las
 * habitaciones y el precio ya están a la vista y la decisión puede seguir.
 */
public class HotelDetailFragment extends Fragment {

    private FragmentHotelDetailBinding binding;
    private HotelDetailViewModel viewModel;
    private RoomAdapter adaptadorHabitaciones;
    private ReviewAdapter adaptadorResenas;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHotelDetailBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        viewModel = new ViewModelProvider(this).get(HotelDetailViewModel.class);

        prepararHeader();
        prepararListas();
        observar();

        String hotelId = getArguments() != null
                ? getArguments().getString("hotelId") : null;
        if (hotelId == null) {
            // Sin identificador no hay nada que pedir: mejor decirlo que dejar
            // la pantalla cargando para siempre.
            binding.detalleError.setVisibility(View.VISIBLE);
            binding.detalleError.conReintento(getString(R.string.estado_error_mensaje), null);
            return;
        }
        viewModel.cargar(hotelId);
    }

    private void prepararHeader() {
        binding.header.mostrarVolver(v ->
                Navigation.findNavController(v).navigateUp());
        binding.header.mostrarAccion(R.drawable.ic_heart, R.string.cd_favorito,
                v -> viewModel.alternarFavorito());
    }

    private void prepararListas() {
        adaptadorHabitaciones = new RoomAdapter(this::elegirHabitacion);
        binding.detalleHabitaciones.setLayoutManager(
                new LinearLayoutManager(getContext()));
        binding.detalleHabitaciones.setAdapter(adaptadorHabitaciones);

        adaptadorResenas = new ReviewAdapter();
        binding.detalleResenas.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.detalleResenas.setAdapter(adaptadorResenas);
    }

    private void observar() {
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintarHotel);
        viewModel.getHabitaciones().observe(getViewLifecycleOwner(), this::pintarHabitaciones);
        viewModel.getResenas().observe(getViewLifecycleOwner(), this::pintarResenas);
        viewModel.getEsFavorito().observe(getViewLifecycleOwner(), this::pintarFavorito);
    }

    // ---------------------------------------------------------------- Hotel

    private void pintarHotel(@NonNull UiState<Hotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.detalleEsqueleto.setVisibility(View.VISIBLE);
                binding.detalleContenido.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.detalleEsqueleto.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.GONE);
                binding.detalleContenido.setVisibility(View.VISIBLE);
                mostrarHotel(estado.requireData());
                break;
            default:
                binding.detalleEsqueleto.setVisibility(View.GONE);
                binding.detalleContenido.setVisibility(View.GONE);
                binding.detalleError.setVisibility(View.VISIBLE);
                binding.detalleError.conReintento(estado.getMessage(), v -> viewModel.recargar());
                break;
        }
    }

    private void mostrarHotel(@NonNull Hotel hotel) {
        binding.header.setTitulo(hotel.getNombre());
        binding.detalleNombre.setText(hotel.getNombre());
        binding.detalleUbicacion.setText(getString(R.string.detalle_ubicacion,
                hotel.getDistrito(), hotel.getCiudad(), hotel.getDireccion()));
        binding.detalleRating.setRating(hotel.getRating());

        int resenas = hotel.getNumReviews();
        binding.detalleNumResenas.setText(resenas > 0
                ? getString(R.string.hotel_resenas, resenas)
                : getString(R.string.hotel_sin_resenas));

        binding.detallePrecio.setText(getString(R.string.hotel_precio_desde,
                org.iot.project.utils.PriceFormatter.format(hotel.getPrecioDesde())));

        String descripcion = hotel.getDescripcion();
        binding.detalleDescripcion.setText(
                descripcion == null || descripcion.isEmpty()
                        ? getString(R.string.detalle_sin_descripcion)
                        : descripcion);

        binding.detalleGaleria.setFotos(hotel.getFotos());
        pintarServicios(hotel.getServicios());
        pintarLugaresCercanos(hotel.getLugaresCercanos());
    }

    private void pintarServicios(@NonNull List<HotelService> servicios) {
        binding.detalleServicios.removeAllViews();
        boolean hayServicios = false;

        for (HotelService asignado : servicios) {
            Service servicio = ServiceLocator.hoteles().servicio(asignado.getServiceId());
            if (servicio == null) {
                // Un servicio fuera del catálogo es un dato roto: se omite en vez
                // de pintar una fila sin nombre.
                continue;
            }
            ServiceChipView chip = new ServiceChipView(requireContext());
            chip.bind(servicio, asignado);
            LinearLayout.LayoutParams parametros = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            parametros.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.space_sm));
            chip.setLayoutParams(parametros);
            binding.detalleServicios.addView(chip);
            hayServicios = true;
        }

        binding.detalleServiciosScroll.setVisibility(hayServicios ? View.VISIBLE : View.GONE);
        binding.detalleServiciosVacio.setVisibility(hayServicios ? View.GONE : View.VISIBLE);
        if (!hayServicios) {
            // Es un estado legítimo, no un fallo: el hotel existe y no publicó
            // servicios (ver MockData, hotel H7).
            binding.detalleServiciosVacio.conTitulo(R.string.detalle_servicios_titulo);
            binding.detalleServiciosVacio.conMensaje(R.string.detalle_servicios_vacio);
        }
    }

    private void pintarLugaresCercanos(@NonNull List<NearbyPlace> lugares) {
        binding.detalleCerca.removeAllViews();
        binding.detalleCercaSeccion.setVisibility(lugares.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (NearbyPlace lugar : lugares) {
            ItemNearbyPlaceBinding fila = ItemNearbyPlaceBinding.inflate(
                    inflador, binding.detalleCerca, false);
            fila.nearbyName.setText(lugar.getNombre());
            fila.nearbyDistance.setText(getString(R.string.detalle_distancia,
                    lugar.getTipo(), String.format(Locale.getDefault(), "%.1f",
                            lugar.getDistanciaKm())));
            binding.detalleCerca.addView(fila.getRoot());
        }
    }

    // --------------------------------------------------------- Habitaciones

    private void pintarHabitaciones(@NonNull UiState<List<Room>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.detalleHabitaciones.setVisibility(View.GONE);
                binding.detalleHabitacionesVacio.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.detalleHabitaciones.setVisibility(View.VISIBLE);
                binding.detalleHabitacionesVacio.setVisibility(View.GONE);
                adaptadorHabitaciones.submitList(estado.requireData());
                break;
            default:
                binding.detalleHabitaciones.setVisibility(View.GONE);
                binding.detalleHabitacionesVacio.setVisibility(View.VISIBLE);
                binding.detalleHabitacionesVacio.conTitulo(estado.isEmpty()
                        ? R.string.detalle_habitaciones_vacio_titulo
                        : R.string.estado_error_titulo);
                binding.detalleHabitacionesVacio.conMensaje(estado.isEmpty()
                        ? getString(R.string.detalle_habitaciones_vacio)
                        : estado.getMessage());
                break;
        }
    }

    private void elegirHabitacion(@NonNull Room room) {
        Bundle argumentos = new Bundle();
        argumentos.putString("hotelId", room.getHotelId());
        argumentos.putString("roomId", room.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.bookingFlowFragment, argumentos);
    }

    // -------------------------------------------------------------- Reseñas

    private void pintarResenas(@NonNull UiState<List<Review>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.detalleResenas.setVisibility(View.GONE);
                binding.detalleResenasVacio.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.detalleResenas.setVisibility(View.VISIBLE);
                binding.detalleResenasVacio.setVisibility(View.GONE);
                adaptadorResenas.submitList(estado.requireData());
                break;
            default:
                binding.detalleResenas.setVisibility(View.GONE);
                binding.detalleResenasVacio.setVisibility(View.VISIBLE);
                binding.detalleResenasVacio.conTitulo(estado.isEmpty()
                        ? R.string.hotel_sin_resenas
                        : R.string.estado_error_titulo);
                binding.detalleResenasVacio.conMensaje(estado.isEmpty()
                        ? getString(R.string.detalle_resenas_vacio)
                        : estado.getMessage());
                break;
        }
    }

    // ------------------------------------------------------------ Favorito

    private void pintarFavorito(boolean esFavorito) {
        ImageView boton = binding.header.getBotonAccion();
        boton.setColorFilter(getResources().getColor(
                esFavorito ? R.color.colorAccent : R.color.colorTextPrimary, null));
        boton.setContentDescription(getString(
                esFavorito ? R.string.cd_quitar_favorito : R.string.cd_favorito));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.detalleHabitaciones.setAdapter(null);
        binding.detalleResenas.setAdapter(null);
        binding = null;
    }
}
