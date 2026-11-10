package org.iot.project.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.core.notificaciones.CanalChat;
import org.iot.project.models.AppNotification;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Notificaciones locales reales; no abre actividades ni conecta repositorios Firebase. */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class CanalChatAndroidTest {
    private static final String OTRO_CANAL = "canal_chat_prueba_otros";
    private static final String OTRO_TAG = "canal-chat-prueba-otros";
    private static final int OTRO_ID = 81037;
    private static Context context;
    private static NotificationManager manager;
    private static boolean otroCanalExistia;

    @BeforeClass
    public static void prepararAndroidDemo() {
        FirebaseEmulatorApplication.exigirProyectoDemo();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        manager = context.getSystemService(NotificationManager.class);
        otroCanalExistia = manager.getNotificationChannel(OTRO_CANAL) != null;
        CanalChat.crear(context);
    }

    @Before
    public void limpiarAvisosDePrueba() {
        CanalChat.cancelarTodas(context);
        manager.cancel(OTRO_TAG, OTRO_ID);
        esperar(() -> cantidadChat() == 0);
    }

    @After
    public void retirarAvisosDePrueba() {
        CanalChat.cancelarTodas(context);
        manager.cancel(OTRO_TAG, OTRO_ID);
    }

    @AfterClass
    public static void retirarCanalAuxiliar() {
        if (manager == null) return;
        CanalChat.cancelarTodas(context);
        manager.cancel(OTRO_TAG, OTRO_ID);
        if (!otroCanalExistia) manager.deleteNotificationChannel(OTRO_CANAL);
    }

    @Test
    public void a_creaCanalAltaImportancia() {
        NotificationChannel canal = manager.getNotificationChannel(CanalChat.ID);
        assertNotNull(canal);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, canal.getImportance());
        assertEquals(context.getString(R.string.chat_canal_nombre), canal.getName().toString());
        assertEquals(context.getString(R.string.chat_canal_descripcion), canal.getDescription());
    }

    @Test
    public void b_sinPermisoNoPublica() {
        assumeFalse("Ejecutar este caso con POST_NOTIFICATIONS denegado desde adb", permisoConcedido());

        assertFalse(CanalChat.mostrar(context, "cliente-prueba", mensaje("mensaje-1", "reserva-1")));

        assertEquals(0, cantidadChat());
    }

    @Test
    public void c_conPermisoPublicaContenidoPrivadoEIntentInmutable() {
        assumeTrue("Ejecutar este caso con POST_NOTIFICATIONS concedido desde adb", permisoConcedido());

        assertTrue(CanalChat.mostrar(context, "cliente-prueba", mensaje("mensaje-1", "reserva-1")));
        esperar(() -> cantidadChat() == 1);
        Notification aviso = aviso("cliente-prueba", "reserva-1").getNotification();

        assertEquals(CanalChat.ID, aviso.getChannelId());
        assertEquals("cliente-prueba", aviso.extras.getString(CanalChat.EXTRA_USUARIO_ID));
        assertEquals("reserva-1", aviso.extras.getString(CanalChat.EXTRA_BOOKING_ID));
        assertEquals("Mensaje privado de prueba", aviso.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(Notification.VISIBILITY_PRIVATE, aviso.visibility);
        assertNotNull(aviso.publicVersion);
        assertEquals(context.getString(R.string.chat_notificacion_publica_mensaje),
                aviso.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertNotNull(aviso.contentIntent);
        assertTrue(aviso.contentIntent.isImmutable());
        assertEquals(Notification.CATEGORY_MESSAGE, aviso.category);
        assertTrue((aviso.flags & Notification.FLAG_AUTO_CANCEL) != 0);
        assertTrue((aviso.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);
    }

    @Test
    public void d_cuentasYReservasIndependientesYCancelacionSoloDelChat() {
        assumeTrue("Ejecutar este caso con POST_NOTIFICATIONS concedido desde adb", permisoConcedido());
        manager.createNotificationChannel(new NotificationChannel(OTRO_CANAL,
                "Canal auxiliar de pruebas", NotificationManager.IMPORTANCE_LOW));
        manager.notify(OTRO_TAG, OTRO_ID, new NotificationCompat.Builder(context, OTRO_CANAL)
                .setSmallIcon(R.drawable.ic_chat).setContentTitle("Aviso ajeno al chat").build());
        assertTrue(CanalChat.mostrar(context, "cliente-prueba", mensaje("mismo-id", "reserva-1")));
        assertTrue(CanalChat.mostrar(context, "admin-prueba", mensaje("mismo-id", "reserva-1")));
        assertTrue(CanalChat.mostrar(context, "cliente-prueba", mensaje("otro-id", "reserva-2")));
        esperar(() -> cantidadChat() == 3 && existeOtroAviso());
        assertNotEquals(aviso("cliente-prueba", "reserva-1").getNotification().contentIntent,
                aviso("admin-prueba", "reserva-1").getNotification().contentIntent);

        CanalChat.cancelarConversacion(context, "cliente-prueba", "reserva-1");
        esperar(() -> cantidadChat() == 2);
        assertNotNull(aviso("admin-prueba", "reserva-1"));
        assertNotNull(aviso("cliente-prueba", "reserva-2"));

        CanalChat.cancelar(context, "cliente-prueba", "otro-id");
        esperar(() -> cantidadChat() == 1);
        assertNotNull(aviso("admin-prueba", "reserva-1"));
        CanalChat.cancelarTodas(context);
        esperar(() -> cantidadChat() == 0);
        assertTrue(existeOtroAviso());
    }

    private static AppNotification mensaje(String id, String reservaId) {
        return new AppNotification(id, AppNotification.Tipo.MENSAJE, "Chat de prueba",
                "Mensaje privado de prueba", LocalDateTime.now(), reservaId, null);
    }

    private static boolean permisoConcedido() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static int cantidadChat() {
        return (int) Arrays.stream(manager.getActiveNotifications())
                .filter(a -> CanalChat.ID.equals(a.getNotification().getChannelId()))
                .filter(a -> a.getNotification().extras.containsKey(CanalChat.EXTRA_USUARIO_ID)).count();
    }

    private static StatusBarNotification aviso(String uid, String reservaId) {
        return Arrays.stream(manager.getActiveNotifications())
                .filter(a -> CanalChat.ID.equals(a.getNotification().getChannelId()))
                .filter(a -> uid.equals(a.getNotification().extras.getString(CanalChat.EXTRA_USUARIO_ID)))
                .filter(a -> reservaId.equals(a.getNotification().extras.getString(CanalChat.EXTRA_BOOKING_ID)))
                .findFirst().orElseThrow(() -> new AssertionError("No se publicó el aviso esperado"));
    }

    private static boolean existeOtroAviso() {
        return Arrays.stream(manager.getActiveNotifications())
                .anyMatch(a -> OTRO_TAG.equals(a.getTag()) && a.getId() == OTRO_ID);
    }

    private static void esperar(BooleanSupplier condicion) {
        long limite = SystemClock.uptimeMillis() + 3000;
        while (!condicion.getAsBoolean() && SystemClock.uptimeMillis() < limite) {
            SystemClock.sleep(25);
        }
        assertTrue("Android no alcanzó el estado esperado", condicion.getAsBoolean());
    }
}
