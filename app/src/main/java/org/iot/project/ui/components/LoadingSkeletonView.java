package org.iot.project.ui.components;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.R;

/**
 * Esqueleto de carga.
 *
 * <p>Se usa en lugar de un indicador giratorio cuando ya se sabe qué forma
 * tendrá el contenido: el usuario ve dónde va a aparecer cada cosa y la
 * pantalla no salta cuando llegan los datos.
 *
 * <p>La animación es un latido de opacidad, no un brillo que recorre la
 * pantalla. Es más barato de dibujar y, al no moverse en horizontal, no
 * distrae mientras el resto de la interfaz sigue siendo legible.
 */
public class LoadingSkeletonView extends LinearLayout {

    private static final long DURACION_LATIDO_MS = 900L;
    private static final float OPACIDAD_MIN = 0.45f;
    private static final float OPACIDAD_MAX = 1f;

    private ValueAnimator latido;

    public LoadingSkeletonView(@NonNull Context context) {
        this(context, null);
    }

    public LoadingSkeletonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadingSkeletonView(@NonNull Context context, @Nullable AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_skeleton, this, true);
    }

    /** Variante sin bloque de imagen, para listas donde no hay fotografía. */
    public void sinImagen() {
        View imagen = findViewById(R.id.skeleton_image);
        if (imagen != null) {
            imagen.setVisibility(GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        iniciarLatido();
    }

    @Override
    protected void onDetachedFromWindow() {
        detenerLatido();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // Animar algo que no se ve solo gasta batería.
        if (visibility == VISIBLE) {
            iniciarLatido();
        } else {
            detenerLatido();
        }
    }

    private void iniciarLatido() {
        if (latido != null && latido.isRunning()) {
            return;
        }
        latido = ObjectAnimator.ofFloat(this, "alpha", OPACIDAD_MAX, OPACIDAD_MIN);
        latido.setDuration(DURACION_LATIDO_MS);
        latido.setRepeatMode(ValueAnimator.REVERSE);
        latido.setRepeatCount(ValueAnimator.INFINITE);
        latido.setInterpolator(new LinearInterpolator());
        latido.start();
    }

    private void detenerLatido() {
        if (latido != null) {
            latido.cancel();
            latido = null;
        }
        setAlpha(OPACIDAD_MAX);
    }
}
