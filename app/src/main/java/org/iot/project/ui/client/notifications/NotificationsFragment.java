package org.iot.project.ui.client.notifications;

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
import org.iot.project.databinding.FragmentNotificationsBinding;
import org.iot.project.models.AppNotification;
import org.iot.project.ui.common.NotificationAdapter;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/** Centro de notificaciones (§39, §41). */
public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private NotificationsViewModel viewModel;
    private NotificationAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.titulo_notificaciones);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptador = new NotificationAdapter();
        binding.notificacionesLista.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.notificacionesLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        viewModel.getNotificaciones().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    private void pintar(@NonNull UiState<List<AppNotification>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.notificacionesEsqueleto.setVisibility(View.VISIBLE);
                binding.notificacionesLista.setVisibility(View.GONE);
                binding.notificacionesVacio.setVisibility(View.GONE);
                binding.notificacionesError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.notificacionesEsqueleto.setVisibility(View.GONE);
                binding.notificacionesError.setVisibility(View.GONE);
                binding.notificacionesVacio.setVisibility(View.GONE);
                binding.notificacionesLista.setVisibility(View.VISIBLE);
                adaptador.submitList(estado.requireData());
                viewModel.marcarLeidas();
                break;
            case EMPTY:
                binding.notificacionesEsqueleto.setVisibility(View.GONE);
                binding.notificacionesLista.setVisibility(View.GONE);
                binding.notificacionesError.setVisibility(View.GONE);
                binding.notificacionesVacio.setVisibility(View.VISIBLE);
                binding.notificacionesVacio.conIcono(R.drawable.ic_notifications);
                binding.notificacionesVacio.conTitulo(R.string.notificaciones_vacio_titulo);
                binding.notificacionesVacio.conMensaje(R.string.notificaciones_vacio);
                break;
            case ERROR:
            default:
                binding.notificacionesEsqueleto.setVisibility(View.GONE);
                binding.notificacionesLista.setVisibility(View.GONE);
                binding.notificacionesVacio.setVisibility(View.GONE);
                binding.notificacionesError.setVisibility(View.VISIBLE);
                binding.notificacionesError.conReintento(estado.getMessage(),
                        v -> viewModel.recargar());
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.notificacionesLista.setAdapter(null);
        binding = null;
    }
}
