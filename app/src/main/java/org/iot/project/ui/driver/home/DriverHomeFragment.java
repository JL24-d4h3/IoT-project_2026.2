package org.iot.project.ui.driver.home;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentDriverHomeBinding;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.driver.qr.ValidarCodigoSheet;
import org.iot.project.utils.InsetUtils;

/**
 * Portada del taxista: su servicio en curso o las solicitudes que puede aceptar.
 * Se refresca sola cada pocos segundos mientras esta a la vista.
 */
public class DriverHomeFragment extends Fragment {

    private static final long INTERVALO_REFRESCO_MS = 10_000L;

    private FragmentDriverHomeBinding binding;
    private DriverHomeViewModel viewModel;
    private SolicitudAdapter adaptador;

    private final Handler reloj = new Handler(Looper.getMainLooper());
    private final Runnable refresco = new Runnable() {
        @Override
        public void run() {
            if (viewModel != null) {
                viewModel.refrescarEnSilencio();
            }
            reloj.postDelayed(this, INTERVALO_REFRESCO_MS);
        }
    };

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

        adaptador = new SolicitudAdapter(this::confirmarAceptar);
        binding.driverSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.driverSolicitudes.setAdapter(adaptador);
        binding.driverActivo.setOnAceptar(null);

        viewModel = new ViewModelProvider(this).get(DriverHomeViewModel.class);
        binding.driverAvanzar.setOnClickListener(v -> viewModel.avanzar());
        binding.driverValidar.setOnClickListener(v -> abrirValidacion());

        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getOcupado().observe(getViewLifecycleOwner(), this::habilitarAcciones);
        viewModel.getAviso().observe(getViewLifecycleOwner(), this::mostrarAviso);
        // Con cada cambio de sesion el ViewModel decide si recarga o retira el acceso.
        SessionManager.sesion().observe(getViewLifecycleOwner(), viewModel::actualizarCuenta);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Al volver a primer plano se actualiza ya; despues, cada INTERVALO_REFRESCO_MS.
        viewModel.refrescarEnSilencio();
        reloj.postDelayed(refresco, INTERVALO_REFRESCO_MS);
    }

    @Override
    public void onStop() {
        reloj.removeCallbacks(refresco);
        super.onStop();
    }

    private void pintar(@NonNull UiState<DriverHomeViewModel.Panel> estado) {
        DriverHomeViewModel.Panel panel = estado.getData();
        boolean disponible = estado.getStatus() == UiState.Status.SUCCESS && panel != null;
        boolean cargando = estado.isLoading();

        binding.driverContenido.setVisibility(disponible ? View.VISIBLE : View.GONE);
        binding.driverCargando.setVisibility(cargando ? View.VISIBLE : View.GONE);
        binding.driverError.setVisibility(!disponible && !cargando ? View.VISIBLE : View.GONE);
        if (!disponible) {
            if (!cargando) {
                String mensaje = estado.getMessage() != null
                        ? estado.getMessage() : getString(R.string.estado_error_mensaje);
                binding.driverError.conReintento(mensaje, v -> viewModel.recargar());
            }
            return;
        }

        binding.driverIniciales.setText(panel.cuenta.getIniciales());
        binding.driverNombre.setText(panel.cuenta.getNombreCompleto());
        binding.driverCorreo.bind(R.string.perfil_correo, panel.cuenta.getEmail(),
                R.string.perfil_sin_dato);
        pintarFoto(panel.perfil != null ? panel.perfil.getFotoUrl() : null);
        boolean hayPlaca = panel.perfil != null && panel.perfil.getVehiculo() != null
                && !panel.perfil.getVehiculo().getPlaca().isEmpty();
        binding.driverPlaca.setVisibility(hayPlaca ? View.VISIBLE : View.GONE);
        if (hayPlaca) {
            binding.driverPlaca.setText(getString(R.string.driver_home_vehiculo_placa,
                    panel.perfil.getVehiculo().getPlaca()));
        }

        TaxiService activo = panel.activo;
        binding.driverActivoSeccion.setVisibility(activo != null ? View.VISIBLE : View.GONE);
        binding.driverOfertasSeccion.setVisibility(activo != null ? View.GONE : View.VISIBLE);
        if (activo != null) {
            pintarActivo(activo);
        } else {
            adaptador.submitList(panel.ofertas);
            boolean vacio = panel.ofertas.isEmpty();
            binding.driverVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
            binding.driverSolicitudes.setVisibility(vacio ? View.GONE : View.VISIBLE);
        }
    }

    /** La foto va encima de las iniciales; si no hay o no carga, se quedan las iniciales. */
    private void pintarFoto(@Nullable String url) {
        final ImageView vista = binding.driverFoto;
        if (url == null || url.isEmpty()) {
            vista.setVisibility(View.GONE);
            return;
        }
        if (url.equals(vista.getTag())) {
            return; // ya cargada: el refresco periodico no la vuelve a pedir
        }
        vista.setTag(url);
        vista.setVisibility(View.VISIBLE);
        Glide.with(vista)
                .load(url)
                .apply(RequestOptions.circleCropTransform())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object modelo,
                                                Target<Drawable> destino, boolean primeraVez) {
                        vista.setTag(null);
                        vista.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable recurso, Object modelo,
                                                   Target<Drawable> destino, DataSource origen,
                                                   boolean primeraVez) {
                        return false;
                    }
                })
                .into(vista);
    }

    private void pintarActivo(@NonNull TaxiService servicio) {
        binding.driverActivo.bind(servicio);
        binding.driverActivo.setOnAceptar(null); // un servicio ya aceptado no se vuelve a aceptar
        TaxiStatus estado = servicio.getEstado();
        boolean puedeAvanzar = estado == TaxiStatus.ASIGNADO || estado == TaxiStatus.EN_CAMINO;
        binding.driverAvanzar.setVisibility(puedeAvanzar ? View.VISIBLE : View.GONE);
        binding.driverAvanzar.setText(estado == TaxiStatus.ASIGNADO
                ? R.string.driver_voy_en_camino : R.string.driver_inicie_traslado);
        // RF-110: FINALIZADO solo se alcanza validando el codigo del cliente.
        binding.driverValidar.setVisibility(estado == TaxiStatus.EN_TRASLADO ? View.VISIBLE : View.GONE);
    }

    private void habilitarAcciones(@Nullable Boolean ocupado) {
        boolean libre = !Boolean.TRUE.equals(ocupado);
        binding.driverAvanzar.setEnabled(libre);
        binding.driverValidar.setEnabled(libre);
    }

    private void mostrarAviso(@Nullable String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) {
            return;
        }
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
        viewModel.limpiarAviso();
    }

    /** RC-031: aceptar compromete al taxista, asi que se confirma. */
    private void confirmarAceptar(@NonNull TaxiService servicio) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.driver_confirmar_aceptar_titulo)
                .setMessage(getString(R.string.driver_confirmar_aceptar_mensaje,
                        servicio.getOrigen(), servicio.getDestino()))
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.driver_aceptar,
                        (dialogo, cual) -> viewModel.aceptar(servicio))
                .show();
    }

    private void abrirValidacion() {
        UiState<DriverHomeViewModel.Panel> estado = viewModel.getContenido().getValue();
        if (estado == null || !estado.isSuccess() || estado.getData() == null
                || estado.getData().activo == null) {
            return;
        }
        ValidarCodigoSheet.newInstance(estado.getData().activo.getId())
                .show(getChildFragmentManager(), ValidarCodigoSheet.TAG);
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
        reloj.removeCallbacks(refresco);
        super.onDestroyView();
        binding = null;
    }
}
