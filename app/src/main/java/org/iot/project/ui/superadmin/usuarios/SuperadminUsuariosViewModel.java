package org.iot.project.ui.superadmin.usuarios;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cuentas de la plataforma (RF-005) y su activacion (RF-006).
 *
 * <p><b>El filtro vive aqui y no en el repositorio.</b> Filtrar por rol es
 * mirar una lista que ya esta en memoria; hacerlo pasar por el repositorio
 * costaria los 400 ms de latencia simulada en cada toque de filtro, y el
 * usuario veria el esqueleto cada vez que cambia de pestaña.
 *
 * <p>Los superadministradores se enseñan y no se tocan: saber quien puede
 * administrar la plataforma es parte de RF-005, y esconderlos haria creer que no
 * existen.
 */
public class SuperadminUsuariosViewModel extends ViewModel {

    /**
     * Filtro de la lista. {@code null} significa "todas".
     *
     * <p>Es el rol y no el identificador del chip: el chip es cosa de la
     * pantalla, y el ViewModel no tiene por que conocer sus identificadores.
     */
    @Nullable
    private Role filtro;

    private final MutableLiveData<UiState<List<User>>> contenido = new MutableLiveData<>();

    /** Resultado de activar o desactivar (RF-006), para el aviso de una vez. */
    private final MutableLiveData<UiState<User>> cambio = new MutableLiveData<>();

    /** La lista completa, tal como llego: el filtro se aplica sobre ella. */
    private List<User> todas = Collections.emptyList();

    private boolean cargando;

    public LiveData<UiState<List<User>>> getContenido() {
        return contenido;
    }

    public LiveData<UiState<User>> getCambio() {
        return cambio;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<User>> actual = contenido.getValue();
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

    private void pedir() {
        ServiceLocator.superadmin().usuarios(new ResultCallback<List<User>>() {
            @Override
            public void onExito(@NonNull List<User> datos) {
                cargando = false;
                todas = datos;
                publicar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    public void filtrarPor(@Nullable Role rol) {
        filtro = rol;
        publicar();
    }

    private void publicar() {
        List<User> visibles = new ArrayList<>();
        for (User usuario : todas) {
            if (filtro == null || usuario.getRol() == filtro) {
                visibles.add(usuario);
            }
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<User>>empty()
                : UiState.success(visibles));
    }

    /**
     * Activa o desactiva una cuenta.
     *
     * <p>Se recarga la lista entera en vez de cambiar la fila en memoria: el
     * repositorio es quien decide si el cambio era posible —un
     * superadministrador no se desactiva—, y adelantarlo en la pantalla seria
     * tener la regla escrita dos veces.
     *
     * <p>Entra el identificador y no la cuenta porque quien lo pide es una fila
     * de la lista, y la fila guarda una copia de lo que pinta, no el objeto del
     * repositorio: lo unico que sobrevive a esa copia y sirve para encontrar a
     * la cuenta es el identificador.
     */
    public void cambiarActivo(@NonNull String usuarioId, boolean activo) {
        ServiceLocator.superadmin().cambiarActivo(usuarioId, activo,
                new ResultCallback<User>() {
                    @Override
                    public void onExito(@NonNull User dato) {
                        cambio.setValue(UiState.success(dato));
                        // Sin esqueleto: la lista ya esta pintada y el cambio de
                        // una fila no justifica borrarla mientras llega.
                        cargando = true;
                        pedir();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        cambio.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** El aviso ya se leyo. */
    public void consumirCambio() {
        cambio.setValue(null);
    }
}
