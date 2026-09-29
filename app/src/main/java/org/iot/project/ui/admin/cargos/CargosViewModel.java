package org.iot.project.ui.admin.cargos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.CargosDeReserva;

import java.util.List;

/**
 * Estado de la lista de cobros del hotel (§44, RF-051 a RF-054).
 *
 * <p>La consulta ya devuelve los cobros agrupados por estadía y con el cliente
 * resuelto; aquí solo se suman, porque el total del encabezado es la única
 * cuenta que la pantalla hace.
 *
 * <p>El total se calcula una vez por carga y no en cada pintado: es una suma
 * sobre todos los cobros del hotel, y repetirla cada vez que una fila se
 * recicla sería trabajo que nadie pidió.
 */
public class CargosViewModel extends ViewModel {

    /** Lo que la pantalla pinta: las estadías con cobros y las cuentas de arriba. */
    public static class Cobros {

        public final List<CargosDeReserva> grupos;

        /** Cuántos cobros hay en total, sumando los de todas las estadías. */
        public final int numCargos;

        /** Lo que suman todos los cobros del hotel. */
        public final double total;

        Cobros(@NonNull List<CargosDeReserva> grupos) {
            this.grupos = grupos;

            int cuantos = 0;
            double suma = 0d;
            for (CargosDeReserva grupo : grupos) {
                cuantos += grupo.getCargos().size();
                suma += grupo.getTotal();
            }
            this.numCargos = cuantos;
            this.total = suma;
        }
    }

    private final MutableLiveData<UiState<Cobros>> cobros = new MutableLiveData<>();

    private boolean cargado;

    public LiveData<UiState<Cobros>> getCobros() {
        return cobros;
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
            cobros.setValue(UiState.<Cobros>error("Todavía no tienes un hotel asignado."));
            return;
        }
        cargado = true;
        cobros.setValue(UiState.<Cobros>loading());
        pedir(hotelId);
    }

    /**
     * Recarga sin volver a pasar por el esqueleto.
     *
     * <p>Se usa al volver del detalle de una reserva, donde se pudo haber
     * registrado un cobro: la lista ya está pintada y hacerla desaparecer para
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
        ServiceLocator.gestion().cargos(hotelId,
                new ResultCallback<List<CargosDeReserva>>() {
                    @Override
                    public void onExito(@NonNull List<CargosDeReserva> datos) {
                        cobros.setValue(datos.isEmpty()
                                ? UiState.<Cobros>empty() : UiState.success(new Cobros(datos)));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        cobros.setValue(UiState.<Cobros>error(mensaje));
                    }
                });
    }
}
