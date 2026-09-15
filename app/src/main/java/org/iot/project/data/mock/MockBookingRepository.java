package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.BookingRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Charge;
import org.iot.project.models.HotelService;
import org.iot.project.models.LogEntry;
import org.iot.project.models.Payment;
import org.iot.project.models.Review;
import org.iot.project.models.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementacion simulada de reservas.
 *
 * <p>Aqui es donde se aplican las dos reglas que la pantalla no puede
 * garantizar por si sola: una habitacion no puede estar tomada dos veces
 * (RF-032) y un cliente no puede tener dos estadias cruzadas, aunque sean en
 * hoteles distintos (§22). Se comprueban en el repositorio y no en el
 * formulario, porque un formulario se puede saltar.
 */
public class MockBookingRepository extends MockRepository implements BookingRepository {

    private int correlativo = 700;

    @Override
    public void reservasDe(@NonNull String clienteId,
                           @NonNull ResultCallback<List<Booking>> callback) {
        entregarLista(callback, () -> {
            List<Booking> resultado = new ArrayList<>();
            for (Booking reserva : MockData.RESERVAS) {
                if (reserva.getClienteId().equals(clienteId)) {
                    resultado.add(reserva);
                }
            }
            // La mas reciente primero: es lo que el usuario acaba de hacer.
            resultado.sort(Comparator.comparing(Booking::getFechaEntrada).reversed());
            return resultado;
        });
    }

    @Override
    public void reservasDeHotel(@NonNull String hotelId,
                                @NonNull ResultCallback<List<Booking>> callback) {
        entregarLista(callback, () -> {
            exigirPuedeGestionar(hotelId);
            List<Booking> resultado = new ArrayList<>();
            for (Booking reserva : MockData.RESERVAS) {
                if (reserva.getHotelId().equals(hotelId)) {
                    resultado.add(reserva);
                }
            }
            // La mas reciente primero, por fecha de entrada y no por la de
            // creacion: lo que el administrador mira es quien llega ahora.
            resultado.sort(Comparator.comparing(Booking::getFechaEntrada).reversed());
            return resultado;
        });
    }

    @Override
    public void obtener(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        entregarDato(callback, () -> MockData.reserva(bookingId),
                "No encontramos esta reserva.");
    }

