package org.iot.project.ui.driver.home;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.core.ubicacion.PoliticaUbicacion;
import org.iot.project.core.ubicacion.SeguimientoTaxista;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.data.rest.SesionTaxistaRest;

import java.util.List;
import java.util.ArrayList;

/** Datos reales del sistema web de taxistas, sin generar ubicación ni viajes locales. */
public class DriverHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<Cuenta>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<Driver>> perfil = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<OfertaDeTaxi>>> ofertas = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<TaxiService>>> servicios = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operando = new MutableLiveData<>(false);
    private final MutableLiveData<String> aviso = new MutableLiveData<>();
    private final MutableLiveData<UiState<Driver>> ubicacion = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ofertasHabilitadas = new MutableLiveData<>(false);
    private String identidad;
    private long generacion;
    private int consultas;
    private static final long VIGENCIA_POSICION_MS = PoliticaUbicacion.MAX_ANTIGUEDAD_MS;
    private boolean permisoPreciso;
    private boolean gpsEncendido;
    private long posicionEnviadaElapsedMs;
    private long revisionUbicacion;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private final Runnable vencerPosicion = () -> puedeConsultarOfertas();

    public LiveData<UiState<Cuenta>> getContenido() {
        return contenido;
    }

    public LiveData<UiState<Driver>> getPerfil() { return perfil; }
    public LiveData<UiState<List<OfertaDeTaxi>>> getOfertas() { return ofertas; }
    public LiveData<UiState<List<TaxiService>>> getServicios() { return servicios; }
    public LiveData<Boolean> getOperando() { return operando; }
    public LiveData<String> getAviso() { return aviso; }
    public LiveData<UiState<Driver>> getUbicacion() { return ubicacion; }
    public LiveData<Boolean> getOfertasHabilitadas() { return ofertasHabilitadas; }
    public void limpiarAviso() { aviso.setValue(null); }

    /** Los datos personales proceden del perfil que ya verificó la sesión. */
    public void actualizarCuenta(@Nullable Cuenta cuenta) {
        if (cuenta == null || cuenta.getRol() != Role.CONDUCTOR) {
            invalidar();
            contenido.setValue(UiState.error(
                    "La sesión del taxista ya no está disponible. Vuelve a iniciar sesión."));
            return;
        }
        if (!cuenta.getIdentidadId().equals(identidad)) {
            invalidar();
            identidad = cuenta.getIdentidadId();
        }
        contenido.setValue(UiState.success(cuenta));
        if (!cuenta.getIdentidadId().startsWith("taxista:")) {
            perfil.setValue(UiState.error("Cierra sesión y elige Taxista para ingresar con tu cuenta del sistema web."));
            return;
        }
        refrescar();
    }

    private void invalidar() {
        generacion++;
        consultas = 0;
        identidad = null;
        perfil.setValue(null);
        ofertas.setValue(null);
        servicios.setValue(null);
        ubicacion.setValue(null);
        operando.setValue(false);
        permisoPreciso = false;
        gpsEncendido = false;
        bloquearOfertas();
    }

    public void cargar() {
        actualizarCuenta(SessionManager.getCuenta());
    }

    public void recargar() {
        cargar();
    }

    /** La pantalla llama cada cinco segundos; no superpone lotes ni escrituras. */
    public void refrescar() {
        if (!sesionValida() || consultas > 0 || Boolean.TRUE.equals(operando.getValue())) return;
        boolean consultarOfertas = puedeConsultarOfertas();
        long version = ++generacion;
        String conductorId = SessionManager.getConductorId();
        consultas = consultarOfertas ? 3 : 2;
        if (perfil.getValue() == null) perfil.setValue(UiState.loading());
        if (consultarOfertas && ofertas.getValue() == null) ofertas.setValue(UiState.loading());
        if (servicios.getValue() == null) servicios.setValue(UiState.loading());
        ServiceLocator.taxis().perfilDe(conductorId, leer(version, perfil));
        if (consultarOfertas) {
            ServiceLocator.taxis().disponibles(conductorId, leerOfertas(version, revisionUbicacion));
        }
        ServiceLocator.taxis().serviciosEnCursoDe(conductorId, leer(version, servicios));
    }

    /** Los cambios de permiso/GPS no interrumpen perfil, avance ni cierre del viaje. */
    public void establecerEntornoUbicacion(boolean preciso, boolean gpsActivo) {
        boolean cambio = permisoPreciso != preciso || gpsEncendido != gpsActivo;
        permisoPreciso = preciso;
        gpsEncendido = gpsActivo;
        if (!preciso || !gpsActivo) {
            if (cambio || posicionEnviadaElapsedMs > 0L) bloquearOfertas();
        } else {
            puedeConsultarOfertas();
        }
    }

    /** Solo una posición real enviada recientemente permite consultar y aceptar ofertas. */
    public boolean puedeConsultarOfertas() {
        long edad = SystemClock.elapsedRealtime() - posicionEnviadaElapsedMs;
        boolean vigente = PoliticaUbicacion.puedeMostrarOfertas(permisoPreciso, gpsEncendido,
                posicionEnviadaElapsedMs > 0L, edad);
        if (!vigente && (posicionEnviadaElapsedMs > 0L || Boolean.TRUE.equals(ofertasHabilitadas.getValue()))) {
            bloquearOfertas();
        }
        return vigente;
    }

    private void bloquearOfertas() {
        revisionUbicacion++;
        posicionEnviadaElapsedMs = 0L;
        principal.removeCallbacks(vencerPosicion);
        ofertasHabilitadas.setValue(false);
        ubicacion.setValue(null);
        ofertas.setValue(UiState.empty());
    }

    /** El servicio renueva la posición únicamente después de enviarla correctamente. */
    public void registrarEnvioSeguimiento(long enviadoElapsedMs) {
        if (!sesionValida() || !permisoPreciso || !gpsEncendido) return;
        registrarPosicionEnviada(enviadoElapsedMs);
    }

    private void registrarPosicionEnviada(long enviadaElapsedMs) {
        long ahora = SystemClock.elapsedRealtime();
        if (enviadaElapsedMs <= 0L || enviadaElapsedMs > ahora
                || ahora - enviadaElapsedMs > VIGENCIA_POSICION_MS
                || enviadaElapsedMs <= posicionEnviadaElapsedMs) return;
        posicionEnviadaElapsedMs = enviadaElapsedMs;
        revisionUbicacion++;
        principal.removeCallbacks(vencerPosicion);
        principal.postDelayed(vencerPosicion, VIGENCIA_POSICION_MS - (ahora - enviadaElapsedMs) + 1L);
        ubicacion.setValue(null);
        ofertas.setValue(UiState.loading());
        ofertasHabilitadas.setValue(true);
    }

    private ResultCallback<List<OfertaDeTaxi>> leerOfertas(long version, long revisionPosicion) {
        return new ResultCallback<List<OfertaDeTaxi>>() {
            @Override public void onExito(@NonNull List<OfertaDeTaxi> datos) {
                if (!sesionValida() || version != generacion) return;
                consultas--;
                if (revisionPosicion == revisionUbicacion && puedeConsultarOfertas()) {
                    ofertas.setValue(UiState.success(datos));
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!sesionValida() || version != generacion) return;
                consultas--;
                if (revisionPosicion == revisionUbicacion && puedeConsultarOfertas()) {
                    ofertas.setValue(UiState.error(mensaje));
                }
            }
        };
    }

    private <T> ResultCallback<T> leer(long version, MutableLiveData<UiState<T>> destino) {
        return new ResultCallback<T>() {
            @Override public void onExito(@NonNull T datos) {
                if (sesionValida() && version == generacion) {
                    consultas--;
                    destino.setValue(UiState.success(datos));
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                if (sesionValida() && version == generacion) {
                    consultas--;
                    destino.setValue(UiState.error(mensaje));
                }
            }
        };
    }

    private boolean conservaIdentidadRest() {
        Cuenta actual = SessionManager.getCuenta();
        return actual != null && actual.getRol() == Role.CONDUCTOR
                && actual.getIdentidadId().equals(identidad)
                && identidad.startsWith("taxista:");
    }

    private boolean sesionValida() {
        if (!conservaIdentidadRest()) return false;
        if (!SesionTaxistaRest.estaActiva()) {
            cerrarSiExpirada(identidad);
            return false;
        }
        Cuenta autorizada = SesionTaxistaRest.getCuenta();
        if (autorizada == null) {
            cerrarSiExpirada(identidad);
            return false;
        }
        if (!identidad.equals(autorizada.getIdentidadId())) return false;
        if (SessionManager.getConductorId() == null) {
            cerrarSiExpirada(identidad);
            return false;
        }
        return true;
    }

    private void cerrarSiExpirada(@NonNull String identidadEsperada) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            principal.post(() -> cerrarSiExpirada(identidadEsperada));
            return;
        }
        // Un callback de una sesión anterior nunca cierra el acceso de otra persona.
        if (!identidadEsperada.equals(identidad) || !conservaIdentidadRest()
                || SesionTaxistaRest.estaActiva()) return;
        invalidar();
        contenido.setValue(UiState.error("Tu sesión de taxista venció. Vuelve a iniciar sesión."));
        SessionManager.cerrarSesion();
    }

    public void aceptar(@NonNull String taxiId) {
        UiState<List<OfertaDeTaxi>> candidatas = ofertas.getValue();
        UiState<List<TaxiService>> actuales = servicios.getValue();
        UiState<Driver> ficha = perfil.getValue();
        if (!puedeConsultarOfertas()) {
            aviso.setValue("Activa la ubicación precisa y actualiza tu posición para aceptar solicitudes.");
            return;
        }
        boolean conocida = candidatas != null && candidatas.isSuccess()
                && candidatas.requireData().stream().anyMatch(oferta -> taxiId.equals(oferta.getServicio().getId()));
        if (!conocida || actuales == null || !actuales.isSuccess() || !actuales.requireData().isEmpty()
                || ficha == null || !ficha.isSuccess() || !ficha.requireData().isHabilitado()) {
            aviso.setValue("Esta solicitud ya no está disponible para aceptar. Actualiza los servicios.");
            return;
        }
        if (!iniciarAccion()) return;
        ServiceLocator.taxis().aceptar(taxiId, SessionManager.getConductorId(), terminarAccion(null));
    }

    public void avanzar(@NonNull String taxiId) {
        if (!iniciarAccion()) return;
        ServiceLocator.taxis().avanzarComoConductor(taxiId, SessionManager.getConductorId(), terminarAccion(null));
    }

    /** Envía únicamente la posición real obtenida tras una acción explícita del conductor. */
    public void actualizarUbicacion(double latitud, double longitud, long capturaElapsedMs) {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90d || latitud > 90d || longitud < -180d || longitud > 180d) {
            ubicacion.setValue(UiState.error("No pudimos obtener una ubicación válida. Inténtalo otra vez."));
            return;
        }
        if (!sesionValida() || !permisoPreciso || !gpsEncendido) {
            bloquearOfertas();
            ubicacion.setValue(UiState.error("Activa el permiso de ubicación precisa y el GPS para actualizar tu posición."));
            return;
        }
        long edad = SystemClock.elapsedRealtime() - capturaElapsedMs;
        if (capturaElapsedMs <= 0L || edad < 0L || edad > VIGENCIA_POSICION_MS) {
            bloquearOfertas();
            ubicacion.setValue(UiState.error("La ubicación ya no está vigente. Vuelve a obtener tu posición."));
            return;
        }
        UiState<Driver> envio = ubicacion.getValue();
        if (envio != null && envio.isLoading()) return;
        bloquearOfertas();
        long revision = revisionUbicacion;
        ubicacion.setValue(UiState.loading());
        ServiceLocator.taxis().actualizarUbicacion(SessionManager.getConductorId(), latitud, longitud,
                new ResultCallback<Driver>() {
                    @Override public void onExito(@NonNull Driver datos) {
                        if (!sesionValida() || revision != revisionUbicacion
                                || !permisoPreciso || !gpsEncendido) return;
                        perfil.setValue(UiState.success(datos));
                        registrarPosicionEnviada(capturaElapsedMs);
                        if (!puedeConsultarOfertas()) {
                            ubicacion.setValue(UiState.error("La ubicación ya no está vigente. Vuelve a obtener tu posición."));
                            return;
                        }
                        ubicacion.setValue(UiState.success(datos));
                        refrescar();
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!sesionValida() || revision != revisionUbicacion) return;
                        ubicacion.setValue(UiState.error(mensaje));
                        refrescar();
                    }
                });
    }

    public void validarCodigo(@NonNull String taxiId, @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        if (!iniciarAccion()) {
            callback.onError("Espera a que termine la operación actual o vuelve a iniciar sesión.");
            return;
        }
        ServiceLocator.taxis().validarCodigo(taxiId, SessionManager.getConductorId(), codigo,
                terminarAccion(callback));
    }

    private boolean iniciarAccion() {
        if (!sesionValida() || Boolean.TRUE.equals(operando.getValue())) return false;
        // Descarta consultas anteriores para no dibujar un estado previo a la escritura.
        generacion++;
        consultas = 0;
        operando.setValue(true);
        return true;
    }

    private ResultCallback<TaxiService> terminarAccion(@Nullable ResultCallback<TaxiService> callback) {
        long version = generacion;
        return new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService datos) {
                if (!sesionValida() || version != generacion) return;
                if (datos.getEstado().isFinished()) {
                    SeguimientoTaxista.detenerActual();
                    // El servidor ya confirmó el cierre: retira el viaje de la
                    // copia visible para que ningún refresco reactive su GPS.
                    List<TaxiService> restantes = new ArrayList<>();
                    UiState<List<TaxiService>> actuales = servicios.getValue();
                    if (actuales != null && actuales.getData() != null) {
                        for (TaxiService actual : actuales.getData()) {
                            if (!datos.getId().equals(actual.getId()) && !actual.getEstado().isFinished()) {
                                restantes.add(actual);
                            }
                        }
                    }
                    servicios.setValue(UiState.success(restantes));
                }
                operando.setValue(false);
                refrescar();
                if (callback != null) callback.onExito(datos);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!sesionValida() || version != generacion) return;
                operando.setValue(false);
                if (callback == null) aviso.setValue(mensaje);
                else callback.onError(mensaje);
                refrescar();
            }
        };
    }

    @Override protected void onCleared() {
        generacion++;
        revisionUbicacion++;
        principal.removeCallbacksAndMessages(null);
    }
}
