package org.iot.project.ui.superadmin.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.ResumenSuperadmin;

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

    private boolean cargando;

    public LiveData<UiState<ResumenSuperadmin>> getContenido() {
        return contenido;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        // Al rotar la pantalla el ViewModel sobrevive con las cifras ya
        // cargadas: volver al esqueleto dejaria la pantalla en blanco un
        // instante sin motivo. El refresco de verdad lo hace refrescarEnSilencio.
        UiState<ResumenSuperadmin> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    /**
     * Recarga sin esqueleto, al volver a la portada.
     *
     * <p>Las cifras cambian desde otras pantallas del propio panel —desactivar
     * una cuenta, habilitar un conductor, dar de alta un hotel— y volver a
     * Inicio tiene que enseñarlas al dia sin borrar el tablero mientras llegan.
     */
    public void refrescarEnSilencio() {
        UiState<ResumenSuperadmin> actual = contenido.getValue();
        if (cargando || actual == null || !actual.isSuccess()) {
            return;
        }
        cargando = true;
        pedir();
    }

    private void pedir() {
        ServiceLocator.superadmin().resumen(new ResultCallback<ResumenSuperadmin>() {
            @Override
            public void onExito(@NonNull ResumenSuperadmin datos) {
                cargando = false;
                contenido.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }
}
