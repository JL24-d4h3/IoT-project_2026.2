package org.iot.project.ui.admin.hotel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentAdminHotelDatosBinding;
import org.iot.project.databinding.ItemNearbyPlaceBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.NearbyPlace;
import org.iot.project.utils.InsetUtils;

import java.util.List;
import java.util.Locale;

/**
 * Datos del hotel (§42, RF-010 a RF-013).
 *
 * <p>La pantalla reúne las tres cosas que el administrador puede cambiar de su
 * hotel fuera de las habitaciones y los servicios: cómo se presenta —nombre,
 * descripción, ubicación—, sus fotografías, y los lugares de interés que tiene
 * alrededor. Las tres se editan desde aquí porque las tres se leen en la misma
 * ficha del cliente, y repartirlas en tres pantallas obligaría a recordar cuál
 * de ellas tiene lo que se busca.
 *
 * <p>Solo las fotografías se editan en la propia pantalla. Agregar una es pegar
 * un enlace y tocar un botón, y para eso una hoja sería un paso de más; el
 * formulario de datos y el del lugar cercano sí son hojas, que es donde caben
 * cinco y tres campos.
 */
public class HotelDatosFragment extends Fragment {

    private FragmentAdminHotelDatosBinding binding;
    private HotelDatosViewModel viewModel;
    private FotoHotelAdapter adaptadorFotos;

    /** El hotel que hay pintado, para abrir la hoja de datos con lo que se ve. */
    @Nullable
    private Hotel hotel;

