package org.iot.project.ui.superadmin.perfil;

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
import org.iot.project.models.User;

/**
 * Estado del perfil del superadministrador (§47).
 *
 * <p>Se piden dos cosas y se publican por separado: la cuenta y las cifras de
 * la plataforma. No van juntas porque un fallo al leer las cifras no tiene por
 * que dejar al superadministrador sin ver su propio nombre —eso es lo que ya
 * resolvieron asi el perfil del cliente (§40) y el del administrador, y no hay
 * razon para hacerlo distinto aqui—. Lo que si hace la pantalla es decir en su
 * sitio que las cifras no llegaron, en vez de vaciar la tarjeta: un cero
 * afirmaria que la plataforma esta vacia, y eso no lo sabemos.
 */
public class SuperadminPerfilViewModel extends ViewModel {

    private final MutableLiveData<UiState<User>> perfil = new MutableLiveData<>();
    private final MutableLiveData<UiState<ResumenSuperadmin>> plataforma = new MutableLiveData<>();
    @Nullable private String uid;
    private boolean visible;
    private long generacion;

    public LiveData<UiState<User>> getPerfil() {
        return perfil;
    }

    public LiveData<UiState<ResumenSuperadmin>> getPlataforma() {
        return plataforma;
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
        if (!mismaCuenta || perfil.getValue() == null || !perfil.getValue().isSuccess()) {
            perfil.setValue(UiState.loading());
        }
        if (!mismaCuenta || plataforma.getValue() == null || !plataforma.getValue().isSuccess()) {
            plataforma.setValue(UiState.loading());
        }
        pedir(actual, ++generacion);
    }

    public void reintentar() {
        if (!visible) return;
        String actual = identidad();
        if (actual == null || !actual.equals(uid)) {
            iniciar();
            return;
        }
        perfil.setValue(UiState.loading());
        plataforma.setValue(UiState.loading());
        pedir(actual, ++generacion);
    }

    public void suspender() {
        visible = false;
        ++generacion;
    }

    private void pedir(@NonNull String autor, long solicitud) {
        ServiceLocator.usuarios().usuario(autor,
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User datos) {
                        if (!vigente(autor, solicitud)) return;
                        perfil.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (!vigente(autor, solicitud)) return;
                        perfil.setValue(UiState.error(mensaje));
                    }
                });
        cargarPlataforma(autor, solicitud);
    }

    /**
     * Las cifras de la plataforma.
     *
     * <p>Es la misma consulta que hace la portada —{@code resumen()}— y no un
     * puñado de listas contadas aqui: que es un usuario activo o un hotel
     * publicado lo decide el dominio, y contarlo en la pantalla seria tenerlo
     * escrito dos veces.
     */
    private void cargarPlataforma(@NonNull String autor, long solicitud) {
        ServiceLocator.superadmin().resumen(new ResultCallback<ResumenSuperadmin>() {
            @Override
            public void onExito(@NonNull ResumenSuperadmin datos) {
                if (!vigente(autor, solicitud)) return;
                plataforma.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (!vigente(autor, solicitud)) return;
                plataforma.setValue(UiState.error(mensaje));
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
        perfil.setValue(UiState.loading());
        plataforma.setValue(UiState.loading());
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
