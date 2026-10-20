package org.iot.project.ui.client.taxi;

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
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Solicitud y seguimiento del taxi, conservando el borrador durante una recreación. */
public class TaxiViewModel extends ViewModel {

    public static final class Estado {
        @Nullable public final TaxiService activo;
        @NonNull public final List<TaxiService> anteriores;

        Estado(@Nullable TaxiService activo, @NonNull List<TaxiService> anteriores) {
            this.activo = activo;
            this.anteriores = anteriores;
        }

        public boolean tieneActivo() { return activo != null; }
    }

    private final SavedStateHandle guardado;
    private final MutableLiveData<UiState<Estado>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Booking>>> reservas = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> hotelElegido = new MutableLiveData<>();
    private final MutableLiveData<UiState<TaxiService>> solicitud = new MutableLiveData<>();
    private final List<Booking> todasLasReservas = new ArrayList<>();
    private final Set<String> reservasConTaxi = new HashSet<>();
    @Nullable private Booking reserva;
    @Nullable private Hotel hotel;
    @Nullable private String cuentaId;
    @Nullable private TaxiRepository.Suscripcion suscripcion;
    private long generacion;
    private long consultaHotel;
    private long consultaReservas;
    private boolean visible;
    private boolean enviando;
    @Nullable private String servicioEnfocadoId;
    private final Set<String> valorando = new HashSet<>();

    public TaxiViewModel(@NonNull SavedStateHandle guardado) {
        this.guardado = guardado;
    }

    public LiveData<UiState<Estado>> getContenido() { return contenido; }
    public LiveData<UiState<List<Booking>>> getReservas() { return reservas; }
    public LiveData<UiState<Hotel>> getHotelElegido() { return hotelElegido; }
    public LiveData<UiState<TaxiService>> getSolicitud() { return solicitud; }
    @Nullable public Booking getReservaElegida() { return reserva; }
    public boolean isEnviando() { return enviando; }

    public void cargar() {
        String uid = SessionManager.getUsuarioId();
        if (uid == null || SessionManager.getRolActivo() != Role.CLIENTE) {
            detenerSeguimiento();
            contenido.setValue(UiState.error("Inicia sesión como cliente para pedir un taxi."));
            return;
        }
        if (!uid.equals(cuentaId)) {
            boolean estabaVisible = visible;
            detenerSeguimiento();
            visible = estabaVisible;
            cuentaId = uid;
            generacion++;
            consultaHotel++;
            consultaReservas++;
            reserva = null;
            hotel = null;
            enviando = false;
            valorando.clear();
            todasLasReservas.clear();
            reservasConTaxi.clear();
            contenido.setValue(UiState.loading());
            hotelElegido.setValue(null);
            solicitud.setValue(null);
            if (!uid.equals(guardado.get("taxiUid"))) {
                limpiarBorrador();
                guardado.set("taxiUid", uid);
            }
        }
        cargarReservas();
    }

