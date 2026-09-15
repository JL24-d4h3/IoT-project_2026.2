package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.iot.project.R;
import org.iot.project.models.BookingStatus;

/**
 * Distintivo de estado de una reserva.
 *
 * <p>El color no es decoración: es lo que permite recorrer una lista de reservas
 * y saber cuáles exigen algo del usuario. Por eso cada estado tiene su pareja
 * fondo/texto elegida para cumplir contraste AA, en vez de pintar el texto del
 * color del estado sobre blanco.
 */
public class BookingStatusBadgeView extends LinearLayout {

    private final TextView texto;

    public BookingStatusBadgeView(@NonNull Context context) {
        this(context, null);
    }

    public BookingStatusBadgeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BookingStatusBadgeView(@NonNull Context context, @Nullable AttributeSet attrs,
                                  int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_booking_status_badge, this, true);
        texto = findViewById(R.id.badge_text);
    }

    public void setEstado(@NonNull BookingStatus estado) {
        texto.setText(estado.getDisplayName());
        texto.setTextColor(ContextCompat.getColor(getContext(), colorDeTexto(estado)));
        texto.setBackgroundResource(fondoDe(estado));
    }

    private int colorDeTexto(@NonNull BookingStatus estado) {
        switch (estado) {
            case CONFIRMADA:
                return R.color.colorOnSuccessContainer;
            case ACTIVA:
                return R.color.colorOnPrimaryContainer;
            case CANCELADA:
                return R.color.colorOnErrorContainer;
            case PENDIENTE:
                return R.color.colorOnWarningContainer;
            case FINALIZADA:
            default:
                return R.color.colorOnSurfaceVariant;
        }
    }

    private int fondoDe(@NonNull BookingStatus estado) {
        switch (estado) {
            case CONFIRMADA:
                return R.drawable.bg_badge_success;
            case ACTIVA:
                return R.drawable.bg_badge_primary;
            case CANCELADA:
                return R.drawable.bg_badge_error;
            case PENDIENTE:
                return R.drawable.bg_badge_warning;
            case FINALIZADA:
            default:
                return R.drawable.bg_chip_neutral;
        }
    }
}
