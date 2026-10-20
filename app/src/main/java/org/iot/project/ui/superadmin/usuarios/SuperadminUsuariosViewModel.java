package org.iot.project.ui.superadmin.usuarios;

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
import org.iot.project.data.repository.SuperadminRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Cuentas de acceso de Firebase; el perfil operativo del taxi se gestiona aparte. */
public class SuperadminUsuariosViewModel extends ViewModel {
    private final MutableLiveData<UiState<List<User>>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<User>> cambio = new MutableLiveData<>();
    private final MutableLiveData<String> errorSeguimiento = new MutableLiveData<>();
    private final MutableLiveData<Set<String>> pendientes =
            new MutableLiveData<>(Collections.emptySet());
    private final Set<String> cambiando = new HashSet<>();
    private final Observer<Cuenta> sesion = cuenta -> revisarSesion();

    @Nullable private Role filtro;
    @Nullable private String uid;
    @Nullable private SuperadminRepository.Suscripcion suscripcion;
    private List<User> todas = Collections.emptyList();
    private boolean visible;
    private boolean datosRecibidos;
    private long contexto;
    private long generacion;
    private long revisionLista;
    @Nullable private String cambioFallidoId;
    private boolean cambioFallidoActivo;

    public SuperadminUsuariosViewModel() {
        SessionManager.sesion().observeForever(sesion);
    }

    public LiveData<UiState<List<User>>> getContenido() { return contenido; }
    public LiveData<UiState<User>> getCambio() { return cambio; }
    public LiveData<String> getErrorSeguimiento() { return errorSeguimiento; }
    public LiveData<Set<String>> getPendientes() { return pendientes; }
    @Nullable public Role getFiltro() { return filtro; }

    public void iniciarSeguimiento() {
        visible = true;
        revisarSesion();
        conectar();
    }

    public void detenerSeguimiento() {
        visible = false;
        cerrarEscucha();
    }

    public void reintentar() {
        cerrarEscucha();
        conectar();
    }

    private void revisarSesion() {
        String actual = SessionManager.getRolActivo() == Role.SUPERADMIN
                ? SessionManager.getIdentidadId() : null;
        if (Objects.equals(uid, actual)) return;
        cerrarEscucha();
        contexto++;
        uid = actual;
        todas = Collections.emptyList();
        datosRecibidos = false;
        cambiando.clear();
        cambioFallidoId = null;
        publicarPendientes();
        cambio.setValue(null);
        errorSeguimiento.setValue(null);
        contenido.setValue(UiState.loading());
        conectar();
    }

    private void conectar() {
        if (!visible || suscripcion != null) return;
        if (uid == null) {
            contenido.setValue(UiState.error("Inicia sesión como superadministrador."));
            return;
        }
        final String cuenta = uid;
        final long solicitud = ++generacion;
        final long identidad = contexto;
        if (!datosRecibidos) contenido.setValue(UiState.loading());
        errorSeguimiento.setValue(null);
        suscripcion = ServiceLocator.superadmin().escucharUsuarios(new ResultCallback<List<User>>() {
            @Override public void onExito(@NonNull List<User> datos) {
                if (!escuchaVigente(cuenta, identidad, solicitud)) return;
                todas = new ArrayList<>(datos);
                revisionLista++;
                datosRecibidos = true;
                errorSeguimiento.setValue(null);
                publicar();
            }

            @Override public void onError(@NonNull String mensaje) {
                if (!escuchaVigente(cuenta, identidad, solicitud)) return;
                if (datosRecibidos) errorSeguimiento.setValue(mensaje);
                else contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    public void filtrarPor(@Nullable Role rol) {
        filtro = rol;
        if (datosRecibidos) publicar();
    }

    private void publicar() {
        List<User> visibles = new ArrayList<>();
        for (User usuario : todas) {
            if (filtro == null || usuario.getRol() == filtro) visibles.add(usuario);
        }
        contenido.setValue(visibles.isEmpty() ? UiState.empty() : UiState.success(visibles));
    }

    public boolean puedeCambiar(@NonNull String usuarioId) {
        if (!visible || uid == null || !vigente(uid, contexto) || cambiando.contains(usuarioId)) {
            return false;
        }
        for (User usuario : todas) {
            if (usuarioId.equals(usuario.getId())) return usuario.esDesactivable();
        }
        return false;
    }

    /** El estado de la fila cambia únicamente después de confirmar la escritura. */
    public void cambiarActivo(@NonNull String usuarioId, boolean activo) {
        if (!puedeCambiar(usuarioId)) return;
        final String cuenta = uid;
        final long identidad = contexto;
        final long revisionInicial = revisionLista;
        cambiando.add(usuarioId);
        publicarPendientes();
        ServiceLocator.superadmin().cambiarActivo(usuarioId, activo, new ResultCallback<User>() {
            @Override public void onExito(@NonNull User dato) {
                if (!vigente(cuenta, identidad)) return;
                if (usuarioId.equals(cambioFallidoId)) cambioFallidoId = null;
                cambiando.remove(usuarioId);
                publicarPendientes();
                // Una instantánea recibida durante el cambio tiene prioridad.
                if (revisionInicial == revisionLista) {
                    List<User> actualizadas = new ArrayList<>(todas.size());
                    for (User usuario : todas) {
                        actualizadas.add(usuarioId.equals(usuario.getId()) ? dato : usuario);
                    }
                    todas = actualizadas;
                    publicar();
                }
                cambio.setValue(UiState.success(dato));
            }

            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(cuenta, identidad)) return;
                cambiando.remove(usuarioId);
                publicarPendientes();
                cambioFallidoId = usuarioId;
                cambioFallidoActivo = activo;
                cambio.setValue(UiState.error(mensaje));
            }
        });
    }

    private boolean vigente(@NonNull String cuenta, long identidad) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return identidad == contexto && actual != null && cuenta.equals(actual.getUid())
                && cuenta.equals(uid) && cuenta.equals(SessionManager.getIdentidadId())
                && SessionManager.getRolActivo() == Role.SUPERADMIN;
    }

    private boolean escuchaVigente(String cuenta, long identidad, long solicitud) {
        return visible && solicitud == generacion && vigente(cuenta, identidad);
    }

    private void publicarPendientes() {
        pendientes.setValue(Collections.unmodifiableSet(new HashSet<>(cambiando)));
    }

    public void consumirCambio() { cambio.setValue(null); }

    public void reintentarCambio() {
        String usuarioId = cambioFallidoId;
        if (usuarioId != null) cambiarActivo(usuarioId, cambioFallidoActivo);
    }

    private void cerrarEscucha() {
        generacion++;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    @Override protected void onCleared() {
        contexto++;
        cerrarEscucha();
        SessionManager.sesion().removeObserver(sesion);
    }
}