    /**
     * Qué aviso toca cuando la operación sobre las fotografías salga bien.
     *
     * <p>Se guarda al pulsar y no se deduce del resultado porque las dos
     * operaciones comparten canal en el ViewModel y devuelven el mismo hotel:
     * desde el resultado no hay forma de saber si la tira creció o menguó.
     */
    @StringRes
    private int avisoFoto = R.string.admin_hotel_foto_agregada;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminHotelDatosBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminHotelHeader);

        binding.adminHotelHeader.setTitulo(R.string.nav_hotel_datos);
        // Vuelve aunque la barra inferior siga visible: esta pantalla no es una
        // de sus secciones, y sin flecha la única salida sería adivinar qué
        // pestaña lleva de vuelta al perfil.
        binding.adminHotelHeader.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptadorFotos = new FotoHotelAdapter();
        adaptadorFotos.setAlQuitar(this::quitarFoto);
        binding.adminHotelFotosLista.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.adminHotelFotosLista.setAdapter(adaptadorFotos);

        binding.adminHotelEditar.setOnClickListener(v -> abrirHojaDatos());
        binding.adminHotelFotoAgregar.setOnClickListener(v -> agregarFoto());
        binding.adminHotelLugarAgregar.setOnClickListener(v -> abrirHojaLugar());

        // El ViewModel es el del grafo y no el del fragmento: las dos hojas viven
        // en otros fragmentos y tienen que dejar el hotel cambiado aquí.
        viewModel = ViewModelGrafo.de(this, R.id.nav_hotel_admin, HotelDatosViewModel.class);
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getGuardado().observe(getViewLifecycleOwner(), this::pintarGuardado);
        viewModel.getFoto().observe(getViewLifecycleOwner(), this::pintarFoto);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<Hotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminHotelEsqueleto.setVisibility(View.VISIBLE);
                binding.adminHotelContenido.setVisibility(View.GONE);
                binding.adminHotelError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                Hotel datos = estado.getData();
                if (datos == null) {
                    // Un éxito sin datos no es un éxito: se trata como error
                    // para no dejar la pantalla en blanco sin explicación.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminHotelEsqueleto.setVisibility(View.GONE);
                binding.adminHotelError.setVisibility(View.GONE);
                binding.adminHotelContenido.setVisibility(View.VISIBLE);
                mostrar(datos);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminHotelEsqueleto.setVisibility(View.GONE);
        binding.adminHotelContenido.setVisibility(View.GONE);
        binding.adminHotelError.setVisibility(View.VISIBLE);
        binding.adminHotelError.conReintento(mensaje, v -> viewModel.recargar());
    }

    private void mostrar(@NonNull Hotel datos) {
        hotel = datos;

        binding.adminHotelNombre.setText(datos.getNombre());
        binding.adminHotelRating.setRating(datos.getRating());
        binding.adminHotelCiudad.setText(datos.getUbicacionCorta());

        binding.adminHotelFilaDireccion.bind(R.string.admin_hotel_direccion,
                datos.getDireccion(), R.string.perfil_sin_dato);
        binding.adminHotelFilaUbicacion.bind(R.string.admin_hotel_ubicacion,
                coordenadas(datos), R.string.perfil_sin_dato);

        // La descripción vacía no se deja en blanco: un hueco bajo el nombre se
        // lee como un fallo de la aplicación, y lo que pasa es que el hotel no
        // la escribió.
        binding.adminHotelDescripcion.setText(
                datos.getDescripcion() == null || datos.getDescripcion().isEmpty()
                        ? getString(R.string.admin_hotel_sin_descripcion)
                        : datos.getDescripcion());

        pintarFotos(datos);
        pintarLugares(datos.getLugaresCercanos());
    }

    /**
     * "−12.1219, −77.0297", o {@code null} si el hotel no tiene ubicación.
     *
     * <p>Se devuelve nulo y no una cadena vacía para que {@code DataRowView}
     * ponga su propio texto de relleno: el dato que falta lo describe la fila,
     * que es quien sabe cómo se llama su hueco.
     */
    @Nullable
    private CharSequence coordenadas(@NonNull Hotel datos) {
        if (datos.getLatitud() == 0d && datos.getLongitud() == 0d) {
            return null;
        }
        return getString(R.string.admin_hotel_coordenadas,
                String.format(Locale.getDefault(), "%.4f", datos.getLatitud()),
                String.format(Locale.getDefault(), "%.4f", datos.getLongitud()));
    }

    // ------------------------------------------------------------- Fotografías

    private void pintarFotos(@NonNull Hotel datos) {
        List<String> fotos = datos.getFotos();

        // La equis se esconde cuando quitar rompería el mínimo de RF-013. El
        // repositorio lo rechazaría igualmente, pero ofrecer un botón que solo
        // puede dar error es ofrecer un error.
        adaptadorFotos.mostrar(fotos, fotos.size() > Hotel.MIN_FOTOS);

        binding.adminHotelSinFotos.setVisibility(fotos.isEmpty() ? View.VISIBLE : View.GONE);
        binding.adminHotelFotosEstado.setText(estadoFotos(datos));
    }

    /**
     * "6 fotografías · mínimo 4", o el aviso de que faltan.
     *
     * <p>Cuando no se llega al mínimo el texto dice cuántas faltan y no cuántas
     * hay: quien lee "3 fotografías · mínimo 4" tiene que restar, y quien lee
     * "falta 1" ya sabe qué hacer. En el mínimo exacto tampoco se da la cuenta:
     * lo que hace falta saber ahí es que la siguiente que se agregue es la que
     * permite quitar, y eso no lo dice un número.
     */
    @NonNull
    private CharSequence estadoFotos(@NonNull Hotel datos) {
        int cuantas = datos.getFotos().size();
        if (cuantas < Hotel.MIN_FOTOS) {
            int faltan = Hotel.MIN_FOTOS - cuantas;
            return getResources().getQuantityString(
                    R.plurals.admin_hotel_fotos_faltantes, faltan, faltan, Hotel.MIN_FOTOS);
        }
        if (cuantas == Hotel.MIN_FOTOS) {
            return getString(R.string.admin_hotel_fotos_justas);
        }
        return getString(R.string.admin_hotel_fotos_estado,
                getResources().getQuantityString(R.plurals.admin_hotel_fotos, cuantas, cuantas),
                Hotel.MIN_FOTOS);
    }

    private void agregarFoto() {
        binding.adminHotelFotoCampo.setError(null);
        CharSequence contenido = binding.adminHotelFoto.getText();
        String url = contenido != null ? contenido.toString().trim() : "";
        if (url.isEmpty()) {
            binding.adminHotelFotoCampo.setError(getString(R.string.admin_hotel_foto_obligatoria));
            return;
        }
        avisoFoto = R.string.admin_hotel_foto_agregada;
        viewModel.agregarFoto(url);
    }

    private void quitarFoto(@NonNull String url) {
        avisoFoto = R.string.admin_hotel_foto_quitada;
        viewModel.quitarFoto(url);
    }

    /**
     * El resultado de agregar o de quitar una fotografía.
     *
     * <p>Al salir bien se limpia el campo del enlace: si se quedara, el
     * siguiente toque en "Agregar" registraría otra vez la misma fotografía, y
     * el hotel acabaría con dos iguales sin que nadie lo haya pedido.
     */
    private void pintarFoto(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminHotelFotoAgregar.setEnabled(false);
                // No se limpia: la operación sigue en curso y su resultado
                // todavía tiene que llegar.
                return;
            case SUCCESS:
                binding.adminHotelFotoAgregar.setEnabled(true);
                binding.adminHotelFoto.setText("");
                avisar(getString(avisoFoto));
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.adminHotelFotoAgregar.setEnabled(true);
                avisar(mensajeDe(estado));
                break;
        }
        viewModel.limpiarFoto();
    }

    // ----------------------------------------------------------- Lugares

    private void pintarLugares(@NonNull List<NearbyPlace> lugares) {
        binding.adminHotelLugaresLista.removeAllViews();
        binding.adminHotelSinLugares.setVisibility(lugares.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (NearbyPlace lugar : lugares) {
            ItemNearbyPlaceBinding fila = ItemNearbyPlaceBinding.inflate(
                    inflador, binding.adminHotelLugaresLista, false);
            fila.nearbyName.setText(lugar.getNombre());
            // Mismo formato que la ficha del cliente (HotelDetailFragment): el
            // lugar se lee igual desde los dos lados porque es el mismo dato.
            fila.nearbyDistance.setText(getString(R.string.detalle_distancia,
                    lugar.getTipo(), String.format(Locale.getDefault(), "%.1f",
                            lugar.getDistanciaKm())));
            binding.adminHotelLugaresLista.addView(fila.getRoot());
        }
    }

    // ------------------------------------------------------------------ Hojas

    private void abrirHojaDatos() {
        Hotel actual = hotel;
        if (actual == null) {
            // Sin hotel cargado no hay nada que editar, y en ese estado la
            // pantalla ni siquiera tiene el botón a la vista.
            return;
        }
        HotelSheet.para(actual).show(getChildFragmentManager(), HotelSheet.TAG);
    }

    private void abrirHojaLugar() {
        if (hotel == null) {
            return;
        }
        LugarSheet.nueva().show(getChildFragmentManager(), LugarSheet.TAG);
    }

    // ------------------------------------------------------------------ Avisos

    /** El resultado de guardar el formulario de datos (RF-010). */
    private void pintarGuardado(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                return;
            case SUCCESS:
                avisar(getString(R.string.admin_hotel_guardado));
                break;
            case EMPTY:
            case ERROR:
            default:
                avisar(mensajeDe(estado));
                break;
        }
        viewModel.limpiarGuardado();
    }

    private void avisar(@NonNull String mensaje) {
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    @NonNull
    private String mensajeDe(@NonNull UiState<Hotel> estado) {
        String mensaje = estado.getMessage();
        return mensaje != null && !mensaje.isEmpty()
                ? mensaje : getString(R.string.estado_error_descripcion);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
