package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Booking;
import org.iot.project.models.Charge;
import org.iot.project.models.Payment;
import org.iot.project.models.Review;

import java.util.List;

/** Acceso a reservas: creacion, pago, checkout, cargos y valoracion. */
public interface BookingRepository {

    void reservasDe(@NonNull String clienteId, @NonNull ResultCallback<List<Booking>> callback);

    /**
     * Reservas de un hotel, de la mas reciente a la mas antigua (RF-041).
     *
     * <p>Es la consulta del administrador, y por eso exige poder gestionar ese
     * hotel (RF-023): las mismas reservas que el cliente ve como suyas, el
     * administrador las ve como ocupacion.
     *
     * <p>Devuelve todas, canceladas incluidas. Filtrarlas aqui escondería que
     * una habitacion se libero precisamente porque alguien canceló, que es
     * justo lo que el administrador necesita saber.
     */
    void reservasDeHotel(@NonNull String hotelId, @NonNull ResultCallback<List<Booking>> callback);

    void obtener(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback);

    /**
     * Crea la reserva.
     *
     * <p>Falla si la habitacion ya esta tomada en esas fechas (RF-032) o si el
     * cliente ya tiene otra reserva que se cruza (§22), aunque sea en otro
     * hotel. Ambas reglas las aplica el repositorio, no la pantalla.
     */
    void crear(@NonNull Booking borrador, @NonNull ResultCallback<Booking> callback);

    /** Solo si el estado lo permite (RC-013). Libera la habitacion. */
    void cancelar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback);

    /** Registra el pago simulado y deja la reserva confirmada (RF-038, RF-039). */
    void pagar(@NonNull String bookingId, @NonNull Payment pago, @NonNull ResultCallback<Booking> callback);

    /** Checkout: cierra la estadia (RF-045). Solo desde una reserva activa. */
    void finalizar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback);

    /**
     * Cobra el total a la tarjeta que el cliente registro al reservar (RF-049).
     *
     * <p>Es simulado (RF-050) y solo tiene sentido despues del checkout: el
     * cliente cierra la estadia por su cuenta (RF-045) y es entonces cuando el
     * hotel entra a cobrar. Falla si la reserva no esta finalizada, si no hay
     * tarjeta registrada, o si ya se cobro —el cliente pudo pagar en recepcion
     * y no hay nada que cobrar.
     */
    void cobrar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback);

    /** Cargo adicional durante la estadia (RF-052). Exige monto y motivo. */
    void agregarCargo(@NonNull String bookingId, @NonNull Charge cargo, @NonNull ResultCallback<Booking> callback);

    /** Valoracion de una reserva finalizada (§22). Escala 1 a 10. */
    void valorar(@NonNull String bookingId, float rating, @NonNull String comentario,
                 @NonNull ResultCallback<Review> callback);
}
