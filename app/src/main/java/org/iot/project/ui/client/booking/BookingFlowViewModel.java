package org.iot.project.ui.client.booking;

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
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.Room;

import java.time.LocalDate;
import java.util.List;

/**
 * Estado de la pantalla de reserva: qué se reserva, para qué fechas y cuántos
 * huéspedes.
 *
 * <p>Las fechas propuestas no son "mañana" sin más. El cliente no puede tener
 * dos estadías cruzadas (§22) y MockData ya le puso una en curso, así que la
 * pantalla busca el primer hueco libre de verdad. Sin eso, la primera reserva
 * de la demo fallaría siempre por una regla que el usuario no ve.
 */
public class BookingFlowViewModel extends ViewModel {

    private static final int NOCHES_PROPUESTAS = 2;

    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<Room> habitacion = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> entrada = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> salida = new MutableLiveData<>();
    private final MutableLiveData<Integer> huespedes = new MutableLiveData<>(1);
    private final MutableLiveData<UiState<Booking>> creacion = new MutableLiveData<>();

    private String hotelId;
    private String roomId;

    public LiveData<UiState<Hotel>> getHotel() {
        return hotel;
    }

    public LiveData<Room> getHabitacion() {
        return habitacion;
    }

    public LiveData<LocalDate> getEntrada() {
        return entrada;
    }

    public LiveData<LocalDate> getSalida() {
        return salida;
    }

    public LiveData<Integer> getHuespedes() {
        return huespedes;
    }

    public LiveData<UiState<Booking>> getCreacion() {
        return creacion;
    }

    public void cargar(@NonNull String hotelId, @NonNull String roomId) {
        if (hotelId.equals(this.hotelId) && roomId.equals(this.roomId)
                && hotel.getValue() != null && hotel.getValue().isSuccess()) {
            return;
        }
        this.hotelId = hotelId;
        this.roomId = roomId;
        hotel.setValue(UiState.loading());
        pedirHotel();
        pedirFechasLibres();
    }

    private void pedirHotel() {
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                Room elegida = buscarHabitacion(datos);
                if (elegida == null) {
                    hotel.setValue(UiState.error(
                            "Esta habitación ya no está publicada en el alojamiento."));
                    return;
                }
                habitacion.setValue(elegida);
                // Un huésped como mínimo y nunca más que la capacidad: el
                // contador no puede ofrecer algo que la habitación no admite.
                huespedes.setValue(Math.max(1, Math.min(
                        valorDe(huespedes), elegida.getCapacidadTotal())));
                hotel.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                hotel.setValue(UiState.error(mensaje));
            }
        });
    }

    @Nullable
    private Room buscarHabitacion(@NonNull Hotel datos) {
        for (Room cuarto : datos.getHabitaciones()) {
            if (cuarto.getId().equals(roomId)) {
                return cuarto;
            }
        }
        return null;
    }

    /** Primer hueco de dos noches que no se cruce con otra estadía del cliente. */
    private void pedirFechasLibres() {
        ServiceLocator.reservas().reservasDe(SessionManager.getUsuarioIdSeguro(),
                new ResultCallback<List<Booking>>() {
                    @Override
                    public void onExito(@NonNull List<Booking> datos) {
                        LocalDate desde = LocalDate.now().plusDays(1);
                        while (seCruza(desde, desde.plusDays(NOCHES_PROPUESTAS), datos)) {
                            desde = desde.plusDays(1);
                        }
                        entrada.setValue(desde);
                        salida.setValue(desde.plusDays(NOCHES_PROPUESTAS));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        // Sin el historial no se puede prometer que el hueco esté
                        // libre; se propone igual y el repositorio decidirá.
                        entrada.setValue(LocalDate.now().plusDays(1));
                        salida.setValue(LocalDate.now().plusDays(NOCHES_PROPUESTAS));
                    }
                });
    }

    private boolean seCruza(@NonNull LocalDate desde, @NonNull LocalDate hasta,
                            @NonNull List<Booking> reservas) {
        for (Booking reserva : reservas) {
            if (reserva.getEstado() == BookingStatus.CANCELADA) {
                continue;
            }
            if (desde.isBefore(reserva.getFechaSalida())
                    && reserva.getFechaEntrada().isBefore(hasta)) {
                return true;
            }
        }
        return false;
    }

    public void setFechas(@NonNull LocalDate nuevaEntrada, @NonNull LocalDate nuevaSalida) {
        entrada.setValue(nuevaEntrada);
        salida.setValue(nuevaSalida);
    }

    public void setHuespedes(int cantidad) {
        Room cuarto = habitacion.getValue();
        int maximo = cuarto != null ? cuarto.getCapacidadTotal() : 1;
        huespedes.setValue(Math.max(1, Math.min(cantidad, maximo)));
    }

    public int getHuespedesActuales() {
        return valorDe(huespedes);
    }

    private int valorDe(@Nullable LiveData<Integer> dato) {
        Integer valor = dato != null ? dato.getValue() : null;
        return valor != null ? valor : 1;
    }

    public long getNumNoches() {
        LocalDate desde = entrada.getValue();
        LocalDate hasta = salida.getValue();
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return 0;
        }
        return hasta.toEpochDay() - desde.toEpochDay();
    }

    public double getTotal() {
        Room cuarto = habitacion.getValue();
        return cuarto == null ? 0 : cuarto.getPrecioNoche() * getNumNoches();
    }

    public boolean puedeConfirmar() {
        return hotel.getValue() != null && hotel.getValue().isSuccess()
                && habitacion.getValue() != null
                && getNumNoches() > 0;
    }

    /** Crea la reserva. Nace pendiente: se confirma al pagar (RF-045). */
    public void confirmar() {
        Room cuarto = habitacion.getValue();
        LocalDate desde = entrada.getValue();
        LocalDate hasta = salida.getValue();
        if (cuarto == null || desde == null || hasta == null) {
            return;
        }

        creacion.setValue(UiState.loading());
        Booking borrador = new Booking("_borrador", "_borrador", hotelId, roomId,
                SessionManager.getUsuarioIdSeguro(), desde, hasta,
                valorDe(huespedes), cuarto.getPrecioNoche(), BookingStatus.PENDIENTE);

        ServiceLocator.reservas().crear(borrador, new ResultCallback<Booking>() {
            @Override
            public void onExito(@NonNull Booking datos) {
                creacion.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                creacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void limpiarCreacion() {
        creacion.setValue(null);
    }
}
