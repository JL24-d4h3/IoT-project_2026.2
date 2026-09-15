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

/**
 * Estado de los datos del hotel (§42, RF-010 a RF-013).
 *
 * <p>Vive en el grafo del administrador y no en el fragmento porque las dos
 * hojas que se abren desde aqui —la de los datos y la del lugar cercano—
 * tambien tienen que alcanzarlo: las dos dejan el hotel cambiado, y quien lo
 * repinta es esta pantalla. Con un estado por fragmento habria que devolver el
 * hotel de una a otra a mano.
 *
 * <p>Este ViewModel no vuelve a pedir el hotel despues de escribir. Todas las
 * operaciones de {@code GestionHotelRepository} devuelven el hotel ya
 * actualizado, asi que se publica ese y no se lanza una consulta para leer lo
 * que se acaba de escribir. Con una recarga, cada fotografia agregada seria dos
 * viajes y un parpadeo del esqueleto.
 *
 * <p>Los canales de las operaciones van separados por quien los pide, no por
 * tipo de operacion: {@link #guardado} y {@link #foto} los lee la pantalla, y
 * {@link #lugar} lo lee la hoja del lugar. Dos observadores sobre un mismo
 * estado se roban el valor: el primero que lo atiende lo limpia, y el segundo
 * recibe un nulo. Es el mismo motivo por el que la gestion de habitaciones
 * separa "guardado" de "operacion".
 */
public class HotelDatosViewModel extends ViewModel {

    /** El hotel, con sus fotografias y sus lugares cercanos. */
    private final MutableLiveData<UiState<Hotel>> hotel = new MutableLiveData<>();

    /** Resultado de guardar el formulario de datos (RF-010). */
    private final MutableLiveData<UiState<Hotel>> guardado = new MutableLiveData<>();

    /**
     * Resultado de agregar o de quitar una fotografia (RF-012, RF-013).
     *
     * <p>Las dos operaciones comparten canal porque las atiende el mismo
     * observador y de la misma forma —repintar la tira y avisar—, y separarlas
     * solo anadiria un canal mas que limpiar. Lo que no comparten es el aviso:
     * cual de los dos toca lo decide la pantalla, que es quien sabe que se
     * pulso.
     */
    private final MutableLiveData<UiState<Hotel>> foto = new MutableLiveData<>();

    /** Resultado de registrar un lugar cercano (RF-011), que pide la hoja. */
    private final MutableLiveData<UiState<Hotel>> lugar = new MutableLiveData<>();

    public LiveData<UiState<Hotel>> getHotel() {
        return hotel;
    }

    public LiveData<UiState<Hotel>> getGuardado() {
        return guardado;
    }

    public LiveData<UiState<Hotel>> getFoto() {
        return foto;
    }

    public LiveData<UiState<Hotel>> getLugar() {
        return lugar;
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    /**
     * Carga el hotel, o no hace nada si ya esta cargado.
     *
     * <p>Al rotar, el ViewModel sobrevive con el hotel dentro: volver a pasar
     * por el esqueleto dejaria la pantalla en blanco un instante sin motivo.
     */
    public void cargar() {
        UiState<Hotel> actual = hotel.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        recargar();
    }

    public void recargar() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            // No es un fallo de red: es que esta cuenta no administra nada. Se
            // dice tal cual, porque reintentar no lo arreglaria.
            hotel.setValue(UiState.<Hotel>error("Esta cuenta no tiene un hotel asignado."));
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
     * Guarda el nombre, la descripcion, la direccion y las coordenadas
     * (RF-010).
     *
     * <p>Aqui no se comprueba que los datos valgan: el repositorio es quien
     * conoce las reglas —nombre obligatorio, direccion obligatoria, coordenadas
     * dentro del mundo— y repetirlas seria tener dos versiones de la misma
     * regla, que con el tiempo discreparian. La hoja solo comprueba lo que
     * puede comprobar un campo: que no este vacio y que sea un numero.
     */
    public void guardarDatos(@NonNull String nombre, @NonNull String descripcion,
                             @NonNull String direccion, double latitud, double longitud) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        guardado.setValue(UiState.<Hotel>loading());
        ServiceLocator.gestion().actualizarDatos(hotelId, nombre, descripcion, direccion,
                latitud, longitud, new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel datos) {
                        hotel.setValue(UiState.success(datos));
                        guardado.setValue(UiState.success(datos));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        guardado.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Agrega una fotografia (RF-012). */
    public void agregarFoto(@NonNull String url) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        foto.setValue(UiState.<Hotel>loading());
        ServiceLocator.gestion().agregarFoto(hotelId, url, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                hotel.setValue(UiState.success(datos));
                foto.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                foto.setValue(UiState.error(mensaje));
            }
        });
    }

    /**
     * Quita una fotografia (RF-013).
     *
     * <p>La pantalla no ofrece quitarla cuando el hotel esta en el minimo, pero
     * la regla se comprueba igualmente en el repositorio: una condicion que solo
     * vive en la interfaz se salta sola en cuanto alguien llame a esto desde
     * otro sitio.
     */
    public void quitarFoto(@NonNull String url) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        foto.setValue(UiState.<Hotel>loading());
        ServiceLocator.gestion().quitarFoto(hotelId, url, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                hotel.setValue(UiState.success(datos));
                foto.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                foto.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Registra un lugar de interes cercano (RF-011). */
    public void agregarLugar(@NonNull NearbyPlace nuevo) {
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        lugar.setValue(UiState.<Hotel>loading());
        ServiceLocator.gestion().agregarLugarCercano(hotelId, nuevo, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel datos) {
                hotel.setValue(UiState.success(datos));
                lugar.setValue(UiState.success(datos));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                lugar.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Limpia el resultado del guardado al terminar de atenderlo. */
    public void limpiarGuardado() {
        guardado.setValue(null);
    }

    /** Limpia el resultado de la ultima operacion sobre las fotografias. */
    public void limpiarFoto() {
        foto.setValue(null);
    }

    /** Limpia el resultado del ultimo lugar registrado, al cerrarse la hoja. */
    public void limpiarLugar() {
        lugar.setValue(null);
    }
}
