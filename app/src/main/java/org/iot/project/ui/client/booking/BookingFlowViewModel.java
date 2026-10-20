package org.iot.project.ui.client.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.Role;
import org.iot.project.models.Room;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Formulario de reserva con borrador estable incluso al recrear el proceso. */
public class BookingFlowViewModel extends ViewModel {

    private static final int MAX_NOCHES = 90;
    private final SavedStateHandle estado;
    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<Room> habitacion = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> entrada = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> salida = new MutableLiveData<>();
    private final MutableLiveData<Integer> huespedes = new MutableLiveData<>(1);
    private final MutableLiveData<UiState<Booking>> creacion = new MutableLiveData<>();
    private final MutableLiveData<UiState<Boolean>> disponibilidad = new MutableLiveData<>();
    private String hotelId;
    private String roomId;
    private String cuentaId;
    private long generacion;
    private long consultaCalendario;
    private boolean cargando;
    private boolean guardando;

    public BookingFlowViewModel(@NonNull SavedStateHandle estado) {
        this.estado = estado;
    }

    public LiveData<UiState<Hotel>> getHotel() { return hotel; }
    public LiveData<Room> getHabitacion() { return habitacion; }
    public LiveData<LocalDate> getEntrada() { return entrada; }
    public LiveData<LocalDate> getSalida() { return salida; }
    public LiveData<Integer> getHuespedes() { return huespedes; }
    public LiveData<UiState<Booking>> getCreacion() { return creacion; }
    public LiveData<UiState<Boolean>> getDisponibilidad() { return disponibilidad; }

    public void cargar(@NonNull String hotelId, @NonNull String roomId) {
        cargar(hotelId, roomId, null, null);
    }

    public void cargar(@NonNull String hotelId, @NonNull String roomId,
                       @Nullable LocalDate entradaInicial, @Nullable LocalDate salidaInicial) {
        String uid = SessionManager.getUsuarioId();
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            hotel.setValue(UiState.error("Inicia sesión como cliente para reservar."));
            return;
        }
        if (Objects.equals(cuentaId, uid) && hotelId.equals(this.hotelId)
                && roomId.equals(this.roomId)
                && (cargando || guardando || (hotel.getValue() != null
                && hotel.getValue().isSuccess()))) return;

