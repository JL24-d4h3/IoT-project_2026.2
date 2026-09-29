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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentDriverHomeBinding;
import org.iot.project.models.TaxiService;
import org.iot.project.ui.driver.qr.ValidarCodigoSheet;
import org.iot.project.utils.InsetUtils;

/**
 * Portada del conductor (§46, RF-085 a RF-110).
 *
 * <p>Una sola pantalla con dos caras que se excluyen, como pide §46: sin viaje
 * en curso, sus metricas, su vehiculo y las solicitudes que puede aceptar; con
 * viaje en curso, el viaje y nada mas. Cual de las dos se ve lo decide el
 * servicio activo, y esa eleccion no la puede hacer la pantalla sola: necesita
 * los tres datos del ViewModel a la vez.
 *
 * <p>La pantalla no habla con los mocks (§49): aceptar, avanzar y validar pasan
 * todos por el ViewModel.
 */
public class DriverHomeFragment extends Fragment {

    private FragmentDriverHomeBinding binding;
    private DriverHomeViewModel viewModel;
    private SolicitudAdapter adaptador;

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
        binding.driverHeader.setTitulo(R.string.titulo_inicio_conductor);

        // El conductor no tiene barra inferior —una sola seccion, y no se
        // enseña— ni pantalla de perfil, asi que esta cabecera es el unico
        // sitio donde puede cerrar sesion. Antes lo hacia desde el panel de
        // rol, y al sustituirlo por esta portada se quedo sin salida.
        binding.driverHeader.mostrarAccion(R.drawable.ic_logout, R.string.cd_cerrar_sesion,
                v -> confirmarCierre());

        // Aceptar pasa por el ViewModel y no por el repositorio: la pantalla no
        // habla con los mocks (§49).
        adaptador = new SolicitudAdapter(servicio -> viewModel.aceptar(servicio.getId()));
        binding.driverSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.driverSolicitudes.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(DriverHomeViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);

        // El aviso es de una sola vez: se muestra y se olvida, para que no
        // vuelva a salir al girar la pantalla.
        viewModel.getAviso().observe(getViewLifecycleOwner(), mensaje -> {
            if (mensaje != null) {
                Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
                viewModel.consumirAviso();
            }
        });

