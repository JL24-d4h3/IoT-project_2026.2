package org.iot.project.ui.superadmin.usuarios;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminUsuariosBinding;
import org.iot.project.models.Role;
import org.iot.project.models.User;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Cuentas de la plataforma (RF-005, RF-006).
 *
 * <p>Los superadministradores se ven pero no se tocan: desactivarlos dejaria la
 * plataforma sin nadie que pueda volver a activar a los demas.
 *
 * <p>Desactivar se pregunta y activar no. Desactivar deja a alguien fuera de la
 * aplicacion (RF-009) y puede ser un error de dedo sobre la fila de al lado;
 * activar devuelve el acceso y no quita nada a nadie.
 */
public class SuperadminUsuariosFragment extends Fragment {

    private FragmentSuperadminUsuariosBinding binding;
    private SuperadminUsuariosViewModel viewModel;
    private CuentaSaAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminUsuariosBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_usuarios);

        nombrarFiltros();

        adaptador = new CuentaSaAdapter(this::confirmar);
        binding.saUsuariosLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saUsuariosLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(SuperadminUsuariosViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getCambio().observe(getViewLifecycleOwner(), this::avisarDelCambio);

        // Despues de tener el ViewModel: el grupo puede anunciar el chip que ya
        // venia marcado en cuanto se registra el oyente.
        binding.saUsuariosFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrarPor(rolDe(marcados.isEmpty() ? 0 : marcados.get(0))));

        viewModel.cargar();
    }

    /**
     * Pone el nombre de cada rol en su chip.
     *
     * <p>El nombre sale de {@link Role#getDisplayName()} y no de una cadena
     * propia: los roles ya se llaman a si mismos en un solo sitio.
     */
    private void nombrarFiltros() {
        binding.saFiltroClientes.setText(Role.CLIENTE.getDisplayName());
        binding.saFiltroAdministradores.setText(Role.ADMIN_HOTEL.getDisplayName());
        binding.saFiltroSuperadmins.setText(Role.SUPERADMIN.getDisplayName());
    }

    @Nullable
    private Role rolDe(int chipId) {
        if (chipId == R.id.sa_filtro_clientes) {
            return Role.CLIENTE;
        }
        if (chipId == R.id.sa_filtro_administradores) {
            return Role.ADMIN_HOTEL;
        }
        if (chipId == R.id.sa_filtro_superadmins) {
            return Role.SUPERADMIN;
        }
        return null;
    }

    /**
     * Desactivar deja a alguien fuera de la aplicacion (RF-009), asi que se
     * pregunta antes. Activar no se pregunta: devuelve el acceso y no quita nada.
     *
     * <p>Llega el identificador y el nombre en vez de la cuenta porque es la
     * fila quien lo pide, y la fila guarda una copia de lo que pinta.
     */
    private void confirmar(@NonNull String usuarioId, @NonNull String nombre, boolean activo) {
        if (activo) {
            viewModel.cambiarActivo(usuarioId, true);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sa_desactivar_titulo, nombre))
                .setMessage(R.string.sa_desactivar_mensaje)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.sa_desactivar,
                        (dialogo, cual) -> viewModel.cambiarActivo(usuarioId, false))
                .show();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<User>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saUsuariosVacio.setVisibility(View.GONE);
                binding.saUsuariosLista.setVisibility(View.VISIBLE);
                adaptador.mostrar(estado.requireData());
                break;
            case EMPTY:
                mostrar(true, false, false);
                binding.saUsuariosLista.setVisibility(View.GONE);
                binding.saUsuariosVacio.setVisibility(View.VISIBLE);
                binding.saUsuariosVacio.conIcono(R.drawable.ic_person_group)
                        .conTitulo(R.string.sa_usuarios_vacio_titulo)
                        .conMensaje(R.string.sa_usuarios_vacio_mensaje);
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    /**
     * El aviso de una sola vez tras activar o desactivar.
     *
     * <p>El texto se arma aqui y no en el ViewModel porque lleva el nombre
     * dentro y los textos de la aplicacion viven en {@code strings.xml}: el
     * ViewModel no tiene {@code Context} con el que leerlos.
     */
    private void avisarDelCambio(@Nullable UiState<User> estado) {
        if (estado == null) {
            return;
        }
        User cuenta = estado.getData();
        avisar(cuenta != null
                ? getString(cuenta.isActivo()
                        ? R.string.sa_cuenta_ya_puede_entrar
                        : R.string.sa_cuenta_ya_no_puede_entrar, cuenta.getNombreCompleto())
                : mensajeDe(estado));
        viewModel.consumirCambio();
    }

    @NonNull
    private CharSequence mensajeDe(@NonNull UiState<User> estado) {
        String mensaje = estado.getMessage();
        return mensaje != null && !mensaje.isEmpty()
                ? mensaje : getString(R.string.estado_error_descripcion);
    }

    private void avisar(@NonNull CharSequence mensaje) {
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saUsuariosLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
