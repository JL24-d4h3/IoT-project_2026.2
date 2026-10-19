package org.iot.project.ui.superadmin.hoteles;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentSuperadminHotelBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.ui.admin.reportes.PeriodoAdapter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.AvisoDePublicacion;

import java.util.List;
import java.util.Locale;

/**
 * Ficha de un hotel para el superadministrador (RF-008, RF-059).
 *
 * <p>La ficha permite asignar al administrador y publicar o retirar el hotel
 * con confirmacion del servidor. Su contenido lo edita el administrador.
 *
 * <p>El ViewModel es el del grafo y no el de este Fragment: la hoja de
 * asignacion es un destino aparte y tiene que alcanzar al mismo. Por eso al
 * abrir otro hotel se tira lo cargado, que era de otro.
 */
public class SuperadminHotelFragment extends Fragment {

    private FragmentSuperadminHotelBinding binding;
    private SuperadminHotelViewModel viewModel;
    private OnBackPressedCallback bloqueoRetroceso;
    private PeriodoAdapter ventas;

    /** Lo ultimo pintado, que es lo que necesitan los botones al pulsarse. */
    @Nullable
    private SuperadminHotelViewModel.Contenido contenido;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminHotelBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.sa_titulo_ficha_hotel);
        binding.saHeader.mostrarVolver(v -> {
            if (viewModel == null || !viewModel.estaModificando()) {
                Navigation.findNavController(v).navigateUp();
            }
        });

        ventas = new PeriodoAdapter();
        binding.saFichaVentas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saFichaVentas.setAdapter(ventas);

        String hotelId = getArguments() == null
                ? null : getArguments().getString(SuperadminHotelesFragment.ARG_HOTEL_ID);
        if (hotelId == null) {
            // Sin hotel no hay ficha: se vuelve en vez de dejar una pantalla en
            // blanco. No deberia ocurrir, porque el unico que navega aqui es la
            // lista y siempre pasa un identificador.
            Navigation.findNavController(vista).popBackStack();
            return;
        }

        viewModel = ViewModelGrafo.de(this, R.id.nav_superadmin, SuperadminHotelViewModel.class);
        bloqueoRetroceso = new OnBackPressedCallback(viewModel.estaModificando()) {
            @Override
            public void handleOnBackPressed() {
                // La operacion debe terminar antes de abandonar esta ficha.
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), bloqueoRetroceso);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getCambio().observe(getViewLifecycleOwner(), this::avisarDelCambio);

        binding.saFichaAsignar.setOnClickListener(v -> abrirAsignacion());
        binding.saFichaPublicacionAccion.setOnClickListener(v -> accionarPublicacion());

        viewModel.abrir(hotelId);
    }

    /** RF-008: la hoja enseña los candidatos que la ficha ya trae cargados. */
    private void abrirAsignacion() {
        if (!viewModel.puedeAsignar() || getChildFragmentManager().isStateSaved()
                || getChildFragmentManager().findFragmentByTag(AsignarAdministradorSheet.TAG) != null) {
            return;
        }
        new AsignarAdministradorSheet()
                .showNow(getChildFragmentManager(), AsignarAdministradorSheet.TAG);
    }

    private void accionarPublicacion() {
        SuperadminHotelViewModel.Contenido datos = contenido;
        if (datos == null || !viewModel.puedeCambiarPublicacion()) {
            return;
        }
        if (!datos.hotel.isPublicado()) {
            viewModel.cambiarPublicacion(true);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sa_retirar_hotel_titulo, datos.hotel.getNombre()))
                .setMessage(R.string.sa_retirar_hotel_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.sa_retirar,
                        (dialogo, cual) -> viewModel.cambiarPublicacion(false))
                .show();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<SuperadminHotelViewModel.Contenido> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                contenido = null;
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                llenar(estado.requireData());
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
        actualizarAcciones();
    }

    private void llenar(@NonNull SuperadminHotelViewModel.Contenido datos) {
        contenido = datos;
        Hotel hotel = datos.hotel;

        binding.saFichaNombre.setText(hotel.getNombre());
        binding.saFichaRating.setRating(hotel.getRating());
        binding.saFichaUbicacion.setText(hotel.getUbicacionCorta());
        binding.saFichaFilaDireccion.bind(R.string.admin_hotel_direccion,
                hotel.getDireccion(), R.string.perfil_sin_dato);
        binding.saFichaFilaUbicacion.bind(R.string.admin_hotel_ubicacion,
                coordenadas(hotel), R.string.perfil_sin_dato);
        binding.saFichaDescripcion.setText(
                hotel.getDescripcion() == null || hotel.getDescripcion().isEmpty()
                        ? getString(R.string.sa_ficha_sin_descripcion)
                        : hotel.getDescripcion());

        // Las dos cifras que deciden si el hotel puede publicarse (RF-013,
        // RF-014). Se cuentan siempre, aunque sobren: "6 de 4" dice que el
        // minimo esta cumplido y de sobra.
        binding.saFichaFotos.setDato(R.string.sa_ficha_fotos,
                getString(R.string.sa_ficha_fotos_valor,
                        hotel.getFotos().size(), Hotel.MIN_FOTOS));
        binding.saFichaHabitaciones.setDato(R.string.sa_ficha_habitaciones,
                String.valueOf(hotel.getHabitaciones().size()));

        pintarAdministrador(datos);
        pintarPublicacion(datos);
        pintarVentas(datos.ventas);
    }

    private void pintarAdministrador(@NonNull SuperadminHotelViewModel.Contenido datos) {
        boolean hay = !datos.sinAsignar && datos.administrador != null;
        binding.saFichaAdmin.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.saFichaAdminVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
        if (hay) {
            binding.saFichaAdmin.setText(datos.administrador);
        } else if (!datos.sinAsignar) {
            // El hotel apunta a una cuenta que ya no esta activa (RF-006): no es
            // lo mismo que no tener administrador, y se dice distinto.
            binding.saFichaAdminVacio.setText(R.string.sa_hotel_admin_inactivo);
        } else {
            binding.saFichaAdminVacio.setText(R.string.sa_hotel_admin_vacio);
        }
        binding.saFichaAsignar.setText(datos.sinAsignar
                ? R.string.sa_asignar_administrador : R.string.sa_cambiar_administrador);
    }

    private void pintarPublicacion(@NonNull SuperadminHotelViewModel.Contenido datos) {
        boolean publicado = datos.hotel.isPublicado();
        binding.saFichaPublicacionEstado.setText(publicado
                ? R.string.sa_hotel_publicado : R.string.sa_hotel_sin_publicar);
        fondo(publicado ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);
        binding.saFichaPublicacionEstado.setTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), publicado
                        ? R.color.colorOnSuccessContainer : R.color.colorOnWarningContainer)));

        CharSequence motivo = publicado ? null : AvisoDePublicacion.motivo(requireContext(), datos.hotel);
        if (!publicado && motivo == null && (datos.sinAsignar || datos.administrador == null)) {
            motivo = getString(R.string.sa_publicacion_administrador_requerido);
        }
        binding.saFichaPublicacionMotivo.setVisibility(motivo == null ? View.GONE : View.VISIBLE);
        if (motivo != null) binding.saFichaPublicacionMotivo.setText(motivo);

        binding.saFichaPublicacionAccion.setText(publicado
                ? R.string.sa_retirar : R.string.sa_publicar);
        binding.saFichaPublicacionAccion.setEnabled(viewModel.puedeCambiarPublicacion());
    }

    private void pintarVentas(@NonNull List<PeriodoDeVentas> periodos) {
        binding.saFichaVentas.setVisibility(View.GONE);
        binding.saFichaVentasVacio.setVisibility(View.VISIBLE);
        binding.saFichaVentasVacio.setText(R.string.sa_ventas_pendiente_conexion);
    }

    private void fondo(int drawable) {
        binding.saFichaPublicacionEstado.setBackground(
                ContextCompat.getDrawable(requireContext(), drawable));
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    /**
     * El aviso de una sola vez tras asignar, publicar o retirar.
     *
     * <p>El texto se arma aqui y no en el ViewModel porque lleva el nombre del
     * hotel dentro y los textos de la aplicacion viven en {@code strings.xml}.
     */
    private void avisarDelCambio(@Nullable UiState<SuperadminHotelViewModel.Cambio> estado) {
        actualizarAcciones();
        if (estado == null || estado.isLoading()) {
            return;
        }
        Fragment hoja = getChildFragmentManager().findFragmentByTag(AsignarAdministradorSheet.TAG);
        if (hoja instanceof AsignarAdministradorSheet) {
            if (estado.isError()) {
                // La hoja conserva el error junto a las cuentas para poder reintentar.
                return;
            }
            ((AsignarAdministradorSheet) hoja).dismiss();
        }
        SuperadminHotelViewModel.Cambio cambio = estado.getData();
        avisar(cambio != null ? textoDe(cambio) : mensajeDe(estado));
        viewModel.consumirCambio();
    }

    private void actualizarAcciones() {
        boolean pendiente = viewModel.estaModificando();
        bloqueoRetroceso.setEnabled(pendiente);
        binding.saHeader.getBotonVolver().setEnabled(!pendiente);
        binding.saFichaAsignar.setEnabled(viewModel.puedeAsignar());
        SuperadminHotelViewModel.Operacion operacion = viewModel.getOperacionPendiente();
        if (pendiente && operacion == SuperadminHotelViewModel.Operacion.ASIGNAR) {
            binding.saFichaAsignar.setText(R.string.sa_asignando_red);
        } else if (contenido != null) {
            binding.saFichaAsignar.setText(contenido.sinAsignar
                    ? R.string.sa_asignar_administrador : R.string.sa_cambiar_administrador);
        }
        binding.saFichaPublicacionAccion.setEnabled(viewModel.puedeCambiarPublicacion());
        if (pendiente && operacion == SuperadminHotelViewModel.Operacion.PUBLICAR) {
            binding.saFichaPublicacionAccion.setText(R.string.sa_publicando_red);
        } else if (pendiente && operacion == SuperadminHotelViewModel.Operacion.RETIRAR) {
            binding.saFichaPublicacionAccion.setText(R.string.sa_retirando_red);
        } else if (contenido != null) {
            binding.saFichaPublicacionAccion.setText(contenido.hotel.isPublicado()
                    ? R.string.sa_retirar : R.string.sa_publicar);
        }
    }

    @NonNull
    private CharSequence textoDe(@NonNull SuperadminHotelViewModel.Cambio cambio) {
        String nombre = cambio.hotel.getNombre();
        switch (cambio.operacion) {
            case PUBLICAR:
                return getString(R.string.sa_hotel_ya_se_ofrece, nombre);
            case RETIRAR:
                return getString(R.string.sa_hotel_ya_no_se_ofrece, nombre);
            case ASIGNAR:
            default:
                return getString(R.string.sa_hotel_ya_tiene_administrador, nombre);
        }
    }

    @NonNull
    private CharSequence mensajeDe(@NonNull UiState<SuperadminHotelViewModel.Cambio> estado) {
        String mensaje = estado.getMessage();
        return mensaje != null && !mensaje.isEmpty()
                ? mensaje : getString(R.string.estado_error_descripcion);
    }

    private void avisar(@NonNull CharSequence mensaje) {
        Snackbar aviso = Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG);
        View barra = requireActivity().findViewById(R.id.bottom_nav);
        if (barra != null && barra.getVisibility() == View.VISIBLE) {
            aviso.setAnchorView(barra);
        }
        aviso.show();
    }

    /**
     * "−12.1219, −77.0297", o {@code null} si el hotel no tiene ubicacion.
     *
     * <p>Se devuelve nulo y no una cadena vacia para que {@code DataRowView}
     * ponga su propio texto de relleno.
     */
    @Nullable
    private CharSequence coordenadas(@NonNull Hotel hotel) {
        if (hotel.getLatitud() == 0d && hotel.getLongitud() == 0d) {
            return null;
        }
        return getString(R.string.admin_hotel_coordenadas,
                String.format(Locale.getDefault(), "%.4f", hotel.getLatitud()),
                String.format(Locale.getDefault(), "%.4f", hotel.getLongitud()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saFichaVentas.setAdapter(null);
        ventas = null;
        contenido = null;
        binding = null;
        bloqueoRetroceso = null;
    }
}
