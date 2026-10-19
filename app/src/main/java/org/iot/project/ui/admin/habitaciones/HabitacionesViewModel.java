package org.iot.project.ui.admin.habitaciones;

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
import org.iot.project.models.Room;

import java.util.List;
import java.util.Objects;

/**
 * Estado de la gestión de habitaciones (RF-014 a RF-018).
 *
 * <p>La operación de disponibilidad tiene su propio estado y no reutiliza el de
 * la lista: retirar una habitación de la venta devuelve la habitación cambiada,
 * no la lista entera, y volver a pedirla para repintar haría parpadear la
 * pantalla cada vez que el administrador mueve un interruptor.
 */
public class HabitacionesViewModel extends ViewModel {

    private final MutableLiveData<UiState<List<Room>>> habitaciones = new MutableLiveData<>();

    /** Resultado del último cambio de disponibilidad. */
    private final MutableLiveData<UiState<Room>> operacion = new MutableLiveData<>();

    /**
     * Resultado de la última vez que se guardó una habitación desde la hoja.
     *
     * <p>Va aparte de {@link #operacion} y no es duplicación: la hoja se abre
     * desde la misma pantalla que atiende los cambios de disponibilidad, y si
     * compartieran estado la hoja se cerraría al mover un interruptor ajeno y la
     * pantalla avisaría de un guardado que hizo la hoja.
     */
    private final MutableLiveData<UiState<Room>> guardado = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ocupado = new MutableLiveData<>(false);
    private final MutableLiveData<Room> creada = new MutableLiveData<>();
    private boolean cargando;
    private boolean escribiendo;
    private String hotelCargado;
    private String cuentaCargada;
    private long solicitud;

    public LiveData<UiState<List<Room>>> getHabitaciones() {
        return habitaciones;
    }

    public LiveData<UiState<Room>> getOperacion() {
        return operacion;
    }

    public LiveData<UiState<Room>> getGuardado() {
        return guardado;
    }

    public LiveData<Boolean> getOcupado() { return ocupado; }

    public LiveData<Room> getCreada() { return creada; }

    public void consumirCreada() { creada.setValue(null); }

    public boolean estaGuardando() { return escribiendo; }

    public boolean puedeGuardar() {
        UiState<List<Room>> actual = habitaciones.getValue();
        return !cargando && !escribiendo && hotelCargado != null
                && mismaSesion(cuentaCargada, hotelCargado)
                && actual != null && (actual.isSuccess() || actual.isEmpty());
    }

    public boolean puedeCrear() {
        UiState<List<Room>> actual = habitaciones.getValue();
        return puedeGuardar() && (actual.getData() == null
                || actual.getData().size() < HotelFirebase.MAX_HABITACIONES);
    }

    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    public void cargar() {
        // Al rotar, el ViewModel sobrevive con la lista cargada: volver a pasar
        // por el esqueleto dejaría la pantalla en blanco un instante sin motivo.
        UiState<List<Room>> actual = habitaciones.getValue();
        if (actual != null && (actual.isSuccess() || actual.isEmpty())
                && hotelCargado != null && mismaSesion(cuentaCargada, hotelCargado)) {
            return;
        }
        recargar();
    }

    public void refrescarAlVolver() {
        recargar();
    }

    public void recargar() {
        if (cargando || escribiendo) return;
        String hotelId = getHotelId();
        if (hotelId == null) {
            habitaciones.setValue(UiState.<List<Room>>error(
                    "Todavía no tienes un hotel asignado."));
            return;
        }
        cargando = true;
        String uid = SessionManager.getUsuarioId();
        long peticion = ++solicitud;
        ocupado.setValue(true);
        habitaciones.setValue(UiState.<List<Room>>loading());
        ServiceLocator.gestion().habitaciones(hotelId, new ResultCallback<List<Room>>() {
            @Override
            public void onExito(@NonNull List<Room> datos) {
                if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                cargando = false;
                hotelCargado = hotelId;
                cuentaCargada = uid;
                // Un hotel sin habitaciones registradas es un estado con nombre
                // propio, no una lista vacía: la pantalla tiene que decir que no
                // hay ninguna y ofrecer registrarla. Sin esta traducción llegaría
                // como éxito y se pintaría una lista en blanco.
                habitaciones.setValue(datos.isEmpty()
                        ? UiState.<List<Room>>empty() : UiState.success(datos));
                ocupado.setValue(false);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                cargando = false;
                habitaciones.setValue(UiState.error(mensaje));
                ocupado.setValue(false);
            }
        });
    }

