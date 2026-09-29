package org.iot.project.ui.superadmin.perfil;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.ResumenSuperadmin;
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

    public LiveData<UiState<User>> getPerfil() {
        return perfil;
    }

    public LiveData<UiState<ResumenSuperadmin>> getPlataforma() {
        return plataforma;
    }

    public void cargar() {
        perfil.setValue(UiState.loading());
        cargarPerfil();
        cargarPlataforma();
    }

    public void reintentar() {
        cargar();
    }

    private void cargarPerfil() {
        ServiceLocator.usuarios().usuario(SessionManager.getIdentidadId(),
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User datos) {
                        perfil.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        perfil.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Las cifras de la plataforma.
     *
     * <p>Es la misma consulta que hace la portada —{@code resumen()}— y no un
     * puñado de listas contadas aqui: que es un usuario activo o un hotel
     * publicado lo decide el dominio, y contarlo en la pantalla seria tenerlo
     * escrito dos veces.
     */
    private void cargarPlataforma() {
        plataforma.setValue(UiState.<ResumenSuperadmin>loading());
        ServiceLocator.superadmin().resumen(new ResultCallback<ResumenSuperadmin>() {
            @Override
            public void onExito(@NonNull ResumenSuperadmin datos) {
                plataforma.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                plataforma.setValue(UiState.error(mensaje));
            }
        });
    }
}
