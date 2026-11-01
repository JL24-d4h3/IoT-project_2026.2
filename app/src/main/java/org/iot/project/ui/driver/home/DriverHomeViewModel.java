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
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;

import java.util.Collections;
import java.util.List;

/**
 * Portada del taxista: su servicio en curso o, si no tiene uno, las solicitudes
 * que puede aceptar (RF-085 a RF-111). Los datos vienen de la API de taxistas a
 * traves de {@code ServiceLocator.taxis()}.
 */
public class DriverHomeViewModel extends ViewModel {

    /** Todo lo que pinta la portada. */
    public static final class Panel {
        @NonNull public final Cuenta cuenta;
        @Nullable public final Driver perfil;
        /** El servicio que atiende ahora; si hay uno, no se ofrecen otros. */
        @Nullable public final TaxiService activo;
        @NonNull public final List<OfertaDeTaxi> ofertas;

        Panel(@NonNull Cuenta cuenta, @Nullable Driver perfil, @Nullable TaxiService activo,
              @NonNull List<OfertaDeTaxi> ofertas) {
            this.cuenta = cuenta;
            this.perfil = perfil;
            this.activo = activo;
            this.ofertas = ofertas;
        }
    }

    private final MutableLiveData<UiState<Panel>> contenido = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocupado = new MutableLiveData<>(false);
    private final MutableLiveData<String> aviso = new MutableLiveData<>();

    @Nullable private Cuenta cuenta;
    @Nullable private Driver perfilEnCache;
    private long generacion;

    public LiveData<UiState<Panel>> getContenido() {
        return contenido;
    }

    /** {@code true} mientras se ejecuta una accion del taxista (aceptar, avanzar). */
    public LiveData<Boolean> getOcupado() {
        return ocupado;
    }

    /** Mensaje de una sola vez para mostrar al usuario; ver {@link #limpiarAviso()}. */
    public LiveData<String> getAviso() {
        return aviso;
    }

    public void limpiarAviso() {
        aviso.setValue(null);
    }

    /**
     * La pantalla lo llama con cada cambio de sesion. Una cuenta que ya no es la
     * de un taxista retira el acceso; una cuenta nueva recarga todo.
     */
    public void actualizarCuenta(@Nullable Cuenta nueva) {
        if (nueva == null || nueva.getRol() != Role.CONDUCTOR
                || !nueva.getIdentidadId().equals(SessionManager.getConductorId())) {
            generacion++;
            cuenta = null;
            perfilEnCache = null;
            contenido.setValue(UiState.error(
                    "La sesión del taxista ya no está disponible. Vuelve a iniciar sesión."));
            return;
        }
        boolean cambio = cuenta == null || !cuenta.getIdentidadId().equals(nueva.getIdentidadId());
        cuenta = nueva;
        if (cambio) {
            perfilEnCache = null;
            cargar(true);
        }
    }

    /** Reintento del usuario: vuelve a mostrar la carga. */
    public void recargar() {
        perfilEnCache = null;
        cargar(true);
    }

    /** Refresco periodico: no tapa la pantalla ni muestra errores pasajeros. */
    public void refrescarEnSilencio() {
        UiState<Panel> actual = contenido.getValue();
        if (actual == null || !actual.isSuccess() || Boolean.TRUE.equals(ocupado.getValue())) {
            return;
        }
        cargar(false);
    }

    // ------------------------------------------------------------------ Carga

    private void cargar(boolean conCarga) {
        final Cuenta actual = cuenta;
        if (actual == null) {
            return;
        }
        final long version = ++generacion;
        if (conCarga) {
            contenido.setValue(UiState.loading());
        }
        if (perfilEnCache != null) {
            cargarServicios(actual, perfilEnCache, version, conCarga);
            return;
        }
        ServiceLocator.taxis().perfilDe(actual.getIdentidadId(), new ResultCallback<Driver>() {
            @Override
            public void onExito(@NonNull Driver perfil) {
                if (version != generacion) {
                    return;
                }
                perfilEnCache = perfil;
                cargarServicios(actual, perfil, version, conCarga);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                fallar(version, mensaje, conCarga);
            }
        });
    }

