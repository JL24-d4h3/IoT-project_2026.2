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
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado de la configuración de servicios del hotel (§45, RF-019 a RF-022).
 *
 * <p>Compartido entre la pantalla y la hoja de agregar o editar: la hoja no
 * tiene hotel propio y, al guardar, lo que cambia es el de la pantalla. Con dos
 * ViewModel distintos habría que devolver el hotel por el resultado de la hoja,
 * y ese viaje de ida y vuelta es justo lo que el ViewModel compartido evita.
 *
 * <p>Hay dos estados y no uno porque son dos momentos: el de la pantalla —que
 * se carga una vez— y el de la operación —que empieza y termina dentro de la
 * hoja—. Mezclarlos dejaría la pantalla entera en esqueleto cada vez que se
 * guarda un servicio.
 */
public class ServiciosViewModel extends ViewModel {

    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();

    /** Resultado de la última operación de la hoja. */
    private final MutableLiveData<UiState<Hotel>> operacion = new MutableLiveData<>();

    public LiveData<UiState<Hotel>> getHotel() {
        return hotel;
    }

    public LiveData<UiState<Hotel>> getOperacion() {
        return operacion;
    }

    /** El catálogo global (§13, §17). Es síncrono: es una lista en memoria. */
    @NonNull
    public List<Service> getCatalogo() {
        return ServiceLocator.hoteles().catalogoServicios();
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    public void cargar() {
        // Al rotar, el ViewModel sobrevive con el hotel ya cargado: volver a
        // pasar por el esqueleto dejaría la pantalla en blanco un instante sin
        // motivo.
        UiState<Hotel> actual = hotel.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        recargar();
    }

    public void recargar() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            hotel.setValue(UiState.<Hotel>error("Todavía no tienes un hotel asignado."));
            return;
        }
        hotel.setValue(UiState.<Hotel>loading());
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                hotel.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                hotel.setValue(UiState.error(mensaje));
            }
        });
    }

    /**
     * Asocia el servicio al hotel o actualiza cómo se ofrece (RF-020, RF-021).
     *
     * <p>El precio y el "incluido" viajan en la relación hotel-servicio y no en
     * el servicio del catálogo: el mismo Wi-Fi es gratis en un hotel y se cobra
     * en otro, y el catálogo es compartido (§18, regla 20).
     */
    public void asignar(@NonNull String serviceId, boolean incluido, double precio) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        operacion.setValue(UiState.<Hotel>loading());
        HotelService asignacion = new HotelService(hotelId, serviceId, incluido, precio);
        ServiceLocator.gestion().asignarServicio(asignacion, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                publicar(datos);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                operacion.setValue(UiState.error(mensaje));
            }
        });
    }

    public void quitar(@NonNull String serviceId) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        operacion.setValue(UiState.<Hotel>loading());
        ServiceLocator.gestion().quitarServicio(hotelId, serviceId,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel datos) {
                        publicar(datos);
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        operacion.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Publica el hotel que devolvió la operación en los dos estados.
     *
     * <p>Los dos, porque el mismo hotel es el de la pantalla y el resultado de
     * la hoja: la pantalla se repinta con lo que acaba de guardarse y la hoja se
     * entera de que puede cerrarse. Se publica el objeto que devuelve el
     * repositorio y no una copia, para que lo que se vea sea exactamente lo que
     * quedó guardado.
     */
    private void publicar(@NonNull Hotel datos) {
        hotel.setValue(UiState.success(datos));
        operacion.setValue(UiState.success(datos));
    }

    /** Limpia el resultado de la operación al cerrar la hoja. */
    public void limpiarOperacion() {
        operacion.setValue(null);
    }

    /**
     * Los servicios del catálogo que el hotel todavía no ofrece (§45).
     *
     * <p>Se calcula aquí y no en la pantalla porque la pantalla ya no tiene el
     * catálogo a mano: lo que necesita para pintar son las relaciones con el
     * hotel, y comparar unas con otro es una consulta, no un pintado.
     */
    @NonNull
    public List<Service> catalogoDisponible(@Nullable Hotel hotelActual) {
        List<Service> libres = new ArrayList<>();
        if (hotelActual == null) {
            return libres;
        }
        for (Service servicio : getCatalogo()) {
            if (!ofrece(hotelActual, servicio.getServiceId())) {
                libres.add(servicio);
            }
        }
        return libres;
    }

    private static boolean ofrece(@NonNull Hotel hotel, @NonNull String serviceId) {
        for (HotelService asignado : hotel.getServicios()) {
            if (asignado.getServiceId().equals(serviceId)) {
                return true;
            }
        }
        return false;
    }
}
