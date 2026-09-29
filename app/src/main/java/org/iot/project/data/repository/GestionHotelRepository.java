package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.CargosDeReserva;
import org.iot.project.models.ClienteDeHotel;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.models.NearbyPlace;
import org.iot.project.models.Periodicidad;
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.ResumenHotel;
import org.iot.project.models.Room;

import java.util.List;

/**
 * Lo que el administrador de hotel hace sobre su propio hotel (§42 a §45).
 *
 * <p>Va aparte de {@link HotelRepository} a proposito. Aquel es la vista del
 * cliente sobre los hoteles: consulta, nunca escribe. Meter aqui las
 * operaciones de escritura y dejar alli solo las de lectura hace que una
 * pantalla del cliente no pueda, ni por descuido, modificar un hotel —no tiene
 * a mano ningun metodo que lo haga— en vez de confiar en que no lo llame.
 *
 * <p>Todas las operaciones exigen poder gestionar el hotel indicado (RF-023).
 * La comprobacion vive en la implementacion y no en quien llama: un formulario
 * se puede saltar, un repositorio no.
 */
public interface GestionHotelRepository {

    /**
     * Todo lo que la portada del administrador necesita de su hotel (§43).
     *
     * <p>Es una sola consulta y no tres porque la pantalla es una sola: pedir
     * por separado el hotel, las estadias y los ingresos obligaria a la pantalla
     * a decidir que hacer si una llega y otra no, y ese hueco se leeria como un
     * cero.
     */
    void resumen(@NonNull String hotelId, @NonNull ResultCallback<ResumenHotel> callback);

    /**
     * Todas las habitaciones del hotel, disponibles o no (RF-014).
     *
     * <p>A diferencia de {@code HotelRepository.habitacionesDisponibles}, que
     * filtra por fechas, esta las devuelve todas: el administrador necesita ver
     * tambien la que retiro de la venta, que es justo la que fue a buscar.
     */
    void habitaciones(@NonNull String hotelId, @NonNull ResultCallback<List<Room>> callback);

    /**
     * Todas las reservas del hotel, de la mas reciente a la mas antigua (§44).
     *
     * <p>Devuelve el cruce ya hecho —reserva, cliente y habitacion— y no las
     * reservas sueltas: la lista tiene que decir quien llega y a que habitacion,
     * y esos dos datos viven en otros dos almacenes que la pantalla no debe
     * conocer (reglas 33-35).
     *
     * <p>Canceladas incluidas, al contrario que la portada: aqui el
     * administrador viene a consultar el historial completo, y una reserva
     * cancelada explica por que una habitacion volvio a estar libre.
     */
    void reservas(@NonNull String hotelId, @NonNull ResultCallback<List<ReservaDeHotel>> callback);

    /**
     * Una reserva del hotel, con su cliente y su habitacion ya resueltos.
     *
     * <p>Existe aparte de {@link #reservas} porque el detalle se puede abrir sin
     * haber pasado por la lista —al volver de un giro de pantalla, el sistema
     * reconstruye el fragmento directamente—, y buscarla entre todas obligaria
     * al detalle a traerse el historial entero del hotel para enseñar una fila.
     *
     * <p>Falla si la reserva no es de este hotel, en vez de devolverla: es la
     * misma comprobacion que hace {@link #reservas} con la lista, y sin ella el
     * identificador de una reserva ajena bastaria para leerla.
     */
    void reserva(@NonNull String hotelId, @NonNull String bookingId,
                 @NonNull ResultCallback<ReservaDeHotel> callback);

    /**
     * Los clientes que se han alojado en el hotel, del mas reciente al mas
     * antiguo (§44).
     *
     * <p>Devuelve a las personas, no a las reservas: cada entrada resume todas
     * las estancias de un mismo cliente en este hotel. Es la vista que §42 pide
     * con "mas densidad informativa" —quien vuelve, cuanto deja— y la que
     * permite reconocer en recepcion a alguien que ya estuvo.
     *
     * <p>Solo entran los clientes que llegaron a ocupar una habitacion, y solo
     * se cuentan las estancias de <em>este</em> hotel: un cliente que tambien se
     * alojo en otro no trae aqui lo que gasto alli (RF-023).
     */
    void clientes(@NonNull String hotelId,
                  @NonNull ResultCallback<List<ClienteDeHotel>> callback);

    /**
     * Los cobros adicionales del hotel, agrupados por estadia (RF-051 a RF-054).
     *
     * <p>Devuelve una entrada por estadia con cobros, y no una por cobro, porque
     * RF-054 pide poder asociar cada cobro con su reserva: agrupados, la
     * asociacion se ve —el cobro cuelga de la estadia a la que pertenece— en vez
     * de tener que deducirse leyendo un codigo repetido en cada fila.
     *
     * <p>Las estadias sin cobros no aparecen. Una lista de cobros que incluyera
     * las que no tienen ninguno seria casi toda filas en cero, y quien entra
     * aqui viene a ver lo que se cobro.
     */
    void cargos(@NonNull String hotelId,
                @NonNull ResultCallback<List<CargosDeReserva>> callback);

