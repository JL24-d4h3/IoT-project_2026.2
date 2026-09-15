package org.iot.project.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.databinding.FragmentPanelRolBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.utils.InsetUtils;

/**
 * Primera pantalla de los roles de administracion de hotel, conductor y
 * superadmin (§73).
 *
 * <p>Las tres comparten pantalla a proposito mientras sus secciones no existan:
 * lo unico que las distingue hoy es el titulo y lo que esta por llegar, y tres
 * archivos con el mismo contenido se separarian en cuanto cada rol tenga el
 * suyo. El destino de cada rol ya es propio en su grafo, asi que sustituir esta
 * pantalla por la de verdad es cambiar el {@code android:name} de ese destino.
 *
 * <p>Lo que si es real aqui es la cuenta en sesion: nombre, correo y rol salen
 * de {@link SessionManager}, que es lo que hace visible RF-004 —cada rol recibe
 * su interfaz— sin inventar datos de gestion que todavia no existen.
 */
public class PanelRolFragment extends Fragment {

    private FragmentPanelRolBinding binding;

    /** Se guarda para poder quitar el observador al destruir la vista. */
    private Observer<Cuenta> observadorSesion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPanelRolBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.panelHeader);
        // La barra inferior no se muestra cuando el rol tiene una sola seccion,
        // asi que esta pantalla se ocupa de su propio borde inferior.
        InsetUtils.applyBottomPadding(binding.panelPie);

        binding.panelCerrarSesion.setOnClickListener(v -> confirmarCierre());

        observadorSesion = this::mostrarCuenta;
        SessionManager.sesion().observe(getViewLifecycleOwner(), observadorSesion);
    }

    /**
     * Nombre, correo y rol de quien acaba de entrar.
     *
     * <p>El texto de la cabecera y el de "que falta" dependen del rol y no de un
     * argumento del destino: son propiedades del rol, y un argumento obligaria a
     * repetirlos en cada grafo. El dia que un rol tenga su pantalla de verdad,
     * esta se sustituye entera.
     */
    private void mostrarCuenta(@Nullable Cuenta cuenta) {
        if (cuenta == null) {
            return;
        }
        binding.panelIniciales.setText(cuenta.getIniciales());
        binding.panelNombre.setText(cuenta.getNombreCompleto());
        binding.panelCorreo.setText(cuenta.getEmail());
        binding.panelHeader.setTitulo(tituloDe(cuenta.getRol()));
        binding.panelSiguiente.setText(siguienteDe(cuenta.getRol()));
    }

    /**
     * Los cuatro roles aparecen uno por uno, sin {@code default}: si se agrega
     * un rol nuevo, el caso que falta salta a la vista al leer el metodo en
     * lugar de quedar escondido detras de un valor por defecto.
     */
    @StringRes
    private static int tituloDe(@NonNull Role rol) {
        switch (rol) {
            case ADMIN_HOTEL:
                return R.string.titulo_panel_admin;
            case CONDUCTOR:
                return R.string.titulo_inicio_conductor;
            case SUPERADMIN:
                return R.string.titulo_panel_superadmin;
            case CLIENTE:
                return R.string.app_name;
        }
        // Inalcanzable con los roles de hoy: el cliente tiene su propio grafo y
        // nunca llega a esta pantalla.
        return R.string.app_name;
    }

    @StringRes
    private static int siguienteDe(@NonNull Role rol) {
        switch (rol) {
            case ADMIN_HOTEL:
                return R.string.admin_panel_siguiente;
            case CONDUCTOR:
                return R.string.driver_home_siguiente;
            case SUPERADMIN:
                return R.string.superadmin_siguiente;
            case CLIENTE:
                return R.string.marcador_en_construccion;
        }
        return R.string.marcador_en_construccion;
    }

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
