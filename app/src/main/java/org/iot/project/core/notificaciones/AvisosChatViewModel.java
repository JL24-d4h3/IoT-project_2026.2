package org.iot.project.core.notificaciones;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Observer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import org.iot.project.core.SessionManager;
import org.iot.project.data.firebase.NotificacionFirebase;
import org.iot.project.models.AppNotification;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Avisos Android del destinatario autenticado, incluidos cliente y administrador.
 * La escucha vive mientras exista este ViewModel, también con la Activity pausada.
 * No sustituye un transporte push cuando Android termina el proceso.
 */
public final class AvisosChatViewModel extends AndroidViewModel {
    private final ControlAvisosChat control = new ControlAvisosChat();
    private final Observer<Cuenta> sesion = cuenta -> actualizarSesion();
    private final FirebaseAuth.AuthStateListener autenticacion = auth -> actualizarSesion();
    private ListenerRegistration registro;
    private String identidad;
    private String usuarioId;
    private String chatVisible;
    private boolean servidorRecibido;
    private boolean cerrado;
    private long generacion;

    public AvisosChatViewModel(@NonNull Application application) {
        super(application);
        CanalChat.crear(application);
        CanalChat.cancelarTodas(application);
        SessionManager.sesion().observeForever(sesion);
        FirebaseAuth.getInstance().addAuthStateListener(autenticacion);
    }

    private void actualizarSesion() {
        if (cerrado) return;
        FirebaseUser auth = FirebaseAuth.getInstance().getCurrentUser();
        String uid = SessionManager.getIdentidadId();
        Role rol = SessionManager.getRolActivo();
        String hotel = SessionManager.getHotelAdministrado();
        boolean permitido = auth != null && auth.getUid().equals(uid)
                && (rol == Role.CLIENTE || (rol == Role.ADMIN_HOTEL && hotel != null
                && !hotel.trim().isEmpty()));
        String siguiente = permitido ? uid + "|" + rol + "|" + hotel : null;
        if (Objects.equals(identidad, siguiente)) return;
        detener();
        CanalChat.cancelarTodas(getApplication());
        identidad = siguiente;
        usuarioId = permitido ? uid : null;
        if (!permitido) return;
        long solicitud = generacion;
        registro = FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .collection("notificaciones").whereEqualTo("tipo", AppNotification.Tipo.MENSAJE.name())
                .addSnapshotListener(MetadataChanges.INCLUDE, (documentos, error) -> {
                    if (!vigente(uid, siguiente, solicitud) || error != null || documentos == null) return;
                    List<AppNotification> avisos = new ArrayList<>();
                    for (DocumentSnapshot documento : documentos) {
                        try {
                            avisos.add(NotificacionFirebase.desde(documento));
                        } catch (IllegalArgumentException datoInvalido) {
                            // Un documento antiguo incompleto no bloquea otros mensajes.
                        }
                    }
                    avisos.sort(Comparator.comparing(AppNotification::getTimestamp)
                            .thenComparing(AppNotification::getId));
                    boolean inicial = !servidorRecibido;
                    if (!documentos.getMetadata().isFromCache()) servidorRecibido = true;
                    for (AppNotification aviso : avisos) {
                        if (aviso.isLeida()) {
                            CanalChat.cancelar(getApplication(), uid, aviso.getId());
                        }
                    }
                    for (AppNotification aviso : control.recibir(avisos, inicial, chatVisible)) {
                        if (!vigente(uid, siguiente, solicitud)) return;
                        CanalChat.mostrar(getApplication(), uid, aviso);
                    }
                });
    }

    /** La Activity pasa null al dejar de estar reanudada. No marca mensajes como leídos. */
    public void chatVisible(@Nullable String bookingId) {
        chatVisible = bookingId;
        if (usuarioId != null && bookingId != null) {
            CanalChat.cancelarConversacion(getApplication(), usuarioId, bookingId);
        }
    }

    private boolean vigente(String uid, String clave, long solicitud) {
        FirebaseUser actual = FirebaseAuth.getInstance().getCurrentUser();
        return solicitud == generacion && Objects.equals(identidad, clave)
                && actual != null && uid.equals(actual.getUid())
                && uid.equals(SessionManager.getIdentidadId())
                && clave.equals(uid + "|" + SessionManager.getRolActivo() + "|"
                + SessionManager.getHotelAdministrado());
    }

    private void detener() {
        generacion++;
        if (registro != null) registro.remove();
        registro = null;
        control.reiniciar();
        servidorRecibido = false;
        chatVisible = null;
    }

    @Override protected void onCleared() {
        cerrado = true;
        SessionManager.sesion().removeObserver(sesion);
        FirebaseAuth.getInstance().removeAuthStateListener(autenticacion);
        detener();
        super.onCleared();
    }
}
