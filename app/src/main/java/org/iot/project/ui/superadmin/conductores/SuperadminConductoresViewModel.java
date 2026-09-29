package org.iot.project.ui.superadmin.conductores;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Driver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conductores de la plataforma (RF-077) y su habilitacion (RF-078).
 *
 * <p>El filtro de pendientes vive aqui por el mismo motivo que el de las
 * cuentas: es mirar una lista que ya esta en memoria, y hacerlo pasar por el
 * repositorio costaria la latencia simulada en cada toque de chip.
 *
 * <p>Habilitar y retirar son la misma operacion con el valor al reves (RF-078):
 * el repositorio decide si el cambio era posible y la lista se recarga entera
 * despues, en vez de cambiar la fila en memoria.
 */
public class SuperadminConductoresViewModel extends ViewModel {

    /** Si el filtro enseña solo los que esperan aprobacion. */
    private boolean soloPendientes;

    private final MutableLiveData<UiState<List<Driver>>> contenido = new MutableLiveData<>();

    /** Resultado de habilitar o retirar (RF-078), para el aviso de una vez. */
    private final MutableLiveData<UiState<Driver>> cambio = new MutableLiveData<>();

    private List<Driver> todos = Collections.emptyList();

    private boolean cargando;

    public LiveData<UiState<List<Driver>>> getContenido() {
        return contenido;
    }

    public LiveData<UiState<Driver>> getCambio() {
        return cambio;
    }

    /**
     * Que filtro esta puesto.
     *
     * <p>La pantalla lo necesita para elegir que decir cuando no hay nada que
     * enseñar: no es lo mismo "no queda ninguno por aprobar" que "no hay
     * conductores registrados", y el filtro es quien decide cual de las dos.
     */
    public boolean isSoloPendientes() {
        return soloPendientes;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<Driver>> actual = contenido.getValue();
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
        ServiceLocator.superadmin().conductores(new ResultCallback<List<Driver>>() {
            @Override
            public void onExito(@NonNull List<Driver> datos) {
                cargando = false;
                todos = datos;
                publicar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                cargando = false;
                contenido.setValue(UiState.error(mensaje));
            }
        });
    }

    /** El filtro de la lista: todos, o solo los que esperan aprobacion. */
    public void filtrar(boolean soloPendientes) {
        this.soloPendientes = soloPendientes;
        publicar();
    }

    private void publicar() {
        List<Driver> visibles = new ArrayList<>();
        for (Driver conductor : todos) {
            if (!soloPendientes || !conductor.isHabilitado()) {
                visibles.add(conductor);
            }
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<Driver>>empty()
                : UiState.success(visibles));
    }

    /**
     * Habilita o retira la habilitacion de un conductor (RF-078).
     *
     * <p>Entra el identificador y no el conductor porque quien lo pide es una
     * fila de la lista, y la fila guarda una copia de lo que pinta, no el objeto
     * del repositorio: lo unico que sobrevive a esa copia y sirve para encontrar
     * al conductor es el identificador.
     */
    public void habilitar(@NonNull String conductorId, boolean habilitado) {
        ServiceLocator.superadmin().habilitarConductor(conductorId, habilitado,
                new ResultCallback<Driver>() {
                    @Override
                    public void onExito(@NonNull Driver dato) {
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
