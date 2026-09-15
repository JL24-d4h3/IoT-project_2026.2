package org.iot.project.ui.admin.reservas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.ReservaDeHotel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Estado de la lista de reservas del hotel (§44, RF-041).
 *
 * <p>Se reparte en los mismos tres bloques que "Mis reservas" del cliente —en
 * curso, próximas e historial— porque son las mismas tres preguntas: quién está
 * ahora, quién viene y qué pasó. Que el administrador y el huésped vean la misma
 * reserva repartida igual es lo que hace que puedan hablar de ella sin
 * traducirse.
 *
 * <p>La consulta ya devuelve el cruce hecho: aquí no se resuelve ningún nombre
 * de cliente ni número de habitación, solo se agrupa.
 */
public class ReservasAdminViewModel extends ViewModel {

    /** Los tres bloques de la pantalla. Cada uno puede estar vacío. */
    public static class Secciones {

        public final List<ReservaDeHotel> enCurso;
        public final List<ReservaDeHotel> proximas;
        public final List<ReservaDeHotel> historial;

        Secciones(@NonNull List<ReservaDeHotel> enCurso,
                  @NonNull List<ReservaDeHotel> proximas,
                  @NonNull List<ReservaDeHotel> historial) {
            this.enCurso = enCurso;
            this.proximas = proximas;
            this.historial = historial;
        }

        public boolean estaVacio() {
            return enCurso.isEmpty() && proximas.isEmpty() && historial.isEmpty();
        }
    }

    private final MutableLiveData<UiState<Secciones>> secciones = new MutableLiveData<>();

    /**
     * La reserva que se acaba de modificar desde la pantalla, para avisar.
     *
     * <p>Cancelar es la única operación que se hace desde la lista, y al
     * terminar hay que recargarla: la reserva cambia de bloque y quedarse en el
     * suyo dejaría "en curso" una estadía cancelada.
     */
    private final MutableLiveData<UiState<ReservaDeHotel>> operacion = new MutableLiveData<>();

    private boolean cargado;

    public LiveData<UiState<Secciones>> getSecciones() {
        return secciones;
    }

    public LiveData<UiState<ReservaDeHotel>> getOperacion() {
        return operacion;
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    public void cargar() {
        if (cargado) {
            return;
        }
        recargar();
    }

    public void recargar() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            secciones.setValue(UiState.<Secciones>error(
                    "Esta cuenta no tiene un hotel asignado."));
            return;
        }
        cargado = true;
        secciones.setValue(UiState.<Secciones>loading());
        pedir(hotelId);
    }

    /**
     * Recarga sin volver a pasar por el esqueleto.
     *
     * <p>Se usa al volver del detalle de una reserva, donde se pudo haber
     * cerrado la estadía: la lista ya está pintada y hacerla desaparecer para
     * volver a aparecer es peor que actualizarla en el sitio.
     */
    public void refrescarEnSilencio() {
        String hotelId = getHotelId();
        if (!cargado || hotelId == null) {
            recargar();
            return;
        }
        pedir(hotelId);
    }

    private void pedir(@NonNull String hotelId) {
        ServiceLocator.gestion().reservas(hotelId,
                new ResultCallback<List<ReservaDeHotel>>() {
                    @Override
                    public void onExito(@NonNull List<ReservaDeHotel> datos) {
                        Secciones agrupadas = agrupar(datos);
                        secciones.setValue(agrupadas.estaVacio()
                                ? UiState.<Secciones>empty() : UiState.success(agrupadas));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        secciones.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Limpia el resultado de la operación al terminar de atenderlo. */
    public void limpiarOperacion() {
        operacion.setValue(null);
    }

    private Secciones agrupar(@NonNull List<ReservaDeHotel> reservas) {
        List<ReservaDeHotel> enCurso = new ArrayList<>();
        List<ReservaDeHotel> proximas = new ArrayList<>();
        List<ReservaDeHotel> historial = new ArrayList<>();

        for (ReservaDeHotel reserva : reservas) {
            // El estado del cruce, que es el mismo que pinta la fila: leerlo de
            // la reserva viva podría mandar una reserva a una sección distinta
            // de la que enseña su propia tarjeta.
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

        // Lo que viene, de lo más cercano a lo más lejano; lo que pasó, al
        // revés: el historial se lee desde lo último. Las que están en curso no
        // se reordenan porque el repositorio ya las trae por fecha de entrada y
        // ese orden es el que el administrador espera al bajar la lista.
        proximas.sort(Comparator.comparing(
                (ReservaDeHotel reserva) -> reserva.getReserva().getFechaEntrada()));
        historial.sort(Comparator.comparing(
                (ReservaDeHotel reserva) -> reserva.getReserva().getFechaEntrada()).reversed());
        return new Secciones(enCurso, proximas, historial);
    }
}
