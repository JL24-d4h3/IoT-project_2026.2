package org.iot.project.ui.admin.servicios;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.data.firebase.HotelFirebase;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Hotel autorizado y operaciones compartidas con la hoja de servicios. */
public class ServiciosViewModel extends ViewModel {
    public static final class Confirmacion {
        public final String servicio;
        public final boolean retirado;
        Confirmacion(String servicio, boolean retirado) {
            this.servicio = servicio;
            this.retirado = retirado;
        }
    }

    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> operacion = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocupado = new MutableLiveData<>(false);
    private final MutableLiveData<Confirmacion> confirmacion = new MutableLiveData<>();
    private boolean cargando;
    private boolean escribiendo;
    private String uidCargado;
    private String hotelCargado;
    private long solicitud;

    public LiveData<UiState<Hotel>> getHotel() { return hotel; }
    public LiveData<UiState<Hotel>> getOperacion() { return operacion; }
    public LiveData<Boolean> getOcupado() { return ocupado; }
    public LiveData<Confirmacion> getConfirmacion() { return confirmacion; }
    public void consumirConfirmacion() { confirmacion.setValue(null); }
    public boolean estaGuardando() { return escribiendo; }

    @NonNull
    public List<Service> getCatalogo() {
        List<Service> activos = new ArrayList<>();
        for (Service servicio : ServiceLocator.hoteles().catalogoServicios()) {
            if (servicio.isActive()) activos.add(servicio);
        }
        return activos;
    }

    @Nullable
    public Service servicio(@NonNull String id) {
        for (Service servicio : getCatalogo()) {
            if (id.equals(servicio.getServiceId())) return servicio;
        }
        return null;
    }

    @Nullable
    public String getHotelId() { return SessionManager.getHotelAdministrado(); }

    public boolean puedeGuardar() {
        UiState<Hotel> actual = hotel.getValue();
        return !cargando && !escribiendo && actual != null && actual.isSuccess()
                && hotelCargado != null && mismaSesion(uidCargado, hotelCargado)
                && Objects.equals(actual.requireData().getAdministradorId(), uidCargado);
    }

    public boolean puedeAgregar() {
        UiState<Hotel> actual = hotel.getValue();
        return puedeGuardar() && actual.requireData().getServicios().size() < HotelFirebase.MAX_SERVICIOS
                && !catalogoDisponible(actual.requireData()).isEmpty();
    }

    @Nullable
    public HotelService asignacion(@NonNull String id) {
        UiState<Hotel> actual = hotel.getValue();
        if (actual == null || !actual.isSuccess()) return null;
        for (HotelService servicio : actual.requireData().getServicios()) {
            if (id.equals(servicio.getServiceId())) return servicio;
        }
        return null;
    }

    /** La hoja restaurada reutiliza una lectura ya autorizada. */
    public void cargar() {
        UiState<Hotel> actual = hotel.getValue();
        if (actual != null && actual.isSuccess() && hotelCargado != null
                && mismaSesion(uidCargado, hotelCargado)) return;
        recargar();
    }

    public void refrescarAlVolver() { recargar(); }

