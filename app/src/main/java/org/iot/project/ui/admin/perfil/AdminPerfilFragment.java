package org.iot.project.ui.admin.perfil;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminPerfilBinding;
import org.iot.project.models.Hotel;
import org.iot.project.models.User;
import org.iot.project.utils.FormatoDeDatos;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.VersionDeLaApp;

/**
 * Perfil del administrador de hotel.
 *
 * <p>Existe porque la cuenta del administrador es una cuenta como cualquier
 * otra y hasta ahora no tenia donde verse: el panel de rol decia su nombre,
 * pero era una pantalla compartida con los otros dos roles de administracion y
 * no tenia nada del hotel. Aqui la identidad viene con lo que administra, que
 * es lo que hace que este perfil sea el de un administrador y no una copia del
 * del cliente.
 *
 * <p>Es de consulta, como el del cliente y por la misma razon: no hay a donde
 * mandar los cambios desde aqui. Lo que si hace es llevar a donde se cambian
 * —los datos del hotel— y a cerrar la sesion.
 */
public class AdminPerfilFragment extends Fragment {

    private FragmentAdminPerfilBinding binding;
    private AdminPerfilViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminPerfilBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminPerfilHeader);

        binding.adminPerfilHeader.setTitulo(R.string.perfil_titulo);
        binding.adminPerfilCerrarSesion.setOnClickListener(v -> confirmarCierre());
        binding.adminPerfilEditarHotel.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.adminHotelFragment));

        binding.adminPerfilVersion.setText(getString(R.string.perfil_version,
                VersionDeLaApp.nombre(requireContext())));

        viewModel = new ViewModelProvider(this).get(AdminPerfilViewModel.class);
        viewModel.getPerfil().observe(getViewLifecycleOwner(), this::pintarPerfil);
        viewModel.getHotel().observe(getViewLifecycleOwner(), this::pintarHotel);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Cuenta

    private void pintarPerfil(@NonNull UiState<User> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminPerfilEsqueleto.setVisibility(View.VISIBLE);
                binding.adminPerfilContenido.setVisibility(View.GONE);
                binding.adminPerfilError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                User usuario = estado.getData();
                if (usuario == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminPerfilEsqueleto.setVisibility(View.GONE);
                binding.adminPerfilError.setVisibility(View.GONE);
                binding.adminPerfilContenido.setVisibility(View.VISIBLE);
                pintarUsuario(usuario);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void pintarError(@Nullable String mensaje) {
        binding.adminPerfilEsqueleto.setVisibility(View.GONE);
        binding.adminPerfilContenido.setVisibility(View.GONE);
        binding.adminPerfilError.setVisibility(View.VISIBLE);
        binding.adminPerfilError.conReintento(mensaje, v -> viewModel.reintentar());
    }

    private void pintarUsuario(@NonNull User usuario) {
        binding.adminPerfilIniciales.setText(usuario.getIniciales());
        binding.adminPerfilNombre.setText(usuario.getNombreCompleto());
        binding.adminPerfilCorreoCabecera.setText(usuario.getEmail());
        // Del enum y no de una cadena suelta: si el rol cambia de nombre, la
        // etiqueta cambia con el.
        binding.adminPerfilRol.setText(usuario.getRol().getDisplayName());

        binding.adminPerfilFilaDocumento.bind(R.string.perfil_documento,
                FormatoDeDatos.documento(usuario), R.string.perfil_sin_dato);
        binding.adminPerfilFilaTelefono.bind(R.string.perfil_telefono,
                usuario.getTelefono(), R.string.perfil_sin_dato);
        binding.adminPerfilFilaCorreo.bind(R.string.perfil_correo,
                usuario.getEmail(), R.string.perfil_sin_dato);
    }

    // ------------------------------------------------------------------ Hotel

    private void pintarHotel(@NonNull UiState<Hotel> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminPerfilHotelEsqueleto.setVisibility(View.VISIBLE);
                binding.adminPerfilHotelDatos.setVisibility(View.GONE);
                binding.adminPerfilHotelError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                Hotel hotel = estado.getData();
                if (hotel == null) {
                    pintarHotelNoDisponible(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminPerfilHotelEsqueleto.setVisibility(View.GONE);
                binding.adminPerfilHotelError.setVisibility(View.GONE);
                binding.adminPerfilHotelDatos.setVisibility(View.VISIBLE);

                binding.adminPerfilHotelNombre.setText(hotel.getNombre());
                binding.adminPerfilHotelRating.setRating(hotel.getRating());
                binding.adminPerfilFilaCiudad.bind(R.string.admin_perfil_ciudad,
                        hotel.getCiudad(), R.string.perfil_sin_dato);
                binding.adminPerfilFilaDireccion.bind(R.string.admin_perfil_direccion,
                        hotel.getDireccion(), R.string.perfil_sin_dato);
                binding.adminPerfilFilaHabitaciones.bind(R.string.admin_perfil_habitaciones,
                        getResources().getQuantityString(R.plurals.admin_perfil_habitaciones,
                                hotel.getHabitaciones().size(), hotel.getHabitaciones().size()),
                        R.string.perfil_sin_dato);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarHotelNoDisponible(estado.getMessage());
                break;
        }
    }

    /**
     * El hotel no llego.
     *
     * <p>La tarjeta se queda con su título y dice que no se pudo leer, en vez
     * de desaparecer. Un administrador que abre su perfil y no ve su hotel no
     * sabe si es que no tiene o es que fallo, y esa duda es peor que el aviso.
     */
    private void pintarHotelNoDisponible(@Nullable String mensaje) {
        binding.adminPerfilHotelEsqueleto.setVisibility(View.GONE);
        binding.adminPerfilHotelDatos.setVisibility(View.GONE);
        binding.adminPerfilHotelError.setVisibility(View.VISIBLE);
        binding.adminPerfilHotelError.setText(mensaje != null && !mensaje.isEmpty()
                ? mensaje : getString(R.string.admin_perfil_hotel_error));
    }

    // ------------------------------------------------------------------ Sesion

    /**
     * Cerrar sesion se pregunta antes.
     *
     * <p>Deja la aplicacion en la pantalla de acceso y obliga a volver a elegir
     * cuenta, asi que no puede depender de un toque accidental.
     */
    private void confirmarCierre() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sesion_cerrar_titulo)
                .setMessage(R.string.sesion_cerrar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.sesion_cerrar,
                        (dialogo, cual) -> SessionManager.cerrarSesion())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
