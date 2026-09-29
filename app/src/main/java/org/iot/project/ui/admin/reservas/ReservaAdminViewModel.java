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
import org.iot.project.models.Booking;
import org.iot.project.models.Charge;
import org.iot.project.models.ReservaDeHotel;

/**
 * Estado del detalle de una reserva del hotel (§44, RF-041 a RF-054).
 *
 * <p>Vive en el grafo del administrador y no en el fragmento porque la hoja de
 * cobro tambien tiene que alcanzarlo: el cobro cambia el total que la pantalla
 * tiene delante, y quien lo suma es esta, no la hoja. Un estado por fragmento
 * obligaria a pasar el resultado de una a otra a mano.
 *
 * <p>Por eso mismo {@link #cargar} lleva el identificador y recuerda cual esta
 * cargado: el ViewModel sobrevive a la navegacion, y sin esa comprobacion
 * abrir una segunda reserva mostraria durante un instante los datos de la
 * primera.
 */
public class ReservaAdminViewModel extends ViewModel {

    /** La reserva abierta, con su huesped y su habitacion ya resueltos. */
    private final MutableLiveData<UiState<ReservaDeHotel>> reserva = new MutableLiveData<>();

    /**
     * Resultado de cancelar o de cobrar el total, que son las dos operaciones
     * que se piden desde esta pantalla.
     *
     * <p>Va aparte de {@link #reserva} porque las dos cosas se atienden distinto:
     * una operacion fallida tiene que dejar la pantalla como estaba y avisar,
     * mientras que una recarga fallida reemplaza el contenido por el estado de
     * error. Mezclarlas haria que un cobro rechazado —sin tarjeta, por
     * ejemplo— borrara la reserva de la pantalla.
     */
    private final MutableLiveData<UiState<Booking>> operacion = new MutableLiveData<>();

    /**
     * Resultado de registrar un cobro adicional (RF-051).
     *
     * <p>Va aparte de {@link #operacion} y no es duplicacion: la hoja de cobro
     * se abre desde esta misma pantalla, y las dos observan. Si compartieran
     * estado, la que atendiera primero limpiaria el resultado —al atenderlo,
     * {@link #limpiarOperacion} pone null— y la otra se quedaria sin enterarse:
     * la hoja no se cerraria nunca o la pantalla avisaria dos veces del mismo
     * cobro. Es el mismo motivo por el que la gestion de habitaciones separa
     * "guardado" de "operacion".
     */
    private final MutableLiveData<UiState<Booking>> cargo = new MutableLiveData<>();

    /** Identificador de la reserva ya cargada, para no repetir la consulta. */
    @Nullable
    private String cargada;

    public LiveData<UiState<ReservaDeHotel>> getReserva() {
        return reserva;
    }

    public LiveData<UiState<Booking>> getOperacion() {
        return operacion;
    }

    public LiveData<UiState<Booking>> getCargo() {
        return cargo;
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    /**
     * Carga la reserva indicada, o no hace nada si ya es la que esta abierta.
     *
     * <p>La segunda mitad importa: al girar la pantalla el fragmento se
     * reconstruye y vuelve a pedir, y sin esta comprobacion cada giro lanzaria
     * una consulta y devolveria la pantalla al esqueleto.
     */
    public void cargar(@NonNull String bookingId) {
        if (bookingId.equals(cargada)) {
            return;
        }
        cargada = bookingId;
        recargar();
    }

    /** Vuelve a pedir la reserva abierta, sin condicion. */
    public void recargar() {
        pedir(true);
    }

    /**
     * Pide la reserva abierta.
     *
     * @param conEsqueleto si la pantalla debe volver al esqueleto mientras llega.
     *                     Se pide sin el despues de una operacion que ya salio
     *                     bien —registrar un cobro, cancelar—: la pantalla esta
     *                     pintada y hacerla desaparecer para volver a aparecer
     *                     cuesta mas de lo que informa.
     */
    private void pedir(boolean conEsqueleto) {
        if (cargada == null) {
            return;
        }
        String hotelId = getHotelId();
        if (hotelId == null) {
            reserva.setValue(UiState.<ReservaDeHotel>error(
                    "Todavía no tienes un hotel asignado."));
            return;
        }
        if (conEsqueleto) {
            reserva.setValue(UiState.<ReservaDeHotel>loading());
        }
        ServiceLocator.gestion().reserva(hotelId, cargada,
                new ResultCallback<ReservaDeHotel>() {
                    @Override
                    public void onExito(@NonNull ReservaDeHotel datos) {
                        reserva.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        reserva.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Registra un consumo o un dano en la reserva abierta (RF-051, RF-052).
     *
     * <p>Al terminar se recarga: el cargo mueve los subtotales y el total, y
     * sumarlos otra vez en la pantalla seria repetir el calculo del modelo
     * ({@code Booking.getTotal()}) en un sitio donde ya nadie lo comprueba.
     */
    public void agregarCargo(@NonNull Charge nuevo) {
        if (cargada == null) {
            return;
        }
        this.cargo.setValue(UiState.<Booking>loading());
        ServiceLocator.reservas().agregarCargo(cargada, nuevo,
                new ResultCallback<Booking>() {
                    @Override
                    public void onExito(@NonNull Booking datos) {
                        cargo.setValue(UiState.success(datos));
                        pedir(false);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        cargo.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Cancela la reserva abierta.
     *
     * <p>El administrador puede hacerlo igual que el cliente (RC-013), y el
     * repositorio comprueba que este hotel sea el que la aloja antes de tocar
     * nada: la pantalla solo decide si ofrece el boton.
     */
    public void cancelar() {
        if (cargada == null) {
            return;
        }
        operacion.setValue(UiState.<Booking>loading());
        ServiceLocator.reservas().cancelar(cargada, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                operacion.setValue(UiState.success(datos));
                pedir(false);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                operacion.setValue(UiState.error(mensaje));
            }
        });
    }

    /**
     * Cobra el total a la tarjeta que el huésped registro al reservar (RF-049).
     *
     * <p>Solo despues del checkout, que es cuando el total ya no puede crecer.
     * El repositorio lo comprueba igual; aqui no se repite la regla, se confia
     * en que la pantalla solo ofrezca el boton cuando tiene sentido.
     */
    public void cobrar() {
        if (cargada == null) {
            return;
        }
        operacion.setValue(UiState.<Booking>loading());
        ServiceLocator.reservas().cobrar(cargada, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                operacion.setValue(UiState.success(datos));
                pedir(false);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                operacion.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Limpia el resultado de la operacion al terminar de atenderlo. */
    public void limpiarOperacion() {
        operacion.setValue(null);
    }

    /** Limpia el resultado del ultimo cobro, al cerrarse la hoja que lo registro. */
    public void limpiarCargo() {
        cargo.setValue(null);
    }
}
