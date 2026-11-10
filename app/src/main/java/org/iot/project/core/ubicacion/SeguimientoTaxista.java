package org.iot.project.core.ubicacion;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.iot.project.core.SessionManager;

/** Arranque explícito desde una pantalla visible; nunca desde alarmas ni al encender el móvil. */
public final class SeguimientoTaxista {
    static final String EXTRA_SERVICIO = "servicio";
    static final String EXTRA_VERSION = "versionSeguimiento";
    private static final MutableLiveData<EstadoSeguimiento> ESTADO = new MutableLiveData<>(
            new EstadoSeguimiento(false, false, "El seguimiento se activa durante un viaje.", 0, null, null));
    private static Context aplicacion;
    private static volatile String solicitado;
    private static volatile long version;

    private SeguimientoTaxista() { }

    public static LiveData<EstadoSeguimiento> estado() { return ESTADO; }

    /** El fragmento comprueba permiso preciso, GPS y viaje activo antes de llamarlo. */
    public static void iniciar(Context contexto, String servicioId) {
        if (servicioId == null || !servicioId.matches("[1-9][0-9]*")) return;
        String identidad = SessionManager.getIdentidadId();
        String clave = identidad + "/" + servicioId;
        if (clave.equals(solicitado)) return;
        aplicacion = contexto.getApplicationContext();
        solicitado = clave;
        long nuevaVersion = ++version;
        try {
            ContextCompat.startForegroundService(aplicacion, new Intent(aplicacion,
                    ServicioUbicacionTaxista.class).putExtra(EXTRA_SERVICIO, servicioId)
                    .putExtra(EXTRA_VERSION, nuevaVersion));
        } catch (SecurityException | IllegalStateException error) {
            publicar(nuevaVersion, identidad, servicioId, false, true,
                    "Abre el panel del taxista y permite la ubicación precisa para reanudar el seguimiento.", 0);
            finalizado(nuevaVersion);
        }
    }

    public static void detener(Context contexto) {
        aplicacion = contexto.getApplicationContext();
        detenerActual();
    }

    /** También lo invoca cerrar sesión, aunque la pantalla del conductor ya no esté visible. */
    public static void detenerActual() {
        solicitado = null;
        version++;
        if (aplicacion != null) aplicacion.stopService(new Intent(aplicacion, ServicioUbicacionTaxista.class));
        EstadoSeguimiento previo = ESTADO.getValue();
        if (previo == null || !previo.isBloqueado()) {
            emitir(new EstadoSeguimiento(false, false, "El seguimiento se activa durante un viaje.", 0,
                    previo == null ? null : previo.getIdentidadId(), previo == null ? null : previo.getServicioId()));
        }
    }

    static boolean vigente(long solicitudVersion) { return version == solicitudVersion && solicitado != null; }

    static boolean haySolicitudPendiente() { return solicitado != null; }

    static void finalizado(long solicitudVersion) {
        if (solicitudVersion == version) solicitado = null;
    }

    static void publicar(long solicitudVersion, String identidad, String servicio, boolean activo,
                         boolean bloqueado, String mensaje, long ultimoEnvioMs) {
        if (solicitudVersion != version) return;
        EstadoSeguimiento nuevo = new EstadoSeguimiento(activo, bloqueado, mensaje, ultimoEnvioMs, identidad, servicio);
        if (Looper.myLooper() == Looper.getMainLooper()) ESTADO.setValue(nuevo);
        else new Handler(Looper.getMainLooper()).post(() -> {
            if (solicitudVersion == version) ESTADO.setValue(nuevo);
        });
    }

    private static void emitir(EstadoSeguimiento nuevo) {
        if (Looper.myLooper() == Looper.getMainLooper()) ESTADO.setValue(nuevo);
        else new Handler(Looper.getMainLooper()).post(() -> ESTADO.setValue(nuevo));
    }
}
