package org.iot.project.ui.admin.reservas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.Charge;
import org.iot.project.models.Cuenta;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.Role;

import java.util.Objects;
import java.util.UUID;

/** Detalle y operaciones de recepción, aislados por cuenta, hotel y reserva. */
public class ReservaAdminViewModel extends ViewModel {
    private final SavedStateHandle guardado;
    private final MutableLiveData<UiState<ReservaDeHotel>> reserva = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> operacion = new MutableLiveData<>();
    private final MutableLiveData<UiState<Booking>> cargo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocupado = new MutableLiveData<>(false);
    private final Observer<Cuenta> sesion = cuenta -> revisarSesion();
    @Nullable private String uid;
    @Nullable private String hotelId;
    @Nullable private String cargada;
    private long generacion;
    private long consulta;
    private long seguimiento;
    @Nullable private GestionHotelRepository.Suscripcion cambios;
    private boolean consultando;
    private boolean enviando;
    private boolean visible;

    public ReservaAdminViewModel(@NonNull SavedStateHandle guardado) {
        this.guardado = guardado;
        SessionManager.sesion().observeForever(sesion);
    }

    public LiveData<UiState<ReservaDeHotel>> getReserva() { return reserva; }
    public LiveData<UiState<Booking>> getOperacion() { return operacion; }
    public LiveData<UiState<Booking>> getCargo() { return cargo; }
    public LiveData<Boolean> getOcupado() { return ocupado; }
    public boolean estaOcupado() { return enviando; }

    private void revisarSesion() {
        String nuevaUid = SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                ? SessionManager.getUsuarioId() : null;
        String nuevoHotel = SessionManager.getHotelAdministrado();
        if (Objects.equals(uid, nuevaUid) && Objects.equals(hotelId, nuevoHotel)) return;
        cerrarCambios();
        uid = nuevaUid;
        hotelId = nuevoHotel;
        generacion++;
        consulta++;
        consultando = false;
        enviando = false;
        ocupado.setValue(false);
        cargada = null;
        reserva.setValue(UiState.error("Inicia sesión como administrador del hotel asignado."));
        operacion.setValue(null);
        cargo.setValue(null);
        if (!Objects.equals(uid, guardado.get("adminReservaUid"))
                || !Objects.equals(hotelId, guardado.get("adminReservaHotel"))) {
            limpiarPendiente();
            limpiarFormularioCargo();
            guardado.set("adminReservaUid", uid);
            guardado.set("adminReservaHotel", hotelId);
        }
    }

    public void cargar(@NonNull String bookingId) {
        revisarSesion();
        if (!bookingId.equals(cargada)) {
            cerrarCambios();
            consulta++;
            consultando = false;
            cargada = bookingId;
            reserva.setValue(UiState.loading());
            operacion.setValue(null);
            cargo.setValue(null);
        }
    }

    public void iniciarSeguimiento() {
        visible = true;
        revisarSesion();
        if (cargada == null) return;
        String pendiente = guardado.get("adminReservaOperacion");
        String reservaPendiente = guardado.get("adminReservaOperacionId");
        if (!enviando && pendiente != null && cargada.equals(reservaPendiente)) {
            ejecutar(pendiente);
        }
        observarDetalle();
    }

    public void detenerSeguimiento() {
        visible = false;
        cerrarCambios();
        consulta++;
        consultando = false;
    }

    public void recargar() {
        revisarSesion();
        if (enviando) return;
        if (visible) {
            cerrarCambios();
            reserva.setValue(UiState.loading());
            observarDetalle();
        } else pedir(true);
    }

