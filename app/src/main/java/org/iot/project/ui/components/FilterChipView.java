package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;

import org.iot.project.models.HotelService;
import org.iot.project.models.Service;

/**
 * Chip de filtro (§18).
 *
 * <p>Se apoya en el {@code Chip} de Material en lugar de dibujarse a mano: el
 * tema ya le da el estilo de la aplicacion ({@code chipStyle} apunta a
 * {@code Widget.App.Chip.Filter}), asi que este componente solo aporta lo que
 * Material no sabe: como se pinta un servicio del catalogo.
 *
 * <p>Los tres estados de §18 salen de la propia seleccion —normal, marcado y
 * deshabilitado—, no de un atributo aparte: un servicio que no se puede
 * aplicar es un servicio que el hotel no ofrece.
 */
public class FilterChipView extends Chip {

    public FilterChipView(@NonNull Context context) {
        this(context, null);
    }

    public FilterChipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setCheckable(true);
        setEnsureMinTouchTargetSize(true);
    }

    /** Chip de un servicio del catalogo global (§13, §17). */
    public void bind(@NonNull Service servicio) {
        setText(servicio.getName());
        setChipIconResource(servicio.getIconRes());
        setChipIconVisible(true);
    }

    /**
     * Chip de un servicio tal como lo ofrece un hotel.
     *
     * <p>Un servicio que el hotel no ofrece se dibuja apagado en vez de
     * desaparecer: que el usuario vea que existe y que ese hotel no lo tiene es
     * informacion util, y una lista de chips que cambia de tamano segun el
     * hotel se lee peor.
     */
    public void bind(@NonNull Service servicio, @NonNull HotelService ofrecido) {
        bind(servicio);
        setChipIconVisible(ofrecido.isIncluded());
    }

    /** Un chip que no lleva icono no debe reservar el hueco del icono. */
    public void sinIcono() {
        setChipIconVisible(false);
    }
}
