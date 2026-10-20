package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
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
    private final FirebaseNotificacionesRepository notificaciones =
            new FirebaseNotificacionesRepository();

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
        notificaciones.consultar(usuarioId, callback);
    }

    @NonNull
    @Override
    public Suscripcion observarNotificaciones(@NonNull String usuarioId,
                               @NonNull ResultCallback<List<AppNotification>> callback) {
        return notificaciones.observar(usuarioId, callback);
    }

    @Override
    public void marcarNotificacionesLeidas(@NonNull String usuarioId) {
        // Se conserva la firma anterior; solo marca la foto de la bandeja que se recibió.
        notificaciones.consultar(usuarioId, new ResultCallback<List<AppNotification>>() {
            @Override public void onExito(@NonNull List<AppNotification> datos) {
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                for (AppNotification notificacion : datos) {
                    if (!notificacion.isLeida()) ids.add(notificacion.getId());
                }
                notificaciones.marcarLeidas(usuarioId, ids, new ResultCallback<Boolean>() {
                    @Override public void onExito(@NonNull Boolean resultado) { }
                    @Override public void onError(@NonNull String mensaje) { }
                });
            }
            @Override public void onError(@NonNull String mensaje) { }
        });
    }

    @Override
    public void marcarNotificacionesLeidas(@NonNull String usuarioId,
                     @NonNull List<String> notificacionIds,
                     @NonNull ResultCallback<Boolean> callback) {
        notificaciones.marcarLeidas(usuarioId, notificacionIds, callback);
    }

    @Override
    public void conversacion(@NonNull String bookingId,
                             @NonNull ResultCallback<Conversation> callback) {
        ServiceLocator.chats().conversacionDe(bookingId, callback);
    }

    @Override
    public void enviarMensaje(@NonNull String bookingId, @NonNull String texto,
                              @NonNull ResultCallback<Message> callback) {
        String mensajeId = java.util.UUID.randomUUID().toString();
        ServiceLocator.chats().enviar(bookingId, mensajeId, texto, new ResultCallback<Conversation>() {
            @Override public void onExito(@NonNull Conversation conversacion) {
                for (Message mensaje : conversacion.getMensajes()) {
                    if (mensajeId.equals(mensaje.getId())) {
                        callback.onExito(mensaje);
                        return;
                    }
                }
                Message ultimo = conversacion.getUltimoMensaje();
                if (ultimo != null && mensajeId.equals(ultimo.getId())) callback.onExito(ultimo);
                else callback.onError("El mensaje se envió. Vuelve a abrir la conversación para consultarlo.");
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    private boolean mismaSesion(@NonNull String usuarioId) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return actual != null && actual.getUid().equals(usuarioId);
    }
}
