package org.iot.project.ui.client.taxi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.models.TaxiService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Estado del modulo de taxi (§35 a §39).
 *
 * <p>Expone <em>dos</em> estados y no uno porque son dos modulos distintos: el
 * servicio de taxi y las reservas. Si el primero se cae —RC-019 a RC-022 dan
 * por hecho que puede—, la pantalla lo dice y se acaba ahi; lo que no puede es
 * arrastrar consigo al selector de reservas del formulario, que depende de
 * otro repositorio y sigue vivo.
 *
 * <p>El servicio en curso y el historial salen de la <em>misma</em> consulta.
 * El historial devuelve todos los servicios del cliente, el activo incluido:
 * partirlo en dos llamadas daria dos fotografias distintas del mismo dato y
 * bastaria con que una llegara antes que la otra para que el servicio apareciera
 * a la vez arriba y en la lista de abajo.
 */
public class TaxiViewModel extends ViewModel {

    /** Lo que el cliente tiene hoy en la pantalla de taxi. */
    public static final class Estado {

        /** Servicio en curso, o {@code null} si no hay ninguno. */
        @Nullable
        public final TaxiService activo;

        /** Servicios ya cerrados, del mas reciente al mas antiguo. */
        @NonNull
        public final List<TaxiService> anteriores;

        Estado(@Nullable TaxiService activo, @NonNull List<TaxiService> anteriores) {
            this.activo = activo;
            this.anteriores = anteriores;
        }

        public boolean tieneActivo() {
            return activo != null;
        }
    }

    private final MutableLiveData<UiState<Estado>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Booking>>> reservas = new MutableLiveData<>();

    /**
     * Hotel de la reserva elegida en el formulario.
     *
     * <p>Se publica aparte de {@link #reservas} porque se pide despues y solo
     * para una: meterlo en la misma lista obligaria a recargar todas las
     * reservas cada vez que el cliente cambia de seleccion.
     */
    private final MutableLiveData<UiState<Hotel>> hotelElegido = new MutableLiveData<>();

    @Nullable
    private Booking reserva;
    @Nullable
    private Hotel hotel;

    private boolean cargando;

    public LiveData<UiState<Estado>> getContenido() {
        return contenido;
    }

    public LiveData<UiState<List<Booking>>> getReservas() {
        return reservas;
    }

    public LiveData<UiState<Hotel>> getHotelElegido() {
        return hotelElegido;
    }

    // ------------------------------------------------------------------
    //  Carga
    // ------------------------------------------------------------------

    public void cargar() {
        if (cargando) {
            return;
        }
        // Al rotar la pantalla el ViewModel sobrevive y ya tiene los datos: la
        // segunda carga solo serviria para volver a pasar por el esqueleto y
        // dejar la pantalla en blanco un instante. El refresco de verdad lo
        // hace onResume, que no borra lo que ya se ve.
        UiState<Estado> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        cargarServicios();
        cargarReservas();
    }

    /**
     * Refresco silencioso al volver a la pantalla.
     *
     * <p>Sin esqueleto: la pantalla ya esta pintada, y volver de otra pantalla
     * con la lista en blanco un instante se lee como si se hubiera perdido algo.
     */
    public void refrescarEnSilencio() {
        if (!cargando) {
            cargando = true;
            cargarServicios();
            cargarReservas();
        }
    }

    private void cargarServicios() {
        ServiceLocator.taxis().historial(SessionManager.getUsuarioIdSeguro(),
                new ResultCallback<List<TaxiService>>() {
                    @Override
                    public void onExito(@NonNull List<TaxiService> datos) {
                        cargando = false;
                        contenido.setValue(UiState.success(separar(datos)));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        cargando = false;
                        contenido.setValue(UiState.error(mensaje));
                    }
                });
    }

    /**
     * Reparte los servicios del cliente en el que esta en curso y los demas.
     *
     * <p>El corte lo marca el estado, no la fecha: un servicio pedido para la
     * semana que viene sigue pendiente mientras no termine, y no es un recuerdo.
     *
     * <p>Cuando hay mas de uno sin cerrar —un traslado que ya va en camino y
     * otro pedido para mas adelante—, el que se enseña arriba es el que mas
     * avanzado va, no el de fecha mas lejana. La lista llega ordenada por fecha
     * descendente, asi que quedarse con el primero dejaba al cliente mirando una
     * solicitud todavia sin conductor mientras su taxi de ahora mismo caia al
     * fondo, como si ya hubiera pasado.
     */
    @NonNull
    private static Estado separar(@NonNull List<TaxiService> servicios) {
        TaxiService activo = null;
        for (TaxiService servicio : servicios) {
            if (!servicio.getEstado().isActive()) {
                continue;
            }
            if (activo == null
                    || servicio.getEstado().ordinal() > activo.getEstado().ordinal()) {
                activo = servicio;
            }
        }

        List<TaxiService> anteriores = new ArrayList<>();
        for (TaxiService servicio : servicios) {
            if (servicio != activo) {
                anteriores.add(servicio);
            }
        }
        return new Estado(activo, anteriores);
    }

    /**
     * Reservas sobre las que se puede pedir un traslado.
     *
     * <p>Una reserva cancelada no da derecho a nada, y una ya terminada no
     * tiene sentido como origen de un traslado futuro. El resto si: el servicio
     * puede ser gratuito (RF-083) o de pago, y esa diferencia la explica la
     * propia tarjeta de la reserva.
     */
    private void cargarReservas() {
        ServiceLocator.reservas().reservasDe(SessionManager.getUsuarioIdSeguro(),
                new ResultCallback<List<Booking>>() {
                    @Override
                    public void onExito(@NonNull List<Booking> datos) {
                        List<Booking> utiles = new ArrayList<>();
                        for (Booking reserva : datos) {
                            if (reserva.getEstado() != BookingStatus.CANCELADA) {
                                utiles.add(reserva);
                            }
                        }
                        reservas.setValue(utiles.isEmpty()
                                ? UiState.<List<Booking>>empty()
                                : UiState.success(utiles));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        reservas.setValue(UiState.<List<Booking>>error(mensaje));
                    }
                });
    }

