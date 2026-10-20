package org.iot.project.ui.driver.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentDriverHomeBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.utils.InsetUtils;

/** Bienvenida del taxista una vez autenticado con Firebase. */
public class DriverHomeFragment extends Fragment {

    private FragmentDriverHomeBinding binding;
    private DriverHomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDriverHomeBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.driverHeader);
        InsetUtils.applyBottomPadding(binding.driverAcciones);
        binding.driverHeader.setTitulo(R.string.titulo_inicio_conductor);
        binding.driverCerrarSesion.setOnClickListener(v -> confirmarCierre());

        viewModel = new ViewModelProvider(this).get(DriverHomeViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        // El listener del perfil de la sesión mantiene el nombre y correo
        // actuales y retira el acceso si se desactiva o cambia el rol.
        SessionManager.sesion().observe(getViewLifecycleOwner(), viewModel::actualizarCuenta);
        viewModel.cargar();
    }

    private void pintar(@NonNull UiState<Cuenta> estado) {
        Cuenta cuenta = estado.getData();
        boolean disponible = estado.getStatus() == UiState.Status.SUCCESS && cuenta != null;
        binding.driverContenido.setVisibility(disponible ? View.VISIBLE : View.GONE);
        binding.driverError.setVisibility(disponible ? View.GONE : View.VISIBLE);
        if (!disponible) {
            binding.driverError.conReintento(estado.getMessage(), v -> viewModel.recargar());
            return;
        }
        binding.driverIniciales.setText(cuenta.getIniciales());
        binding.driverNombre.setText(cuenta.getNombreCompleto());
        binding.driverCorreo.bind(R.string.perfil_correo, cuenta.getEmail(),
                R.string.perfil_sin_dato);
    }

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
