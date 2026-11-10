package org.iot.project.ui.driver.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

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
import org.iot.project.core.ubicacion.EstadoSeguimiento;
import org.iot.project.core.ubicacion.PoliticaUbicacion;
import org.iot.project.core.ubicacion.SeguimientoTaxista;
import org.iot.project.databinding.FragmentDriverHomeBinding;
import org.iot.project.databinding.ItemDriverApiServicioBinding;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.ui.driver.qr.ValidarCodigoSheet;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.List;

/** Servicios reales del taxista autenticado en el sistema web. */
public class DriverHomeFragment extends Fragment {

    private FragmentDriverHomeBinding binding;
    private DriverHomeViewModel viewModel;
    private CancellationSignal cancelacionUbicacion;
    private Runnable esperaUbicacion;
    private boolean buscandoUbicacion;
    private boolean esperandoPermisoUbicacion;
    private String identidadUbicacion;
    private String servicioSeguimiento;
    private String identidadSeguimiento;
    private int errorCaptura;
    private boolean permisoPreciso;
    private boolean gpsEncendido;
    private boolean entornoComprobado;
    private String identidadFoto;
    private AlertDialog confirmacionAceptacion;
    private Context contextoReceptorGps;
    private final BroadcastReceiver cambiosGps = new BroadcastReceiver() {
        @Override public void onReceive(Context contexto, Intent evento) {
            comprobarEntornoUbicacion();
        }
    };
    private final ActivityResultLauncher<String[]> permisosUbicacion = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), permisos -> {
                esperandoPermisoUbicacion = false;
                if (!vistaDisponible() || identidadUbicacion == null) {
                    cancelarUbicacion();
                    return;
                }
                comprobarEntornoUbicacion(true);
                if (permisoPreciso && gpsEncendido) {
                    consultarUbicacion();
                } else {
                    cancelarUbicacion();
                    pintarEstadoUbicacion();
                }
            });
    private final Handler actualizaciones = new Handler(Looper.getMainLooper());
    private final Runnable refrescar = new Runnable() {
        @Override public void run() {
            if (binding == null) return;
            comprobarEntornoUbicacion();
            viewModel.refrescar();
            actualizaciones.postDelayed(this, 5_000L);
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

        viewModel = new ViewModelProvider(this).get(DriverHomeViewModel.class);
        Cuenta inicial = SessionManager.getCuenta();
        identidadFoto = inicial == null ? null : inicial.getIdentidadId();
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getPerfil().observe(getViewLifecycleOwner(), this::pintarPerfil);
        viewModel.getServicios().observe(getViewLifecycleOwner(), estado -> {
            pintarServicios();
            sincronizarSeguimiento();
        });
        viewModel.getOfertas().observe(getViewLifecycleOwner(), estado -> pintarOfertas());
        viewModel.getOperando().observe(getViewLifecycleOwner(), ocupado -> {
            binding.driverRefrescar.setEnabled(!Boolean.TRUE.equals(ocupado));
            pintarBotonUbicacion();
            pintarServicios();
            pintarOfertas();
        });
        viewModel.getAviso().observe(getViewLifecycleOwner(), mensaje -> {
            if (mensaje != null) {
                Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
                viewModel.limpiarAviso();
            }
        });
        viewModel.getUbicacion().observe(getViewLifecycleOwner(), estado -> pintarEstadoUbicacion());
        viewModel.getOfertasHabilitadas().observe(getViewLifecycleOwner(), permitido -> {
            pintarOfertas();
            pintarEstadoUbicacion();
        });
        SeguimientoTaxista.estado().observe(getViewLifecycleOwner(), this::pintarSeguimiento);
        binding.driverRefrescar.setOnClickListener(v -> {
            comprobarEntornoUbicacion();
            viewModel.refrescar();
        });
        binding.driverActualizarUbicacion.setOnClickListener(v -> pedirUbicacion());
        binding.driverUbicacionAjustes.setOnClickListener(v -> abrirAjustesUbicacion());
        SessionManager.sesion().observe(getViewLifecycleOwner(), cuenta -> {
            String nuevaIdentidad = cuenta == null ? null : cuenta.getIdentidadId();
            if (nuevaIdentidad == null ? identidadFoto != null : !nuevaIdentidad.equals(identidadFoto)) {
                identidadFoto = nuevaIdentidad;
                pintarFoto(null);
                cerrarConfirmacionAceptacion();
            }
            if (cuenta == null || (identidadSeguimiento != null
                    && !identidadSeguimiento.equals(cuenta.getIdentidadId()))) detenerSeguimiento();
            viewModel.actualizarCuenta(cuenta);
        });
        viewModel.cargar();
    }

    private void pintar(@NonNull UiState<Cuenta> estado) {
        if (binding == null) return;
        Cuenta cuenta = estado.getData();
        boolean disponible = estado.getStatus() == UiState.Status.SUCCESS && cuenta != null;
        binding.driverContenido.setVisibility(disponible ? View.VISIBLE : View.GONE);
        binding.driverError.setVisibility(disponible ? View.GONE : View.VISIBLE);
        if (!disponible) {
            pintarFoto(null);
            binding.driverError.conReintento(estado.getMessage(), v -> viewModel.recargar());
            return;
        }
        binding.driverIniciales.setText(cuenta.getIniciales());
        binding.driverNombre.setText(cuenta.getNombreCompleto());
        binding.driverCorreo.bind(R.string.perfil_correo, cuenta.getEmail(),
                R.string.perfil_sin_dato);
        binding.driverPanelServicios.setVisibility(cuenta.getIdentidadId().startsWith("taxista:")
                ? View.VISIBLE : View.GONE);
    }

    private void pintarPerfil(@Nullable UiState<Driver> estado) {
        if (binding == null) return;
        binding.driverPerfilAviso.setVisibility(View.GONE);
        binding.driverVehiculo.setText(null);
        if (estado == null || !estado.isSuccess()) pintarFoto(null);
        if (estado == null) {
            pintarOfertas();
            return;
        }
        if (estado.isLoading()) {
            binding.driverPerfilAviso.setText(R.string.api_driver_cargando);
            binding.driverPerfilAviso.setVisibility(View.VISIBLE);
            pintarOfertas();
            return;
        }
        if (!estado.isSuccess()) {
            binding.driverPerfilAviso.setText(estado.getMessage());
            binding.driverPerfilAviso.setVisibility(View.VISIBLE);
            pintarOfertas();
            return;
        }
        Driver conductor = estado.requireData();
        String conductorId = SessionManager.getConductorId();
        pintarFoto(conductorId != null && conductorId.equals(conductor.getId()) ? conductor.getFotoUrl() : null);
        binding.driverIniciales.setText(conductor.getIniciales());
        binding.driverNombre.setText(conductor.getNombreCompleto());
        Cuenta cuenta = SessionManager.getCuenta();
        String correo = conductor.getEmail();
        if ((correo == null || correo.isEmpty()) && cuenta != null) correo = cuenta.getEmail();
        binding.driverCorreo.bind(R.string.perfil_correo, correo, R.string.perfil_sin_dato);
        binding.driverVehiculo.setText(conductor.getVehiculo() == null
                ? getString(R.string.api_driver_sin_vehiculo)
                : getString(R.string.api_driver_vehiculo, conductor.getVehiculo().getPlaca(),
                        conductor.getVehiculo().getDescripcion()));
        if (!conductor.isHabilitado()) {
            binding.driverPerfilAviso.setText(R.string.api_driver_perfil_inactivo);
            binding.driverPerfilAviso.setVisibility(View.VISIBLE);
        }
        pintarOfertas();
    }

    /** La foto sustituye a las iniciales solo cuando la carga de esta cuenta termina. */
    private void pintarFoto(@Nullable String url) {
        if (binding == null) return;
        ImageView vista = binding.driverFoto;
        String identidad = SessionManager.getIdentidadId();
        String limpia = url == null ? null : url.trim();
        String clave = identidad == null || limpia == null || limpia.isEmpty()
                ? null : identidad + "|" + limpia;
        if (clave != null && clave.equals(vista.getTag())) return;
        vista.setTag(null);
        Glide.with(vista).clear(vista);
        vista.setImageDrawable(null);
        vista.setVisibility(View.GONE);
        if (clave == null) return;
        vista.setTag(clave);
        Glide.with(vista)
                .load(limpia)
                .apply(RequestOptions.circleCropTransform())
                .listener(new RequestListener<Drawable>() {
                    private boolean vigente() {
                        return binding != null && binding.driverFoto == vista && clave.equals(vista.getTag())
                                && identidad.equals(SessionManager.getIdentidadId());
                    }

                    @Override
                    public boolean onLoadFailed(@Nullable GlideException error, Object modelo,
                                                Target<Drawable> destino, boolean primeraVez) {
                        if (!vigente()) return true;
                        vista.setTag(null);
                        vista.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable imagen, Object modelo, Target<Drawable> destino,
                                                    DataSource origen, boolean primeraVez) {
                        if (!vigente()) return true;
                        vista.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(vista);
    }

    private void pintarServicios() {
        if (binding == null) return;
        binding.driverServiciosLista.removeAllViews();
        UiState<List<TaxiService>> estado = viewModel.getServicios().getValue();
        List<TaxiService> datos = estado != null ? estado.getData() : null;
        pintarEstadoLista(binding.driverActivosAviso, estado,
                datos == null || datos.isEmpty(), R.string.api_driver_sin_activo);
        if (datos != null) {
            for (TaxiService servicio : datos) {
                ItemDriverApiServicioBinding tarjeta = tarjeta(servicio, binding.driverServiciosLista);
                TaxiStatus status = servicio.getEstado();
                boolean avanzar = status == TaxiStatus.ASIGNADO || status == TaxiStatus.EN_CAMINO;
                boolean cerrar = status == TaxiStatus.EN_TRASLADO;
                tarjeta.servicioAccion.setVisibility(avanzar || cerrar ? View.VISIBLE : View.GONE);
                tarjeta.servicioAccion.setEnabled(!Boolean.TRUE.equals(viewModel.getOperando().getValue()));
                tarjeta.servicioAccion.setText(status == TaxiStatus.ASIGNADO
                        ? R.string.driver_voy_en_camino : status == TaxiStatus.EN_CAMINO
                        ? R.string.driver_inicie_traslado : R.string.driver_validar_codigo);
                tarjeta.servicioAccion.setOnClickListener(v -> {
                    if (cerrar) abrirValidacion(servicio.getId());
                    else viewModel.avanzar(servicio.getId());
                });
            }
        }
        pintarOfertas();
    }

    private void pintarOfertas() {
        if (binding == null) return;
        binding.driverOfertasLista.removeAllViews();
        if (!entornoComprobado || !permisoPreciso || !gpsEncendido || !viewModel.puedeConsultarOfertas()) {
            cerrarConfirmacionAceptacion();
            binding.driverOfertasLista.setVisibility(View.GONE);
            binding.driverOfertasAviso.setText(R.string.api_driver_ofertas_bloqueadas);
            binding.driverOfertasAviso.setVisibility(View.VISIBLE);
            return;
        }
        binding.driverOfertasLista.setVisibility(View.VISIBLE);
        UiState<List<OfertaDeTaxi>> estado = viewModel.getOfertas().getValue();
        List<OfertaDeTaxi> datos = estado != null ? estado.getData() : null;
        pintarEstadoLista(binding.driverOfertasAviso, estado,
                datos == null || datos.isEmpty(), R.string.api_driver_sin_ofertas);
        UiState<List<TaxiService>> activos = viewModel.getServicios().getValue();
        boolean hayActivo = activos != null && activos.getData() != null && !activos.getData().isEmpty();
        UiState<Driver> perfil = viewModel.getPerfil().getValue();
        boolean habilitado = perfil != null && perfil.isSuccess() && perfil.requireData().isHabilitado();
        boolean puedeAceptar = activos != null && activos.isSuccess() && !hayActivo && habilitado
                && !Boolean.TRUE.equals(viewModel.getOperando().getValue());
        if (hayActivo) {
            binding.driverOfertasAviso.setText(R.string.api_driver_ocupado);
            binding.driverOfertasAviso.setVisibility(View.VISIBLE);
        }
        if (datos != null) {
            for (OfertaDeTaxi oferta : datos) {
                TaxiService servicio = oferta.getServicio();
                ItemDriverApiServicioBinding tarjeta = tarjeta(servicio, binding.driverOfertasLista);
                tarjeta.servicioAccion.setText(R.string.driver_aceptar);
                tarjeta.servicioAccion.setEnabled(puedeAceptar);
                tarjeta.servicioAccion.setOnClickListener(v -> confirmarAceptar(servicio));
            }
        }
    }

    private void confirmarAceptar(@NonNull TaxiService servicio) {
        if (!vistaDisponible() || Boolean.TRUE.equals(viewModel.getOperando().getValue())
                || (confirmacionAceptacion != null && confirmacionAceptacion.isShowing())) return;
        Cuenta cuenta = SessionManager.getCuenta();
        if (cuenta == null) return;
        String identidad = cuenta.getIdentidadId();
        confirmacionAceptacion = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.driver_confirmar_aceptar_titulo)
                .setMessage(getString(R.string.driver_confirmar_aceptar_mensaje,
                        servicio.getOrigen(), servicio.getDestino()))
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.driver_aceptar, (dialogo, cual) -> {
                    if (!vistaDisponible() || !identidad.equals(SessionManager.getIdentidadId())) return;
                    // El permiso, GPS y disponibilidad pueden cambiar mientras el dialogo esta abierto.
                    comprobarEntornoUbicacion();
                    viewModel.aceptar(servicio.getId());
                })
                .create();
        confirmacionAceptacion.setOnDismissListener(dialogo -> {
            if (confirmacionAceptacion == dialogo) confirmacionAceptacion = null;
        });
        confirmacionAceptacion.show();
    }

    private void cerrarConfirmacionAceptacion() {
        AlertDialog dialogo = confirmacionAceptacion;
        confirmacionAceptacion = null;
        if (dialogo != null) dialogo.dismiss();
    }

    private void pintarEstadoLista(@NonNull TextView aviso, @Nullable UiState<?> estado,
                                   boolean vacio, int mensajeVacio) {
        Cuenta cuenta = SessionManager.getCuenta();
        if (cuenta == null || !cuenta.getIdentidadId().startsWith("taxista:")) {
            aviso.setVisibility(View.GONE);
        } else if (estado == null || estado.isLoading()) {
            aviso.setText(R.string.api_driver_cargando);
            aviso.setVisibility(View.VISIBLE);
        } else if (estado.isError()) {
            aviso.setText(estado.getMessage());
            aviso.setVisibility(View.VISIBLE);
        } else {
            aviso.setText(mensajeVacio);
            aviso.setVisibility(vacio ? View.VISIBLE : View.GONE);
        }
    }

    private ItemDriverApiServicioBinding tarjeta(@NonNull TaxiService servicio,
                                                 @NonNull LinearLayout lista) {
        ItemDriverApiServicioBinding tarjeta = ItemDriverApiServicioBinding.inflate(
                getLayoutInflater(), lista, false);
        tarjeta.servicioEstado.setText(servicio.getEstado().getDisplayName());
        tarjeta.servicioRuta.setText(getString(R.string.api_driver_ruta,
                servicio.getOrigen(), servicio.getDestino()));
        tarjeta.servicioHorario.setText(servicio.getFecha() == null || servicio.getHora() == null
                ? getString(R.string.api_driver_horario_pendiente)
                : getString(R.string.api_driver_horario, DateFormatter.fechaLarga(servicio.getFecha()),
                        DateFormatter.hora(servicio.getHora())));
        // El DTO web no informa pasajeros y una tarifa ausente no significa gratuidad.
        tarjeta.servicioDetalle.setText(servicio.getPrecio() > 0d
                ? getString(R.string.api_driver_tarifa, PriceFormatter.formatConDecimales(servicio.getPrecio()))
                : getString(R.string.api_driver_tarifa_pendiente));
        lista.addView(tarjeta.getRoot());
        return tarjeta;
    }

    private void abrirValidacion(@NonNull String taxiId) {
        if (getChildFragmentManager().isStateSaved()
                || getChildFragmentManager().findFragmentByTag(ValidarCodigoSheet.TAG) != null) return;
        ValidarCodigoSheet.newInstance(taxiId).show(getChildFragmentManager(), ValidarCodigoSheet.TAG);
    }

    private void pedirUbicacion() {
        UiState<Driver> envio = viewModel.getUbicacion().getValue();
        if (buscandoUbicacion || (envio != null && envio.isLoading())) return;
        Cuenta cuenta = SessionManager.getCuenta();
        if (cuenta == null || !cuenta.getIdentidadId().startsWith("taxista:")) return;
        errorCaptura = 0;
        comprobarEntornoUbicacion(true);
        identidadUbicacion = cuenta.getIdentidadId();
        buscandoUbicacion = true;
        pintarBotonUbicacion();
        if (!permisoPreciso) {
            // El permiso se solicita solo como consecuencia de tocar el botón.
            esperandoPermisoUbicacion = true;
            permisosUbicacion.launch(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else if (gpsEncendido) {
            consultarUbicacion();
        } else {
            cancelarUbicacion();
            abrirAjustesUbicacion();
        }
    }

    private boolean tienePermisoUbicacion() {
        Context contexto = getContext();
        return contexto != null && ContextCompat.checkSelfPermission(contexto,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean tieneGpsEncendido() {
        Context contexto = getContext();
        if (contexto == null) return false;
        LocationManager ubicacion = (LocationManager) contexto.getSystemService(Context.LOCATION_SERVICE);
        try {
            return ubicacion != null && ubicacion.isLocationEnabled()
                    && ubicacion.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException | IllegalArgumentException error) {
            return false;
        }
    }

    private void comprobarEntornoUbicacion() {
        comprobarEntornoUbicacion(false);
    }

    private void comprobarEntornoUbicacion(boolean reintentarSeguimiento) {
        if (binding == null || viewModel == null) return;
        boolean precisoPrevio = permisoPreciso;
        boolean gpsPrevio = gpsEncendido;
        permisoPreciso = tienePermisoUbicacion();
        gpsEncendido = tieneGpsEncendido();
        entornoComprobado = true;
        if (precisoPrevio != permisoPreciso || gpsPrevio != gpsEncendido) errorCaptura = 0;
        if ((!permisoPreciso || !gpsEncendido) && !esperandoPermisoUbicacion) cancelarUbicacion();
        viewModel.establecerEntornoUbicacion(permisoPreciso, gpsEncendido);
        if (!permisoPreciso || !gpsEncendido) detenerSeguimiento();
        else sincronizarSeguimiento(reintentarSeguimiento || !precisoPrevio || !gpsPrevio);
        pintarEstadoUbicacion();
        pintarOfertas();
    }

    private void abrirAjustesUbicacion() {
        if (!isAdded()) return;
        Intent ajustes = tienePermisoUbicacion()
                ? new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(ajustes);
    }

    @SuppressLint("MissingPermission")
    private void consultarUbicacion() {
        Cuenta cuenta = SessionManager.getCuenta();
        if (!vistaDisponible() || cuenta == null || !cuenta.getIdentidadId().equals(identidadUbicacion)) {
            cancelarUbicacion();
            return;
        }
        if (!tienePermisoUbicacion() || !tieneGpsEncendido()) {
            cancelarUbicacion();
            comprobarEntornoUbicacion();
            return;
        }
        LocationManager ubicacion = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        String proveedor = null;
        try {
            if (ubicacion != null && ubicacion.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                proveedor = LocationManager.GPS_PROVIDER;
            }
            if (proveedor == null) {
                cancelarUbicacion();
                informarErrorCaptura(R.string.api_driver_ubicacion_apagada);
                return;
            }
            CancellationSignal solicitud = new CancellationSignal();
            cancelacionUbicacion = solicitud;
            String identidadSolicitud = identidadUbicacion;
            pintarEstadoUbicacion();
            esperaUbicacion = () -> {
                if (cancelacionUbicacion != solicitud) return;
                cancelarUbicacion();
                informarErrorCaptura(R.string.api_driver_ubicacion_sin_senal);
            };
            actualizaciones.postDelayed(esperaUbicacion, 25_000L);
            ubicacion.getCurrentLocation(proveedor, solicitud, requireContext().getMainExecutor(), posicion -> {
                if (cancelacionUbicacion != solicitud || !vistaDisponible()) return;
                Cuenta actual = SessionManager.getCuenta();
                cancelarUbicacion();
                if (actual == null || !actual.getIdentidadId().equals(identidadSolicitud)) return;
                comprobarEntornoUbicacion();
                if (!permisoPreciso || !gpsEncendido) return;
                long edad = posicion == null ? Long.MAX_VALUE : posicion.getElapsedRealtimeAgeMillis();
                if (posicion == null || !posicion.hasAccuracy() || !PoliticaUbicacion.posicionValida(
                        posicion.getLatitude(), posicion.getLongitude(), posicion.getAccuracy(), edad)) {
                    informarErrorCaptura(R.string.api_driver_ubicacion_sin_senal);
                } else {
                    viewModel.actualizarUbicacion(posicion.getLatitude(), posicion.getLongitude(),
                            SystemClock.elapsedRealtime() - edad);
                }
            });
        } catch (SecurityException error) {
            cancelarUbicacion();
            comprobarEntornoUbicacion();
            informarErrorCaptura(R.string.api_driver_ubicacion_permiso);
        } catch (IllegalArgumentException error) {
            cancelarUbicacion();
            informarErrorCaptura(R.string.api_driver_ubicacion_sin_senal);
        }
    }

    private boolean vistaDisponible() {
        return binding != null && isAdded()
                && getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void pintarBotonUbicacion() {
        if (binding == null || viewModel == null) return;
        UiState<Driver> envio = viewModel.getUbicacion().getValue();
        binding.driverActualizarUbicacion.setEnabled(!buscandoUbicacion
                && (envio == null || !envio.isLoading()));
        binding.driverActualizarUbicacion.setText(permisoPreciso && gpsEncendido
                ? R.string.api_driver_ubicacion_actualizar : R.string.api_driver_ubicacion_activar);
        binding.driverUbicacionAjustes.setVisibility(!permisoPreciso || !gpsEncendido
                ? View.VISIBLE : View.GONE);
    }

    private void pintarEstadoUbicacion() {
        if (binding == null || viewModel == null) return;
        pintarBotonUbicacion();
        UiState<Driver> envio = viewModel.getUbicacion().getValue();
        if (!permisoPreciso) {
            binding.driverUbicacionEstado.setText(R.string.api_driver_ubicacion_permiso);
        } else if (!gpsEncendido) {
            binding.driverUbicacionEstado.setText(R.string.api_driver_ubicacion_apagada);
        } else if (buscandoUbicacion) {
            binding.driverUbicacionEstado.setText(R.string.api_driver_ubicacion_buscando);
        } else if (envio != null && envio.isLoading()) {
            binding.driverUbicacionEstado.setText(R.string.api_driver_ubicacion_guardando);
        } else if (errorCaptura != 0) {
            binding.driverUbicacionEstado.setText(errorCaptura);
        } else if (envio != null && envio.isError()) {
            binding.driverUbicacionEstado.setText(envio.getMessage());
        } else {
            binding.driverUbicacionEstado.setText(viewModel.puedeConsultarOfertas()
                    ? R.string.api_driver_ubicacion_lista : R.string.api_driver_ubicacion_ayuda);
        }
    }

    private void informarErrorCaptura(int mensaje) {
        errorCaptura = mensaje;
        pintarEstadoUbicacion();
    }

    private void cancelarUbicacion() {
        CancellationSignal pendiente = cancelacionUbicacion;
        cancelacionUbicacion = null;
        if (pendiente != null) pendiente.cancel();
        if (esperaUbicacion != null) actualizaciones.removeCallbacks(esperaUbicacion);
        esperaUbicacion = null;
        identidadUbicacion = null;
        buscandoUbicacion = false;
        esperandoPermisoUbicacion = false;
        pintarEstadoUbicacion();
    }

    @Nullable
    private TaxiService servicioActivoConfirmado() {
        UiState<List<TaxiService>> estado = viewModel.getServicios().getValue();
        if (estado == null || !estado.isSuccess()) return null;
        for (TaxiService servicio : estado.requireData()) {
            if (!servicio.getEstado().isFinished() && servicio.getEstado() != TaxiStatus.SOLICITADO) return servicio;
        }
        return null;
    }

    private void sincronizarSeguimiento() {
        sincronizarSeguimiento(false);
    }

    private void sincronizarSeguimiento(boolean reintentar) {
        if (binding == null || !isAdded() || !entornoComprobado) return;
        Cuenta cuenta = SessionManager.getCuenta();
        if (cuenta == null || !cuenta.getIdentidadId().startsWith("taxista:")
                || !permisoPreciso || !gpsEncendido) {
            detenerSeguimiento();
            pintarSeguimiento(SeguimientoTaxista.estado().getValue());
            return;
        }
        UiState<List<TaxiService>> estado = viewModel.getServicios().getValue();
        if (estado != null && estado.isSuccess()) {
            TaxiService activo = servicioActivoConfirmado();
            EstadoSeguimiento seguimiento = SeguimientoTaxista.estado().getValue();
            boolean siguiendo = seguimiento != null && seguimiento.isActivo()
                    && cuenta.getIdentidadId().equals(seguimiento.getIdentidadId())
                    && activo != null && activo.getId().equals(seguimiento.getServicioId());
            if (activo == null) {
                detenerSeguimiento();
            } else if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                    && (!activo.getId().equals(servicioSeguimiento)
                    || !cuenta.getIdentidadId().equals(identidadSeguimiento)
                    || (reintentar && !siguiendo))) {
                servicioSeguimiento = activo.getId();
                identidadSeguimiento = cuenta.getIdentidadId();
                SeguimientoTaxista.iniciar(requireContext(), activo.getId());
            }
        }
        pintarSeguimiento(SeguimientoTaxista.estado().getValue());
    }

    private void detenerSeguimiento() {
        Context contexto = getContext();
        EstadoSeguimiento estado = SeguimientoTaxista.estado().getValue();
        boolean detener = servicioSeguimiento != null || identidadSeguimiento != null
                || (estado != null && (estado.isActivo() || estado.isBloqueado()));
        servicioSeguimiento = null;
        identidadSeguimiento = null;
        if (detener && contexto != null) SeguimientoTaxista.detener(contexto);
    }

    private void pintarSeguimiento(@Nullable EstadoSeguimiento estado) {
        if (binding == null || viewModel == null) return;
        TaxiService activo = servicioActivoConfirmado();
        Cuenta cuenta = SessionManager.getCuenta();
        boolean vigente = activo != null && cuenta != null && estado != null
                && cuenta.getIdentidadId().equals(estado.getIdentidadId())
                && activo.getId().equals(estado.getServicioId());
        binding.driverSeguimientoEstado.setVisibility(activo != null ? View.VISIBLE : View.GONE);
        if (activo == null) return;
        if (!entornoComprobado) {
            binding.driverSeguimientoEstado.setText(R.string.api_driver_seguimiento_preparando);
        } else if (!permisoPreciso || !gpsEncendido) {
            binding.driverSeguimientoEstado.setText(R.string.api_driver_seguimiento_pausado);
        } else if (!vigente) {
            binding.driverSeguimientoEstado.setText(R.string.api_driver_seguimiento_preparando);
        } else {
            binding.driverSeguimientoEstado.setText(estado.getMensaje());
            if (estado.isActivo() && !estado.isBloqueado() && estado.getUltimoEnvioElapsedMs() > 0L) {
                viewModel.registrarEnvioSeguimiento(estado.getUltimoEnvioElapsedMs());
                if (binding != null && viewModel.puedeConsultarOfertas()) {
                    errorCaptura = 0;
                    pintarEstadoUbicacion();
                }
            }
        }
    }

    private void confirmarCierre() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sesion_cerrar_titulo)
                .setMessage(R.string.sesion_cerrar_mensaje)
                .setNegativeButton(R.string.accion_volver, null)
                .setPositiveButton(R.string.sesion_cerrar,
                        (dialogo, cual) -> {
                            cancelarUbicacion();
                            detenerSeguimiento();
                            SessionManager.cerrarSesion();
                        })
                .show();
    }

    @Override public void onStart() {
        super.onStart();
        actualizaciones.post(refrescar);
    }

    @Override public void onResume() {
        super.onResume();
        if (contextoReceptorGps == null) {
            contextoReceptorGps = requireContext();
            IntentFilter cambios = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
            cambios.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
            ContextCompat.registerReceiver(contextoReceptorGps, cambiosGps, cambios,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        comprobarEntornoUbicacion(true);
    }

    @Override public void onPause() {
        if (contextoReceptorGps != null) {
            contextoReceptorGps.unregisterReceiver(cambiosGps);
            contextoReceptorGps = null;
        }
        super.onPause();
    }

    @Override public void onStop() {
        // El diálogo del sistema puede detener la actividad antes de devolver el permiso.
        // Aún no se solicitó una posición en ese caso; conserva solo esa intención manual.
        if (!esperandoPermisoUbicacion) cancelarUbicacion();
        actualizaciones.removeCallbacks(refrescar);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        cancelarUbicacion();
        actualizaciones.removeCallbacks(refrescar);
        cerrarConfirmacionAceptacion();
        pintarFoto(null);
        identidadFoto = null;
        entornoComprobado = false;
        super.onDestroyView();
        binding = null;
    }
}
