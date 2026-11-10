package org.iot.project.core.ubicacion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import org.iot.project.MainActivity;
import org.iot.project.R;
import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.rest.ApiException;
import org.iot.project.data.rest.SesionTaxistaRest;
import org.iot.project.data.rest.TaxistasApi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiStatus;

/** GPS durante un viaje, con notificación visible y envío exclusivo al backend REST. */
public final class ServicioUbicacionTaxista extends Service {
    private static final String CANAL = "seguimiento_taxi";
    private static final int NOTIFICACION = 2601;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private LocationManager gps;
    private String servicioId;
    private String conductorId;
    private String token;
    private volatile boolean cerrado;
    private volatile long solicitudVersion;
    private boolean escuchando;
    private boolean redOcupada;
    private long ultimoIntentoMs = -PoliticaUbicacion.INTERVALO_ENVIO_MS;
    private long ultimoEnvioMs;
    private long posicionEnviadaNanos;
    private Location ultimaPosicion;
    private String textoNotificacion;

    private final LocationListener posiciones = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location posicion) {
            if (cerrado || !accesoGps()) return;
            if (valida(posicion) && (ultimaPosicion == null
                    || posicion.getElapsedRealtimeNanos() > ultimaPosicion.getElapsedRealtimeNanos())) {
                ultimaPosicion = new Location(posicion);
            }
        }
        @Override public void onProviderDisabled(@NonNull String proveedor) {
            if (LocationManager.GPS_PROVIDER.equals(proveedor)) pausarPorPermiso();
        }
    };

    private final Runnable ciclo = new Runnable() {
        @Override public void run() {
            if (cerrado) return;
            if (!sesionVigente()) {
                detener("Tu sesión cambió. Ingresa de nuevo para reanudar el seguimiento.", true);
                return;
            }
            if (!accesoGps()) { pausarPorPermiso(); return; }
            long ahora = SystemClock.elapsedRealtime();
            if (!redOcupada && ahora - ultimoIntentoMs >= PoliticaUbicacion.INTERVALO_ENVIO_MS) {
                sincronizar();
            }
            principal.postDelayed(this, 2_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        NotificationManager gestor = getSystemService(NotificationManager.class);
        if (gestor != null) {
            NotificationChannel canal = new NotificationChannel(CANAL, "Ubicación del viaje",
                    NotificationManager.IMPORTANCE_LOW);
            canal.setDescription("Seguimiento GPS mientras atiendes un servicio de taxi.");
            gestor.createNotificationChannel(canal);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String solicitado = intent == null ? null : intent.getStringExtra(SeguimientoTaxista.EXTRA_SERVICIO);
        long nuevaVersion = intent == null ? -1 : intent.getLongExtra(SeguimientoTaxista.EXTRA_VERSION, -1);
        // Un arranque retrasado de otra cuenta o viaje no sustituye el solicitado más reciente.
        if (!SeguimientoTaxista.vigente(nuevaVersion)) {
            if (servicioId == null && !SeguimientoTaxista.haySolicitudPendiente()) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (servicioId != null && servicioId.equals(solicitado)
                && solicitudVersion == nuevaVersion && sesionVigente()) return START_NOT_STICKY;
        // Puede llegar un viaje nuevo antes de que Android destruya el servicio anterior.
        // Se cambia el contexto local y los callbacks antiguos quedan invalidados por versión.
        limpiarGps();
        principal.removeCallbacks(ciclo);
        solicitudVersion = nuevaVersion;
        cerrado = false;
        redOcupada = false;
        ultimoIntentoMs = -PoliticaUbicacion.INTERVALO_ENVIO_MS;
        ultimoEnvioMs = 0;
        posicionEnviadaNanos = 0;
        ultimaPosicion = null;
        if (solicitado == null || !solicitado.matches("[1-9][0-9]*") || !SesionTaxistaRest.estaActiva()) {
            detener("Inicia sesión para compartir la ubicación durante el viaje.", true);
            return START_NOT_STICKY;
        }
        servicioId = solicitado;
        conductorId = SesionTaxistaRest.getId();
        token = SesionTaxistaRest.getToken();
        if (!sesionVigente() || !accesoGps()) {
            pausarPorPermiso();
            return START_NOT_STICKY;
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICACION,
                    notificacion("Comprobando el viaje antes de compartir la ubicación…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            publicar("Comprobando el viaje antes de compartir la ubicación…");
            principal.post(ciclo);
        } catch (SecurityException | IllegalStateException error) {
            detener("Permite la ubicación precisa y abre el panel para iniciar el seguimiento.", true);
        }
        // El JWT no se persiste. Si Android termina el proceso, se recupera el viaje al ingresar.
        return START_NOT_STICKY;
    }

    private boolean sesionVigente() {
        return sesionVigente(solicitudVersion, conductorId, token);
    }

    private boolean sesionVigente(long esperada, String conductor, String credencial) {
        return !cerrado && esperada == solicitudVersion && SeguimientoTaxista.vigente(esperada)
                && conductor != null && credencial != null
                && SessionManager.getRolActivo() == Role.CONDUCTOR
                && ("taxista:" + conductor).equals(SessionManager.getIdentidadId())
                && conductor.equals(SesionTaxistaRest.getId()) && credencial.equals(SesionTaxistaRest.getToken());
    }

    private boolean accesoGps() {
        try {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED && gps != null && gps.isLocationEnabled()
                    && gps.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (RuntimeException error) { return false; }
    }

    private static boolean valida(Location posicion) {
        return posicion != null && posicion.hasAccuracy() && PoliticaUbicacion.posicionValida(
                posicion.getLatitude(), posicion.getLongitude(), posicion.getAccuracy(),
                posicion.getElapsedRealtimeAgeMillis());
    }

    @SuppressLint("MissingPermission")
    private void escucharGps() {
        if (escuchando || cerrado) return;
        if (!accesoGps()) { pausarPorPermiso(); return; }
        try {
            gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, posiciones,
                    Looper.getMainLooper());
            escuchando = true;
            if (gps.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                gps.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000L, 0f, posiciones,
                        Looper.getMainLooper());
            }
        } catch (SecurityException error) { pausarPorPermiso(); }
        catch (IllegalArgumentException error) {
            detener("Activa el GPS del dispositivo para reanudar el seguimiento.", true);
        }
    }

    private void sincronizar() {
        redOcupada = true;
        final long versionEnvio = solicitudVersion;
        final String viajeEnvio = servicioId;
        final String conductorEnvio = conductorId;
        final String tokenEnvio = token;
        ultimoIntentoMs = SystemClock.elapsedRealtime();
        final Location posicion = ultimaPosicion != null && valida(ultimaPosicion)
                && ultimaPosicion.getElapsedRealtimeNanos() > posicionEnviadaNanos
                ? new Location(ultimaPosicion) : null;
        TaxistasApi.ejecutar(() -> {
            if (!sesionVigente(versionEnvio, conductorEnvio, tokenEnvio) || !accesoGps()) return Resultado.bloqueado();
            try {
                TaxistasApi api = TaxistasApi.getInstancia();
                JsonObject estado = api.objeto("GET", "/api/servicios/" + viajeEnvio + "/estado", null, tokenEnvio);
                if (!estado.has("servicioId") || estado.get("servicioId").isJsonNull()
                        || !viajeEnvio.equals(estado.get("servicioId").getAsString())
                        || !estado.has("taxistaId") || estado.get("taxistaId").isJsonNull()
                        || !conductorEnvio.equals(estado.get("taxistaId").getAsString())) {
                    return Resultado.error("Este servicio ya no está asignado a tu cuenta.", true);
                }
                TaxiStatus status = TaxiStatus.valueOf(estado.get("estado").getAsString());
                if (status.isFinished()) return Resultado.finalizado();
                if (status == TaxiStatus.SOLICITADO || !status.isActive()) {
                    return Resultado.error("El servicio todavía no está asignado para iniciar el seguimiento.", true);
                }
                if (!sesionVigente(versionEnvio, conductorEnvio, tokenEnvio) || !accesoGps()) return Resultado.bloqueado();
                if (posicion == null || !valida(posicion)) return Resultado.esperando();
                JsonObject cuerpo = new JsonObject();
                cuerpo.addProperty("latitud", posicion.getLatitude());
                cuerpo.addProperty("longitud", posicion.getLongitude());
                JsonObject reporte = api.objeto("PUT", "/api/taxistas/" + conductorEnvio + "/ubicacion", cuerpo, tokenEnvio);
                if (!conductorEnvio.equals(reporte.get("taxistaId").getAsString())) {
                    return Resultado.error("El backend no confirmó tu ubicación. Revisa tu sesión.", true);
                }
                return Resultado.enviado();
            } catch (ApiException error) {
                boolean terminal = error.getStatus() >= 400 && error.getStatus() < 500
                        && error.getStatus() != 408 && error.getStatus() != 429;
                return Resultado.error(terminal
                        ? "El backend no permite continuar el seguimiento. Revisa la sesión y el viaje."
                        : "Sin conexión con el backend. El viaje sigue activo; reintentaremos con una posición reciente.", terminal);
            } catch (RuntimeException error) {
                return Resultado.error("No pudimos comprobar el viaje. Reintentaremos sin cambiar su estado.", false);
            }
        }, new ResultCallback<Resultado>() {
            @Override public void onExito(@NonNull Resultado resultado) {
                if (cerrado || versionEnvio != solicitudVersion || !SeguimientoTaxista.vigente(versionEnvio)) return;
                redOcupada = false;
                if (!sesionVigente() || !accesoGps() || resultado.bloqueado) { pausarPorPermiso(); return; }
                if (resultado.finalizado) { detener("El viaje finalizó. Seguimiento detenido.", false); return; }
                if (resultado.terminal) { detener(resultado.mensaje, true); return; }
                if (resultado.mensaje != null) { publicar(resultado.mensaje); return; }
                escucharGps();
                if (cerrado) return;
                if (resultado.enviado && posicion != null) {
                    posicionEnviadaNanos = posicion.getElapsedRealtimeNanos();
                    // La vigencia se mide desde la captura, incluso si la red tarda en confirmar.
                    ultimoEnvioMs = posicion.getElapsedRealtimeNanos() / 1_000_000L;
                    publicar("Ubicación del viaje compartida con el backend.");
                } else {
                    publicar("Esperando una posición GPS reciente. El viaje permanece activo.");
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                if (cerrado || versionEnvio != solicitudVersion || !SeguimientoTaxista.vigente(versionEnvio)) return;
                redOcupada = false;
                if (!cerrado) publicar("No se pudo enviar la ubicación. Reintentaremos sin cancelar el viaje.");
            }
        });
    }

    private void pausarPorPermiso() {
        detener("Seguimiento pausado. Activa el GPS y permite la ubicación precisa; tu viaje se conserva.", true);
    }

    private void publicar(String mensaje) {
        SeguimientoTaxista.publicar(solicitudVersion, "taxista:" + conductorId, servicioId,
                true, false, mensaje, ultimoEnvioMs);
        if (!mensaje.equals(textoNotificacion)) {
            textoNotificacion = mensaje;
            NotificationManager gestor = getSystemService(NotificationManager.class);
            if (gestor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) gestor.notify(NOTIFICACION, notificacion(mensaje));
        }
    }

    private Notification notificacion(String mensaje) {
        Intent abrir = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent destino = PendingIntent.getActivity(this, NOTIFICACION, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_location).setContentTitle("Seguimiento del viaje")
                .setContentText(mensaje).setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setContentIntent(destino).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void detener(String mensaje, boolean bloqueado) {
        cerrado = true;
        limpiarGps();
        principal.removeCallbacks(ciclo);
        // Si B ya fue solicitado, A se retira sin cancelar el onStartCommand(B) pendiente.
        // Un cierre explícito de la fachada ya envió stopService por su propia cuenta.
        if (!SeguimientoTaxista.vigente(solicitudVersion)) return;
        SeguimientoTaxista.publicar(solicitudVersion, "taxista:" + conductorId, servicioId,
                false, bloqueado, mensaje, ultimoEnvioMs);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        cerrado = true;
        principal.removeCallbacksAndMessages(null);
        limpiarGps();
        EstadoSeguimiento anterior = SeguimientoTaxista.estado().getValue();
        if (anterior != null && anterior.isActivo()) {
            SeguimientoTaxista.publicar(solicitudVersion, "taxista:" + conductorId, servicioId,
                    false, false, "El seguimiento está detenido. Abre el panel para reanudarlo.", 0);
        }
        SeguimientoTaxista.finalizado(solicitudVersion);
        super.onDestroy();
    }

    private void limpiarGps() {
        if (gps != null && escuchando) {
            try { gps.removeUpdates(posiciones); } catch (RuntimeException ignorado) { }
        }
        escuchando = false;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private static final class Resultado {
        boolean finalizado;
        boolean bloqueado;
        boolean enviado;
        boolean terminal;
        String mensaje;
        static Resultado finalizado() { Resultado r = new Resultado(); r.finalizado = true; return r; }
        static Resultado bloqueado() { Resultado r = new Resultado(); r.bloqueado = true; return r; }
        static Resultado enviado() { Resultado r = new Resultado(); r.enviado = true; return r; }
        static Resultado esperando() { return new Resultado(); }
        static Resultado error(String mensaje, boolean terminal) {
            Resultado r = new Resultado(); r.mensaje = mensaje; r.terminal = terminal; return r;
        }
    }
}