    /**
     * La habitación que hay ahora mismo en la lista, o {@code null}.
     *
     * <p>La hoja de edición la busca así en vez de recibirla por argumento: el
     * paquete de un Fragment solo admite tipos primitivos y serializables, y
     * meter la habitación entera obligaría a hacer serializable el modelo
     * completo. Además, así la hoja trabaja siempre sobre la versión vigente y
     * no sobre una copia que pudo quedar vieja.
     */
    @Nullable
    public Room buscarHabitacion(@NonNull String roomId) {
        UiState<List<Room>> actual = habitaciones.getValue();
        if (actual == null || actual.getData() == null) {
            return null;
        }
        for (Room habitacion : actual.getData()) {
            if (habitacion.getId().equals(roomId)) {
                return habitacion;
            }
        }
        return null;
    }

    /**
     * Registra una habitación nueva o guarda los cambios de una existente
     * (RF-014 a RF-018).
     *
     * <p>Al terminar se recarga la lista: una habitación nueva puede entrar en
     * cualquier punto del orden —el repositorio las ordena por disponibilidad y
     * precio—, y adivinar dónde va sería duplicar en la pantalla un criterio que
     * ya vive en el repositorio.
     */
    public void guardarHabitacion(@NonNull Room borrador) {
        if (!puedeGuardar()) return;
        if (!borrador.getHotelId().equals(getHotelId())) {
            guardado.setValue(UiState.error("Tu asignación cambió. Vuelve a abrir la habitación."));
            return;
        }
        boolean esNueva = buscarHabitacion(borrador.getId()) == null;
        if (esNueva && !puedeCrear()) {
            guardado.setValue(UiState.error("El hotel admite hasta 10 habitaciones."));
            return;
        }
        String uid = SessionManager.getUsuarioId();
        String hotelId = borrador.getHotelId();
        long peticion = ++solicitud;
        escribiendo = true;
        ocupado.setValue(true);
        guardado.setValue(UiState.<Room>loading());
        ServiceLocator.gestion().guardarHabitacion(borrador, new ResultCallback<Room>() {
            @Override
            public void onExito(@NonNull Room datos) {
                if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                escribiendo = false;
                guardado.setValue(UiState.success(datos));
                if (esNueva) creada.setValue(datos);
                recargar();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                escribiendo = false;
                ocupado.setValue(false);
                guardado.setValue(UiState.error(mensaje));
            }
        });
    }

    /** Limpia el resultado del guardado al terminar de atenderlo. */
    public void limpiarGuardado() {
        UiState<Room> actual = guardado.getValue();
        if (actual == null || !actual.isLoading()) guardado.setValue(null);
    }

    /**
     * Retira o devuelve una habitación a la venta (RF-014).
     *
     * <p>Al terminar se recarga la lista en lugar de sustituir la fila: el
     * repositorio ordena las disponibles primero, así que cambiar la
     * disponibilidad mueve la habitación de sitio, y dejar la fila donde estaba
     * daría una lista que dice un orden que ya no es el suyo.
     */
    public void cambiarDisponibilidad(@NonNull String roomId, boolean disponible) {
        if (!puedeGuardar()) return;
        String hotelId = getHotelId();
        if (hotelId == null) {
            return;
        }
        String uid = SessionManager.getUsuarioId();
        long peticion = ++solicitud;
        escribiendo = true;
        ocupado.setValue(true);
        operacion.setValue(UiState.<Room>loading());
        ServiceLocator.gestion().cambiarDisponibilidad(hotelId, roomId, disponible,
                new ResultCallback<Room>() {
                    @Override
                    public void onExito(@NonNull Room datos) {
                        if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                        escribiendo = false;
                        operacion.setValue(UiState.success(datos));
                        recargar();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (!aceptarRespuesta(peticion, uid, hotelId)) return;
                        escribiendo = false;
                        ocupado.setValue(false);
                        operacion.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Limpia el resultado de la operación al terminar de atenderlo. */
    public void limpiarOperacion() {
        operacion.setValue(null);
    }

    private boolean mismaSesion(@Nullable String uid, @NonNull String hotelId) {
        return uid != null && uid.equals(SessionManager.getUsuarioId())
                && Objects.equals(hotelId, getHotelId());
    }

    /** Nunca entrega resultados de una cuenta o asignación anterior. */
    private boolean aceptarRespuesta(long peticion, String uid, String hotelId) {
        if (peticion != solicitud) return false;
        if (mismaSesion(uid, hotelId)) return true;
        cargando = false;
        escribiendo = false;
        hotelCargado = null;
        cuentaCargada = null;
        guardado.setValue(null);
        operacion.setValue(null);
        creada.setValue(null);
        habitaciones.setValue(UiState.error("Tu asignación cambió. Vuelve a abrir Habitaciones."));
        ocupado.setValue(false);
        return false;
    }

    @Override
    protected void onCleared() {
        solicitud++;
        super.onCleared();
    }
}
