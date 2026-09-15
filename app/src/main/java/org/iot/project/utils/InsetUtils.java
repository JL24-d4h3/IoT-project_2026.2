package org.iot.project.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Reparto de los insets del sistema.
 *
 * <p>Con targetSdk 36 el modo borde a borde es obligatorio en API 35+ y
 * {@code android:statusBarColor} deja de tener efecto: la aplicacion dibuja
 * detras de la barra de estado y de navegacion. Por eso los insets se aplican
 * aqui, en codigo, conservando el padding que el layout ya tuviera.
 *
 * <p>Se usa {@code core.graphics.Insets} con nombre completo para no chocar con
 * {@code android.graphics.Insets}.
 */
public final class InsetUtils {

    private InsetUtils() {
    }

    /**
     * Suma el inset superior al padding superior de la vista. Se usa en las
     * cabeceras, para que el titulo no quede bajo la barra de estado.
     */
    public static void applyTopPadding(@NonNull View view) {
        final int initialTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), initialTop + bars.top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Suma el inset inferior al padding inferior. Se usa en la barra de
     * navegacion, para que no quede bajo la barra de gestos.
     */
    public static void applyBottomPadding(@NonNull View view) {
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), initialBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Aplica ambos insets. Se usa en el contenedor raiz de una pantalla cuyo
     * contenido no se dibuja a sangre.
     */
    public static void applySystemBarsPadding(@NonNull View view) {
        final int initialTop = view.getPaddingTop();
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), initialTop + bars.top,
                    v.getPaddingRight(), initialBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Deja pasar los insets a los hijos sin consumirlos. Necesario en el
     * contenedor que envuelve al NavHostFragment: si lo consumiera, ninguna
     * pantalla los recibiria.
     */
    public static void passThrough(@NonNull View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> windowInsets);
    }
}
