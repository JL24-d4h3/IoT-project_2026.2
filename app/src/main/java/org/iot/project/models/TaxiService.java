package org.iot.project.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Servicio de taxi desde el hotel hacia un aeropuerto (RF-083, RT-011).
 *
 * <p>Es gratuito cuando la reserva alcanza el monto minimo del hotel
 * (RF-084, RT-012) y siempre opcional para el cliente (RF-085, RT-013).
 */
public class TaxiService {

    /**
     * Tarifa plana del traslado al aeropuerto cuando la reserva no cubre el
     * minimo del hotel (RT-012). Es una tarifa unica para toda la ciudad, no
     * depende de la distancia: el servicio se contrata como extras fijo.
     */
    public static final double TARIFA_AEROPUERTO = 120d;

    private final String id;
    private final String codigo;
    private final String bookingId;
    private final String clienteId;
    private String origen;
    private String destino;
    private LocalDate fecha;
    private LocalTime hora;
    private int numPasajeros = 1;
    private boolean idaYVuelta;
    private double precio;
    private Driver driver;
    private TaxiStatus estado = TaxiStatus.SOLICITADO;

    /** Nota que el cliente puso al conductor (RF-105). Cero si aun no valoro. */
    private float ratingCliente;

    private double latConductor;
    private double lngConductor;
    private LocalDateTime ultimaActualizacionUbicacion;

    /**
     * Coordenadas del punto de recojo.
     *
     * <p>Viajan con el servicio y no se deducen del hotel de la reserva cada
     * vez que hacen falta: el punto de recojo es un dato del traslado, y quien
     * lo dibuja (RF-099) o lo usa para acercar al conductor (RF-098) tiene que
     * ver exactamente el mismo, sin recorrer media aplicacion para averiguarlo.
     */
    private double latRecojo;
    private double lngRecojo;
    private boolean hayRecojo;

    public TaxiService(String id, String codigo, String bookingId, String clienteId) {
        this.id = id;
        this.codigo = codigo;
        this.bookingId = bookingId;
        this.clienteId = clienteId;
    }

    public String getId() {
        return id;
    }

    /** Identificador que se muestra bajo el QR (§39). */
    public String getCodigo() {
        return codigo;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public int getNumPasajeros() {
        return numPasajeros;
    }

    public boolean isIdaYVuelta() {
        return idaYVuelta;
    }

    public double getPrecio() {
        return precio;
    }

    /** El servicio es gratuito cuando la reserva supero el minimo del hotel (RT-012). */
    public boolean isGratuito() {
        return precio <= 0d;
    }

    public Driver getDriver() {
        return driver;
    }

    public TaxiStatus getEstado() {
        return estado;
    }

    /** La nota que el cliente puso al conductor, o cero si todavia no valoro. */
    public float getRatingCliente() {
        return ratingCliente;
    }

    /** RF-105: un servicio cerrado se puede valorar una sola vez. */
    public boolean isValorado() {
        return ratingCliente > 0f;
    }

    /**
     * Guarda la valoracion del cliente.
     *
     * @throws IllegalStateException si el servicio aun no termino, o si ya
     *                               estaba valorado.
     */
    public void valorar(float rating) {
        if (!estado.isFinished()) {
            throw new IllegalStateException("Solo puedes valorar un servicio terminado.");
        }
        if (isValorado()) {
            throw new IllegalStateException("Ya valoraste este servicio.");
        }
        this.ratingCliente = rating;
    }

    public double getLatConductor() {
        return latConductor;
    }

    public double getLngConductor() {
        return lngConductor;
    }

    public LocalDateTime getUltimaActualizacionUbicacion() {
        return ultimaActualizacionUbicacion;
    }

    public double getLatRecojo() {
        return latRecojo;
    }

    public double getLngRecojo() {
        return lngRecojo;
    }

    /** true si el servicio sabe donde recoge: sin esto no hay nada que seguir. */
    public boolean hasRecojo() {
        return hayRecojo;
    }

    /** Fija el punto de recojo del traslado. */
    public TaxiService withRecojo(double latitud, double longitud) {
        this.latRecojo = latitud;
        this.lngRecojo = longitud;
        this.hayRecojo = true;
        return this;
    }

    public TaxiService withRuta(String origen, String destino) {
        this.origen = origen;
        this.destino = destino;
        return this;
    }

    public TaxiService withProgramacion(LocalDate fecha, LocalTime hora, boolean idaYVuelta) {
        this.fecha = fecha;
        this.hora = hora;
        this.idaYVuelta = idaYVuelta;
        return this;
    }

    public TaxiService withPasajeros(int numPasajeros) {
        this.numPasajeros = Math.max(1, numPasajeros);
        return this;
    }

    /** Un precio de cero significa que aplica el beneficio de taxi gratuito. */
    public TaxiService withPrecio(double precio) {
        this.precio = Math.max(0d, precio);
        return this;
    }

    /**
     * RF-107: aceptar un pedido mueve el estado a ASIGNADO y deja el servicio
     * ligado al conductor que lo acepto (RF-091).
     *
     * @throws IllegalStateException si el servicio ya no esta en SOLICITADO.
     */
    public void asignarA(Driver driver) {
        if (estado != TaxiStatus.SOLICITADO) {
            throw new IllegalStateException(
                    "Solo se puede asignar un servicio en estado SOLICITADO, no " + estado);
        }
        this.driver = driver;
        this.estado = TaxiStatus.ASIGNADO;
    }

    /**
     * RF-111, RC-016: solo se permiten las transiciones del flujo definido.
     *
     * @throws IllegalStateException si el salto de estado no es valido.
     */
    public void avanzarA(TaxiStatus siguiente) {
        if (!estado.canTransitionTo(siguiente)) {
            throw new IllegalStateException(
                    "Transicion no permitida: " + estado + " -> " + siguiente);
        }
        this.estado = siguiente;
    }

    public void actualizarUbicacion(double latitud, double longitud, LocalDateTime momento) {
        this.latConductor = latitud;
        this.lngConductor = longitud;
        this.ultimaActualizacionUbicacion = momento;
    }

    /** RC-027: la ubicacion mostrada puede estar desactualizada. */
    public boolean ubicacionDesactualizada(LocalDateTime ahora, long umbralSegundos) {
        if (ultimaActualizacionUbicacion == null) {
            return true;
        }
        return java.time.Duration.between(ultimaActualizacionUbicacion, ahora).getSeconds()
                > umbralSegundos;
    }

    /** RF-101: el QR solo se muestra cuando el conductor ya esta en camino. */
    public boolean debeMostrarQr() {
        return estado.allowsQrDisplay() && driver != null;
    }

    public boolean tieneConductorAsignado() {
        return driver != null;
    }
}