    @Override
    public void crear(@NonNull Booking borrador, @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            validarPeriodo(borrador.getFechaEntrada(), borrador.getFechaSalida());
            validarHabitacionLibre(borrador);
            validarAgendaDelCliente(borrador);

            Booking nueva = new Booking(
                    "B" + (MockData.RESERVAS.size() + 1),
                    codigoNuevo(),
                    borrador.getHotelId(),
                    borrador.getRoomId(),
                    borrador.getClienteId(),
                    borrador.getFechaEntrada(),
                    borrador.getFechaSalida(),
                    borrador.getNumHuespedes(),
                    borrador.getPrecioNoche(),
                    // Nace pendiente: se confirma al pagar (RF-045).
                    BookingStatus.PENDIENTE);
            for (HotelService servicio : borrador.getServiciosAdicionales()) {
                nueva.addServicioAdicional(servicio);
            }
            MockData.RESERVAS.add(nueva);
            registrar("RESERVA", "Creó la reserva " + nueva.getCodigo() + " en "
                    + MockData.nombreHotel(nueva.getHotelId()) + ".");
            return nueva;
        });
    }

    @Override
    public void cancelar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            exigirPuedeOperar(reserva);
            if (!reserva.getEstado().allowsCancellation()) {
                throw new IllegalStateException(
                        "Una reserva " + reserva.getEstado().getDisplayName().toLowerCase()
                                + " ya no se puede cancelar.");
            }
            reserva.setEstado(BookingStatus.CANCELADA);
            registrar("CANCELACION", "Canceló la reserva " + reserva.getCodigo() + ".");
            return reserva;
        });
    }

    @Override
    public void pagar(@NonNull String bookingId, @NonNull Payment pago,
                      @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            exigirPuedeOperar(reserva);
            if (reserva.getEstado() != BookingStatus.PENDIENTE) {
                throw new IllegalStateException(
                        "Esta reserva ya está " + reserva.getEstado().getDisplayName().toLowerCase()
                                + ", no hace falta pagarla de nuevo.");
            }
            if (pago.getMonto() <= 0) {
                throw new IllegalArgumentException("El monto del pago debe ser mayor que cero.");
            }
            reserva.setEstado(BookingStatus.CONFIRMADA);
            registrar("PAGO", "Registró el pago de la reserva " + reserva.getCodigo() + ".");
            return reserva;
        });
    }

    @Override
    public void finalizar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            exigirPuedeOperar(reserva);
            if (!reserva.getEstado().allowsCheckout()) {
                throw new IllegalStateException(
                        "Solo se puede hacer checkout de una estadía activa. Esta reserva está "
                                + reserva.getEstado().getDisplayName().toLowerCase() + ".");
            }
            reserva.setEstado(BookingStatus.FINALIZADA);
            registrar("CHECKOUT", "Registró la salida de la reserva " + reserva.getCodigo() + ".");
            return reserva;
        });
    }

    @Override
    public void cobrar(@NonNull String bookingId, @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            // Aqui no vale exigirPuedeOperar: esa deja pasar al cliente con su
            // propia reserva, y el cobro no es suyo. RF-049 se lo da al hotel, y
            // un cliente que pudiera marcarse la reserva como cobrada estaria
            // diciendo que pagó sin que nadie haya cobrado nada.
            exigirPuedeGestionar(reserva.getHotelId());
            if (reserva.getEstado() != BookingStatus.FINALIZADA) {
                // Se cobra despues del checkout, no antes: mientras la estadia
                // siga abierta el total todavia puede crecer con consumos, y
                // cobrar entonces obligaria a un segundo cobro por la diferencia.
                throw new IllegalStateException(
                        "Primero hay que cerrar la estadía. Esta reserva está "
                                + reserva.getEstado().getDisplayName().toLowerCase() + ".");
            }
            if (reserva.getTarjeta() == null) {
                throw new IllegalStateException(
                        "El huésped no registró ninguna tarjeta. El cobro se hace en recepción.");
            }
            if (reserva.isCobrado()) {
                throw new IllegalStateException("Esta reserva ya está cobrada.");
            }
            reserva.marcarCobrado();
            // Ni el numero de la tarjeta ni su codigo pasan por aqui (RC-011,
            // RT-038): la bitacora dice que se cobro y a que reserva, nada mas.
            registrar("PAGO", "Cobró la reserva " + reserva.getCodigo()
                    + " a la tarjeta registrada por el huésped.");
            // RF-053: el aviso al huésped, con el monto para que lo pueda
            // contrastar con su recibo.
            MockData.notificar(AppNotification.Tipo.PAGO,
                    "Cobro de tu estadía",
                    "Se cobraron S/ " + String.format("%.2f", reserva.getTotal())
                            + " a la tarjeta que registraste.");
            return reserva;
        });
    }

    @Override
    public void agregarCargo(@NonNull String bookingId, @NonNull Charge cargo,
                             @NonNull ResultCallback<Booking> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            exigirPuedeOperar(reserva);
            if (reserva.getEstado() != BookingStatus.ACTIVA) {
                throw new IllegalStateException(
                        "Solo se pueden cargar consumos durante la estadía.");
            }
            if (cargo.getMonto() <= 0) {
                throw new IllegalArgumentException("El monto del cargo debe ser mayor que cero.");
            }
            if (cargo.getMotivo() == null || cargo.getMotivo().trim().isEmpty()) {
                throw new IllegalArgumentException("Indica el motivo del cargo.");
            }
            reserva.addCargo(cargo);
            registrar("CARGO_ADICIONAL", "Registró un cargo de S/ "
                    + String.format("%.2f", cargo.getMonto()) + " en la reserva "
                    + reserva.getCodigo() + ".");
            // RF-053: el huésped tiene que enterarse del cobro. Se avisa con el
            // motivo, que es lo que le permite reconocerlo, y no con el
            // identificador de la reserva, que no le dice nada.
            MockData.notificar(AppNotification.Tipo.CARGO_ADICIONAL,
                    "Se registró un consumo",
                    cargo.getMotivo() + ": S/ " + String.format("%.2f", cargo.getMonto())
                            + " en tu estadía.");
            return reserva;
        });
    }

    @Override
    public void valorar(@NonNull String bookingId, float rating, @NonNull String comentario,
                        @NonNull ResultCallback<Review> callback) {
        ejecutar(callback, () -> {
            Booking reserva = exigirReserva(bookingId);
            exigirPuedeOperar(reserva);
            if (reserva.getEstado() != BookingStatus.FINALIZADA) {
                throw new IllegalStateException(
                        "Solo puedes valorar una estadía que ya terminó.");
            }
            if (reserva.getValoracion() != null) {
                throw new IllegalStateException("Ya valoraste esta estadía.");
            }
            if (rating < Review.RATING_MIN || rating > Review.RATING_MAX) {
                throw new IllegalArgumentException(
                        "La calificación va de " + Review.RATING_MIN + " a " + Review.RATING_MAX + ".");
            }
            Review resena = new Review(
                    "RV" + (MockData.RESENAS.size() + 1),
                    reserva.getHotelId(),
                    nombreDelCliente(reserva.getClienteId()),
                    rating,
                    comentario == null || comentario.trim().isEmpty()
                            ? "Sin comentario." : comentario.trim(),
                    LocalDate.now());
            reserva.setValoracion(resena);
            // Se suma al catalogo para que aparezca en la ficha del hotel.
            MockData.RESENAS.add(resena);
            registrar("ACCION_ADMINISTRATIVA", "Valoró la reserva " + reserva.getCodigo() + ".");
            return resena;
        });
    }

    // ------------------------------------------------------------------
    //  Reglas de negocio
    // ------------------------------------------------------------------

    private void validarPeriodo(LocalDate entrada, LocalDate salida) {
        if (entrada == null || salida == null) {
            throw new IllegalArgumentException("Elige las fechas de entrada y salida.");
        }
        if (!salida.isAfter(entrada)) {
            throw new IllegalArgumentException(
                    "La fecha de salida debe ser posterior a la de entrada.");
        }
        if (entrada.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de entrada no puede ser anterior a hoy.");
        }
    }

    /** RF-032, RC-012: la misma habitacion no puede tener dos reservas cruzadas. */
    private void validarHabitacionLibre(Booking borrador) {
        for (Booking existente : MockData.RESERVAS) {
            if (existente.seSuperponeCon(borrador)) {
                throw new IllegalStateException(
                        "Esa habitación ya está reservada en esas fechas. Prueba con otras "
                                + "fechas o con otra habitación del mismo hotel.");
            }
        }
    }

    /** §22: tampoco el cliente puede estar en dos hoteles a la vez. */
    private void validarAgendaDelCliente(Booking borrador) {
        for (Booking existente : MockData.RESERVAS) {
            if (existente.seSuperponeConOtraDelCliente(borrador)) {
                throw new IllegalStateException(
                        "Ya tienes una reserva en esas fechas. Puedes cancelarla o elegir "
                                + "otras fechas.");
            }
        }
    }

    private Booking exigirReserva(String bookingId) {
        Booking reserva = MockData.reserva(bookingId);
        if (reserva == null) {
            throw new IllegalArgumentException("No encontramos esta reserva.");
        }
        return reserva;
    }

    /** RF-023: las reservas de un hotel las ve quien lo administra. */
    private void exigirPuedeGestionar(String hotelId) {
        if (!SessionManager.puedeGestionar(hotelId)) {
            throw new IllegalStateException(
                    "Estas reservas pertenecen a otro hotel.");
        }
    }

    /**
     * RF-044: una reserva solo la tocan su cliente y el hotel que la aloja.
     *
     * <p>Las operaciones que reciben un identificador de reserva —pagar,
     * cancelar, cerrar la estadia, cargar consumos, valorar— no pasan por
     * {@link #exigirPuedeGestionar}, porque no conocen el hotel hasta que han
     * leido la reserva. Sin esta comprobacion quedaban abiertas: con una sesion
     * de administrador, cualquiera de ellas se podia ejecutar sobre la reserva
     * de otro hotel con solo conocer su identificador. Ninguna pantalla llega a
     * ofrecerlo, pero un repositorio no se protege con que la pantalla no lo
     * llame.
     */
    private void exigirPuedeOperar(Booking reserva) {
        String usuarioId = SessionManager.getUsuarioId();
        if (usuarioId != null && usuarioId.equals(reserva.getClienteId())) {
            return;
        }
        exigirPuedeGestionar(reserva.getHotelId());
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private String codigoNuevo() {
        correlativo++;
        return String.format("EST-2026-%04d", correlativo);
    }

    private String nombreDelCliente(String clienteId) {
        User usuario = MockData.usuario(clienteId);
        return usuario != null ? usuario.getNombreCompleto() : "Huésped";
    }

    /**
     * Anota el movimiento en la bitacora. El detalle se redacta aqui y nunca
     * incluye contrasenas ni numeros de tarjeta (RC-042, RT-038).
     */
    private void registrar(String tipo, String detalle) {
        MockData.BITACORA.add(new LogEntry(
                LocalDateTime.now(),
                nombreDelCliente(SessionManager.getUsuarioIdSeguro()),
                LogEntry.Evento.valueOf(tipo),
                detalle));
    }
}