        viewModel.cargar();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<DriverHomeViewModel.Estado> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                binding.driverEsqueleto.setVisibility(View.VISIBLE);
                binding.driverError.setVisibility(View.GONE);
                // Mientras no se sabe cual es el servicio en curso no se enseña
                // ninguna de las dos caras: elegir ahora seria elegir a ciegas.
                binding.driverLibre.setVisibility(View.GONE);
                binding.driverOcupado.setVisibility(View.GONE);
                break;
            case SUCCESS:
                DriverHomeViewModel.Estado datos = estado.getData();
                if (datos == null) {
                    // Un exito sin datos no es un exito: se trata como error
                    // para no dejar la pantalla en blanco sin explicacion.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.driverEsqueleto.setVisibility(View.GONE);
                binding.driverError.setVisibility(View.GONE);
                pintarCara(datos);
                break;
            case EMPTY:
                // El ViewModel no publica este estado: sin solicitudes la
                // pantalla sigue teniendo contenido. Si llegara, es un fallo.
                pintarError(getString(R.string.estado_error_descripcion));
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    /** §46: con servicio activo, la lista de solicitudes desaparece entera. */
    private void pintarCara(@NonNull DriverHomeViewModel.Estado datos) {
        boolean ocupado = datos.activo != null;
        binding.driverLibre.setVisibility(ocupado ? View.GONE : View.VISIBLE);
        binding.driverOcupado.setVisibility(ocupado ? View.VISIBLE : View.GONE);

        if (ocupado) {
            pintarServicio(datos.activo);
        } else {
            pintarLibre(datos);
        }
    }

    private void pintarLibre(@NonNull DriverHomeViewModel.Estado datos) {
        binding.driverMetricaDisponibles.setDato(
                R.string.driver_home_disponibles, String.valueOf(datos.disponibles.size()));
        // Esta cara solo se pinta cuando no hay viaje, y por eso "en curso" es
        // cero: el número sale del hecho de estar aquí, no de contarlo otra vez.
        binding.driverMetricaActivos.setDato(R.string.driver_home_activos, "0");
        binding.driverMetricaCompletados.setDato(R.string.driver_home_completados,
                String.valueOf(datos.conductor.getNumServicios()));

        // Una sola tarjeta: bind ya pinta al conductor y su vehiculo.
        binding.driverConductor.bind(datos.conductor);

        boolean hay = !datos.disponibles.isEmpty();
        binding.driverSolicitudes.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.driverVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
        if (!hay) {
            binding.driverVacio.conIcono(R.drawable.ic_taxi)
                    .conTitulo(R.string.driver_sin_servicio_titulo)
                    .conMensaje(R.string.driver_sin_servicio);
        }
        adaptador.submitList(datos.disponibles);
    }

    /**
     * §46: con un servicio en curso, la pantalla entera es ese servicio.
     *
     * <p>No lleva la lista de solicitudes ni las metricas de la jornada: el
     * conductor que ya va con alguien no puede quedarse con otro pedido, asi que
     * enseñarselos seria ofrecerle algo que el dominio le va a rechazar.
     */
    private void pintarServicio(@NonNull TaxiService servicio) {
        // En voz de conductor: la de cliente le cuenta su propio viaje a él.
        binding.driverEstado.bindParaConductor(servicio);
        binding.driverFilaRecogida.bind(R.string.driver_recogida, servicio.getOrigen(),
                R.string.perfil_sin_dato);
        binding.driverFilaDestino.bind(R.string.driver_destino, servicio.getDestino(),
                R.string.perfil_sin_dato);
        binding.driverFilaPasajeros.bind(R.string.driver_pasajeros,
                getResources().getQuantityString(R.plurals.taxi_pasajeros,
                        servicio.getNumPasajeros(), servicio.getNumPasajeros()),
                R.string.perfil_sin_dato);
        pintarAccion(servicio);
    }

    /**
     * §46 y §7.1: una sola accion primaria, y cambia con el estado.
     *
     * <p>No hay boton de finalizar: RF-110 lo reserva al codigo del cliente, y
     * {@code avanzarComoConductor} lo rechazaria. La accion de EN_TRASLADO abre
     * la hoja del codigo, que es la unica puerta a FINALIZADO.
     */
    private void pintarAccion(@NonNull TaxiService servicio) {
        switch (servicio.getEstado()) {
            case ASIGNADO:
                binding.driverAccion.setText(R.string.driver_voy_en_camino);
                binding.driverAccion.setOnClickListener(v -> viewModel.avanzar());
                break;
            case EN_CAMINO:
                binding.driverAccion.setText(R.string.driver_inicie_traslado);
                binding.driverAccion.setOnClickListener(v -> viewModel.avanzar());
                break;
            case EN_TRASLADO:
                binding.driverAccion.setText(R.string.driver_validar_codigo);
                binding.driverAccion.setOnClickListener(v -> abrirValidacion(servicio.getId()));
                break;
            default:
                // SOLICITADO y FINALIZADO no llegan aqui —el servicio en curso
                // esta asignado, y al cerrarse deja de estar en curso—, pero si
                // llegaran no hay accion que ofrecer, y un boton viejo con el
                // texto del estado anterior seria peor que ninguno.
                binding.driverAccion.setVisibility(View.GONE);
                break;
        }
    }

    private void abrirValidacion(@NonNull String taxiId) {
        ValidarCodigoSheet.newInstance(taxiId)
                .show(getChildFragmentManager(), ValidarCodigoSheet.TAG);
    }

    /**
     * Cerrar sesion se pregunta antes, igual que en el resto de la app.
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

    private void pintarError(@Nullable String mensaje) {
        binding.driverEsqueleto.setVisibility(View.GONE);
        binding.driverLibre.setVisibility(View.GONE);
        binding.driverOcupado.setVisibility(View.GONE);
        binding.driverError.setVisibility(View.VISIBLE);
        binding.driverError.conReintento(mensaje, v -> viewModel.recargar());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.driverSolicitudes.setAdapter(null);
        binding = null;
    }
}
