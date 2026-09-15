package org.iot.project.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Hotel;
import org.iot.project.models.Pagina;
import org.iot.project.models.Review;
import org.iot.project.models.SearchQuery;
import org.iot.project.models.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a hoteles, habitaciones y resenas.
 *
 * <p>La interfaz no sabe que los datos son simulados: cuando exista un backend
 * real, se sustituye la implementacion y ninguna pantalla cambia.
 */
public interface HotelRepository {

    /**
     * Una pagina de hoteles que cumplen los filtros de la busqueda.
     *
     * <p>Se pide por paginas y no entera porque una busqueda sin destino puede
     * devolver el catalogo completo, y esa lista crece con el negocio. El
     * tamano de pagina lo decide quien cuenta los resultados —el repositorio—
     * y no la pantalla: es una decision del servicio, no de la interfaz.
     *
     * @param pagina numero de pagina, empezando en cero
     */
    void buscar(@NonNull SearchQuery filtros, int pagina,
                @NonNull ResultCallback<Pagina<Hotel>> callback);

    void obtener(@NonNull String hotelId, @NonNull ResultCallback<Hotel> callback);

    /** Hoteles destacados para el inicio, ordenados por rating. */
    void recomendados(int limite, @NonNull ResultCallback<List<Hotel>> callback);

    /** Hoteles de una ciudad, ordenados por cercania al centro. */
    void enCiudad(@NonNull String ciudad, int limite, @NonNull ResultCallback<List<Hotel>> callback);

    void ciudades(@NonNull ResultCallback<List<String>> callback);

    void resenas(@NonNull String hotelId, @NonNull ResultCallback<List<Review>> callback);

    /**
     * Habitaciones del hotel libres en el periodo indicado.
     *
     * <p>Aplica RF-032: una habitacion ya reservada en fechas que se cruzan no
     * se ofrece. La cancelada libera la habitacion.
     */
    void habitacionesDisponibles(@NonNull String hotelId,
                                 @NonNull LocalDate entrada,
                                 @NonNull LocalDate salida,
                                 @NonNull ResultCallback<List<org.iot.project.models.Room>> callback);

    /**
     * Catalogo global de servicios (§13, §17). Es sincrono a proposito: es una
     * tabla de referencia que la aplicacion ya tiene en memoria, no una
     * consulta de red.
     */
    @NonNull
    List<Service> catalogoServicios();

    /** Servicio del catalogo, o {@code null} si el identificador no existe. */
    @Nullable
    Service servicio(@NonNull String serviceId);

    /** Ciudades disponibles para el selector de destino. */
    @NonNull
    List<String> ciudadesDisponibles();

    /**
     * Distritos disponibles para el selector de destino.
     *
     * <p>Van aparte de las ciudades porque la busqueda acepta los dos: el
     * selector tiene que ofrecer exactamente lo que la busqueda entiende, o el
     * usuario vera "sin resultados" para un texto que si funciona.
     */
    @NonNull
    List<String> distritosDisponibles();

    /**
     * Precio por noche mas alto del catalogo.
     *
     * <p>Es el techo del filtro de precio (§18). Lo decide el catalogo y no la
     * pantalla: con un tope escrito a mano, o se queda corto y el usuario no
     * puede pedir "cualquiera por encima", o se pasa y ofrece media barra de
     * precios que no existen.
     */
    double precioMaximo();

    /**
     * Hotel por identificador, o {@code null} si no existe.
     *
     * <p>Sincrono por el mismo motivo que {@link #servicio(String)}: las listas
     * que solo guardan el identificador —reservas, taxis, bitacora— necesitan
     * el nombre para pintarse, y envolver cada fila en una consulta asincrona
     * obligaria a pintar la lista en dos pasos sin ganar nada.
     */
    @Nullable
    Hotel hotel(@NonNull String hotelId);
}
