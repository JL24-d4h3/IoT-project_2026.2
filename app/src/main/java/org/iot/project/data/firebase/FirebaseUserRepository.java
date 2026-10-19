package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.data.repository.UserRepository;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Card;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;
import org.iot.project.models.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Perfil de la cuenta autenticada, sin recurrir a identidades de demostracion. */
public final class FirebaseUserRepository implements UserRepository {

    private static final String SESION_REQUERIDA =
            "Inicia sesión con esta cuenta para consultar o cambiar su perfil.";
    private static final String SESION_CAMBIADA =
            "La sesión cambió. Vuelve a cargar tu perfil.";
    private static final String TARJETAS_PENDIENTES =
            "La gestión de tarjetas todavía no está conectada.";
    private static final String NOTIFICACIONES_PENDIENTES =
            "Las notificaciones todavía no están conectadas.";
    private static final String CHAT_PENDIENTE =
            "El chat de las reservas todavía no está conectado.";

    @Override
    public void usuario(@NonNull String usuarioId, @NonNull ResultCallback<User> callback) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        if (actual == null || usuarioId == null || !usuarioId.equals(actual.getUid())) {
            callback.onError(SESION_REQUERIDA);
            return;
        }

        FirebaseFirestore.getInstance().collection("usuarios").document(usuarioId).get(Source.SERVER)
                .addOnSuccessListener(documento -> {
                    if (!mismaSesion(usuarioId)) {
                        callback.onError(SESION_CAMBIADA);
                        return;
                    }
                    if (!documento.exists() || documento.getData() == null) {
                        callback.onError("No encontramos el perfil de tu cuenta.");
                        return;
                    }
                    User perfil;
                    try {
                        perfil = PerfilFirebase.usuario(
                                usuarioId, actual.getEmail(), documento.getData());
                    } catch (IllegalArgumentException error) {
                        callback.onError(FirebaseErrores.mensaje(error));
                        return;
                    }
                    if (!mismaSesion(usuarioId)) {
                        callback.onError(SESION_CAMBIADA);
                        return;
                    }
                    callback.onExito(perfil);
                })
                .addOnFailureListener(error -> callback.onError(mismaSesion(usuarioId)
                        ? FirebaseErrores.mensaje(error) : SESION_CAMBIADA));
    }

    @Override
    public void actualizar(@NonNull User datos, @NonNull ResultCallback<User> callback) {
        String usuarioId = datos.getId();
        if (!mismaSesion(usuarioId)) {
            callback.onError(SESION_REQUERIDA);
            return;
        }

        Map<String, Object> cambios;
        try {
            cambios = new HashMap<>(PerfilFirebase.actualizacion(datos));
            cambios.put("actualizadoEn", FieldValue.serverTimestamp());
        } catch (IllegalArgumentException error) {
            callback.onError(FirebaseErrores.mensaje(error));
            return;
        }

        FirebaseFirestore.getInstance().collection("usuarios").document(usuarioId).update(cambios)
                .addOnSuccessListener(ignorado -> {
                    if (!mismaSesion(usuarioId)) {
                        callback.onError(SESION_CAMBIADA);
                        return;
                    }
                    usuario(usuarioId, callback);
                })
                .addOnFailureListener(error -> callback.onError(mismaSesion(usuarioId)
                        ? FirebaseErrores.mensaje(error) : SESION_CAMBIADA));
    }

    @Override
    public void guardarTarjeta(@NonNull String usuarioId, @NonNull Card tarjeta,
                               @NonNull ResultCallback<User> callback) {
        callback.onError(TARJETAS_PENDIENTES);
    }

    @Override
    public void eliminarTarjeta(@NonNull String usuarioId, @NonNull String tarjetaId,
                                @NonNull ResultCallback<User> callback) {
        callback.onError(TARJETAS_PENDIENTES);
    }

    @Override
    public void notificaciones(@NonNull String usuarioId,
                               @NonNull ResultCallback<List<AppNotification>> callback) {
        callback.onError(NOTIFICACIONES_PENDIENTES);
    }

    @Override
    public void marcarNotificacionesLeidas(@NonNull String usuarioId) {
        // Sin efecto hasta migrar las notificaciones; nunca modifica datos simulados.
    }

    @Override
    public void conversacion(@NonNull String bookingId,
                             @NonNull ResultCallback<Conversation> callback) {
        callback.onError(CHAT_PENDIENTE);
    }

    @Override
    public void enviarMensaje(@NonNull String bookingId, @NonNull String texto,
                              @NonNull ResultCallback<Message> callback) {
        callback.onError(CHAT_PENDIENTE);
    }

    private boolean mismaSesion(@NonNull String usuarioId) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return actual != null && actual.getUid().equals(usuarioId);
    }
}