    private void cargarServicios(@NonNull Cuenta actual, @NonNull Driver perfil,
                                 long version, boolean conCarga) {
        ServiceLocator.taxis().serviciosEnCursoDe(actual.getIdentidadId(),
                new ResultCallback<List<TaxiService>>() {
                    @Override
                    public void onExito(@NonNull List<TaxiService> enCurso) {
                        if (version != generacion) {
                            return;
                        }
                        if (!enCurso.isEmpty()) {
                            publicar(actual, perfil, enCurso.get(0), Collections.emptyList());
                        } else {
                            cargarOfertas(actual, perfil, version, conCarga);
                        }
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        fallar(version, mensaje, conCarga);
                    }
                });
    }

    private void cargarOfertas(@NonNull Cuenta actual, @NonNull Driver perfil,
                               long version, boolean conCarga) {
        ServiceLocator.taxis().disponibles(actual.getIdentidadId(),
                new ResultCallback<List<OfertaDeTaxi>>() {
                    @Override
                    public void onExito(@NonNull List<OfertaDeTaxi> ofertas) {
                        if (version == generacion) {
                            publicar(actual, perfil, null, ofertas);
                        }
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        fallar(version, mensaje, conCarga);
                    }
                });
    }

    private void publicar(@NonNull Cuenta actual, @Nullable Driver perfil,
                          @Nullable TaxiService activo, @NonNull List<OfertaDeTaxi> ofertas) {
        contenido.setValue(UiState.success(new Panel(actual, perfil, activo, ofertas)));
    }

    private void fallar(long version, @NonNull String mensaje, boolean mostrar) {
        if (version != generacion) {
            return;
        }
        UiState<Panel> actual = contenido.getValue();
        boolean hayContenido = actual != null && actual.isSuccess();
        // Un fallo del refresco periodico no borra lo que ya se ve.
        if (mostrar || !hayContenido) {
            contenido.setValue(UiState.error(mensaje));
        }
    }

    // ---------------------------------------------------------------- Acciones

    /** RF-090 a RF-092: acepta la solicitud. Si otro taxista la tomo antes, avisa y refresca. */
    public void aceptar(@NonNull TaxiService servicio) {
        final Cuenta actual = cuenta;
        if (actual == null || Boolean.TRUE.equals(ocupado.getValue())) {
            return;
        }
        ocupado.setValue(true);
        ServiceLocator.taxis().aceptar(servicio.getId(), actual.getIdentidadId(),
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        ocupado.setValue(false);
                        aviso.setValue("Servicio aceptado. Ve al punto de recojo.");
                        cargar(false);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        ocupado.setValue(false);
                        aviso.setValue(mensaje);
                        cargar(false);
                    }
                });
    }

    /** RF-108, RF-109: ASIGNADO -> EN_CAMINO -> EN_TRASLADO. */
    public void avanzar() {
        final Cuenta actual = cuenta;
        UiState<Panel> estado = contenido.getValue();
        Panel panel = estado != null && estado.isSuccess() ? estado.getData() : null;
        if (actual == null || panel == null || panel.activo == null
                || Boolean.TRUE.equals(ocupado.getValue())) {
            return;
        }
        ocupado.setValue(true);
        ServiceLocator.taxis().avanzarComoConductor(panel.activo.getId(), actual.getIdentidadId(),
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        ocupado.setValue(false);
                        cargar(false);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        ocupado.setValue(false);
                        aviso.setValue(mensaje);
                        cargar(false);
                    }
                });
    }

    /** RF-102 a RF-104, RF-110: cierra el servicio con el codigo que dicta el cliente. */
    public void validarCodigo(@NonNull String taxiId, @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        final Cuenta actual = cuenta;
        if (actual == null) {
            callback.onError("La sesión del taxista ya no está disponible. Vuelve a iniciar sesión.");
            return;
        }
        ServiceLocator.taxis().validarCodigo(taxiId, actual.getIdentidadId(), codigo,
                new ResultCallback<TaxiService>() {
                    @Override
                    public void onExito(@NonNull TaxiService dato) {
                        cargar(false);
                        callback.onExito(dato);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        callback.onError(mensaje);
                    }
                });
    }
}
