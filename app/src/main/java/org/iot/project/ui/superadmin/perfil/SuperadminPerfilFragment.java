package org.iot.project.ui.superadmin.perfil;

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
import org.iot.project.databinding.FragmentSuperadminPerfilBinding;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.User;
import org.iot.project.utils.FormatoDeDatos;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.VersionDeLaApp;

/**
 * Perfil del superadministrador (§47).
 *
 * <p>Existe por el mismo motivo que el del administrador de hotel: la cuenta
 * del superadministrador es una cuenta como cualquier otra y no tenia donde
 * verse. Hasta ahora su unica salida era el icono de cerrar sesion de la
 * cabecera de la portada, y desde las otras cuatro secciones no habia ninguna:
 * quien estaba en Usuarios tenia que volver a Inicio para salir. Aqui la salida
 * esta donde la busca todo el mundo, que es donde la tienen los otros roles.
 *
 * <p>Lo que hace que este perfil sea el de un superadministrador y no una copia
 * del del cliente es la segunda tarjeta: no es el hotel que administra —no
 * administra ninguno— sino las cifras de la plataforma entera y la puerta a la
 * auditoria, que es la seccion que dejo de caber en la barra al entrar Perfil.
 *
 * <p>Es de consulta, como los otros dos y por la misma razon: no hay a donde
 * mandar los cambios desde aqui. Lo que si hace es llevar a donde se consultan
 * —la bitacora— y a cerrar la sesion.
 */
public class SuperadminPerfilFragment extends Fragment {

    private FragmentSuperadminPerfilBinding binding;
    private SuperadminPerfilViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminPerfilBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saPerfilHeader);

        binding.saPerfilHeader.setTitulo(R.string.perfil_titulo);
        binding.saPerfilCerrarSesion.setOnClickListener(v -> confirmarCierre());
        binding.saPerfilVerAuditoria.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.superadminBitacoraFragment));

        binding.saPerfilVersion.setText(getString(R.string.perfil_version,
                VersionDeLaApp.nombre(requireContext())));

        viewModel = new ViewModelProvider(this).get(SuperadminPerfilViewModel.class);
        viewModel.getPerfil().observe(getViewLifecycleOwner(), this::pintarPerfil);
        viewModel.getPlataforma().observe(getViewLifecycleOwner(), this::pintarPlataforma);
        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Cuenta

    private void pintarPerfil(@NonNull UiState<User> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.saPerfilEsqueleto.setVisibility(View.VISIBLE);
                binding.saPerfilContenido.setVisibility(View.GONE);
                binding.saPerfilError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                User usuario = estado.getData();
                if (usuario == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no pintar una pantalla vacia sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.saPerfilEsqueleto.setVisibility(View.GONE);
                binding.saPerfilError.setVisibility(View.GONE);
                binding.saPerfilContenido.setVisibility(View.VISIBLE);
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
        binding.saPerfilEsqueleto.setVisibility(View.GONE);
        binding.saPerfilContenido.setVisibility(View.GONE);
        binding.saPerfilError.setVisibility(View.VISIBLE);
        binding.saPerfilError.conReintento(mensaje, v -> viewModel.reintentar());
    }

    private void pintarUsuario(@NonNull User usuario) {
        binding.saPerfilIniciales.setText(usuario.getIniciales());
        binding.saPerfilNombre.setText(usuario.getNombreCompleto());
        binding.saPerfilCorreoCabecera.setText(usuario.getEmail());
        // Del enum y no de una cadena suelta: si el rol cambia de nombre, la
        // etiqueta cambia con el.
        binding.saPerfilRol.setText(usuario.getRol().getDisplayName());

        binding.saPerfilFilaDocumento.bind(R.string.perfil_documento,
                FormatoDeDatos.documento(usuario), R.string.perfil_sin_dato);
        binding.saPerfilFilaTelefono.bind(R.string.perfil_telefono,
                usuario.getTelefono(), R.string.perfil_sin_dato);
        binding.saPerfilFilaCorreo.bind(R.string.perfil_correo,
                usuario.getEmail(), R.string.perfil_sin_dato);
    }

    // -------------------------------------------------------------- Plataforma

    private void pintarPlataforma(@NonNull UiState<ResumenSuperadmin> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.saPerfilPlataformaEsqueleto.setVisibility(View.VISIBLE);
                binding.saPerfilPlataformaDatos.setVisibility(View.GONE);
                binding.saPerfilPlataformaError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                ResumenSuperadmin resumen = estado.getData();
                if (resumen == null) {
                    pintarPlataformaNoDisponible(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.saPerfilPlataformaEsqueleto.setVisibility(View.GONE);
                binding.saPerfilPlataformaError.setVisibility(View.GONE);
                binding.saPerfilPlataformaDatos.setVisibility(View.VISIBLE);
                pintarCifras(resumen);
                break;
            case EMPTY:
            case ERROR:
            default:
                pintarPlataformaNoDisponible(estado.getMessage());
                break;
        }
    }

    /**
     * Las cifras no llegaron.
     *
     * <p>La tarjeta se queda con su titulo y dice que no se pudo leer, en vez de
     * desaparecer. Un superadministrador que abre su perfil y no ve la plataforma
     * no sabe si es que no cargo o es que de verdad no hay nada dentro, y esa
     * duda es peor que el aviso: un cero es una afirmacion.
     */
    private void pintarPlataformaNoDisponible(@Nullable String mensaje) {
        binding.saPerfilPlataformaEsqueleto.setVisibility(View.GONE);
        binding.saPerfilPlataformaDatos.setVisibility(View.GONE);
        binding.saPerfilPlataformaError.setVisibility(View.VISIBLE);
        binding.saPerfilPlataformaError.setText(mensaje != null && !mensaje.isEmpty()
                ? mensaje : getString(R.string.sa_perfil_plataforma_error));
    }

    private void pintarCifras(@NonNull ResumenSuperadmin resumen) {
        binding.saPerfilFilaUsuarios.bind(R.string.sa_stat_usuarios,
                deTotales(resumen.getUsuariosActivos(), resumen.getUsuariosTotales()),
                R.string.perfil_sin_dato);
        binding.saPerfilFilaConductores.bind(R.string.sa_stat_conductores,
                deTotales(resumen.getConductoresHabilitados(), resumen.getConductoresTotales()),
                R.string.perfil_sin_dato);
        binding.saPerfilFilaHoteles.bind(R.string.sa_stat_hoteles,
                deTotales(resumen.getHotelesPublicados(), resumen.getHotelesTotales()),
                R.string.perfil_sin_dato);
        // Esta no va "de totales" porque no es una parte de un todo: es lo que
        // espera una decision suya, y el numero solo ya lo dice todo.
        binding.saPerfilFilaPendientes.bind(R.string.sa_stat_pendientes,
                String.valueOf(resumen.getConductoresPendientes()), R.string.perfil_sin_dato);
    }

    @NonNull
    private CharSequence deTotales(int parte, int total) {
        return getString(R.string.sa_de_total, parte, total);
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
