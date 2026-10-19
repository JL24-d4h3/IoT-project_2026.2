package org.iot.project.ui.admin.hotel;

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
import org.iot.project.models.NearbyPlace;

import java.util.Objects;

/** Ficha compartida por las hojas; permite una escritura de hotel a la vez. */
public class HotelDatosViewModel extends ViewModel {
    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> guardado = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> foto = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> lugar = new MutableLiveData<>();
    private final MutableLiveData<UiState<Hotel>> publicacion = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocupado = new MutableLiveData<>(false);
    private boolean cargando;
    private boolean escribiendo;
    private boolean ultimaFotoAgregada = true;
    private long consulta;

    public LiveData<UiState<Hotel>> getHotel() { return hotel; }
    public LiveData<UiState<Hotel>> getGuardado() { return guardado; }
    public LiveData<UiState<Hotel>> getFoto() { return foto; }
    public LiveData<UiState<Hotel>> getLugar() { return lugar; }
    public LiveData<UiState<Hotel>> getPublicacion() { return publicacion; }
    public LiveData<Boolean> getOcupado() { return ocupado; }
    public boolean estaEscribiendo() { return escribiendo; }
    public boolean fueFotoAgregada() { return ultimaFotoAgregada; }

    public boolean puedeModificar() {
        UiState<Hotel> actual = hotel.getValue();
        return !cargando && !escribiendo && actual != null && actual.isSuccess()
                && actual.getData() != null && actual.getData().getId().equals(getHotelId());
    }

    @Nullable
    public String getHotelId() { return SessionManager.getHotelAdministrado(); }

    public void cargar() {
        UiState<Hotel> actual = hotel.getValue();
        if (actual != null && actual.isSuccess() && actual.getData() != null
                && actual.getData().getId().equals(getHotelId())) return;
        solicitar(false);
    }

    public void recargar() { solicitar(false); }

    /** Al volver de Habitaciones/Servicios se vuelve a comprobar el agregado. */
    public void refrescarAlVolver() { solicitar(true); }

    private void solicitar(boolean silencioso) {
        if (cargando || escribiendo) return;
        String hotelId = getHotelId();
        if (hotelId == null) {
            hotel.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            return;
        }
        String uid = SessionManager.getUsuarioId();
        long mia = ++consulta;
        cargando = true;
        ocupado.setValue(true);
        UiState<Hotel> anterior = hotel.getValue();
        if (!silencioso || anterior == null || !anterior.isSuccess()
                || anterior.getData() == null || !hotelId.equals(anterior.getData().getId())) {
            hotel.setValue(UiState.loading());
        }
        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                if (mia != consulta) return;
                cargando = false;
                hotel.setValue(vigente(uid, hotelId) ? UiState.success(datos)
                        : UiState.error("Tu asignación cambió. Vuelve a abrir los datos del hotel."));
                ocupado.setValue(false);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia != consulta) return;
                cargando = false;
                hotel.setValue(UiState.error(mensaje));
                ocupado.setValue(false);
            }
        });
    }

    public void guardarDatos(@NonNull String nombre, @NonNull String descripcion,
                             @NonNull String direccion, double latitud, double longitud) {
        String id = iniciar(guardado);
        if (id == null) return;
        ServiceLocator.gestion().actualizarDatos(id, nombre, descripcion, direccion,
                latitud, longitud, respuesta(id, guardado));
    }

    public void agregarFoto(@NonNull String url) {
        String id = iniciar(foto);
        if (id == null) return;
        ultimaFotoAgregada = true;
        ServiceLocator.gestion().agregarFoto(id, url, respuesta(id, foto));
    }

    public void quitarFoto(@NonNull String url) {
        String id = iniciar(foto);
        if (id == null) return;
        ultimaFotoAgregada = false;
        ServiceLocator.gestion().quitarFoto(id, url, respuesta(id, foto));
    }

    public void agregarLugar(@NonNull NearbyPlace nuevo) {
        String id = iniciar(lugar);
        if (id == null) return;
        ServiceLocator.gestion().agregarLugarCercano(id, nuevo, respuesta(id, lugar));
    }

    public void cambiarPublicacion(boolean publicado) {
        String id = iniciar(publicacion);
        if (id == null) return;
        ServiceLocator.gestion().cambiarPublicacion(id, publicado, respuesta(id, publicacion));
    }

    @Nullable
    private String iniciar(MutableLiveData<UiState<Hotel>> canal) {
        if (escribiendo || cargando) return null;
        String id = getHotelId();
        if (id == null) {
            canal.setValue(UiState.error("Todavía no tienes un hotel asignado."));
            return null;
        }
        if (!puedeModificar()) return null;
        escribiendo = true;
        ++consulta;
        ocupado.setValue(true);
        canal.setValue(UiState.loading());
        return id;
    }

    private ResultCallback<Hotel> respuesta(String id, MutableLiveData<UiState<Hotel>> canal) {
        String uid = SessionManager.getUsuarioId();
        return new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                escribiendo = false;
                if (vigente(uid, id)) {
                    hotel.setValue(UiState.success(datos));
                    ocupado.setValue(false);
                    canal.setValue(UiState.success(datos));
                } else {
                    ocupado.setValue(false);
                    canal.setValue(UiState.error("Tu asignación cambió. Vuelve a abrir los datos del hotel."));
                }
            }

            @Override
            public void onError(@NonNull String mensaje) {
                escribiendo = false;
                ocupado.setValue(false);
                canal.setValue(UiState.error(mensaje));
            }
        };
    }

    private boolean vigente(String uid, String id) {
        return uid != null && Objects.equals(uid, SessionManager.getUsuarioId())
                && id.equals(getHotelId());
    }

    public void limpiarGuardado() { limpiar(guardado); }
    public void limpiarFoto() { limpiar(foto); }
    public void limpiarLugar() { limpiar(lugar); }
    public void limpiarPublicacion() { limpiar(publicacion); }

    /** Recrear una hoja nunca cancela el estado de una escritura en curso. */
    private void limpiar(MutableLiveData<UiState<Hotel>> canal) {
        UiState<Hotel> actual = canal.getValue();
        if (actual == null || !actual.isLoading()) canal.setValue(null);
    }
}
