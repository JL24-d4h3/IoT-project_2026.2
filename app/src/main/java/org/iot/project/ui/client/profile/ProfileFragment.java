package org.iot.project.ui.client.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentProfileBinding;
import org.iot.project.models.Card;
import org.iot.project.models.User;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.VersionDeLaApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Perfil del cliente (§40). Prioridad 8 de §79.
 *
 * <p>Es una pantalla de consulta. No hay campos editables porque no hay a donde
 * mandar los cambios todavia, y un campo que se puede escribir pero no guardar
 * es peor que uno que solo se lee: el usuario escribe, sale, y descubre que no
 * quedo nada.
 *
 * <p>Lo que si hace es llevar a donde se cambia cada cosa: las notificaciones a
 * su pantalla, y la sesion a cerrarse.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.perfil_titulo);

        binding.perfilFilaNotificaciones.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.notificationsFragment));
        binding.perfilCerrarSesion.setOnClickListener(v -> confirmarCierre());

        // Idioma y moneda no dependen del usuario: la aplicacion tiene uno solo
        // de cada, y por eso se escriben aqui y no en el modelo.
        binding.perfilFilaIdioma.bind(R.string.perfil_idioma,
                getString(R.string.perfil_idioma_valor), R.string.perfil_sin_dato);
        binding.perfilFilaMoneda.bind(R.string.perfil_moneda,
                getString(R.string.perfil_moneda_valor), R.string.perfil_sin_dato);

        binding.perfilVersion.setText(getString(R.string.perfil_version,
                VersionDeLaApp.nombre(requireContext())));
        binding.perfilFilaNotificaciones.bind(R.drawable.ic_notifications,
                R.string.perfil_notificaciones, null);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        viewModel.getPerfil().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getSinLeer().observe(getViewLifecycleOwner(), this::pintarSinLeer);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<User> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.perfilEsqueleto.setVisibility(View.VISIBLE);
                binding.perfilContenido.setVisibility(View.GONE);
                binding.perfilError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                User usuario = estado.getData();
                if (usuario == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.perfilEsqueleto.setVisibility(View.GONE);
                binding.perfilError.setVisibility(View.GONE);
                binding.perfilContenido.setVisibility(View.VISIBLE);
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
        binding.perfilEsqueleto.setVisibility(View.GONE);
        binding.perfilContenido.setVisibility(View.GONE);
        binding.perfilError.setVisibility(View.VISIBLE);
        binding.perfilError.conReintento(mensaje, v -> viewModel.reintentar());
    }

    private void pintarUsuario(@NonNull User usuario) {
        binding.perfilIniciales.setText(usuario.getIniciales());
        binding.perfilNombre.setText(usuario.getNombreCompleto());
        binding.perfilCorreoCabecera.setText(usuario.getEmail());

        binding.perfilFilaDocumento.bind(R.string.perfil_documento,
                documento(usuario), R.string.perfil_sin_dato);
        binding.perfilFilaNacimiento.bind(R.string.perfil_nacimiento,
                fechaNacimiento(usuario), R.string.perfil_sin_dato);
        binding.perfilFilaDireccion.bind(R.string.perfil_direccion,
                usuario.getDireccion(), R.string.perfil_sin_dato);
        binding.perfilFilaTelefono.bind(R.string.perfil_telefono,
                usuario.getTelefono(), R.string.perfil_sin_dato);
        binding.perfilFilaCorreo.bind(R.string.perfil_correo,
                usuario.getEmail(), R.string.perfil_sin_dato);

        pintarTarjetas(usuario);
    }

    /** "DNI 45872310", o solo el numero si el tipo no esta registrado. */
    @Nullable
    private CharSequence documento(@NonNull User usuario) {
        String numero = usuario.getNumeroDocumento();
        if (numero == null || numero.isEmpty()) {
            return null;
        }
        String tipo = usuario.getTipoDocumento();
        return tipo == null || tipo.isEmpty() ? numero : tipo + " " + numero;
    }

    @Nullable
    private CharSequence fechaNacimiento(@NonNull User usuario) {
        String fecha = usuario.getFechaNacimiento();
        return fecha == null || fecha.isEmpty() ? null : fecha;
    }

    private void pintarTarjetas(@NonNull User usuario) {
        List<Card> tarjetas = usuario.getTarjetas() != null
                ? usuario.getTarjetas() : new ArrayList<>();

        binding.perfilTarjetas.removeAllViews();
        binding.perfilPagoVacio.setVisibility(tarjetas.isEmpty()
                ? View.VISIBLE : View.GONE);
        binding.perfilTarjetas.setVisibility(tarjetas.isEmpty()
                ? View.GONE : View.VISIBLE);

        LayoutInflater inflador = LayoutInflater.from(requireContext());
        for (Card tarjeta : tarjetas) {
            View fila = inflador.inflate(R.layout.item_tarjeta_guardada,
                    binding.perfilTarjetas, false);

            ((TextView) fila.findViewById(R.id.tarjeta_nombre))
                    .setText(tarjeta.getDisplayName());
            ((TextView) fila.findViewById(R.id.tarjeta_titular))
                    .setText(tarjeta.getTitular());
            ((TextView) fila.findViewById(R.id.tarjeta_vence))
                    .setText(getString(R.string.tarjeta_vence, tarjeta.getExpiracion()));

            LinearLayout.LayoutParams parametros = (LinearLayout.LayoutParams)
                    fila.getLayoutParams();
            if (binding.perfilTarjetas.getChildCount() > 0) {
                parametros.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.space_sm);
            }
            binding.perfilTarjetas.addView(fila);
        }
    }

    /**
     * El resumen de notificaciones, cuando llega.
     *
     * <p>Si no llego —el contador es lo unico que se pide aparte— la fila se
     * queda sin resumen en vez de decir "Al día": afirmar que no hay nada
     * pendiente sin saberlo es peor que no decir nada.
     */
    private void pintarSinLeer(@Nullable Integer cuenta) {
        if (cuenta == null) {
            return;
        }
        binding.perfilFilaNotificaciones.setResumen(cuenta > 0
                ? getString(R.string.perfil_notificaciones_resumen, cuenta)
                : getString(R.string.perfil_notificaciones_resumen_vacio));
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
