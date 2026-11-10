package org.iot.project.ui.client.bookings;

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
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Hotel;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Reservas del cliente con búsqueda, filtros y orden conservados al recrear la pantalla. */
public class BookingsViewModel extends ViewModel {
    interface SesionCliente {
        LiveData<Cuenta> cambios();
        @Nullable String uid();
        boolean esCliente();
    }

    private final SavedStateHandle guardado;
    private final BookingRepository repositorio;
    private final SesionCliente sesion;
    private final Function<Booking, String> nombreHotel;
    private final MutableLiveData<UiState<List<Booking>>> reservas = new MutableLiveData<>();
    private final Observer<Cuenta> observador = cuenta -> revisarSesion();
    private List<Booking> todas = Collections.emptyList();
    private String busqueda = "";
    private ConsultaReservasCliente.Orden orden = ConsultaReservasCliente.Orden.FECHA_SALIDA;
    private boolean ascendente;
    @Nullable private BookingStatus estado;
    @Nullable private String cuentaId;
    private boolean sesionObservada;
    private boolean datosRecibidos;
    private boolean cargando;
    private boolean visible;
    private long generacion;

    public BookingsViewModel(@NonNull SavedStateHandle guardado) {
        this(guardado, ServiceLocator.reservas(), new SesionCliente() {
            @Override public LiveData<Cuenta> cambios() { return SessionManager.sesion(); }
            @Override public String uid() { return SessionManager.getUsuarioId(); }
            @Override public boolean esCliente() { return SessionManager.getRolActivo() == Role.CLIENTE; }
        }, reserva -> {
            Hotel hotel = ServiceLocator.hoteles().hotel(reserva.getHotelId());
            return hotel == null ? reserva.getHotelNombre() : hotel.getNombre();
        });
    }

    BookingsViewModel(SavedStateHandle guardado, BookingRepository repositorio,
                     SesionCliente sesion, Function<Booking, String> nombreHotel) {
        this.guardado = guardado;
        this.repositorio = repositorio;
        this.sesion = sesion;
        this.nombreHotel = nombreHotel;
        sesion.cambios().observeForever(observador);
    }

    public LiveData<UiState<List<Booking>>> getReservas() { return reservas; }
    public String getBusqueda() { return busqueda; }
    public ConsultaReservasCliente.Orden getOrden() { return orden; }
    public boolean isAscendente() { return ascendente; }
    @Nullable public BookingStatus getEstadoFiltrado() { return estado; }
    public boolean tieneReservas() { return !todas.isEmpty(); }

    public void buscar(@NonNull String texto) {
        revisarSesion();
        if (busqueda.equals(texto)) return;
        busqueda = texto;
        opcionesCambiadas();
    }

    public void ordenarPor(@NonNull ConsultaReservasCliente.Orden nuevoOrden) {
        revisarSesion();
        if (orden == nuevoOrden) return;
        orden = nuevoOrden;
        opcionesCambiadas();
    }

    public void cambiarSentido(boolean nuevoAscendente) {
        revisarSesion();
        if (ascendente == nuevoAscendente) return;
        ascendente = nuevoAscendente;
        opcionesCambiadas();
    }

    public void filtrarEstado(@Nullable BookingStatus nuevoEstado) {
        revisarSesion();
        if (estado == nuevoEstado) return;
        estado = nuevoEstado;
        opcionesCambiadas();
    }

    /** Quita la búsqueda y el estado sin perder el orden que el cliente eligió. */
    public void limpiarFiltros() {
        revisarSesion();
        busqueda = "";
        estado = null;
        opcionesCambiadas();
    }

    private void opcionesCambiadas() {
        guardarOpciones();
        if (datosRecibidos && visible && sesionVigente()) publicar();
    }

    public void cargar() {
        visible = true;
        revisarSesion();
        if (!datosRecibidos) pedir(false);
        else if (sesionVigente()) publicar();
    }

