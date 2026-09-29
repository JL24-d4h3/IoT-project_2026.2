package org.iot.project.ui.superadmin.hoteles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.SheetAsignarAdministradorBinding;
import org.iot.project.models.User;

import java.util.Collections;
import java.util.List;

/**
 * Elegir quien administra el hotel (RF-008).
 *
 * <p>La hoja no tiene ViewModel propio: trabaja sobre el de la ficha, que es el
 * del grafo, y asignar es una accion suya. Pedir los candidatos otra vez al
 * repositorio seria poder enseñar dos listas distintas de lo mismo.
 *
 * <p>Al elegir no cierra ella misma la ficha ni dice nada: el aviso lo da la
 * pantalla que sigue debajo, que es la que sabe si la asignacion salio bien.
 */
public class AsignarAdministradorSheet extends BottomSheetDialogFragment {

    public static final String TAG = "asignarAdministrador";

    private SheetAsignarAdministradorBinding binding;
    private SuperadminHotelViewModel viewModel;
    private AdministradorElegibleAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetAsignarAdministradorBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);

        // El ViewModel es el del grafo: la hoja no tiene hotel propio y a quien
        // se le asigna es al hotel de la ficha que la abrio.
        viewModel = ViewModelGrafo.de(this, R.id.nav_superadmin, SuperadminHotelViewModel.class);

        binding.saAsignarCerrar.setOnClickListener(v -> dismiss());

        adaptador = new AdministradorElegibleAdapter(this::elegir);
        binding.saAsignarLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saAsignarLista.setAdapter(adaptador);

        pintar(viewModel.getContenido().getValue());
    }

    private void elegir(@NonNull User candidato) {
        viewModel.asignar(candidato.getId());
        dismiss();
    }

    private void pintar(@Nullable UiState<SuperadminHotelViewModel.Contenido> estado) {
        List<User> candidatos = estado == null || estado.getData() == null
                ? Collections.<User>emptyList()
                : estado.getData().candidatos;

        boolean hay = !candidatos.isEmpty();
        binding.saAsignarLista.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.saAsignarVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
        adaptador.submitList(candidatos);

        if (!hay) {
            // El caso normal de esta hoja es que haya a quien elegir: si no lo
            // hay se dice por que, en vez de dejar un hueco.
            binding.saAsignarVacio.conIcono(R.drawable.ic_person_group)
                    .conTitulo(R.string.sa_asignar_vacio_titulo)
                    .conMensaje(R.string.sa_asignar_vacio_mensaje);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.saAsignarLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
