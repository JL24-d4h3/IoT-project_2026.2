package org.iot.project.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Una reserva de un hotel con el cliente y la habitacion ya resueltos.
 *
 * <p>Es el resultado de cruzar una reserva con su cliente y con la habitacion
 * que ocupa. Ese cruce lo hace el repositorio, que es quien tiene los tres
 * conjuntos a mano; hacerlo en la pantalla obligaria al Fragment a conocer el
 * almacen de usuarios, que es justo lo que las reglas 33-35 prohiben.
 *
 * <p>Guarda la {@link Booking} entera para que la fila pueda abrir el detalle de
 * la reserva con un identificador real, y para leer de ella lo que no se mueve:
 * las fechas, el codigo, la tarjeta y los cargos.
 *
 * <p>Lo que si se mueve —el estado, que cambia al cancelar o al cerrar la
 * estadia, y el total, que sube con cada cobro— se copia al cruzar y se lee de
 * la copia. No es una duplicacion por gusto: el objeto {@code Booking} es el
 * mismo dentro del almacen, asi que dos cruces de la misma reserva acabarian
 * siendo el mismo objeto, y comparar una fila con la de antes diria siempre que
 * no ha cambiado nada. La lista seguiria enseñando "Confirmada" despues de
 * cancelarla, y el total de antes de cargarle un consumo.
 *
 * <p>Lo usan las dos pantallas del administrador que enseñan reservas: las
 * estadias en curso de la portada (§43) y la lista completa de reservas (§44).
 * Es el mismo cruce en los dos casos, y tenerlo una sola vez evita que la
 * portada y la lista acaben diciendo cosas distintas de la misma reserva.
 */
public final class ReservaDeHotel {

    @NonNull
    private final Booking reserva;

    /**
     * El cliente que reservo, o {@code null} si su cuenta ya no existe.
     *
     * <p>Se guarda el usuario entero, y no su nombre ya escrito, porque la ficha
     * de la reserva necesita ademas su correo y su documento: son los dos datos
     * con los que el administrador identifica a quien tiene delante en recepcion.
     * Copiarlos aqui seria repetir el mismo cruce tres veces y arriesgarse a que
     * una de las copias se quedara atras.
     */
    @Nullable
    private final User cliente;

    /** Numero visible de la habitacion ("601"), no su identificador interno. */
    @NonNull
    private final String habitacionNumero;

    /** Tipo de la habitacion ("Doble clasica"), o vacio si ya no se puede leer. */
    @NonNull
    private final String habitacionTipo;

    /** Estado de la reserva en el momento del cruce. */
    @NonNull
    private final BookingStatus estado;

    /** Total de la reserva en el momento del cruce. */
    private final double total;

    public ReservaDeHotel(@NonNull Booking reserva, @Nullable User cliente,
                          @NonNull String habitacionNumero, @NonNull String habitacionTipo) {
        this.reserva = reserva;
        this.cliente = cliente;
        this.habitacionNumero = habitacionNumero;
        this.habitacionTipo = habitacionTipo;
        this.estado = reserva.getEstado();
        this.total = reserva.getTotal();
    }

    @NonNull
    public Booking getReserva() {
        return reserva;
    }

    /**
     * Estado de la reserva al cruzar la lista.
     *
     * <p>Es lo que pinta la fila. Se lee de aqui y no de {@code getReserva()}
     * para que el estado que se compara al actualizar la lista sea el mismo que
     * el que se ve.
     */
    @NonNull
    public BookingStatus getEstado() {
        return estado;
    }

    /** Total de la reserva al cruzar la lista, con lo que se le hubiera cargado. */
    public double getTotal() {
        return total;
    }

    @Nullable
    public User getCliente() {
        return cliente;
    }

    /**
     * Nombre del cliente, o {@code porDefecto} si su cuenta ya no existe.
     *
     * <p>El relleno lo pone quien llama y no este modelo porque es un texto de
     * la interfaz, y el modelo no tiene —ni debe tener— acceso a los recursos
     * del idioma. Es la misma convencion que {@code FormatoDeDatos.nombreServicio}.
     */
    @NonNull
    public String getClienteNombre(@NonNull String porDefecto) {
        return cliente != null ? cliente.getNombreCompleto() : porDefecto;
    }

    @NonNull
    public String getHabitacionNumero() {
        return habitacionNumero;
    }

    @NonNull
    public String getHabitacionTipo() {
        return habitacionTipo;
    }

    /** Si la estadia termina hoy, que es lo que el administrador mira primero. */
    public boolean terminaHoy() {
        return reserva.getFechaSalida().equals(LocalDate.now());
    }

    /** Si el cliente esta alojado ahora mismo (RF-045). */
    public boolean estaEnCurso() {
        return reserva.getEstado() == BookingStatus.ACTIVA;
    }
}
