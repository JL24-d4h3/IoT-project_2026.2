package org.iot.project.core.notificaciones;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.iot.project.MainActivity;
import org.iot.project.R;
import org.iot.project.models.AppNotification;

/** Canal y avisos del sistema para los mensajes de una reserva. */
public final class CanalChat {
    public static final String ID = "chat_cliente_hotel";
    public static final String ACTION_VER_CHAT = "org.iot.project.action.VER_CHAT";
    public static final String EXTRA_USUARIO_ID = "org.iot.project.extra.USUARIO_ID";
    public static final String EXTRA_BOOKING_ID = "org.iot.project.extra.BOOKING_ID";
    private static final String EXTRA_EVENTO_ID = "org.iot.project.extra.EVENTO_ID";

    private CanalChat() { }

    /** Registrar de nuevo el canal conserva las preferencias elegidas en Android. */
    public static void crear(@NonNull Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel canal = new NotificationChannel(ID,
                context.getString(R.string.chat_canal_nombre), NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription(context.getString(R.string.chat_canal_descripcion));
        canal.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(canal);
    }

    /** Devuelve false si el aviso no corresponde a chat o Android no permite mostrarlo. */
    public static boolean mostrar(@NonNull Context context, @NonNull String usuarioId,
                                  @NonNull AppNotification aviso) {
        if (usuarioId.isBlank() || aviso.getTipo() != AppNotification.Tipo.MENSAJE
                || aviso.isLeida() || vacio(aviso.getId()) || vacio(aviso.getBookingId())) return false;
        crear(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        NotificationChannel canal = manager.getNotificationChannel(ID);
        if (canal == null || canal.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;

        Uri identificador = new Uri.Builder().scheme("iotchat").authority(context.getPackageName())
                .appendPath(usuarioId).appendPath(aviso.getBookingId()).appendPath(aviso.getId()).build();
        Intent abrir = new Intent(context, MainActivity.class)
                .setAction(ACTION_VER_CHAT).setData(identificador)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_USUARIO_ID, usuarioId)
                .putExtra(EXTRA_BOOKING_ID, aviso.getBookingId());
        PendingIntent pendiente = PendingIntent.getActivity(context, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bundle datos = new Bundle();
        datos.putString(EXTRA_USUARIO_ID, usuarioId);
        datos.putString(EXTRA_BOOKING_ID, aviso.getBookingId());
        datos.putString(EXTRA_EVENTO_ID, aviso.getId());

        Notification publica = new NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(context.getString(R.string.chat_notificacion_publica_titulo))
                .setContentText(context.getString(R.string.chat_notificacion_publica_mensaje))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build();
        String titulo = vacio(aviso.getTitulo())
                ? context.getString(R.string.chat_notificacion_publica_titulo) : aviso.getTitulo();
        String mensaje = aviso.getMensaje() == null ? "" : aviso.getMensaje();
        Notification notificacion = new NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publica)
                .setContentIntent(pendiente)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .addExtras(datos)
                .build();
        try {
            String tag = identificador.toString();
            manager.notify(tag, tag.hashCode() & Integer.MAX_VALUE, notificacion);
            return true;
        } catch (SecurityException permisoRevocado) {
            // El permiso puede revocarse después de comprobarlo.
            return false;
        }
    }

    public static void cancelarTodas(@NonNull Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        for (StatusBarNotification activa : manager.getActiveNotifications()) {
            if (ID.equals(activa.getNotification().getChannelId())) {
                manager.cancel(activa.getTag(), activa.getId());
            }
        }
    }

    /** Retira un aviso leído sin afectar otros mensajes de la misma reserva. */
    public static void cancelar(@NonNull Context context, @NonNull String usuarioId,
                                 @NonNull String eventoId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || usuarioId.isBlank() || eventoId.isBlank()) return;
        for (StatusBarNotification activa : manager.getActiveNotifications()) {
            Notification notificacion = activa.getNotification();
            Bundle extras = notificacion.extras;
            if (ID.equals(notificacion.getChannelId()) && extras != null
                    && usuarioId.equals(extras.getString(EXTRA_USUARIO_ID))
                    && eventoId.equals(extras.getString(EXTRA_EVENTO_ID))) {
                manager.cancel(activa.getTag(), activa.getId());
            }
        }
    }

    /** Retira únicamente los mensajes de esta cuenta y reserva. */
    public static void cancelarConversacion(@NonNull Context context, @NonNull String usuarioId,
                                            @NonNull String bookingId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || usuarioId.isBlank() || bookingId.isBlank()) return;
        for (StatusBarNotification activa : manager.getActiveNotifications()) {
            Notification notificacion = activa.getNotification();
            Bundle extras = notificacion.extras;
            if (ID.equals(notificacion.getChannelId()) && extras != null
                    && usuarioId.equals(extras.getString(EXTRA_USUARIO_ID))
                    && bookingId.equals(extras.getString(EXTRA_BOOKING_ID))) {
                manager.cancel(activa.getTag(), activa.getId());
            }
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
