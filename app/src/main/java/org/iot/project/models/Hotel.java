package org.iot.project.models;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hotel: raiz de agregado de habitaciones, servicios y fotografias.
 *
 * <p>Las listas viven aqui porque el repositorio entrega el hotel ya armado;
 * cuando en la Fase 3 los datos vengan de NoSQL, sera el repositorio quien
 * componga el objeto y ninguna pantalla tendra que cambiar.
 */
public class Hotel {

    /** Minimo obligatorio de fotografias por hotel (RF-013, regla 14). */
    public static final int MIN_FOTOS = 4;

    private final String id;
    private String nombre;
    private String ciudad;
    private String distrito;
    private String direccion;
    private String descripcion;
    private float rating;
    private int numReviews;
    private double precioDesde;
    private double montoMinimoTaxi;
    private double latitud;
    private double longitud;

    /** Si el hotel ya se ofrece en el catalogo del cliente (RF-007). */
    private boolean publicado;

    /** Administrador asignado, o {@code null} si todavia no tiene (RF-008). */
    @Nullable
    private String administradorId;

    private final List<String> fotos = new ArrayList<>();
    private final List<NearbyPlace> lugaresCercanos = new ArrayList<>();
    private final List<Room> habitaciones = new ArrayList<>();
    private final List<HotelService> servicios = new ArrayList<>();

    public Hotel(String id, String nombre, String distrito, String ciudad) {
        this.id = id;
        this.nombre = nombre;
        this.distrito = distrito;
        this.ciudad = ciudad;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getDistrito() {
        return distrito;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Calificacion de 1 a 10 (regla 9). Nunca se representa con estrellas 1-5. */
    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = Math.max(1f, Math.min(10f, rating));
    }

    public int getNumReviews() {
        return numReviews;
    }

    public void setNumReviews(int numReviews) {
        this.numReviews = numReviews;
    }

    /**
     * El "Desde S/ ..." del hotel, que es el de su habitacion mas barata.
     *
     * <p>Delega en {@link #calcularPrecioDesde()} y no devuelve el campo tal
     * cual: un hotel recien registrado no tiene precio propio -- el alta del
     * superadministrador no lo pide -- y el precio lo pone su administrador al
     * cargar habitaciones. Si se devolviera el campo, todo hotel nacido del alta
     * se anunciaria en el catalogo como "Desde S/ 0".
     */
    public double getPrecioDesde() {
        return calcularPrecioDesde();
    }

    /**
     * Fija el precio de respaldo: el que se anuncia mientras el hotel no tenga
     * habitaciones cargadas. En cuanto tenga una, manda la mas barata.
     */
    public void setPrecioDesde(double precioDesde) {
        this.precioDesde = precioDesde;
    }

    /** Monto minimo de reserva para acceder al taxi gratuito (RF-084, RT-012). */
    public double getMontoMinimoTaxi() {
        return montoMinimoTaxi;
    }

    public void setMontoMinimoTaxi(double montoMinimoTaxi) {
        this.montoMinimoTaxi = montoMinimoTaxi;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setUbicacion(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public List<String> getFotos() {
        return Collections.unmodifiableList(fotos);
    }

    public List<NearbyPlace> getLugaresCercanos() {
        return Collections.unmodifiableList(lugaresCercanos);
    }

    public List<Room> getHabitaciones() {
        return Collections.unmodifiableList(habitaciones);
    }

    public List<HotelService> getServicios() {
        return Collections.unmodifiableList(servicios);
    }

    public Hotel addFoto(String url) {
        fotos.add(url);
        return this;
    }

    /**
     * Quita una fotografia. Devuelve si estaba.
     *
     * <p>No comprueba aqui el minimo de RF-013: quien decide si se puede
     * quitar es la operacion completa —sustituir una foto por otra nunca baja
     * del minimo aunque se quite primero— y esa vista la tiene el repositorio,
     * no la lista.
     */
    public boolean quitarFoto(String url) {
        return fotos.remove(url);
    }

    public Hotel addLugarCercano(NearbyPlace lugar) {
        lugaresCercanos.add(lugar);
        return this;
    }

    public Hotel addHabitacion(Room habitacion) {
        habitaciones.add(habitacion);
        return this;
    }

    /**
     * Vincula un servicio del catalogo. Si el mismo servicio ya estaba
     * asociado, lo reemplaza en vez de duplicarlo (regla 4).
     */
    public Hotel addServicio(HotelService hotelService) {
        for (int i = 0; i < servicios.size(); i++) {
            if (servicios.get(i).getServiceId().equals(hotelService.getServiceId())) {
                servicios.set(i, hotelService);
                return this;
            }
        }
        servicios.add(hotelService);
        return this;
    }

    /**
     * Desvincula un servicio del catalogo. Devuelve si estaba asociado.
     *
     * <p>Quitar un servicio del hotel no lo borra del catalogo global (§13):
     * el catalogo es de la aplicacion, la asociacion es del hotel.
     */
    public boolean quitarServicio(String serviceId) {
        for (int i = 0; i < servicios.size(); i++) {
            if (servicios.get(i).getServiceId().equals(serviceId)) {
                servicios.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Cumple el minimo de 4 fotografias exigido por RF-013. */
    public boolean cumpleMinimoFotos() {
        return fotos.size() >= MIN_FOTOS;
    }

    /** Si el hotel se ofrece en el catalogo del cliente (RF-007). */
    public boolean isPublicado() {
        return publicado;
    }

    /** Publica o retira el hotel. Retirar no tiene condiciones. */
    public void setPublicado(boolean publicado) {
        this.publicado = publicado;
    }

    /** Identificador del administrador asignado, o {@code null} si no tiene. */
    @Nullable
    public String getAdministradorId() {
        return administradorId;
    }

    public void setAdministradorId(@Nullable String administradorId) {
        this.administradorId = administradorId;
    }

    /**
     * Si el hotel reune lo minimo para ofrecerse al cliente.
     *
     * <p>Un hotel sin fotografias ni habitaciones no es publicable (RF-013,
     * RF-014): ofrecerlo seria enseñar una ficha que no dice nada. La regla vive
     * aqui y no en el repositorio porque es del hotel, no de quien lo guarda, y
     * asi la comprueban igual el administrador antes de publicar y el
     * superadministrador al mirar la ficha.
     */
    public boolean aptoParaPublicar() {
        return cumpleMinimoFotos() && !habitaciones.isEmpty();
    }

    /** "Miraflores · Lima", el subtitulo que acompana al nombre en la HotelCard. */
    public String getUbicacionCorta() {
        if (distrito == null || distrito.isEmpty()) {
            return ciudad;
        }
        return distrito + " · " + ciudad;
    }

    /** Precio mas bajo entre sus habitaciones, que es el "Desde S/ ..." de la card. */
    public double calcularPrecioDesde() {
        double minimo = Double.MAX_VALUE;
        for (Room habitacion : habitaciones) {
            minimo = Math.min(minimo, habitacion.getPrecioNoche());
        }
        return minimo == Double.MAX_VALUE ? precioDesde : minimo;
    }
}
