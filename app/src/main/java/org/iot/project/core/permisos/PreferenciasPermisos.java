package org.iot.project.core.permisos;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Historial por instalación. Nunca sustituye la comprobación del permiso real de Android. */
public final class PreferenciasPermisos {
    public enum Permiso { NOTIFICACIONES, CAMARA }
    public enum Estado { NO_SOLICITADO, CONCEDIDO, DENEGADO }

    private final SharedPreferences preferencias;

    public static PreferenciasPermisos desde(@NonNull Context contexto) {
        Context app = contexto.getApplicationContext();
        return new PreferenciasPermisos(app.getSharedPreferences("permisos_aplicacion", Context.MODE_PRIVATE),
                app.getSharedPreferences("permisos_notificaciones", Context.MODE_PRIVATE));
    }

    PreferenciasPermisos(SharedPreferences preferencias, SharedPreferences avisosLegacy) {
        this.preferencias = preferencias;
        // Conserva un rechazo anterior al actualizar la app; no vuelve a abrir el diálogo.
        if (!preferencias.contains(solicitado(Permiso.NOTIFICACIONES))
                && avisosLegacy.getBoolean("chat_solicitado", false)) {
            registrarResultado(Permiso.NOTIFICACIONES, false);
        }
    }

    public boolean fueSolicitado(@NonNull Permiso permiso) {
        return preferencias.getBoolean(solicitado(permiso), false);
    }

    @NonNull public Estado getEstado(@NonNull Permiso permiso) {
        String guardado = preferencias.getString(estado(permiso), "");
        try { return Estado.valueOf(guardado); }
        catch (IllegalArgumentException fallo) {
            return fueSolicitado(permiso) ? Estado.DENEGADO : Estado.NO_SOLICITADO;
        }
    }

    /** Se registra antes de abrir el diálogo para sobrevivir a una recreación del proceso. */
    public void registrarSolicitud(@NonNull Permiso permiso) {
        registrarResultado(permiso, false);
    }

    public void registrarResultado(@NonNull Permiso permiso, boolean concedido) {
        preferencias.edit().putBoolean(solicitado(permiso), true)
                .putString(estado(permiso), (concedido ? Estado.CONCEDIDO : Estado.DENEGADO).name()).apply();
    }

    /** permisoRealAndroid debe proceder de checkSelfPermission, nunca de la copia guardada. */
    @NonNull public Estado sincronizar(@NonNull Permiso permiso, boolean permisoRealAndroid) {
        Estado actual = permisoRealAndroid ? Estado.CONCEDIDO
                : fueSolicitado(permiso) ? Estado.DENEGADO : Estado.NO_SOLICITADO;
        if (getEstado(permiso) != actual || (permisoRealAndroid && !fueSolicitado(permiso))) {
            SharedPreferences.Editor editor = preferencias.edit().putString(estado(permiso), actual.name());
            if (permisoRealAndroid) editor.putBoolean(solicitado(permiso), true);
            editor.apply();
        }
        return actual;
    }

    private static String solicitado(Permiso permiso) { return permiso.name() + ".solicitado"; }
    private static String estado(Permiso permiso) { return permiso.name() + ".estado"; }
}
