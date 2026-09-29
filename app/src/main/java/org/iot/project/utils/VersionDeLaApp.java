package org.iot.project.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

/**
 * El numero de version que se enseña al pie de los perfiles.
 *
 * <p>Vive aqui y no en cada perfil porque los tres roles lo escriben igual y el
 * manejo del error es la parte que importa: si el paquete no se encontrara —no
 * deberia, es el de la propia aplicacion— vale mas una linea de version sin
 * numero que una pantalla que no abre.
 */
public final class VersionDeLaApp {

    private VersionDeLaApp() {
    }

    /** Nombre de la version instalada, o cadena vacia si no se pudo leer. */
    @NonNull
    public static String nombre(@NonNull Context contexto) {
        try {
            String nombre = contexto.getPackageManager()
                    .getPackageInfo(contexto.getPackageName(), 0).versionName;
            return nombre != null ? nombre : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