    public void recargar() {
        if (cargando || escribiendo) return;
        String id = getHotelId();
        String uid = SessionManager.getUsuarioId();
        if (id == null || uid == null) {
            hotelCargado = null;
            uidCargado = null;
            hotel.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            ocupado.setValue(false);
            return;
        }
        long peticion = ++solicitud;
        cargando = true;
        ocupado.setValue(true);
        hotel.setValue(UiState.loading());
        ServiceLocator.hoteles().obtener(id, new ResultCallback<Hotel>() {
            @Override public void onExito(@NonNull Hotel datos) {
                if (!aceptar(peticion, uid, id)) return;
                cargando = false;
                if (!id.equals(datos.getId()) || !uid.equals(datos.getAdministradorId())) {
                    hotel.setValue(UiState.error("No tienes permiso para configurar este hotel."));
                } else {
                    hotelCargado = id;
                    uidCargado = uid;
                    hotel.setValue(UiState.success(datos));
                }
                ocupado.setValue(false);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!aceptar(peticion, uid, id)) return;
                cargando = false;
                hotel.setValue(UiState.error(mensaje));
                ocupado.setValue(false);
            }
        });
    }

    public void asignar(@NonNull String serviceId, boolean incluido, double precio) {
        if (!puedeGuardar()) return;
        Service catalogo = servicio(serviceId);
        if (catalogo == null) {
            operacion.setValue(UiState.error("Ese servicio ya no está disponible en el catálogo."));
            return;
        }
        if (asignacion(serviceId) == null && !puedeAgregar()) {
            operacion.setValue(UiState.error("El hotel admite hasta 12 servicios. Edita o retira uno existente."));
            return;
        }
        if (!incluido && (!Double.isFinite(precio) || precio <= 0 || precio > 1000000)) {
            operacion.setValue(UiState.error("Indica un precio mayor que cero y no superior a S/ 1000000."));
            return;
        }
        String uid = uidCargado;
        String id = hotelCargado;
        long peticion = comenzar();
        ServiceLocator.gestion().asignarServicio(new HotelService(id, serviceId, incluido, incluido ? 0 : precio),
                resultado(peticion, uid, id, catalogo.getName(), false));
    }

    public void quitar(@NonNull String serviceId) {
        if (!puedeGuardar()) return;
        HotelService actual = asignacion(serviceId);
        if (actual == null) {
            operacion.setValue(UiState.error("Ese servicio ya no está configurado en el hotel."));
            return;
        }
        Service catalogo = servicio(serviceId);
        String uid = uidCargado;
        String id = hotelCargado;
        long peticion = comenzar();
        ServiceLocator.gestion().quitarServicio(id, serviceId,
                resultado(peticion, uid, id, catalogo != null ? catalogo.getName() : serviceId, true));
    }

    private long comenzar() {
        long peticion = ++solicitud;
        escribiendo = true;
        ocupado.setValue(true);
        operacion.setValue(UiState.loading());
        return peticion;
    }

    private ResultCallback<Hotel> resultado(long peticion, String uid, String id,
                                            String nombre, boolean retirado) {
        return new ResultCallback<Hotel>() {
            @Override public void onExito(@NonNull Hotel datos) {
                if (!aceptar(peticion, uid, id)) return;
                escribiendo = false;
                hotel.setValue(UiState.success(datos));
                operacion.setValue(UiState.success(datos));
                ocupado.setValue(false);
                confirmacion.setValue(new Confirmacion(nombre, retirado));
            }
            @Override public void onError(@NonNull String mensaje) {
                if (!aceptar(peticion, uid, id)) return;
                escribiendo = false;
                ocupado.setValue(false);
                operacion.setValue(UiState.error(mensaje));
            }
        };
    }

    public void limpiarOperacion() {
        UiState<Hotel> actual = operacion.getValue();
        if (actual == null || !actual.isLoading()) operacion.setValue(null);
    }

    @NonNull
    public List<Service> catalogoDisponible(@Nullable Hotel hotelActual) {
        List<Service> libres = new ArrayList<>();
        if (hotelActual == null) return libres;
        for (Service servicio : getCatalogo()) {
            boolean existe = false;
            for (HotelService asignado : hotelActual.getServicios()) {
                if (servicio.getServiceId().equals(asignado.getServiceId())) { existe = true; break; }
            }
            if (!existe) libres.add(servicio);
        }
        return libres;
    }

    private boolean mismaSesion(String uid, String id) {
        return uid != null && uid.equals(SessionManager.getUsuarioId())
                && Objects.equals(id, getHotelId());
    }

    private boolean aceptar(long peticion, String uid, String id) {
        if (peticion != solicitud) return false;
        if (mismaSesion(uid, id)) return true;
        cargando = false;
        escribiendo = false;
        uidCargado = null;
        hotelCargado = null;
        operacion.setValue(null);
        confirmacion.setValue(null);
        hotel.setValue(UiState.error("Tu sesión o asignación cambió. Vuelve a abrir Servicios."));
        ocupado.setValue(false);
        return false;
    }

    @Override protected void onCleared() { solicitud++; super.onCleared(); }
}