    private void observarDetalle() {
        if (!visible || cambios != null || cargada == null) return;
        if (uid == null || hotelId == null) {
            reserva.setValue(UiState.error("Inicia sesión con la cuenta del administrador del hotel asignado."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final String bookingId = cargada;
        final long version = generacion;
        final long escucha = ++seguimiento;
        cambios = ServiceLocator.gestion().escucharReserva(hotel, bookingId, new ResultCallback<ReservaDeHotel>() {
            @Override public void onExito(@NonNull ReservaDeHotel datos) {
                if (!visible || escucha != seguimiento || !vigente(cuenta, hotel, bookingId, version)) return;
                if (!enviando) {
                    consulta++;
                    consultando = false;
                    reserva.setValue(UiState.success(datos));
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!visible || escucha != seguimiento || !vigente(cuenta, hotel, bookingId, version)) return;
                if (!enviando) {
                    consulta++;
                    consultando = false;
                    reserva.setValue(UiState.error(mensaje));
                }
            }
        });
    }

    private void cerrarCambios() {
        seguimiento++;
        if (cambios != null) cambios.cerrar();
        cambios = null;
    }

    private void pedir(boolean conEsqueleto) {
        if (cargada == null || consultando || enviando) return;
        if (uid == null || hotelId == null) {
            reserva.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final String bookingId = cargada;
        final long version = generacion;
        final long solicitud = ++consulta;
        consultando = true;
        if (conEsqueleto) reserva.setValue(UiState.loading());
        ServiceLocator.gestion().reserva(hotel, bookingId, new ResultCallback<ReservaDeHotel>() {
            @Override public void onExito(@NonNull ReservaDeHotel datos) {
                if (!vigente(cuenta, hotel, bookingId, version) || solicitud != consulta) return;
                consultando = false;
                if (!enviando) reserva.setValue(UiState.success(datos));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(cuenta, hotel, bookingId, version) || solicitud != consulta) return;
                consultando = false;
                if (!enviando) reserva.setValue(UiState.error(mensaje));
            }
        });
    }

    private boolean vigente(String cuenta, String hotel, String bookingId, long version) {
        return version == generacion && Objects.equals(uid, cuenta)
                && Objects.equals(hotelId, hotel) && Objects.equals(cargada, bookingId)
                && cuenta.equals(SessionManager.getUsuarioId())
                && hotel.equals(SessionManager.getHotelAdministrado())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
    }

    public void iniciarEstadia() { comenzar("INICIAR"); }
    public void cancelar() { comenzar("CANCELAR"); }
    public void cobrar() { comenzar("COBRAR"); }

    private void comenzar(String tipo) {
        if (enviando || cargada == null) return;
        guardado.set("adminReservaOperacion", tipo);
        guardado.set("adminReservaOperacionId", cargada);
        ejecutar(tipo);
    }

    private void ejecutar(String tipo) {
        if (enviando || cargada == null || uid == null || hotelId == null) return;
        final String cuenta = uid;
        final String hotel = hotelId;
        final String bookingId = cargada;
        final long version = generacion;
        final boolean esCargo = "CARGO".equals(tipo);
        final MutableLiveData<UiState<Booking>> canal = esCargo ? cargo : operacion;
        consulta++;
        consultando = false;
        enviando = true;
        ocupado.setValue(true);
        canal.setValue(UiState.loading());
        ResultCallback<Booking> respuesta = new ResultCallback<Booking>() {
            @Override public void onExito(@NonNull Booking datos) {
                if (!vigente(cuenta, hotel, bookingId, version)) {
                    terminarOperacionAjena(cuenta, hotel, version);
                    return;
                }
                enviando = false;
                ocupado.setValue(false);
                limpiarPendiente();
                UiState<ReservaDeHotel> anterior = reserva.getValue();
                ReservaDeHotel detalle = anterior == null ? null : anterior.getData();
                if (detalle != null) {
                    reserva.setValue(UiState.success(new ReservaDeHotel(datos, detalle.getCliente(),
                            detalle.getHabitacionNumero(), detalle.getHabitacionTipo())));
                }
                canal.setValue(UiState.success(datos));
                if (visible) pedir(false);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(cuenta, hotel, bookingId, version)) {
                    terminarOperacionAjena(cuenta, hotel, version);
                    return;
                }
                enviando = false;
                ocupado.setValue(false);
                limpiarPendiente();
                canal.setValue(UiState.error(mensaje));
            }
        };
        switch (tipo) {
            case "INICIAR":
                ServiceLocator.reservas().iniciarEstadia(bookingId, respuesta);
                break;
            case "CANCELAR":
                ServiceLocator.reservas().cancelar(bookingId, respuesta);
                break;
            case "COBRAR":
                ServiceLocator.reservas().cobrar(bookingId, respuesta);
                break;
            case "CARGO":
                try {
                    Double monto = guardado.get("adminCargoMonto");
                    String motivo = guardado.get("adminCargoMotivo");
                    String observacion = guardado.get("adminCargoObservacion");
                    String operacionId = guardado.get("adminCargoId");
                    if (monto == null || motivo == null || observacion == null || operacionId == null) {
                        throw new IllegalArgumentException("Revisa los datos del cargo antes de guardarlo.");
                    }
                    ServiceLocator.reservas().agregarCargo(bookingId, operacionId,
                            new Charge(monto, motivo, observacion), respuesta);
                } catch (IllegalArgumentException fallo) {
                    respuesta.onError(fallo.getMessage() == null
                            ? "Revisa los datos del cargo." : fallo.getMessage());
                }
                break;
            default:
                respuesta.onError("La operación solicitada no está disponible.");
        }
    }

    private void terminarOperacionAjena(String cuenta, String hotel, long version) {
        if (version != generacion || !Objects.equals(cuenta, uid)
                || !Objects.equals(hotel, hotelId)) return;
        enviando = false;
        ocupado.setValue(false);
        limpiarPendiente();
        if (visible) pedir(false);
    }

    private void limpiarPendiente() {
        guardado.remove("adminReservaOperacion");
        guardado.remove("adminReservaOperacionId");
    }

    public boolean prepararCargo(@Nullable String bookingId) {
        if (bookingId == null || !bookingId.equals(cargada)) return false;
        if (!bookingId.equals(guardado.get("adminCargoReserva"))) {
            if (enviando) return false;
            limpiarFormularioCargo();
            guardado.set("adminCargoReserva", bookingId);
            guardado.set("adminCargoId", UUID.randomUUID().toString());
            cargo.setValue(null);
        }
        return true;
    }

    public String textoCargo(String campo) {
        String texto = guardado.get("adminCargoTexto" + campo);
        return texto == null ? "" : texto;
    }

    public void guardarTextoCargo(String campo, String texto) {
        if (!cargoIntentado()) guardado.set("adminCargoTexto" + campo, texto);
    }

    public boolean cargoIntentado() { return Boolean.TRUE.equals(guardado.get("adminCargoIntentado")); }

    public void agregarCargo(@NonNull Charge nuevo) {
        if (enviando || cargada == null || !prepararCargo(cargada)) return;
        if (!cargoIntentado()) {
            guardado.set("adminCargoMonto", nuevo.getMonto());
            guardado.set("adminCargoMotivo", nuevo.getMotivo());
            guardado.set("adminCargoObservacion", nuevo.getObservacion());
            guardado.set("adminCargoIntentado", true);
        }
        comenzar("CARGO");
    }

    public void reintentarCargo() { if (cargoIntentado()) comenzar("CARGO"); }
    public void limpiarOperacion() { if (!enviando) operacion.setValue(null); }
    public void limpiarCargo() { if (!enviando) cargo.setValue(null); }

    public void cargoAtendido() {
        if (enviando) return;
        cargo.setValue(null);
        limpiarFormularioCargo();
    }

    private void limpiarFormularioCargo() {
        String[] claves = {"Reserva", "Id", "Intentado", "Monto", "Motivo", "Observacion",
                "TextoMonto", "TextoMotivo", "TextoObservacion"};
        for (String clave : claves) guardado.remove("adminCargo" + clave);
    }

    @Override protected void onCleared() {
        cerrarCambios();
        generacion++;
        consulta++;
        SessionManager.sesion().removeObserver(sesion);
        super.onCleared();
    }
}