    // ------------------------------------------------------------------
    //  Acciones
    // ------------------------------------------------------------------

    /**
     * El cliente eligio una reserva en el formulario.
     *
     * <p>Se pide el hotel ademas de guardar la reserva porque de el salen dos
     * datos que el formulario no puede inventar: la direccion de recojo con la
     * que se rellena el campo, y las coordenadas que el plano de seguimiento
     * necesita. Sin hotel no hay punto de recojo, y el repositorio rechazaria
     * la solicitud al confirmarla.
     */
    public void elegirReserva(@NonNull Booking elegida) {
        reserva = elegida;
        hotel = null;
        hotelElegido.setValue(UiState.loading());
        ServiceLocator.hoteles().obtener(elegida.getHotelId(), new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel dato) {
                // Si mientras se cargaba el hotel el cliente eligio otra
                // reserva, esta respuesta ya no describe lo que hay en pantalla.
                if (reserva != elegida) {
                    return;
                }
                hotel = dato;
                hotelElegido.setValue(UiState.success(dato));
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (reserva != elegida) {
                    return;
                }
                hotelElegido.setValue(UiState.error(mensaje));
            }
        });
    }

    /** El formulario esta listo para enviarse: hay reserva y sabemos donde recoger. */
    public boolean puedeSolicitar() {
        return reserva != null && hotel != null;
    }

    public boolean esGratuito() {
        return reserva != null && hotel != null
                && reserva.calificaParaTaxiGratuito(hotel.getMontoMinimoTaxi());
    }

    /**
     * Arma la solicitud con los datos del formulario.
     *
     * <p>El armado vive aqui y no en la pantalla porque lo que hace valida a
     * una solicitud —el cliente, la reserva, el punto de recojo— no lo elige el
     * usuario: lo elige la sesion y la reserva que selecciono. Dejar esos campos
     * en manos del fragment seria repartir la regla por dos sitios.
     */
    public void solicitar(@NonNull String origen, @NonNull String destino,
                          @NonNull LocalDate fecha, @NonNull LocalTime hora,
                          int pasajeros, boolean idaYVuelta,
                          @NonNull ResultCallback<TaxiService> callback) {
        if (reserva == null || hotel == null) {
            callback.onError("Elige la reserva del traslado.");
            return;
        }

        TaxiService solicitud = new TaxiService("nuevo", "", reserva.getId(),
                SessionManager.getUsuarioIdSeguro());
        solicitud.withRuta(origen.trim(), destino.trim())
                .withRecojo(hotel.getLatitud(), hotel.getLongitud())
                .withProgramacion(fecha, hora, idaYVuelta)
                .withPasajeros(pasajeros)
                .withPrecio(esGratuito() ? 0d : TaxiService.TARIFA_AEROPUERTO);

        ServiceLocator.taxis().solicitar(solicitud, new ResultCallback<TaxiService>() {
            @Override
            public void onExito(@NonNull TaxiService dato) {
                // El servicio recien pedido ya es el estado vigente, asi que se
                // pinta sin volver a preguntar: una segunda consulta dejaria al
                // cliente mirando el formulario un momento mas, y el formulario
                // ya no tiene nada que hacer.
                publicarCon(dato);
                callback.onExito(dato);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                callback.onError(mensaje);
            }
        });
    }

    /** RF-105: la nota del cliente sobre un traslado ya terminado. */
    public void valorar(@NonNull String taxiId, float rating,
                        @NonNull ResultCallback<TaxiService> callback) {
        ServiceLocator.taxis().calificar(taxiId, rating, new ResultCallback<TaxiService>() {
            @Override
            public void onExito(@NonNull TaxiService dato) {
                // El historial se rehace con el servicio ya valorado para que la
                // tarjeta deje de ofrecer "Valorar": si no, el cliente volveria a
                // pulsarlo y el repositorio lo rechazaria por repetido.
                reemplazar(dato);
                callback.onExito(dato);
            }

            @Override
            public void onError(@NonNull String mensaje) {
                callback.onError(mensaje);
            }
        });
    }

    /** Cambia un servicio del estado actual por su version nueva. */
    private void reemplazar(@NonNull TaxiService actualizado) {
        UiState<Estado> actual = contenido.getValue();
        if (actual == null || actual.getData() == null) {
            return;
        }
        Estado estado = actual.getData();
        List<TaxiService> todos = new ArrayList<>();
        if (estado.activo != null) {
            todos.add(estado.activo.getId().equals(actualizado.getId())
                    ? actualizado : estado.activo);
        }
        for (TaxiService anterior : estado.anteriores) {
            todos.add(anterior.getId().equals(actualizado.getId()) ? actualizado : anterior);
        }
        contenido.setValue(UiState.success(separar(todos)));
    }

    /** Rehace el reparto añadiendo un servicio sin perder lo que ya habia. */
    private void publicarCon(@NonNull TaxiService nuevo) {
        List<TaxiService> todos = new ArrayList<>();
        UiState<Estado> actual = contenido.getValue();
        if (actual != null && actual.getData() != null) {
            Estado estado = actual.getData();
            if (estado.activo != null) {
                todos.add(estado.activo);
            }
            todos.addAll(estado.anteriores);
        }
        todos.add(nuevo);
        contenido.setValue(UiState.success(separar(todos)));
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }
}
