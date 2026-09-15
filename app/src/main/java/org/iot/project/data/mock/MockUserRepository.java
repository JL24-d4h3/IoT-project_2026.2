package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.data.repository.UserRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Booking;
import org.iot.project.models.Card;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Implementacion simulada de perfil, tarjetas, notificaciones y chat. */
public class MockUserRepository extends MockRepository implements UserRepository {

    private int correlativoMensaje = 100;

    @Override
    public void usuario(@NonNull String usuarioId, @NonNull ResultCallback<User> callback) {
        entregarDato(callback, () -> MockData.usuario(usuarioId),
                "No pudimos cargar tu perfil.");
    }

    @Override
    public void actualizar(@NonNull User datos, @NonNull ResultCallback<User> callback) {
        ejecutar(callback, () -> {
            User actual = MockData.usuario(datos.getId());
            if (actual == null) {
                throw new IllegalArgumentException("No pudimos encontrar tu perfil.");
            }
            if (esVacio(datos.getNombres()) || esVacio(datos.getApellidos())) {
                throw new IllegalArgumentException("El nombre y los apellidos son obligatorios.");
            }
            actual.setNombres(datos.getNombres().trim());
            actual.setApellidos(datos.getApellidos().trim());
            actual.setEmail(datos.getEmail());
            actual.setTelefono(datos.getTelefono());
            actual.setDireccion(datos.getDireccion());
            return actual;
        });
    }

    /**
     * Alta o edicion de tarjeta.
     *
     * <p>Solo se guardan marca, ultimos cuatro digitos, titular y vencimiento.
     * El numero completo y el CVV no llegan siquiera a este metodo (RC-011,
     * RT-038): no hay donde guardarlos porque el modelo no tiene esos campos.
     */
    @Override
    public void guardarTarjeta(@NonNull String usuarioId, @NonNull Card tarjeta,
                               @NonNull ResultCallback<User> callback) {
        ejecutar(callback, () -> {
            User usuario = exigirUsuario(usuarioId);
            if (esVacio(tarjeta.getMarca()) || esVacio(tarjeta.getTitular())) {
                throw new IllegalArgumentException("Completa la marca y el titular de la tarjeta.");
            }
            if (tarjeta.getUltimos4() == null || tarjeta.getUltimos4().length() != 4) {
                throw new IllegalArgumentException("Ingresa los cuatro últimos dígitos.");
            }
            if (esVacio(tarjeta.getExpiracion())) {
                throw new IllegalArgumentException("Ingresa el vencimiento de la tarjeta.");
            }

            if (tarjeta.getId() == null || tarjeta.getId().isEmpty()) {
                Card nueva = new Card(
                        "C" + (usuario.getTarjetas().size() + 1),
                        tarjeta.getMarca(), tarjeta.getUltimos4(),
                        tarjeta.getTitular(), tarjeta.getExpiracion());
                usuario.addTarjeta(nueva);
            } else {
                for (Card existente : usuario.getTarjetas()) {
                    if (existente.getId().equals(tarjeta.getId())) {
                        existente.setTitular(tarjeta.getTitular());
                        existente.setExpiracion(tarjeta.getExpiracion());
                        return usuario;
                    }
                }
                usuario.addTarjeta(tarjeta);
            }
            return usuario;
        });
    }

    @Override
    public void eliminarTarjeta(@NonNull String usuarioId, @NonNull String tarjetaId,
                                @NonNull ResultCallback<User> callback) {
        ejecutar(callback, () -> {
            User usuario = exigirUsuario(usuarioId);
            Card objetivo = null;
            for (Card tarjeta : usuario.getTarjetas()) {
                if (tarjeta.getId().equals(tarjetaId)) {
                    objetivo = tarjeta;
                    break;
                }
            }
            if (objetivo == null) {
                throw new IllegalArgumentException("No encontramos esa tarjeta.");
            }
            usuario.getTarjetas().remove(objetivo);
            return usuario;
        });
    }

    @Override
    public void notificaciones(@NonNull String usuarioId,
                               @NonNull ResultCallback<List<AppNotification>> callback) {
        entregarLista(callback, () -> {
            List<AppNotification> resultado = new ArrayList<>(MockData.NOTIFICACIONES);
            // Mas recientes primero (RF-072).
            resultado.sort(Comparator.comparing(AppNotification::getTimestamp).reversed());
            return resultado;
        });
    }

    @Override
    public void marcarNotificacionesLeidas(@NonNull String usuarioId) {
        for (AppNotification notificacion : MockData.NOTIFICACIONES) {
            notificacion.setLeida(true);
        }
    }

    /**
     * Conversacion de una reserva.
     *
     * <p>RF-065: el chat solo existe mientras la reserva esta activa. Si ya
     * termino, se informa en vez de mostrar una conversacion que el usuario no
     * deberia poder abrir.
     */
    @Override
    public void conversacion(@NonNull String bookingId,
                             @NonNull ResultCallback<Conversation> callback) {
        entregarDato(callback, () -> {
            Booking reserva = MockData.reserva(bookingId);
            if (reserva == null || !reserva.permiteChat()) {
                return null;
            }
            for (Conversation conversacion : MockData.CONVERSACIONES) {
                if (conversacion.getBookingId().equals(bookingId)) {
                    return conversacion;
                }
            }
            // Una reserva activa sin conversacion previa estrena una vacia.
            Conversation nueva = new Conversation(
                    "CV" + (MockData.CONVERSACIONES.size() + 1),
                    bookingId, reserva.getClienteId(), reserva.getHotelId());
            MockData.CONVERSACIONES.add(nueva);
            return nueva;
        }, "El chat solo está disponible durante una estadía activa.");
    }

    @Override
    public void enviarMensaje(@NonNull String bookingId, @NonNull String texto,
                              @NonNull ResultCallback<Message> callback) {
        ejecutar(callback, () -> {
            if (esVacio(texto)) {
                throw new IllegalArgumentException("Escribe un mensaje antes de enviarlo.");
            }
            Booking reserva = MockData.reserva(bookingId);
            if (reserva == null) {
                throw new IllegalArgumentException("No encontramos esta reserva.");
            }
            if (!reserva.permiteChat()) {
                throw new IllegalStateException(
                        "El chat solo está disponible durante una estadía activa.");
            }

            Conversation conversacion = null;
            for (Conversation c : MockData.CONVERSACIONES) {
                if (c.getBookingId().equals(bookingId)) {
                    conversacion = c;
                    break;
                }
            }
            if (conversacion == null) {
                conversacion = new Conversation(
                        "CV" + (MockData.CONVERSACIONES.size() + 1),
                        bookingId, reserva.getClienteId(), reserva.getHotelId());
                MockData.CONVERSACIONES.add(conversacion);
            }

            correlativoMensaje++;
            Message mensaje = new Message(
                    "M" + correlativoMensaje,
                    conversacion.getId(),
                    texto.trim(),
                    LocalDateTime.now(),
                    Message.Autor.CLIENTE);
            mensaje.setLeido(true);
            conversacion.addMensaje(mensaje);
            return mensaje;
        });
    }

    // ------------------------------------------------------------------

    private User exigirUsuario(String usuarioId) {
        User usuario = MockData.usuario(usuarioId);
        if (usuario == null) {
            throw new IllegalArgumentException("No pudimos encontrar tu perfil.");
        }
        return usuario;
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}
