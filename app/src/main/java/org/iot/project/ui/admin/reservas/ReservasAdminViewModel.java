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
import org.iot.project.data.repository.GestionHotelRepository;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Cuenta;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Reservas reales del hotel; la escucha vive solamente mientras la lista está visible. */
public class ReservasAdminViewModel extends ViewModel {
    public static class Secciones {
        public final List<ReservaDeHotel> enCurso;
        public final List<ReservaDeHotel> proximas;
        public final List<ReservaDeHotel> historial;

        Secciones(List<ReservaDeHotel> enCurso, List<ReservaDeHotel> proximas,
                  List<ReservaDeHotel> historial) {
            this.enCurso = enCurso;
            this.proximas = proximas;
            this.historial = historial;
        }
        public boolean estaVacio() {
            return enCurso.isEmpty() && proximas.isEmpty() && historial.isEmpty();
        }
    }

    private final MutableLiveData<UiState<Secciones>> secciones = new MutableLiveData<>();
    private final Observer<Cuenta> sesion = cuenta -> revisarSesion();
    @Nullable private GestionHotelRepository.Suscripcion suscripcion;
    @Nullable private String uid;
    @Nullable private String hotelId;
    private long generacion;
    private boolean visible;
    // Última lista recibida y texto de búsqueda: al escribir se vuelve a filtrar sin consultar.
    private List<ReservaDeHotel> reservas = new ArrayList<>();
    private boolean cargado;
    private String consulta = "";

    public ReservasAdminViewModel() { SessionManager.sesion().observeForever(sesion); }
    public LiveData<UiState<Secciones>> getSecciones() { return secciones; }
    public String getConsulta() { return consulta; }

    /** Filtra por nombre del cliente, DNI o código. Vacío muestra todo. */
    public void buscar(@Nullable String texto) {
        String nueva = texto == null ? "" : texto;
        if (nueva.equals(consulta)) return;
        consulta = nueva;
        if (cargado) publicar();
    }

    private void publicar() {
        if (reservas.isEmpty()) {
            secciones.setValue(UiState.empty());
            return;
        }
        // Con filtro puede no coincidir ninguna: sigue siendo éxito y la pantalla
        // lo explica, en vez de confundirlo con "este hotel no tiene reservas".
        secciones.setValue(UiState.success(
                agrupar(ConsultaReservasAdmin.aplicar(reservas, consulta))));
    }

    private void revisarSesion() {
        String nuevaUid = SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                ? SessionManager.getUsuarioId() : null;
        String nuevoHotel = SessionManager.getHotelAdministrado();
        if (Objects.equals(uid, nuevaUid) && Objects.equals(hotelId, nuevoHotel)) return;
        cerrarEscucha();
        uid = nuevaUid;
        hotelId = nuevoHotel;
        // Otra cuenta u otro hotel: ni la lista ni la búsqueda anteriores valen.
        reservas = new ArrayList<>();
        cargado = false;
        consulta = "";
        secciones.setValue(UiState.loading());
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

    public void recargar() {
        cerrarEscucha();
        secciones.setValue(UiState.loading());
        if (visible) conectar();
    }

    private void conectar() {
        if (!visible || suscripcion != null) return;
        if (uid == null || hotelId == null) {
            secciones.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            return;
        }
        final String cuenta = uid;
        final String hotel = hotelId;
        final long version = ++generacion;
        suscripcion = ServiceLocator.gestion().escucharReservas(hotel,
                new ResultCallback<List<ReservaDeHotel>>() {
                    @Override public void onExito(@NonNull List<ReservaDeHotel> datos) {
                        if (!vigente(cuenta, hotel, version)) return;
                        reservas = new ArrayList<>(datos);
                        cargado = true;
                        publicar();
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (vigente(cuenta, hotel, version)) {
                            secciones.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    private boolean vigente(String cuenta, String hotel, long version) {
        return visible && version == generacion && cuenta.equals(SessionManager.getUsuarioId())
                && hotel.equals(SessionManager.getHotelAdministrado())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL;
    }

    private void cerrarEscucha() {
        generacion++;
        if (suscripcion != null) {
            suscripcion.cerrar();
            suscripcion = null;
        }
    }

    private Secciones agrupar(List<ReservaDeHotel> reservas) {
        List<ReservaDeHotel> enCurso = new ArrayList<>();
        List<ReservaDeHotel> proximas = new ArrayList<>();
        List<ReservaDeHotel> historial = new ArrayList<>();
        for (ReservaDeHotel detalle : reservas) {
            BookingStatus estado = detalle.getEstado();
            if (estado == BookingStatus.ACTIVA) enCurso.add(detalle);
            else if (estado == BookingStatus.PENDIENTE || estado == BookingStatus.CONFIRMADA) {
                proximas.add(detalle);
            } else historial.add(detalle);
        }
        Comparator<ReservaDeHotel> porEntrada = Comparator.comparing(
                detalle -> detalle.getReserva().getFechaEntrada());
        enCurso.sort(porEntrada);
        proximas.sort(porEntrada);
        historial.sort(porEntrada.reversed());
        return new Secciones(enCurso, proximas, historial);
    }

    @Override protected void onCleared() {
        cerrarEscucha();
        SessionManager.sesion().removeObserver(sesion);
    }
}
