package org.iot.project.ui.client.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.User;

import java.util.List;

/**
 * Estado de la pantalla de inicio.
 *
 * <p>El ViewModel no conoce ni la vista ni el repositorio concreto: pide a
 * {@link ServiceLocator} y recibe de vuelta un {@link UiState}, que es lo que
 * la pantalla necesita para decidir si muestra el esqueleto de carga, la lista
 * o el estado de error (§50).
 */
public class HomeViewModel extends ViewModel {

    private static final int LIMITE_RECOMENDADOS = 5;

    private final MutableLiveData<UiState<List<Hotel>>> recomendados =
            new MutableLiveData<>();
    private final MutableLiveData<User> usuario = new MutableLiveData<>();

    public LiveData<UiState<List<Hotel>>> getRecomendados() {
        return recomendados;
    }

    public LiveData<User> getUsuario() {
        return usuario;
    }

    /** Carga inicial. Se puede volver a llamar desde el botón de reintento. */
    public void cargar() {
        recomendados.setValue(UiState.loading());

        ServiceLocator.hoteles().recomendados(LIMITE_RECOMENDADOS,
                new ResultCallback<List<Hotel>>() {
                    @Override
                    public void onExito(@NonNull List<Hotel> datos) {
                        // Una lista vacía no es un error: es un estado vacío, y
                        // la pantalla lo dibuja distinto (§50).
                        recomendados.setValue(datos.isEmpty()
                                ? UiState.empty()
                                : UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        recomendados.setValue(UiState.error(mensaje));
                    }
                });

        cargarUsuario();
    }

    private void cargarUsuario() {
        String usuarioId = SessionManager.getUsuarioIdSeguro();
        if (usuarioId == null) {
            return;
        }
        ServiceLocator.usuarios().usuario(usuarioId, new ResultCallback<User>() {
            @Override
            public void onExito(@NonNull User datos) {
                usuario.setValue(datos);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                // El saludo es un adorno de la cabecera: si falla, la pantalla
                // sigue siendo util y no se muestra un error por esto.
            }
        });
    }
}
