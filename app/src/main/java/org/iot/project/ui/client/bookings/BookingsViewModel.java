package org.iot.project.ui.client.bookings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Estado de "Mis reservas" (§30 a §32).
 *
 * <p>Las reservas se reparten en tres bloques porque cada uno responde a una
 * pregunta distinta: qué está pasando ahora (en curso), qué viene (próximas) y
 * qué ya pasó (historial). Una sola lista ordenada por fecha obligaría a
 * recorrerla entera para encontrar la estadía en la que estás.
 */
public class BookingsViewModel extends ViewModel {

    /** Las tres secciones de la pantalla. Cada una puede estar vacía. */
    public static class Secciones {

        public final List<Booking> enCurso;
        public final List<Booking> proximas;
        public final List<Booking> historial;

        Secciones(@NonNull List<Booking> enCurso, @NonNull List<Booking> proximas,
                  @NonNull List<Booking> historial) {
            this.enCurso = enCurso;
            this.proximas = proximas;
            this.historial = historial;
        }

        public boolean estaVacio() {
            return enCurso.isEmpty() && proximas.isEmpty() && historial.isEmpty();
        }
    }

    private final MutableLiveData<UiState<Secciones>> secciones = new MutableLiveData<>();
    private boolean cargado;
    private boolean cargando;
    private String cuentaId;
    private long generacion;

    public LiveData<UiState<Secciones>> getSecciones() {
        return secciones;
    }

    public void cargar() {
        if (cargado && Objects.equals(cuentaId, SessionManager.getUsuarioId())) {
            return;
        }
        recargar();
    }

    public void recargar() {
        if (cargando && Objects.equals(cuentaId, SessionManager.getUsuarioId())) return;
        cargado = true;
        secciones.setValue(UiState.loading());
        pedir();
    }

    /**
     * Recarga sin pasar por el esqueleto.
     *
     * <p>Se usa al volver a la pantalla, por ejemplo después de crear una
     * reserva: la lista ya está pintada y hacerla desaparecer para volver a
     * aparecer es peor que actualizarla en el sitio.
     */
    public void refrescarEnSilencio() {
        if (!cargado) {
            recargar();
            return;
        }
        pedir();
    }

    /** Conserva la lista visible; una respuesta anterior ya no sustituye la próxima recarga. */
    public void suspender() {
        generacion++;
        cargando = false;
    }

    private void pedir() {
        String uid = SessionManager.getUsuarioId();
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            generacion++;
            cargando = false;
            cuentaId = null;
            secciones.setValue(UiState.error("Inicia sesión como cliente para ver tus reservas."));
            return;
        }
        if (cargando && uid.equals(cuentaId)) return;
        cuentaId = uid;
        long solicitud = ++generacion;
        cargando = true;
        ServiceLocator.reservas().reservasDe(uid,
                new ResultCallback<List<Booking>>() {
                    @Override
                    public void onExito(@NonNull List<Booking> datos) {
                        if (!vigente(uid, solicitud)) return;
                        cargando = false;
                        Secciones agrupadas = agrupar(datos);
                        secciones.setValue(agrupadas.estaVacio()
                                ? UiState.empty() : UiState.success(agrupadas));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (!vigente(uid, solicitud)) return;
                        cargando = false;
                        secciones.setValue(UiState.error(mensaje));
                    }
                });
    }

    private boolean vigente(String uid, long solicitud) {
        return solicitud == generacion && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    @Override protected void onCleared() {
        generacion++;
        super.onCleared();
    }

    private Secciones agrupar(@NonNull List<Booking> reservas) {
        List<Booking> enCurso = new ArrayList<>();
        List<Booking> proximas = new ArrayList<>();
        List<Booking> historial = new ArrayList<>();

        for (Booking reserva : reservas) {
            BookingStatus estado = reserva.getEstado();
            switch (estado) {
                case ACTIVA:
                    enCurso.add(reserva);
                    break;
                case PENDIENTE:
                case CONFIRMADA:
                    proximas.add(reserva);
                    break;
                case FINALIZADA:
                case CANCELADA:
                default:
                    historial.add(reserva);
                    break;
            }
        }

        // Lo que viene, de lo más cercano a lo más lejano; lo que pasó, al revés:
        // el historial se lee desde lo último.
        proximas.sort(Comparator.comparing(Booking::getFechaEntrada));
        historial.sort(Comparator.comparing(Booking::getFechaEntrada).reversed());
        return new Secciones(enCurso, proximas, historial);
    }
}