    public void iniciarSeguimiento() {
        visible = true;
        cargar();
        if (!visible || cuentaId == null || suscripcion != null) return;
        final String uid = cuentaId;
        final long version = generacion;
        suscripcion = ServiceLocator.taxis().escucharHistorial(uid,
                new ResultCallback<List<TaxiService>>() {
                    @Override public void onExito(@NonNull List<TaxiService> datos) {
                        if (!visible || !vigente(uid, version)) return;
                        aplicarServicios(datos);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (visible && vigente(uid, version)) {
                            contenido.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    public void detenerSeguimiento() {
        visible = false;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    public void refrescarEnSilencio() {
        cargar();
        if (suscripcion == null) iniciarSeguimiento();
    }

    private boolean vigente(String uid, long version) {
        return version == generacion && Objects.equals(cuentaId, uid)
                && uid.equals(SessionManager.getUsuarioId())
                && SessionManager.getRolActivo() == Role.CLIENTE;
    }

    private void cargarReservas() {
        if (cuentaId == null) return;
        String uid = cuentaId;
        long version = generacion;
        long consulta = ++consultaReservas;
        reservas.setValue(UiState.loading());
        ServiceLocator.reservas().reservasDe(uid, new ResultCallback<List<Booking>>() {
            @Override public void onExito(@NonNull List<Booking> datos) {
                if (!vigente(uid, version) || consulta != consultaReservas) return;
                todasLasReservas.clear();
                for (Booking dato : datos) {
                    if (elegible(dato)) todasLasReservas.add(dato);
                }
                publicarReservas();
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(uid, version) && consulta == consultaReservas) {
                    reservas.setValue(UiState.error(mensaje));
                }
            }
        });
    }

    private static boolean elegible(Booking dato) {
        BookingStatus estado = dato.getEstado();
        return (estado == BookingStatus.CONFIRMADA || estado == BookingStatus.ACTIVA
                || estado == BookingStatus.FINALIZADA)
                && !dato.getFechaSalida().isBefore(LocalDate.now());
    }

    private void aplicarServicios(List<TaxiService> datos) {
        reservasConTaxi.clear();
        for (TaxiService taxi : datos) reservasConTaxi.add(taxi.getBookingId());
        contenido.setValue(UiState.success(separar(datos)));
        String elegidaId = guardado.get("taxiReservaId");
        if (elegidaId != null && reservasConTaxi.contains(elegidaId)
                && (enviando || isFormularioAbierto())) {
            for (TaxiService taxi : datos) {
                if (elegidaId.equals(taxi.getBookingId())) {
                    enviando = false;
                    guardado.set("taxiFormulario", false);
                    solicitud.setValue(UiState.success(taxi));
                    break;
                }
            }
        }
        publicarReservas();
    }

    private void publicarReservas() {
        List<Booking> utiles = new ArrayList<>();
        for (Booking dato : todasLasReservas) {
            if (!reservasConTaxi.contains(dato.getId())) utiles.add(dato);
        }
        String elegidaId = guardado.get("taxiReservaId");
        Booking actualizada = null;
        for (Booking dato : utiles) {
            if (dato.getId().equals(elegidaId)) actualizada = dato;
        }
        if (actualizada != null) {
            if (reserva == null || !reserva.getId().equals(actualizada.getId())) {
                elegirReserva(actualizada);
            } else reserva = actualizada;
        } else if (!enviando) {
            reserva = null;
            hotel = null;
            consultaHotel++;
            hotelElegido.setValue(null);
        }
        reservas.setValue(utiles.isEmpty() ? UiState.empty() : UiState.success(utiles));
    }

    @NonNull private Estado separar(@NonNull List<TaxiService> datos) {
        TaxiService activo = null;
        for (TaxiService taxi : datos) {
            if (!taxi.getEstado().isActive()) continue;
            if (activo == null || taxi.getEstado().ordinal() > activo.getEstado().ordinal()
                    || (taxi.getEstado() == activo.getEstado()
                    && taxi.getFecha().atTime(taxi.getHora()).isBefore(
                    activo.getFecha().atTime(activo.getHora())))) activo = taxi;
        }
        if (servicioEnfocadoId != null) {
            for (TaxiService taxi : datos) {
                if (servicioEnfocadoId.equals(taxi.getId()) && taxi.getEstado().isActive()) {
                    activo = taxi;
                    break;
                }
            }
        }
        List<TaxiService> anteriores = new ArrayList<>();
        for (TaxiService taxi : datos) if (taxi != activo) anteriores.add(taxi);
        return new Estado(activo, anteriores);
    }

    public void elegirReserva(@NonNull Booking elegida) {
        if (enviando || cuentaId == null || !elegible(elegida)) return;
        reserva = elegida;
        hotel = null;
        boolean misma = elegida.getId().equals(guardado.get("taxiReservaId"));
        guardado.set("taxiReservaId", elegida.getId());
        if (!misma) {
            guardado.set("taxiBorradorId", UUID.randomUUID().toString());
            guardado.set("taxiOrigen", "");
            guardado.set("taxiPasajeros", 1);
            guardado.set("taxiFecha", elegida.getFechaSalida().toEpochDay());
            LocalTime sugerida = LocalTime.of(9, 0);
            if (elegida.getFechaSalida().equals(LocalDate.now())
                    && !sugerida.isAfter(LocalTime.now())) {
                LocalDateTime siguiente = LocalDateTime.now().plusMinutes(15);
                sugerida = siguiente.toLocalDate().equals(LocalDate.now())
                        ? siguiente.toLocalTime().withSecond(0).withNano(0) : LocalTime.of(23, 59);
            }
            guardado.set("taxiHora", sugerida.toSecondOfDay());
        }
        String uid = cuentaId;
        long version = generacion;
        long consulta = ++consultaHotel;
        hotelElegido.setValue(UiState.loading());
        ServiceLocator.hoteles().obtenerDeReserva(elegida.getId(), new ResultCallback<Hotel>() {
            @Override public void onExito(@NonNull Hotel dato) {
                if (!vigente(uid, version) || consulta != consultaHotel) return;
                hotel = dato;
                if (getOrigen().isEmpty()) guardado.set("taxiOrigen", dato.getDireccion());
                hotelElegido.setValue(UiState.success(dato));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(uid, version) && consulta == consultaHotel) {
                    hotelElegido.setValue(UiState.error(mensaje));
                }
            }
        });
    }

    public boolean puedeSolicitar() { return reserva != null && hotel != null && !enviando; }
    public boolean esGratuito() {
        return reserva != null && hotel != null
                && reserva.calificaParaTaxiGratuito(hotel.getMontoMinimoTaxi());
    }

    public void solicitar(@NonNull String origen, @NonNull String destino,
                          @NonNull LocalDate fecha, @NonNull LocalTime hora,
                          int pasajeros, boolean idaYVuelta) {
        if (enviando) return;
        if (!puedeSolicitar() || cuentaId == null) {
            solicitud.setValue(UiState.error("Elige una reserva y espera a que cargue su hotel."));
            return;
        }
        if (origen.trim().isEmpty() || destino.trim().isEmpty()
                || origen.trim().length() > 300 || destino.trim().length() > 300) {
            solicitud.setValue(UiState.error("Indica el recojo y el aeropuerto de destino (máximo 300 caracteres)."));
            return;
        }
        if (fecha.isBefore(reserva.getFechaEntrada()) || fecha.isAfter(reserva.getFechaSalida())
                || fecha.atTime(hora).isBefore(LocalDateTime.now())) {
            solicitud.setValue(UiState.error("Elige una fecha y hora futuras dentro de las fechas de tu reserva, incluido el día de salida."));
            return;
        }
        if (pasajeros < 1 || pasajeros > Math.min(6, reserva.getNumHuespedes())) {
            solicitud.setValue(UiState.error("Los pasajeros no pueden superar a los huéspedes de la reserva ni las seis plazas."));
            return;
        }
        guardarFormulario(origen, destino, fecha, hora, pasajeros, idaYVuelta);
        String borradorId = guardado.get("taxiBorradorId");
        if (borradorId == null) {
            borradorId = UUID.randomUUID().toString();
            guardado.set("taxiBorradorId", borradorId);
        }
        TaxiService borrador = new TaxiService(borradorId, "", reserva.getId(), cuentaId)
                .withRuta(origen.trim(), destino.trim())
                .withRecojo(hotel.getLatitud(), hotel.getLongitud())
                .withProgramacion(fecha, hora, idaYVuelta).withPasajeros(pasajeros)
                .withPrecio(esGratuito() ? 0d : TaxiService.TARIFA_AEROPUERTO);
        String uid = cuentaId;
        long version = generacion;
        enviando = true;
        solicitud.setValue(UiState.loading());
        ServiceLocator.taxis().solicitar(borrador, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService dato) {
                if (!vigente(uid, version)) return;
                enviando = false;
                guardado.set("taxiFormulario", false);
                publicarCon(dato);
                solicitud.setValue(UiState.success(dato));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, version)) return;
                enviando = false;
                solicitud.setValue(UiState.error(mensaje));
            }
        });
    }

