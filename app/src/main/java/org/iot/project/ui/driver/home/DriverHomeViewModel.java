package org.iot.project.ui.driver.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.TaxiService;

import java.util.Collections;
import java.util.List;

/**
 * Estado de la portada del conductor (§46, RF-096 a RF-110).
 *
 * <p>La pantalla necesita tres datos que llegan por separado —el perfil, si hay
 * viaje en curso y que solicitudes hay— y no puede pintar ninguna de sus dos
 * caras hasta tenerlos todos: con dos elegiria cara a medias, y el conductor
 * veria parpadear la lista de solicitudes antes de que le desaparezca. De ahi el
 * contador de pendientes.
 *
 * <p>Los fallos de una accion —aceptar, avanzar, validar— no tumban la pantalla:
 * van por {@link #getAviso()} para que se lean en un aviso y lo que habia siga
 * ahi. Un error de carga, en cambio, si ocupa la pantalla entera, porque no hay
 * nada que mostrar.
 *
 * <p>Nunca se publica {@code UiState.EMPTY}: que no haya solicitudes no vacia la
 * pantalla, porque el conductor sigue teniendo sus metricas, su vehiculo y su
 * nota. El vacio de §50 lo pinta {@code EmptyStateView} dentro de la cara libre.
 */
public class DriverHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<Estado>> contenido = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    private boolean cargado;

    /** Cuantas de las tres consultas faltan por llegar. */
    private int pendientes;

    /**
     * Numero de la carga en curso.
     *
     * <p>Con 400 ms de latencia, dos toques seguidos en "Aceptar" disparan dos
     * recargas y sus seis respuestas se entrelazan: sin esto, las de la primera
     * llegarian despues y pintarian un estado que ya no es cierto.
     */
    private int generacion;

    private Driver conductor;
    private TaxiService activo;
    private List<OfertaDeTaxi> disponibles = Collections.emptyList();

    public LiveData<UiState<Estado>> getContenido() {
        return contenido;
    }

    /** Un mensaje de una sola vez, para el aviso emergente. */
    public LiveData<String> getAviso() {
        return aviso;
    }

    /** Marca el aviso como ya mostrado, para que no reaparezca al girar. */
    public void consumirAviso() {
        aviso.setValue(null);
    }

    public void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        pedir();
    }

    /**
     * Volver a pedir los datos desde cero.
     *
     * <p>Se usa despues de aceptar, de avanzar o de cerrar el servicio: lo que
     * hay en pantalla deja de ser cierto en cuanto el servicio cambia de estado.
     */
    public void recargar() {
        pedir();
    }

    private void pedir() {
        final int mia = ++generacion;
        String conductorId = SessionManager.getConductorId();
        if (conductorId == null) {
            contenido.setValue(UiState.<Estado>error(
                    "Esta cuenta no está dada de alta como conductor."));
            return;
        }
        TaxiRepository taxis = ServiceLocator.taxis();

        contenido.setValue(UiState.<Estado>loading());
        pendientes = 3;

        taxis.perfilDe(conductorId, new ResultCallback<Driver>() {
            @Override
            public void onExito(@NonNull Driver dato) {
                if (mia != generacion) {
                    return;
                }
                conductor = dato;
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == generacion) {
                    fallar(mensaje);
                }
            }
        });

        taxis.serviciosEnCursoDe(conductorId, new ResultCallback<List<TaxiService>>() {
            @Override
            public void onExito(@NonNull List<TaxiService> datos) {
                if (mia != generacion) {
                    return;
                }
                activo = datos.isEmpty() ? null : datos.get(0);
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == generacion) {
                    fallar(mensaje);
                }
            }
        });

        taxis.disponibles(conductorId, new ResultCallback<List<OfertaDeTaxi>>() {
            @Override
            public void onExito(@NonNull List<OfertaDeTaxi> datos) {
                if (mia != generacion) {
                    return;
                }
                disponibles = datos;
                recibido();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == generacion) {
                    fallar(mensaje);
                }
            }
        });
    }

    /** Una de las tres ha llegado. Cuando estan las tres, se publica. */
    private void recibido() {
        pendientes--;
        if (pendientes == 0) {
            contenido.setValue(UiState.success(new Estado(conductor, activo, disponibles)));
        }
    }

    private void fallar(@NonNull String mensaje) {
        // Se pone a cero para que las otras dos respuestas, que ya vienen en
        // camino, no resuciten la pantalla despues del error.
        pendientes = 0;
        contenido.setValue(UiState.<Estado>error(mensaje));
    }

    // ------------------------------------------------------------------ Acciones

    /**
     * RF-108, RF-109: pasar al siguiente estado del servicio en curso.
     *
     * <p>El destino no lo elige la pantalla: se dice "avanza" y el dominio decide
     * cual toca y rechaza lo que no proceda (RF-110, RF-111).
     */
    public void avanzar() {
        if (activo == null) {
            return;
        }
        ServiceLocator.taxis().avanzarComoConductor(activo.getId(),
                SessionManager.getConductorId(), new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    /** RF-090, RF-091, RF-107: quedarse con una solicitud de la lista. */
    public void aceptar(@NonNull String taxiId) {
        ServiceLocator.taxis().aceptar(taxiId, SessionManager.getConductorId(),
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        aviso.setValue(mensaje);
                    }
                });
    }

    /**
     * RF-102, RF-103, RF-104, RF-110: cerrar el servicio con el codigo del
     * cliente.
     *
     * <p>Lo llama la hoja de validacion, que pide <em>este</em> ViewModel al
     * fragmento padre: es el mismo modismo de {@code ValorarTaxiSheet}. El
     * resultado se le devuelve a la hoja —que es quien decide si se cierra o
     * enseña el error— y ademas se recarga la portada, porque el servicio acaba
     * de cerrarse y lo que hay en pantalla ya no es cierto.
     */
    public void validarCodigo(@NonNull String taxiId, @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        ServiceLocator.taxis().validarCodigo(taxiId, SessionManager.getConductorId(), codigo,
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        callback.onExito(dato);
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        callback.onError(mensaje);
                    }
                });
    }

    /** Lo que la portada necesita para pintarse. */
    public static final class Estado {

        public final Driver conductor;
        /** El servicio en curso, o {@code null} si el conductor esta libre. */
        public final TaxiService activo;
        public final List<OfertaDeTaxi> disponibles;

        Estado(Driver conductor, @Nullable TaxiService activo,
               @NonNull List<OfertaDeTaxi> disponibles) {
            this.conductor = conductor;
            this.activo = activo;
            this.disponibles = disponibles;
        }
    }
}