    /**
     * Registra o actualiza una habitacion (RF-014 a RF-018).
     *
     * <p>Si el identificador ya existe en el hotel, actualiza; si no —o si viene
     * sin identificador, que es como llega una habitacion nueva—, la anade. El
     * tipo, el numero, la capacidad de adultos y ninos, el area y el precio son
     * obligatorios: una habitacion sin ellos no se puede ofrecer.
     */
    void guardarHabitacion(@NonNull Room habitacion, @NonNull ResultCallback<Room> callback);

    /** Retira o devuelve una habitacion a la venta (RF-014). */
    void cambiarDisponibilidad(@NonNull String hotelId, @NonNull String roomId,
                               boolean disponible, @NonNull ResultCallback<Room> callback);

    /**
     * Asocia un servicio del catalogo al hotel (§45, RF-019 a RF-021).
     *
     * <p>Recibe el servicio <em>del catalogo</em> mas el precio y si va
     * incluido, porque esas dos cosas pertenecen a la relacion hotel-servicio y
     * no al catalogo (regla 8). Si el servicio ya estaba asociado, lo reemplaza
     * en vez de duplicarlo.
     */
    void asignarServicio(@NonNull HotelService asignacion, @NonNull ResultCallback<Hotel> callback);

    /** Desvincula un servicio del hotel. El catalogo global no se toca. */
    void quitarServicio(@NonNull String hotelId, @NonNull String serviceId,
                        @NonNull ResultCallback<Hotel> callback);

    /** Nombre, descripcion, direccion y coordenadas del hotel (RF-010). */
    void actualizarDatos(@NonNull String hotelId, @NonNull String nombre,
                         @NonNull String descripcion, @NonNull String direccion,
                         double latitud, double longitud,
                         @NonNull ResultCallback<Hotel> callback);

    /**
     * Publica el hotel o lo retira del catalogo (RF-007).
     *
     * <p>Publicar exige que el hotel este completo —fotografias y habitaciones—;
     * retirar no tiene condiciones. La regla la aplica el repositorio y no quien
     * llama: un formulario se puede saltar, un repositorio no.
     */
    void cambiarPublicacion(@NonNull String hotelId, boolean publicado,
                            @NonNull ResultCallback<Hotel> callback);

    /** Anade una fotografia. Falla si la direccion viene vacia (RF-012). */
    void agregarFoto(@NonNull String hotelId, @NonNull String url,
                     @NonNull ResultCallback<Hotel> callback);

    /** Quita una fotografia. Falla si dejaria el hotel por debajo del minimo (RF-013). */
    void quitarFoto(@NonNull String hotelId, @NonNull String url,
                    @NonNull ResultCallback<Hotel> callback);

    /**
     * Registra un lugar de interes cercano al hotel (RF-011).
     *
     * <p>No hay operacion para quitarlos: RF-011 solo pide registrarlos, y un
     * lugar historico cercano no deja de estarlo porque el hotel lo borre del
     * sistema. Si algun dia hace falta corregir uno mal escrito, la operacion
     * que falta es la de editar, no la de borrar.
     */
    void agregarLugarCercano(@NonNull String hotelId, @NonNull NearbyPlace lugar,
                             @NonNull ResultCallback<Hotel> callback);

    /**
     * Reservas y ventas del hotel agrupadas por periodo (RF-055 a RF-058).
     *
     * <p>Una sola consulta para los tres reportes —diario, mensual y anual— que
     * solo se diferencian en la granularidad: separarlas en tres obligaria a
     * triplicar la misma suma y a que las tres pudieran divergir.
     *
     * <p>Solo entran las reservas que generan ingreso, con el mismo criterio que
     * {@link #ingresosPorServicios}: una pendiente todavia no es una venta y una
     * cancelada nunca lo fue. Que el criterio sea el mismo no es una coincidencia
     * —es lo que hace que el desglose por servicios sume dentro del total de
     * ventas y los dos reportes se puedan leer juntos.
     *
     * <p>Vienen del periodo mas reciente al mas antiguo: se consulta el reporte
     * para ver como va el mes, y eso esta al final de la lista.
     */
    void ventas(@NonNull String hotelId, @NonNull Periodicidad periodicidad,
                @NonNull ResultCallback<List<PeriodoDeVentas>> callback);

    /**
     * Ingresos por servicios adicionales, de menor a mayor monto (RF-060, RF-061).
     *
     * <p>Solo cuenta servicios <em>adicionales</em>: uno incluido no genera
     * ingreso, y sumarlo inflaria el reporte con dinero que nadie pago.
     *
     * <p>El orden ascendente lo fija RF-061 y es lo contrario de lo que uno
     * pediria por instinto. No se ordena en la pantalla: si el reporte cambia
     * de criterio, cambia en un sitio.
     */
    void ingresosPorServicios(@NonNull String hotelId,
                              @NonNull ResultCallback<List<IngresoPorServicio>> callback);
}
