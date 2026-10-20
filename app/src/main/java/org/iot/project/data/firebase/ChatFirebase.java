package org.iot.project.data.firebase;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import org.iot.project.models.Booking;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Esquema de conversación y mensajes; los datos de acceso se contrastan con la reserva. */
public final class ChatFirebase {
    public static final int MAX_TEXTO = 4000;

    private ChatFirebase() { }

    public static String texto(String valor) {
        if (valor == null || valor.trim().isEmpty() || valor.trim().length() > MAX_TEXTO) {
            throw new IllegalArgumentException("Escribe un mensaje de entre 1 y 4000 caracteres.");
        }
        return valor.trim();
    }

    public static Conversation vacia(Booking reserva) {
        return new Conversation(reserva.getId(), reserva.getId(), reserva.getClienteId(), reserva.getHotelId());
    }

    public static void validarPadre(DocumentSnapshot documento, Booking reserva) {
        if (!documento.exists()) return;
        if (!reserva.getId().equals(documento.getString("bookingId"))
                || !reserva.getHotelId().equals(documento.getString("hotelId"))
                || !reserva.getClienteId().equals(documento.getString("clienteId"))) {
            throw new IllegalArgumentException("La conversación no corresponde a esta reserva.");
        }
    }

    public static Map<String, Object> mensaje(String bookingId, String uid, Message.Autor autor, String texto) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("bookingId", bookingId);
        datos.put("autorId", uid);
        datos.put("autor", autor.name());
        datos.put("texto", texto(texto));
        datos.put("creadoEn", FieldValue.serverTimestamp());
        datos.put("leido", false);
        return datos;
    }

    public static Message mensaje(String id, String bookingId, Map<String, Object> datos) {
        try {
            BookingFirebase.exigirId(id);
            if (datos == null || !bookingId.equals(datos.get("bookingId"))) throw new IllegalArgumentException();
            Message.Autor autor = Message.Autor.valueOf((String) datos.get("autor"));
            BookingFirebase.exigirId((String) datos.get("autorId"));
            Object fecha = datos.get("creadoEn");
            if (!(fecha instanceof Timestamp)) throw new IllegalArgumentException();
            Timestamp marca = (Timestamp) fecha;
            LocalDateTime momento = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(marca.getSeconds(), marca.getNanoseconds()),
                    ZoneId.systemDefault());
            Message resultado = new Message(id, bookingId, texto((String) datos.get("texto")), momento, autor);
            resultado.setLeido(Boolean.TRUE.equals(datos.get("leido")));
            return resultado;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Un mensaje del chat tiene datos incompletos. Contacta al administrador.");
        }
    }

    public static Conversation historial(Booking reserva, List<DocumentSnapshot> documentos) {
        List<Message> mensajes = new ArrayList<>();
        for (DocumentSnapshot documento : documentos) {
            Message mensaje = mensaje(documento.getId(), reserva.getId(), documento.getData());
            if (mensaje.getAutor() == Message.Autor.CLIENTE
                    && !reserva.getClienteId().equals(documento.getString("autorId"))) {
                throw new IllegalArgumentException("Un mensaje no corresponde al cliente de esta reserva.");
            }
            mensajes.add(mensaje);
        }
        mensajes.sort(Comparator.comparing(Message::getTimestamp).thenComparing(Message::getId));
        Conversation conversacion = vacia(reserva);
        for (Message mensaje : mensajes) conversacion.addMensaje(mensaje);
        return conversacion;
    }

    @SuppressWarnings("unchecked")
    public static Conversation resumen(Booking reserva, DocumentSnapshot documento) {
        validarPadre(documento, reserva);
        if (!documento.exists()) return vacia(reserva);
        int total = contador(documento, "totalMensajes");
        Message ultimo = null;
        Object datos = documento.get("ultimoMensaje");
        if (total > 0) {
            if (!(datos instanceof Map)) throw new IllegalArgumentException("La conversación no tiene un resumen válido.");
            Map<String, Object> mapa = (Map<String, Object>) datos;
            ultimo = mensaje((String) mapa.get("id"), reserva.getId(), mapa);
        }
        return vacia(reserva).withResumen(total, contador(documento, "sinLeerCliente"),
                contador(documento, "sinLeerHotel"), ultimo);
    }

    public static int contador(DocumentSnapshot documento, String campo) {
        Object valor = documento.get(campo);
        if (valor == null && !documento.exists()) return 0;
        if (!(valor instanceof Number) || ((Number) valor).longValue() < 0
                || ((Number) valor).longValue() > Integer.MAX_VALUE
                || ((Number) valor).doubleValue() != ((Number) valor).longValue()) {
            throw new IllegalArgumentException("El contador de mensajes del chat no es válido.");
        }
        return ((Number) valor).intValue();
    }
}