    public void valorar(@NonNull String taxiId, float rating,
                        @NonNull ResultCallback<TaxiService> callback) {
        if (cuentaId == null) {
            callback.onError("Inicia sesión para valorar el traslado.");
            return;
        }
        if (!valorando.add(taxiId)) {
            callback.onError("La valoración se está guardando.");
            return;
        }
        String uid = cuentaId;
        long version = generacion;
        ServiceLocator.taxis().calificar(taxiId, rating, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService dato) {
                if (!vigente(uid, version)) return;
                valorando.remove(taxiId);
                publicarCon(dato);
                callback.onExito(dato);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, version)) return;
                valorando.remove(taxiId);
                callback.onError(mensaje);
            }
        });
    }

    private void publicarCon(TaxiService nuevo) {
        List<TaxiService> todos = new ArrayList<>();
        UiState<Estado> actual = contenido.getValue();
        if (actual != null && actual.getData() != null) {
            Estado datos = actual.getData();
            if (datos.activo != null && !datos.activo.getId().equals(nuevo.getId())) todos.add(datos.activo);
            for (TaxiService previo : datos.anteriores) {
                if (!previo.getId().equals(nuevo.getId())) todos.add(previo);
            }
        }
        todos.add(nuevo);
        aplicarServicios(todos);
    }

    public void guardarFormulario(String origen, String destino, LocalDate fecha, LocalTime hora,
                                   int pasajeros, boolean idaYVuelta) {
        if (enviando) return;
        guardado.set("taxiOrigen", origen);
        guardado.set("taxiDestino", destino);
        guardado.set("taxiFecha", fecha.toEpochDay());
        guardado.set("taxiHora", hora.toSecondOfDay());
        guardado.set("taxiPasajeros", pasajeros);
        guardado.set("taxiVuelta", idaYVuelta);
    }

    public String getOrigen() { String valor = guardado.get("taxiOrigen"); return valor == null ? "" : valor; }
    public String getDestino() { String valor = guardado.get("taxiDestino"); return valor == null ? "" : valor; }
    public LocalDate getFecha() { Long valor = guardado.get("taxiFecha"); return valor == null ? LocalDate.now().plusDays(1) : LocalDate.ofEpochDay(valor); }
    public LocalTime getHora() { Integer valor = guardado.get("taxiHora"); return valor == null ? LocalTime.of(9, 0) : LocalTime.ofSecondOfDay(valor); }
    public int getPasajeros() { Integer valor = guardado.get("taxiPasajeros"); return valor == null ? 1 : valor; }
    public boolean isIdaYVuelta() { return Boolean.TRUE.equals(guardado.get("taxiVuelta")); }
    public boolean isFormularioAbierto() { return Boolean.TRUE.equals(guardado.get("taxiFormulario")); }
    public void setFormularioAbierto(boolean abierto) { if (!enviando) guardado.set("taxiFormulario", abierto); }
    public void consumirSolicitud() { if (!enviando) solicitud.setValue(null); }

    public void enfocarServicio(@NonNull String taxiId) {
        servicioEnfocadoId = taxiId;
        UiState<Estado> actual = contenido.getValue();
        if (actual == null || actual.getData() == null) return;
        List<TaxiService> datos = new ArrayList<>(actual.getData().anteriores);
        if (actual.getData().activo != null) datos.add(actual.getData().activo);
        contenido.setValue(UiState.success(separar(datos)));
    }

    private void limpiarBorrador() {
        for (String clave : new String[]{"taxiReservaId", "taxiBorradorId", "taxiOrigen", "taxiDestino",
                "taxiFecha", "taxiHora", "taxiPasajeros", "taxiVuelta", "taxiFormulario"}) guardado.remove(clave);
    }

    public void reintentar() {
        detenerSeguimiento();
        iniciarSeguimiento();
    }

    @Override protected void onCleared() {
        detenerSeguimiento();
        generacion++;
        super.onCleared();
    }
}
