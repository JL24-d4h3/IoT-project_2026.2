package org.iot.project.ui.superadmin.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.models.Role;

/**
 * Portada del superadministrador (§47).
 *
 * <p>Una sola consulta: el repositorio entrega las cifras ya contadas y los
 * ultimos movimientos de la bitacora juntos. Contarlas aqui obligaria a la
 * pantalla a saber que es un usuario activo o un hotel sin administrador, y esa
 * definicion es del dominio.
 */
public class SuperadminHomeViewModel extends ViewModel {

    private final MutableLiveData<UiState<ResumenSuperadmin>> contenido = new MutableLiveData<>();

    @Nullable private String uid;
    private boolean visible;
    private boolean cargando;
    private long generacion;

    public LiveData<UiState<ResumenSuperadmin>> getContenido() {
        return contenido;
    }

    public void iniciar() {
        String actual = identidad();
        if (actual == null) {
            limpiarSesion();
            return;
        }
        if (visible && actual.equals(uid)) return;
        boolean mismaCuenta = actual.equals(uid);
        visible = true;
        uid = actual;
        cargando = false;
        ++generacion;
        if (!mismaCuenta || contenido.getValue() == null
                || !contenido.getValue().isSuccess()) {
            contenido.setValue(UiState.loading());
        }
        pedir(actual);
    }

    public void reintentar() {
        if (!visible) return;
        String actual = identidad();
        if (actual == null || !actual.equals(uid)) {
            iniciar();
            return;
        }
        if (cargando) return;
        contenido.setValue(UiState.loading());
        pedir(actual);
    }

    public void suspender() {
        visible = false;
        cargando = false;
        ++generacion;
    }

    private void pedir(@NonNull String autor) {
        cargando = true;
        long solicitud = ++generacion;
        ServiceLocator.superadmin().resumen(new ResultCallback<ResumenSuperadmin>() {
            @Override
            public void onExito(@NonNull ResumenSuperadmin datos) {
                if (!vigente(autor, solicitud)) return;
                cargando = false;
                contenido.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (!vigente(autor, solicitud)) return;
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    private boolean vigente(@NonNull String autor, long solicitud) {
        return visible && solicitud == generacion && autor.equals(uid)
                && autor.equals(identidad());
    }

    private void limpiarSesion() {
        suspender();
        uid = null;
        contenido.setValue(UiState.loading());
    }

    @Nullable
    private String identidad() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario != null && SessionManager.getRolActivo() == Role.SUPERADMIN
                && usuario.getUid().equals(SessionManager.getIdentidadId())
                ? usuario.getUid() : null;
    }

    @Override
    protected void onCleared() {
        suspender();
    }
}