        boolean mismoBorrador = uid.equals(estado.get("reservaUid"))
                && hotelId.equals(estado.get("reservaHotel"))
                && roomId.equals(estado.get("reservaHabitacion"));
        if (!mismoBorrador) {
            estado.set("reservaUid", uid);
            estado.set("reservaHotel", hotelId);
            estado.set("reservaHabitacion", roomId);
            estado.set("reservaBorradorId", UUID.randomUUID().toString());
            estado.remove("reservaCreadaId");
            estado.remove("reservaEntrada");
            estado.remove("reservaSalida");
            estado.remove("reservaHuespedes");
            estado.remove("reservaIntentada");
            estado.remove("reservaIntentoFirma");
        }
        if (estado.get("reservaBorradorId") == null) {
            estado.set("reservaBorradorId", UUID.randomUUID().toString());
        }
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.cuentaId = uid;
        consultaCalendario++;
        disponibilidad.setValue(UiState.loading());
        guardando = false;
        creacion.setValue(null);
        Long diaEntrada = estado.get("reservaEntrada");
        Long diaSalida = estado.get("reservaSalida");
        boolean fechasInicialesValidas = errorFechas(entradaInicial, salidaInicial) == null;
        LocalDate desde = diaEntrada != null ? LocalDate.ofEpochDay(diaEntrada)
                : fechasInicialesValidas ? entradaInicial : LocalDate.now().plusDays(1);
        LocalDate hasta = diaSalida != null ? LocalDate.ofEpochDay(diaSalida)
                : fechasInicialesValidas ? salidaInicial : desde.plusDays(2);
        guardarFechas(desde, hasta);
        Integer cantidad = estado.get("reservaHuespedes");
        huespedes.setValue(cantidad != null ? cantidad : 1);
        long solicitud = ++generacion;
        cargando = true;
        habitacion.setValue(null);
        hotel.setValue(UiState.loading());
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override public void onExito(@NonNull Hotel datos) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                Room elegida = buscarHabitacion(datos);
                if (!datos.isPublicado() || elegida == null || !elegida.isDisponible()) {
                    hotel.setValue(UiState.error(
                            "Esta habitación ya no está disponible en el alojamiento."));
                    return;
                }
                habitacion.setValue(elegida);
                setHuespedes(getHuespedesActuales());
                hotel.setValue(UiState.success(datos));
                consultarDisponibilidad();
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                hotel.setValue(UiState.error(mensaje));
            }
        });
        String creadaId = estado.get("reservaCreadaId");
        if (creadaId != null) recuperarCreada(creadaId, uid, solicitud);
        else if (Boolean.TRUE.equals(estado.get("reservaIntentada"))) {
            recuperarIntento(uid, solicitud);
        }
    }

    private void recuperarCreada(String id, String uid, long solicitud) {
        creacion.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(id, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (vigente(uid, solicitud)) creacion.setValue(UiState.success(datos));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(uid, solicitud)) creacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void refrescarReservaCreada() {
        String id = estado.get("reservaCreadaId");
        if (id != null && cuentaId != null && !estaConfirmando()
                && cuentaId.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE) {
            recuperarCreada(id, cuentaId, generacion);
        }
    }

    private void recuperarIntento(String uid, long solicitud) {
        String id = estado.get("reservaBorradorId");
        if (id == null) return;
        creacion.setValue(UiState.loading());
        ServiceLocator.reservas().obtener(id, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (!vigente(uid, solicitud)) return;
                estado.set("reservaCreadaId", datos.getId());
                estado.remove("reservaIntentada");
                creacion.setValue(UiState.success(datos));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                creacion.setValue(UiState.error(
                        "No pudimos confirmar el intento anterior. Puedes reintentar la misma "
                                + "solicitud o consultar Mis reservas."));
            }
        });
    }

    /** Si se perdió la respuesta, la transacción recupera el mismo ID sin reservar dos veces. */
    public boolean esReintentoMismoBorrador() {
        return Boolean.TRUE.equals(estado.get("reservaIntentada"))
                && firmaActual().equals(estado.get("reservaIntentoFirma"));
    }

    private String firmaActual() {
        Room cuarto = habitacion.getValue();
        return hotelId + "|" + roomId + "|" + entrada.getValue() + "|" + salida.getValue()
                + "|" + getHuespedesActuales() + "|"
                + (cuarto != null ? Double.toString(cuarto.getPrecioNoche()) : "");
    }

    public void reintentarDisponibilidad() {
        if (!estaConfirmando() && !reservaCreada()) consultarDisponibilidad();
    }

    private void consultarDisponibilidad() {
        if (reservaCreada() || guardando) return;
        LocalDate desde = entrada.getValue();
        LocalDate hasta = salida.getValue();
        String error = errorFechas(desde, hasta);
        if (error != null) {
            disponibilidad.setValue(UiState.error(error));
            return;
        }
        UiState<Hotel> actual = hotel.getValue();
        if (actual == null || !actual.isSuccess() || cuentaId == null) return;
        String uid = cuentaId;
        long solicitud = generacion;
        long calendario = ++consultaCalendario;
        disponibilidad.setValue(UiState.loading());
        ServiceLocator.hoteles().habitacionesDisponibles(hotelId, desde, hasta,
                new ResultCallback<List<Room>>() {
                    @Override public void onExito(@NonNull List<Room> libres) {
                        if (!calendarioVigente(uid, solicitud, calendario, desde, hasta)) return;
                        boolean libre = false;
                        for (Room cuarto : libres) {
                            if (roomId.equals(cuarto.getId()) && cuarto.isDisponible()) {
                                libre = true;
                                break;
                            }
                        }
                        disponibilidad.setValue(UiState.success(libre));
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!calendarioVigente(uid, solicitud, calendario, desde, hasta)) return;
                        disponibilidad.setValue(UiState.error(mensaje));
                    }
                });
    }

    private boolean calendarioVigente(String uid, long solicitud, long calendario,
                                      LocalDate desde, LocalDate hasta) {
        return vigente(uid, solicitud) && calendario == consultaCalendario
                && desde.equals(entrada.getValue()) && hasta.equals(salida.getValue());
    }

    public boolean puedeRecuperarCreada() {
        return estado.get("reservaCreadaId") != null;
    }

    public void reintentarCarga() {
        if (hotelId == null || roomId == null || estaConfirmando()) return;
        String creadaId = estado.get("reservaCreadaId");
        if (creadaId != null && Objects.equals(cuentaId, SessionManager.getUsuarioId())) {
            recuperarCreada(creadaId, cuentaId, generacion);
        } else {
            cargar(hotelId, roomId);
        }
    }

    @Nullable private Room buscarHabitacion(Hotel datos) {
        for (Room cuarto : datos.getHabitaciones()) {
            if (cuarto.getId().equals(roomId)) return cuarto;
        }
        return null;
    }

    public void setFechas(@NonNull LocalDate desde, @NonNull LocalDate hasta) {
        if (estaConfirmando() || reservaCreada()) return;
        String error = errorFechas(desde, hasta);
        if (error != null) {
            creacion.setValue(UiState.error(error));
            return;
        }
        consultaCalendario++;
        disponibilidad.setValue(UiState.loading());
        guardarFechas(desde, hasta);
        limpiarCreacion();
        consultarDisponibilidad();
    }

    private void guardarFechas(LocalDate desde, LocalDate hasta) {
        estado.set("reservaEntrada", desde.toEpochDay());
        estado.set("reservaSalida", hasta.toEpochDay());
        entrada.setValue(desde);
        salida.setValue(hasta);
    }

    public void setHuespedes(int cantidad) {
        if (estaConfirmando() || reservaCreada()) return;
        Room cuarto = habitacion.getValue();
        int maximo = cuarto != null ? cuarto.getCapacidadTotal() : 1;
        int numero = Math.max(1, Math.min(cantidad, maximo));
        estado.set("reservaHuespedes", numero);
        huespedes.setValue(numero);
    }

    public int getHuespedesActuales() {
        Integer cantidad = huespedes.getValue();
        return cantidad != null ? cantidad : 1;
    }

    public long getNumNoches() {
        LocalDate desde = entrada.getValue();
        LocalDate hasta = salida.getValue();
        return desde != null && hasta != null && hasta.isAfter(desde)
                ? hasta.toEpochDay() - desde.toEpochDay() : 0;
    }

    public double getTotal() {
        Room cuarto = habitacion.getValue();
        return cuarto != null ? cuarto.getPrecioNoche() * getNumNoches() : 0;
    }

    public boolean estaConfirmando() {
        UiState<Booking> actual = creacion.getValue();
        return guardando || (actual != null && actual.isLoading());
    }

    private boolean reservaCreada() {
        UiState<Booking> actual = creacion.getValue();
        return estado.get("reservaCreadaId") != null || (actual != null && actual.isSuccess());
    }

    public boolean puedeConfirmar() {
        Room cuarto = habitacion.getValue();
        UiState<Boolean> calendario = disponibilidad.getValue();
        return !estaConfirmando() && !reservaCreada()
                && Objects.equals(cuentaId, SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE
                && hotel.getValue() != null && hotel.getValue().isSuccess()
                && cuarto != null && cuarto.isDisponible()
                && ((calendario != null && calendario.isSuccess() && calendario.requireData())
                || esReintentoMismoBorrador())
                && getHuespedesActuales() >= 1
                && getHuespedesActuales() <= cuarto.getCapacidadTotal()
                && errorFechas(entrada.getValue(), salida.getValue()) == null;
    }

    @Nullable private String errorFechas(@Nullable LocalDate desde, @Nullable LocalDate hasta) {
        if (desde == null || hasta == null) return "Elige las fechas de entrada y salida.";
        if (desde.isBefore(LocalDate.now())) return "La entrada no puede ser anterior a hoy.";
        if (!hasta.isAfter(desde)) return "La salida debe ser posterior a la entrada.";
        if (hasta.toEpochDay() - desde.toEpochDay() > MAX_NOCHES) {
            return "Puedes reservar hasta 90 noches por operación.";
        }
        return null;
    }

    /** El repositorio confirma inventario y precio dentro de la transacción. */
    public void confirmar() {
        if (!puedeConfirmar()) return;
        Room cuarto = habitacion.getValue();
        LocalDate desde = entrada.getValue();
        LocalDate hasta = salida.getValue();
        if (cuarto == null || desde == null || hasta == null) return;
        String uid = cuentaId;
        long solicitud = generacion;
        String borradorId = estado.get("reservaBorradorId");
        if (borradorId == null) return;
        Booking borrador = new Booking(borradorId, "", hotelId, roomId, uid,
                desde, hasta, getHuespedesActuales(), cuarto.getPrecioNoche(),
                BookingStatus.PENDIENTE);
        estado.set("reservaIntentada", true);
        estado.set("reservaIntentoFirma", firmaActual());
        guardando = true;
        creacion.setValue(UiState.loading());
        ServiceLocator.reservas().crear(borrador, new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (!vigente(uid, solicitud)) return;
                guardando = false;
                estado.set("reservaCreadaId", datos.getId());
                estado.remove("reservaIntentada");
                creacion.setValue(UiState.success(datos));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                guardando = false;
                creacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void limpiarCreacion() {
        UiState<Booking> actual = creacion.getValue();
        if (actual != null && actual.isError()) creacion.setValue(null);
    }

    private boolean vigente(String uid, long solicitud) {
        return solicitud == generacion && Objects.equals(cuentaId, uid)
                && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    @Override protected void onCleared() {
        generacion++;
        consultaCalendario++;
        super.onCleared();
    }
}
