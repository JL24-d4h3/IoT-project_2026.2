package org.iot.project.ui.superadmin.conductores;

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
import org.iot.project.databinding.FragmentSuperadminConductoresBinding;
import org.iot.project.models.Driver;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Conductores de la plataforma (RF-077, RF-078).
 *
 * <p>Es donde se aprueba a un conductor nuevo, y por eso la lista llega con los
 * pendientes primero: son los unicos sobre los que hay algo que decidir.
 *
 * <p>Retirar la habilitacion se pregunta y habilitar no. Retirar deja a alguien
 * sin poder trabajar y puede ser un error de dedo sobre la fila de al lado;
 * habilitar devuelve el acceso y no quita nada a nadie. Es la misma regla que
 * sigue la lista de cuentas.
 */
public class SuperadminConductoresFragment extends Fragment {

    private FragmentSuperadminConductoresBinding binding;
    private SuperadminConductoresViewModel viewModel;
    private ConductorSaAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminConductoresBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.nav_conductores);

        adaptador = new ConductorSaAdapter(this::confirmar);
        binding.saConductoresLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.saConductoresLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(SuperadminConductoresViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getCambio().observe(getViewLifecycleOwner(), this::avisarDelCambio);

        // Despues de tener el ViewModel: el grupo puede anunciar el chip que ya
        // venia marcado en cuanto se registra el oyente.
        binding.saConductoresFiltros.setOnCheckedStateChangeListener((grupo, marcados) ->
                viewModel.filtrar(!marcados.isEmpty()
                        && marcados.get(0) == R.id.sa_filtro_solo_pendientes));

        viewModel.cargar();
    }

    /**
     * Retirar la habilitacion deja a alguien sin poder trabajar, asi que se
     * pregunta antes. Habilitar no se pregunta: devuelve el acceso y no quita
     * nada.
     *
     * <p>Llega el identificador y el nombre en vez del conductor porque es la
     * fila quien lo pide, y la fila guarda una copia de lo que pinta.
     */
    private void confirmar(@NonNull String conductorId, @NonNull String nombre, boolean habilitado) {
        if (habilitado) {
            viewModel.habilitar(conductorId, true);
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sa_retirar_titulo, nombre))
                .setMessage(R.string.sa_retirar_mensaje)
                .setNegativeButton(R.string.accion_cancelar, null)
                .setPositiveButton(R.string.sa_retirar_habilitacion,
                        (dialogo, cual) -> viewModel.habilitar(conductorId, false))
                .show();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<Driver>> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                binding.saConductoresVacio.setVisibility(View.GONE);
                binding.saConductoresLista.setVisibility(View.VISIBLE);
                adaptador.mostrar(estado.requireData());
                break;
            case EMPTY:
                mostrar(true, false, false);
                binding.saConductoresLista.setVisibility(View.GONE);
                binding.saConductoresVacio.setVisibility(View.VISIBLE);
                pintarVacio();
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    /**
     * El vacio dice cosas distintas segun el filtro.
     *
     * <p>Con "Por aprobar" puesto, que no haya nadie es una buena noticia y se
     * dice como tal; sin filtro significa que la plataforma no tiene ningun
     * conductor registrado, que es otra cosa completamente distinta.
     */
    private void pintarVacio() {
        boolean soloPendientes = viewModel.isSoloPendientes();
        binding.saConductoresVacio.conIcono(R.drawable.ic_taxi)
                .conTitulo(soloPendientes
                        ? R.string.sa_conductores_vacio_titulo
                        : R.string.sa_conductores_nadie_titulo)
                .conMensaje(soloPendientes
                        ? R.string.sa_conductores_vacio_mensaje
                        : R.string.sa_conductores_nadie_mensaje);
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    /**
     * El aviso de una sola vez tras habilitar o retirar.
     *
     * <p>El texto se arma aqui y no en el ViewModel porque lleva el nombre
     * dentro y los textos de la aplicacion viven en {@code strings.xml}: el
     * ViewModel no tiene {@code Context} con el que leerlos.
     */
    private void avisarDelCambio(@Nullable UiState<Driver> estado) {
        if (estado == null) {
            return;
        }
        Driver conductor = estado.getData();
        avisar(conductor != null
                ? getString(conductor.isHabilitado()
                        ? R.string.sa_conductor_ya_puede_recibir
                        : R.string.sa_conductor_ya_no_recibe, conductor.getNombreCompleto())
                : mensajeDe(estado));
        viewModel.consumirCambio();
    }

    @NonNull
    private CharSequence mensajeDe(@NonNull UiState<Driver> estado) {
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
        binding.saConductoresLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
