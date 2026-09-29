package org.iot.project.ui.superadmin.bitacora;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.LogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bitacora de la plataforma (RF-118 a RF-120).
 *
 * <p><b>El filtro vive aqui y no en el repositorio.</b> Es un filtro de texto:
 * si cada tecla pasara por el repositorio, cada tecla costaria los 400 ms de
 * latencia simulada y la lista iria por detras de lo que se escribe. La
 * bitacora cabe entera en memoria, asi que se pide una vez y se filtra aqui.
 *
 * <p>Se filtra sobre el detalle, el autor y el tipo de evento a la vez: quien
 * busca "Ana" busca lo que hizo, y quien busca "taxi" busca los movimientos de
 * taxis. El nombre del tipo entra en la busqueda, asi que el filtro por tipo
 * que pide la spec §5.8 se puede escribir a mano ademas del chip.
 */
public class SuperadminBitacoraViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<LogEntry>>> contenido = new MutableLiveData<>();

    private List<LogEntry> todos = Collections.emptyList();

    @Nullable
    private String busqueda;

    /** Si el filtro enseña solo los movimientos que cambiaron algo. */
    private boolean soloCambios;

    private boolean cargando;

    public LiveData<UiState<List<LogEntry>>> getContenido() {
        return contenido;
    }

    public boolean isSoloCambios() {
        return soloCambios;
    }

    /** Si hay algo escrito o marcado que pueda estar escondiendo movimientos. */
    public boolean isFiltrando() {
        return soloCambios || busqueda != null;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<LogEntry>> actual = contenido.getValue();
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
     * Recarga sin esqueleto, al volver a la pantalla.
     *
     * <p>Hace falta porque la barra inferior guarda el estado de cada seccion:
     * volver a Auditoria devuelve el mismo ViewModel con la lista de la ultima
     * visita, y sin esto el superadministrador no veria lo que acaba de hacer
     * —retirar un hotel, desactivar una cuenta— hasta cerrar la aplicacion.
     *
     * <p>El filtro no se toca: se vuelve a pedir y se vuelve a aplicar, que es
     * lo que el usuario dejo puesto.
     */
    public void refrescarEnSilencio() {
        UiState<List<LogEntry>> actual = contenido.getValue();
        if (cargando || actual == null || !(actual.isSuccess() || actual.isEmpty())) {
            return;
        }
        cargando = true;
        pedir();
    }

    private void pedir() {
        ServiceLocator.superadmin().bitacora(new ResultCallback<List<LogEntry>>() {
            @Override
            public void onExito(@NonNull List<LogEntry> datos) {
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

    public void buscar(@Nullable String texto) {
        busqueda = texto == null || texto.trim().isEmpty() ? null : texto.trim().toLowerCase();
        publicar();
    }

    public void filtrarSoloCambios(boolean soloCambios) {
        this.soloCambios = soloCambios;
        publicar();
    }

    private void publicar() {
        List<LogEntry> visibles = new ArrayList<>();
        for (LogEntry evento : todos) {
            if (soloCambios && !esCambio(evento)) {
                continue;
            }
            if (busqueda != null && !coincide(evento, busqueda)) {
                continue;
            }
            visibles.add(evento);
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<LogEntry>>empty()
                : UiState.success(visibles));
    }

    /**
     * Si el movimiento cambio algo del sistema.
     *
     * <p>Son las acciones que alguien con privilegios tomo sobre la plataforma,
     * que es lo que se viene a auditar; los inicios de sesion y las reservas se
     * consultan, pero no se vigilan.
     */
    private static boolean esCambio(@NonNull LogEntry evento) {
        switch (evento.getEvento()) {
            case ACTIVACION:
            case DESACTIVACION:
            case APROBACION:
            case ACCION_ADMINISTRATIVA:
                return true;
            default:
                return false;
        }
    }

    private static boolean coincide(@NonNull LogEntry evento, @NonNull String buscado) {
        return evento.getDetalle().toLowerCase().contains(buscado)
                || evento.getUsuario().toLowerCase().contains(buscado)
                || evento.getEvento().getDisplayName().toLowerCase().contains(buscado);
    }
}
