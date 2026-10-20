package org.iot.project.data.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import org.iot.project.models.AppNotification;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Evento persistido en la misma transacción que la reserva o el taxi que lo originó. */
public final class NotificacionFirebase {

    private NotificacionFirebase() {
    }

    public static void registrar(@NonNull Transaction transaccion, @NonNull String usuarioId,
                                 @NonNull String eventoId, @NonNull AppNotification.Tipo tipo,
                                 @NonNull String titulo, @NonNull String mensaje,
                                 @Nullable String bookingId, @Nullable String taxiId) {
        exigirId(usuarioId);
        exigirId(eventoId);
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipo", tipo.name());
        datos.put("titulo", texto(titulo, 180));
        datos.put("mensaje", texto(mensaje, 1500));
        datos.put("creadoEn", FieldValue.serverTimestamp());
        datos.put("leida", false);
        if (bookingId != null && !bookingId.isEmpty()) {
            exigirId(bookingId);
            datos.put("bookingId", bookingId);
        }
        if (taxiId != null && !taxiId.isEmpty()) {
            exigirId(taxiId);
            datos.put("taxiId", taxiId);
        }
        // El eventoId es estable y el llamador solo registra transiciones nuevas.
        transaccion.set(FirebaseFirestore.getInstance().collection("usuarios")
                .document(usuarioId).collection("notificaciones").document(eventoId), datos);
    }

    @NonNull
    public static AppNotification desde(@NonNull DocumentSnapshot documento) {
        try {
            String titulo = texto(documento.getString("titulo"), 180);
            String mensaje = texto(documento.getString("mensaje"), 1500);
            String tipo = documento.getString("tipo");
            if (tipo == null) throw new IllegalArgumentException();
            Timestamp fecha = documento.getTimestamp("creadoEn",
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            LocalDateTime momento = fecha == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                    : LocalDateTime.ofInstant(fecha.toDate().toInstant(), ZoneId.systemDefault());
            AppNotification resultado = new AppNotification(documento.getId(),
                    AppNotification.Tipo.valueOf(tipo), titulo, mensaje, momento,
                    destino(documento, "bookingId"), destino(documento, "taxiId"));
            resultado.setLeida(Boolean.TRUE.equals(documento.getBoolean("leida")));
            return resultado;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Una notificación tiene datos incompletos. "
                    + "Contacta al administrador para revisarla.");
        }
    }

    static void exigirId(String id) {
        if (id == null || id.isEmpty() || id.length() > 300 || id.contains("/")
                || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("No pudimos identificar esta notificación.");
        }
    }

    private static String destino(DocumentSnapshot documento, String campo) {
        String id = documento.getString(campo);
        if (id == null || id.isEmpty()) return null;
        exigirId(id);
        return id;
    }

    private static String texto(String valor, int maximo) {
        if (valor == null || valor.trim().isEmpty() || valor.length() > maximo) {
            throw new IllegalArgumentException("La notificación no contiene un mensaje válido.");
        }
        return valor.trim();
    }
}
