package org.iot.project.models;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reserva de alojamiento (RF-034 a RF-044).
 *
 * <p>Es el centro del sistema: asocia cliente, hotel, habitacion, periodo,
 * servicios adicionales, cargos y pago (RF-040, RF-041, RF-042).
 */
public class Booking {

    private final String id;
    private final String codigo;
    private final String hotelId;
    private final String roomId;
    private final String clienteId;
    private String hotelNombre = "";
    private String hotelCiudad = "";
    private String hotelDistrito = "";
    private String hotelFotoUrl = "";
    private String habitacionTipo = "";
    private String habitacionNumero = "";
    private LocalDate fechaEntrada;
    private LocalDate fechaSalida;
    private int numHuespedes;
    private double precioNoche;
    private BookingStatus estado;
    private Card tarjeta;

    private final List<HotelService> serviciosAdicionales = new ArrayList<>();
    private final List<Charge> cargos = new ArrayList<>();

    private Review valoracion;

    /** Registro de cobro simulado en recepción, independiente del checkout del cliente. */
    private boolean cobrado;

    public Booking(String id,
                   String codigo,
                   String hotelId,
                   String roomId,
                   String clienteId,
                   LocalDate fechaEntrada,
                   LocalDate fechaSalida,
                   int numHuespedes,
                   double precioNoche,
                   BookingStatus estado) {
        if (!fechaSalida.isAfter(fechaEntrada)) {
            throw new IllegalArgumentException(
                    "La fecha de salida debe ser posterior a la de entrada");
        }
        this.id = id;
        this.codigo = codigo;
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.clienteId = clienteId;
        this.fechaEntrada = fechaEntrada;
        this.fechaSalida = fechaSalida;
        this.numHuespedes = numHuespedes;
        this.precioNoche = precioNoche;
        this.estado = estado;
    }

    public String getId() {
        return id;
    }

    /** Codigo corto que se muestra en la pantalla de confirmacion (§29). */
    public String getCodigo() {
        return codigo;
    }

    public String getHotelId() {
        return hotelId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getClienteId() {
        return clienteId;
    }

    /** Conserva los datos del alojamiento aunque deje de aparecer en el catálogo. */
    public Booking withAlojamiento(String nombre, String ciudad, String distrito,
                                   String fotoUrl, String tipo, String numero) {
        hotelNombre = textoSeguro(nombre);
        hotelCiudad = textoSeguro(ciudad);
        hotelDistrito = textoSeguro(distrito);
        hotelFotoUrl = textoSeguro(fotoUrl);
        habitacionTipo = textoSeguro(tipo);
        habitacionNumero = textoSeguro(numero);
        return this;
    }

    public String getHotelNombre() {
        return hotelNombre;
    }

    public String getHotelCiudad() {
        return hotelCiudad;
    }

    public String getHotelDistrito() {
        return hotelDistrito;
    }

    public String getHotelFotoUrl() {
        return hotelFotoUrl;
    }

    public String getHabitacionTipo() {
        return habitacionTipo;
    }

    public String getHabitacionNumero() {
        return habitacionNumero;
    }

    private static String textoSeguro(String texto) {
        return texto == null ? "" : texto.trim();
    }

    public LocalDate getFechaEntrada() {
        return fechaEntrada;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    public int getNumHuespedes() {
        return numHuespedes;
    }

    public double getPrecioNoche() {
        return precioNoche;
    }

    public BookingStatus getEstado() {
        return estado;
    }

    public void setEstado(BookingStatus estado) {
        this.estado = estado;
    }

    public Card getTarjeta() {
        return tarjeta;
    }

    public void setTarjeta(Card tarjeta) {
        this.tarjeta = tarjeta;
    }

    /** Si el hotel ya registró el cobro simulado después del checkout. */
    public boolean isCobrado() {
        return cobrado;
    }

    public void marcarCobrado() {
        this.cobrado = true;
    }

    public Review getValoracion() {
        return valoracion;
    }

    /** El checkout exige valoracion y observacion (RF-046, RF-047). */
    public void setValoracion(Review valoracion) {
        this.valoracion = valoracion;
    }

    public List<HotelService> getServiciosAdicionales() {
        return Collections.unmodifiableList(serviciosAdicionales);
    }

    public List<Charge> getCargos() {
        return Collections.unmodifiableList(cargos);
    }

    public void addServicioAdicional(HotelService servicio) {
        if (servicio.isIncluded()) {
            throw new IllegalArgumentException(
                    "Solo se agregan servicios adicionales a la reserva: " + servicio.getServiceId());
        }
        serviciosAdicionales.add(servicio);
    }

    public void addCargo(Charge cargo) {
        cargos.add(cargo);
    }

    public void setPeriodo(LocalDate entrada, LocalDate salida) {
        if (!salida.isAfter(entrada)) {
            throw new IllegalArgumentException(
                    "La fecha de salida debe ser posterior a la de entrada");
        }
        this.fechaEntrada = entrada;
        this.fechaSalida = salida;
    }

    public long getNumNoches() {
        return ChronoUnit.DAYS.between(fechaEntrada, fechaSalida);
    }

    /** Alojamiento: precio por noche multiplicado por las noches. */
    public double getSubtotalAlojamiento() {
        return precioNoche * getNumNoches();
    }

    public double getSubtotalServiciosAdicionales() {
        double total = 0d;
        for (HotelService servicio : serviciosAdicionales) {
            total += servicio.getPrice();
        }
        return total;
    }

    public double getSubtotalCargos() {
        double total = 0d;
        for (Charge cargo : cargos) {
            total += cargo.getMonto();
        }
        return total;
    }

    public double getTotal() {
        return getSubtotalAlojamiento() + getSubtotalServiciosAdicionales() + getSubtotalCargos();
    }

    /** RF-084, RT-012: el taxi gratuito depende del monto minimo del hotel. */
    public boolean calificaParaTaxiGratuito(double montoMinimoHotel) {
        return getTotal() >= montoMinimoHotel;
    }

    /**
     * RF-032, RC-012: dos reservas de la misma habitacion no pueden solaparse.
     *
     * <p>Se comparan periodos semiabiertos, de modo que una reserva que
     * termina el dia 20 y otra que empieza el 20 no se consideran superpuestas
     * (ese dia es de rotacion). Una reserva cancelada libera la habitacion.
     */
    public boolean seSuperponeCon(Booking otra) {
        if (otra == null || !hotelId.equals(otra.hotelId) || !roomId.equals(otra.roomId)) {
            return false;
        }
        if (estado == BookingStatus.CANCELADA || otra.estado == BookingStatus.CANCELADA) {
            return false;
        }
        return fechaEntrada.isBefore(otra.fechaSalida) && otra.fechaEntrada.isBefore(fechaSalida);
    }

    /** Dos reservas del mismo cliente tampoco pueden solaparse (§22 de project_detail). */
    public boolean seSuperponeConOtraDelCliente(Booking otra) {
        if (otra == null || !clienteId.equals(otra.clienteId)) {
            return false;
        }
        if (estado == BookingStatus.CANCELADA || otra.estado == BookingStatus.CANCELADA) {
            return false;
        }
        return fechaEntrada.isBefore(otra.fechaSalida) && otra.fechaEntrada.isBefore(fechaSalida);
    }

    public boolean permiteChat() {
        return estado.allowsChat();
    }

    public boolean permiteCheckout() {
        return estado.allowsCheckout();
    }
}
