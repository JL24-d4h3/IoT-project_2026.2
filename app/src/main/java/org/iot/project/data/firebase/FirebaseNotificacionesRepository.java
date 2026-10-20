package org.iot.project.data.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.UserRepository;
import org.iot.project.models.AppNotification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bandeja privada de la cuenta autenticada; no mezcla eventos de otras cuentas. */
public final class FirebaseNotificacionesRepository {

    private static final String SESION =
            "La sesión cambió. Inicia sesión de nuevo para ver tus notificaciones.";

    public void consultar(@NonNull String uid,
                          @NonNull ResultCallback<List<AppNotification>> callback) {
        if (!mismaSesion(uid)) {
            callback.onError(SESION);
            return;
        }
        bandeja(uid).get(Source.SERVER).addOnSuccessListener(documentos -> {
            if (!mismaSesion(uid)) {
                callback.onError(SESION);
                return;
            }
            entregar(documentos, callback);
        }).addOnFailureListener(error -> callback.onError(mismaSesion(uid)
                ? mensaje(error) : SESION));
    }

    @NonNull
    public UserRepository.Suscripcion observar(@NonNull String uid,
                          @NonNull ResultCallback<List<AppNotification>> callback) {
        if (!mismaSesion(uid)) {
            callback.onError(SESION);
            return () -> { };
        }
        AtomicBoolean cerrada = new AtomicBoolean();
        ListenerRegistration[] registro = new ListenerRegistration[1];
        FirebaseAuth.AuthStateListener[] autenticacion = new FirebaseAuth.AuthStateListener[1];
        UserRepository.Suscripcion suscripcion = () -> {
            if (!cerrada.compareAndSet(false, true)) return;
            if (registro[0] != null) registro[0].remove();
            if (autenticacion[0] != null) {
                FirebaseAuth.getInstance().removeAuthStateListener(autenticacion[0]);
            }
        };
        autenticacion[0] = auth -> {
            if (!cerrada.get() && !mismaSesion(uid)) {
                suscripcion.cancelar();
                callback.onError(SESION);
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(autenticacion[0]);
        registro[0] = bandeja(uid).addSnapshotListener(MetadataChanges.INCLUDE,
                (documentos, error) -> {
                    if (cerrada.get()) return;
                    if (!mismaSesion(uid)) {
                        suscripcion.cancelar();
                        callback.onError(SESION);
                        return;
                    }
                    if (error != null) {
                        callback.onError(mensaje(error));
                        return;
                    }
                    if (documentos != null) entregar(documentos, callback);
                });
        // Cubre una cancelación durante la entrega inicial del listener.
        if (cerrada.get()) registro[0].remove();
        return suscripcion;
    }

    public void marcarLeidas(@NonNull String uid, @NonNull List<String> ids,
                            @NonNull ResultCallback<Boolean> callback) {
        if (!mismaSesion(uid)) {
            callback.onError(SESION);
            return;
        }
        List<String> unicos = new ArrayList<>(new LinkedHashSet<>(ids));
        try {
            for (String id : unicos) NotificacionFirebase.exigirId(id);
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        marcarBloque(uid, unicos, 0, callback);
    }

    private void marcarBloque(String uid, List<String> ids, int inicio,
                             ResultCallback<Boolean> callback) {
        if (!mismaSesion(uid)) {
            callback.onError(SESION);
            return;
        }
        if (inicio == ids.size()) {
            callback.onExito(true);
            return;
        }
        int fin = Math.min(inicio + 400, ids.size());
        FirebaseFirestore.getInstance().runTransaction(transaccion -> {
            if (!mismaSesion(uid)) throw new IllegalArgumentException(SESION);
            List<DocumentSnapshot> recibidas = new ArrayList<>();
            for (int indice = inicio; indice < fin; indice++) {
                recibidas.add(transaccion.get(bandeja(uid).document(ids.get(indice))));
            }
            if (!mismaSesion(uid)) throw new IllegalArgumentException(SESION);
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("leida", true);
            cambios.put("leidaEn", FieldValue.serverTimestamp());
            for (DocumentSnapshot documento : recibidas) {
                if (documento.exists() && !Boolean.TRUE.equals(documento.getBoolean("leida"))) {
                    transaccion.update(documento.getReference(), cambios);
                }
            }
            return true;
        }).addOnSuccessListener(ignorado -> marcarBloque(uid, ids, fin, callback))
                .addOnFailureListener(error -> callback.onError(mismaSesion(uid)
                        ? mensaje(error) : SESION));
    }

    private void entregar(QuerySnapshot documentos,
                          ResultCallback<List<AppNotification>> callback) {
        List<AppNotification> resultado = new ArrayList<>();
        try {
            for (DocumentSnapshot documento : documentos) {
                resultado.add(NotificacionFirebase.desde(documento));
            }
            resultado.sort(Comparator.comparing(AppNotification::getTimestamp).reversed()
                    .thenComparing(AppNotification::getId));
        } catch (IllegalArgumentException error) {
            callback.onError(error.getMessage());
            return;
        }
        callback.onExito(resultado);
    }

    private CollectionReference bandeja(String uid) {
        return FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .collection("notificaciones");
    }

    private boolean mismaSesion(String uid) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return !uid.isEmpty() && actual != null && uid.equals(actual.getUid())
                && uid.equals(SessionManager.getUsuarioId());
    }

    private String mensaje(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                    return "No tienes permiso para consultar o actualizar tus notificaciones.";
                case UNAUTHENTICATED:
                    return "Tu sesión venció. Inicia sesión de nuevo.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "No pudimos conectar con tus notificaciones. Revisa tu conexión.";
                default:
                    return "No pudimos actualizar tus notificaciones. Inténtalo de nuevo.";
            }
        }
        return error instanceof IllegalArgumentException && error.getMessage() != null
                ? error.getMessage() : "No pudimos actualizar tus notificaciones. Inténtalo de nuevo.";
    }
}
