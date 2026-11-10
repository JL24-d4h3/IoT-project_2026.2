package org.iot.project.ui.superadmin.conductores;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxistasAdminRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.TaxistaAdministrado;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Directorio web: aprobar para trabajar y activar una cuenta son operaciones independientes. */
public class SuperadminConductoresViewModel extends ViewModel {
    interface SesionAdmin {
        LiveData<Cuenta> cambios();
        @Nullable String uid();
        boolean esSuperadmin();
    }

    public enum Accion { HABILITACION, CUENTA }

    public static final class Cambio {
        @NonNull public final TaxistaAdministrado taxista;
        @NonNull public final Accion accion;
        Cambio(TaxistaAdministrado taxista, Accion accion) {
            this.taxista = taxista;
            this.accion = accion;
        }
    }

    private final TaxistasAdminRepository repositorio;
    private final SesionAdmin sesion;
    private final MutableLiveData<UiState<List<TaxistaAdministrado>>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<Cambio>> cambio = new MutableLiveData<>();
    private final MutableLiveData<String> aviso = new MutableLiveData<>();
    private final MutableLiveData<Boolean> leyendo = new MutableLiveData<>(false);
    private final MutableLiveData<Set<String>> pendientes = new MutableLiveData<>(Collections.emptySet());
    private final Set<String> cambiando = new HashSet<>();
    private final Observer<Cuenta> observador = cuenta -> revisarSesion();
    private List<TaxistaAdministrado> todos = Collections.emptyList();
    @Nullable private String uid;
    private long contexto;
    private long consulta;
    private boolean visible;
    private boolean datosRecibidos;
    private boolean cargando;
    private boolean soloPendientes;

    public SuperadminConductoresViewModel() {
        this(ServiceLocator.taxistasAdmin(), new SesionAdmin() {
            @Override public LiveData<Cuenta> cambios() { return SessionManager.sesion(); }
            @Override public String uid() {
                FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
                return actual != null && actual.getUid().equals(SessionManager.getIdentidadId())
                        ? actual.getUid() : null;
            }
            @Override public boolean esSuperadmin() {
                return SessionManager.getRolActivo() == Role.SUPERADMIN;
            }
        });
    }

    SuperadminConductoresViewModel(TaxistasAdminRepository repositorio, SesionAdmin sesion) {
        this.repositorio = repositorio;
        this.sesion = sesion;
        sesion.cambios().observeForever(observador);
    }

    public LiveData<UiState<List<TaxistaAdministrado>>> getContenido() { return contenido; }
    public LiveData<UiState<Cambio>> getCambio() { return cambio; }
    public LiveData<String> getAviso() { return aviso; }
    public LiveData<Boolean> getLeyendo() { return leyendo; }
    public LiveData<Set<String>> getPendientes() { return pendientes; }
    public boolean isSoloPendientes() { return soloPendientes; }

    public void iniciar() {
        visible = true;
        revisarSesion();
        cargar();
    }

    public void detener() {
        visible = false;
        cancelarLectura();
    }

    public void reintentar() {
        revisarSesion();
        cargar();
    }

    private void revisarSesion() {
        String actual = sesion.esSuperadmin() ? sesion.uid() : null;
        if (Objects.equals(uid, actual)) return;
        cancelarLectura();
        contexto++;
        uid = actual;
        todos = Collections.emptyList();
        datosRecibidos = false;
        cambiando.clear();
        publicarPendientes();
        cambio.setValue(null);
        aviso.setValue(null);
        contenido.setValue(UiState.loading());
        if (visible) cargar();
    }

