package org.iot.project.ui.admin.reservas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Seguimiento de lectura para recepción: nunca solicita ni modifica un traslado. */
public class AdminTaxiReservaViewModel extends ViewModel {
    interface SesionHotel {
        LiveData<Cuenta> cambios();
        @Nullable String uid();
        @Nullable String hotel();
        boolean esAdministrador();
    }

    public static final class Estado {
        @NonNull public final List<TaxiService> confirmados;
        @NonNull public final List<TaxiService> sinConfirmar;
        @Nullable public final TaxiService seleccionado;
        @Nullable public final String aviso;

        Estado(List<TaxiService> confirmados, List<TaxiService> sinConfirmar,
               @Nullable TaxiService seleccionado, @Nullable String aviso) {
            this.confirmados = Collections.unmodifiableList(new ArrayList<>(confirmados));
            this.sinConfirmar = Collections.unmodifiableList(new ArrayList<>(sinConfirmar));
            this.seleccionado = seleccionado;
            this.aviso = aviso;
        }
    }

    private final TaxiRepository repositorio;
    private final SesionHotel sesion;
    private final MutableLiveData<UiState<Estado>> contenido = new MutableLiveData<>();
    private final Observer<Cuenta> observadorSesion = cuenta -> revisarSesion();
    @Nullable private TaxiRepository.Suscripcion suscripcion;
    @Nullable private String uid;
    @Nullable private String hotelId;
    @Nullable private String bookingId;
    @Nullable private String taxiSeleccionado;
    @Nullable private Estado ultimo;
    private long generacion;
    private boolean visible;
    private boolean seleccionManual;

    public AdminTaxiReservaViewModel() {
        this(ServiceLocator.taxis(), new SesionHotel() {
            @Override public LiveData<Cuenta> cambios() { return SessionManager.sesion(); }
            @Override public String uid() { return SessionManager.getUsuarioId(); }
            @Override public String hotel() { return SessionManager.getHotelAdministrado(); }
            @Override public boolean esAdministrador() {
                return SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
            }
        });
    }

    AdminTaxiReservaViewModel(TaxiRepository repositorio, SesionHotel sesion) {
        this.repositorio = repositorio;
        this.sesion = sesion;
        sesion.cambios().observeForever(observadorSesion);
    }

    public LiveData<UiState<Estado>> getContenido() { return contenido; }

    public void cargar(@Nullable String reservaId) {
        revisarSesion();
        if (Objects.equals(bookingId, reservaId)) return;
        cerrarEscucha();
        bookingId = reservaId;
        taxiSeleccionado = null;
        seleccionManual = false;
        ultimo = null;
        contenido.setValue(UiState.loading());
        if (visible) conectar();
    }

    public void iniciarSeguimiento() {
        visible = true;
        revisarSesion();
        conectar();
    }

    public void detenerSeguimiento() {
        visible = false;
        cerrarEscucha();
    }

    public void reintentar() {
        revisarSesion();
        cerrarEscucha();
        if (ultimo == null) contenido.setValue(UiState.loading());
        conectar();
    }

    public void seleccionar(@NonNull String servicioId) {
        if (uid == null || !uid.equals(sesion.uid()) || !Objects.equals(hotelId, sesion.hotel())
                || !sesion.esAdministrador()) {
            revisarSesion();
            return;
        }
        if (ultimo == null) return;
        for (TaxiService servicio : ultimo.confirmados) {
            if (!servicioId.equals(servicio.getId())) continue;
            taxiSeleccionado = servicioId;
            seleccionManual = true;
            ultimo = new Estado(ultimo.confirmados, ultimo.sinConfirmar, servicio, ultimo.aviso);
            contenido.setValue(UiState.success(ultimo));
            return;
        }
    }

    private void revisarSesion() {
        String nuevaUid = sesion.esAdministrador() ? sesion.uid() : null;
        String nuevoHotel = sesion.esAdministrador() ? sesion.hotel() : null;
        if (Objects.equals(uid, nuevaUid) && Objects.equals(hotelId, nuevoHotel)) return;
        cerrarEscucha();
        uid = nuevaUid;
        hotelId = nuevoHotel;
        taxiSeleccionado = null;
        seleccionManual = false;
        ultimo = null;
        contenido.setValue(UiState.loading());
        if (visible) conectar();
    }

    private void conectar() {
        if (!visible || suscripcion != null) return;
        if (uid == null || hotelId == null || !sesion.esAdministrador()) {
            contenido.setValue(UiState.error("Inicia sesión como administrador de un hotel asignado."));
            return;
        }
        if (bookingId == null || bookingId.trim().isEmpty()) {
            contenido.setValue(UiState.error("No se identificó la reserva del traslado."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final String reservaId = bookingId;
        final long version = ++generacion;
        suscripcion = repositorio.escucharServiciosDeReserva(hotel, reservaId,
                new ResultCallback<List<TaxiService>>() {
                    @Override public void onExito(@NonNull List<TaxiService> datos) {
                        if (!vigente(cuenta, hotel, reservaId, version)) return;
                        aplicar(datos);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (!vigente(cuenta, hotel, reservaId, version)) return;
                        if (ultimo == null) contenido.setValue(UiState.error(mensaje));
                        else {
                            ultimo = new Estado(ultimo.confirmados, ultimo.sinConfirmar,
                                    ultimo.seleccionado, mensaje);
                            contenido.setValue(UiState.success(ultimo));
                        }
                    }
                });
    }

    private boolean vigente(String cuenta, String hotel, String reservaId, long version) {
        return visible && version == generacion && cuenta.equals(sesion.uid())
                && hotel.equals(sesion.hotel()) && reservaId.equals(bookingId)
                && sesion.esAdministrador();
    }

    private void aplicar(List<TaxiService> datos) {
        List<TaxiService> confirmados = new ArrayList<>();
        List<TaxiService> sinConfirmar = new ArrayList<>();
        for (TaxiService servicio : datos) {
            // Una solicitud local sin ID del sistema web no demuestra que haya taxi operativo.
            if (!Objects.equals(bookingId, servicio.getBookingId())) continue;
            if (servicio.getApiId() == null || servicio.getApiId().isEmpty()) sinConfirmar.add(servicio);
            else confirmados.add(servicio);
        }
        Comparator<TaxiService> porFecha = Comparator
                .comparing(TaxiService::getFecha, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(TaxiService::getHora, Comparator.nullsFirst(Comparator.<LocalTime>naturalOrder()))
                .thenComparing(TaxiService::getId);
        confirmados.sort(porFecha.reversed());
        sinConfirmar.sort(porFecha.reversed());
        TaxiService elegido = null;
        if (seleccionManual) {
            for (TaxiService servicio : confirmados) {
                if (servicio.getId().equals(taxiSeleccionado)) elegido = servicio;
            }
        }
        if (elegido == null) {
            for (TaxiService servicio : confirmados) {
                if (!servicio.getEstado().isFinished()) { elegido = servicio; break; }
            }
        }
        if (elegido == null && !confirmados.isEmpty()) elegido = confirmados.get(0);
        taxiSeleccionado = elegido == null ? null : elegido.getId();
        ultimo = new Estado(confirmados, sinConfirmar, elegido, null);
        contenido.setValue(confirmados.isEmpty() && sinConfirmar.isEmpty()
                ? UiState.empty() : UiState.success(ultimo));
    }

    private void cerrarEscucha() {
        generacion++;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    @Override protected void onCleared() {
        cerrarEscucha();
        sesion.cambios().removeObserver(observadorSesion);
    }
}