    public void recargar() {
        visible = true;
        revisarSesion();
        pedir(true);
    }

    public void refrescarEnSilencio() {
        visible = true;
        revisarSesion();
        pedir(false);
    }

    /** Invalida respuestas que llegan cuando la pantalla ya no está visible. */
    public void suspender() {
        visible = false;
        generacion++;
        cargando = false;
    }

    private void revisarSesion() {
        String actual = sesion.esCliente() ? sesion.uid() : null;
        if (sesionObservada && Objects.equals(cuentaId, actual)) return;
        boolean primera = !sesionObservada;
        sesionObservada = true;
        generacion++;
        cargando = false;
        cuentaId = actual;
        todas = Collections.emptyList();
        datosRecibidos = false;
        busqueda = "";
        orden = ConsultaReservasCliente.Orden.FECHA_SALIDA;
        ascendente = false;
        estado = null;
        if (actual != null && actual.equals(guardado.get("reservas.uid"))) restaurarOpciones();
        if (actual != null || !primera) guardarOpciones();
        reservas.setValue(UiState.loading());
        if (visible) pedir(false);
    }

    private void pedir(boolean mostrarCarga) {
        if (!visible || cargando) return;
        if (!sesionVigente()) {
            reservas.setValue(UiState.error("Inicia sesión como cliente para ver tus reservas."));
            return;
        }
        final String uid = cuentaId;
        final long solicitud = ++generacion;
        cargando = true;
        if (mostrarCarga || !datosRecibidos) reservas.setValue(UiState.loading());
        repositorio.reservasDe(uid, new ResultCallback<List<Booking>>() {
            @Override public void onExito(@NonNull List<Booking> datos) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                datosRecibidos = true;
                List<Booking> propias = new ArrayList<>();
                for (Booking reserva : datos) if (uid.equals(reserva.getClienteId())) propias.add(reserva);
                todas = propias;
                publicar();
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!vigente(uid, solicitud)) return;
                cargando = false;
                datosRecibidos = false;
                todas = Collections.emptyList();
                reservas.setValue(UiState.error(mensaje));
            }
        });
    }

    private void publicar() {
        List<Booking> filtradas = ConsultaReservasCliente.aplicar(todas, busqueda, estado,
                orden, ascendente, nombreHotel);
        reservas.setValue(filtradas.isEmpty() ? UiState.empty()
                : UiState.success(Collections.unmodifiableList(filtradas)));
    }

    private boolean sesionVigente() {
        return cuentaId != null && cuentaId.equals(sesion.uid()) && sesion.esCliente();
    }

    private boolean vigente(String uid, long solicitud) {
        return visible && solicitud == generacion && uid.equals(cuentaId) && sesionVigente();
    }

    private void guardarOpciones() {
        guardado.set("reservas.uid", cuentaId);
        guardado.set("reservas.busqueda", busqueda);
        guardado.set("reservas.orden", orden.name());
        guardado.set("reservas.ascendente", ascendente);
        guardado.set("reservas.estado", estado == null ? null : estado.name());
    }

    private void restaurarOpciones() {
        String texto = guardado.get("reservas.busqueda");
        busqueda = texto == null ? "" : texto;
        String campo = guardado.get("reservas.orden");
        try { if (campo != null) orden = ConsultaReservasCliente.Orden.valueOf(campo); }
        catch (IllegalArgumentException ignorado) { orden = ConsultaReservasCliente.Orden.FECHA_SALIDA; }
        ascendente = Boolean.TRUE.equals(guardado.get("reservas.ascendente"));
        String estadoGuardado = guardado.get("reservas.estado");
        try { if (estadoGuardado != null) estado = BookingStatus.valueOf(estadoGuardado); }
        catch (IllegalArgumentException ignorado) { estado = null; }
    }

    @Override protected void onCleared() {
        generacion++;
        visible = false;
        sesion.cambios().removeObserver(observador);
        super.onCleared();
    }
}