    private void cargar() {
        if (!visible || cargando || !cambiando.isEmpty()) return;
        if (uid == null || !sesion.esSuperadmin()) {
            contenido.setValue(UiState.error("Inicia sesión como superadministrador para consultar taxistas."));
            return;
        }
        final String cuenta = uid;
        final long identidad = contexto;
        final long solicitud = ++consulta;
        cargando = true;
        leyendo.setValue(true);
        if (!datosRecibidos) contenido.setValue(UiState.loading());
        aviso.setValue(null);
        repositorio.listar(new ResultCallback<List<TaxistaAdministrado>>() {
            @Override public void onExito(@NonNull List<TaxistaAdministrado> datos) {
                if (!lecturaVigente(cuenta, identidad, solicitud)) return;
                cargando = false;
                leyendo.setValue(false);
                datosRecibidos = true;
                todos = new ArrayList<>(datos);
                publicar();
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!lecturaVigente(cuenta, identidad, solicitud)) return;
                cargando = false;
                leyendo.setValue(false);
                if (datosRecibidos) aviso.setValue(mensaje);
                else contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    public void filtrar(boolean soloPendientes) {
        this.soloPendientes = soloPendientes;
        if (datosRecibidos) publicar();
    }

    private void publicar() {
        List<TaxistaAdministrado> visibles = new ArrayList<>();
        for (TaxistaAdministrado taxista : todos) {
            if (!soloPendientes || !taxista.isHabilitado()) visibles.add(taxista);
        }
        visibles.sort(Comparator.comparing(TaxistaAdministrado::isHabilitado)
                .thenComparing(t -> t.getPerfil().getNombreCompleto(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TaxistaAdministrado::getId));
        contenido.setValue(visibles.isEmpty() ? UiState.empty() : UiState.success(visibles));
    }

    public boolean puedeCambiar(@NonNull String taxistaId) {
        if (!visible || uid == null || !vigente(uid, contexto) || cambiando.contains(taxistaId)) return false;
        return buscar(taxistaId) != null;
    }

    @Nullable private TaxistaAdministrado buscar(String id) {
        for (TaxistaAdministrado taxista : todos) if (id.equals(taxista.getId())) return taxista;
        return null;
    }

    public void habilitar(@NonNull String id, boolean habilitado) {
        cambiar(id, habilitado, Accion.HABILITACION);
    }

    public void cambiarCuenta(@NonNull String id, boolean activa) {
        cambiar(id, activa, Accion.CUENTA);
    }

    private void cambiar(String id, boolean valor, Accion accion) {
        if (!puedeCambiar(id)) return;
        TaxistaAdministrado anterior = buscar(id);
        if (anterior == null || (accion == Accion.HABILITACION
                ? anterior.isHabilitado() : anterior.isCuentaActiva()) == valor) return;
        final String cuenta = uid;
        final long identidad = contexto;
        cancelarLectura();
        cambiando.add(id);
        publicarPendientes();
        ResultCallback<TaxistaAdministrado> respuesta = new ResultCallback<TaxistaAdministrado>() {
            @Override public void onExito(@NonNull TaxistaAdministrado dato) {
                if (!vigente(cuenta, identidad)) return;
                cambiando.remove(id);
                publicarPendientes();
                if (!id.equals(dato.getId()) || (accion == Accion.HABILITACION
                        ? dato.isHabilitado() : dato.isCuentaActiva()) != valor) {
                    cambio.setValue(UiState.error("El sistema de taxis no confirmó el cambio solicitado. Actualiza el estado antes de intentarlo otra vez."));
                    return;
                }
                List<TaxistaAdministrado> actualizados = new ArrayList<>(todos.size());
                for (TaxistaAdministrado taxista : todos) {
                    actualizados.add(id.equals(taxista.getId()) ? dato : taxista);
                }
                todos = actualizados;
                publicar();
                cambio.setValue(UiState.success(new Cambio(dato, accion)));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(cuenta, identidad)) return;
                cambiando.remove(id);
                publicarPendientes();
                // No se repite la escritura: una pérdida de respuesta no prueba que haya fallado.
                cambio.setValue(UiState.error(mensaje));
            }
        };
        if (accion == Accion.HABILITACION) repositorio.cambiarHabilitacion(id, valor, respuesta);
        else repositorio.cambiarCuenta(id, valor, respuesta);
    }

    private boolean vigente(String cuenta, long identidad) {
        return identidad == contexto && cuenta != null && cuenta.equals(uid)
                && cuenta.equals(sesion.uid()) && sesion.esSuperadmin();
    }

    private boolean lecturaVigente(String cuenta, long identidad, long solicitud) {
        return visible && solicitud == consulta && vigente(cuenta, identidad);
    }

    private void publicarPendientes() {
        pendientes.setValue(Collections.unmodifiableSet(new HashSet<>(cambiando)));
    }

    public void consumirCambio() { cambio.setValue(null); }

    private void cancelarLectura() {
        consulta++;
        cargando = false;
        leyendo.setValue(false);
    }

    @Override protected void onCleared() {
        contexto++;
        cancelarLectura();
        sesion.cambios().removeObserver(observador);
    }
}
