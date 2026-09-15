package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.models.Booking;
import org.iot.project.models.BookingStatus;
import org.iot.project.models.Hotel;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Resumen de una reserva (§27, §58).
 *
 * <p>Es la reserva contada en cuatro lineas, para cuando hace falta saber de
 * cual se esta hablando sin abrirla: elegir la reserva de la que cuelga un taxi
 * (§36), o confirmar antes de pagar. La tarjeta completa de Mis Reservas
 * ({@link BookingCardView}) enseña mas cosas y ocupa mas; esta es la version
 * que cabe dentro de otro formulario.
 *
 * <p>El nombre del hotel lo pide al repositorio y no lo recibe: quien tiene una
 * reserva tiene un identificador de hotel, y obligar a cada pantalla a resolver
 * el nombre seria repetir la misma consulta en todas.
 */
public class BookingSummaryView extends MaterialCardView {

    private final TextView hotel;
    private final TextView codigo;
    private final TextView fechas;
    private final TextView estado;
    private final TextView total;
    private final TextView pistaTaxi;
    private final ImageView marca;

    public BookingSummaryView(@NonNull Context context) {
        this(context, null);
    }

    public BookingSummaryView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BookingSummaryView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.view_booking_summary, this, true);

        hotel = findViewById(R.id.summary_hotel);
        codigo = findViewById(R.id.summary_code);
        fechas = findViewById(R.id.summary_dates);
        estado = findViewById(R.id.summary_status);
        total = findViewById(R.id.summary_total);
        pistaTaxi = findViewById(R.id.summary_taxi_hint);
        marca = findViewById(R.id.summary_check);
    }

    public void bind(@NonNull Booking reserva) {
        Hotel alojamiento = org.iot.project.core.ServiceLocator.hoteles()
                .hotel(reserva.getHotelId());
        hotel.setText(alojamiento != null
                ? alojamiento.getNombre()
                : getContext().getString(R.string.reserva_hotel_desconocido));

        codigo.setText(reserva.getCodigo());

        String rango = DateFormatter.rangoConNoches(
                reserva.getFechaEntrada(), reserva.getFechaSalida(), reserva.getNumNoches());
        fechas.setText(rango + " · " + getContext().getResources().getQuantityString(
                R.plurals.reserva_huespedes, reserva.getNumHuespedes(), reserva.getNumHuespedes()));

        pintarEstado(reserva.getEstado());

        total.setText(PriceFormatter.format(reserva.getTotal()));

        // Solo se explica el taxi gratuito cuando de verdad lo es: el minimo lo
        // fija cada hotel (RF-084), asi que no vale suponerlo por el monto.
        boolean taxiGratis = alojamiento != null
                && reserva.calificaParaTaxiGratuito(alojamiento.getMontoMinimoTaxi());
        pistaTaxi.setVisibility(taxiGratis ? VISIBLE : GONE);
    }

    /** Marca la tarjeta como la elegida, o la devuelve al estado normal. */
    public void setElegida(boolean elegida) {
        marca.setVisibility(elegida ? VISIBLE : GONE);
        setStrokeWidth(elegida
                ? getResources().getDimensionPixelSize(R.dimen.stroke_selected)
                : getResources().getDimensionPixelSize(R.dimen.border_width));
        setStrokeColor(ContextCompat.getColor(getContext(),
                elegida ? R.color.colorPrimary : R.color.colorBorder));
    }

    private void pintarEstado(@NonNull BookingStatus estadoReserva) {
        estado.setText(estadoReserva.getDisplayName());

        int fondo;
        int colorTexto;
        switch (estadoReserva) {
            case PENDIENTE:
                fondo = R.drawable.bg_badge_warning;
                colorTexto = R.color.colorOnWarningContainer;
                break;
            case CONFIRMADA:
            case ACTIVA:
                fondo = R.drawable.bg_badge_primary;
                colorTexto = R.color.colorOnPrimaryContainer;
                break;
            case FINALIZADA:
                fondo = R.drawable.bg_badge_success;
                colorTexto = R.color.colorOnSuccessContainer;
                break;
            case CANCELADA:
            default:
                fondo = R.drawable.bg_badge_error;
                colorTexto = R.color.colorOnErrorContainer;
                break;
        }
        estado.setBackground(ContextCompat.getDrawable(getContext(), fondo));
        estado.setTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), colorTexto)));
    }
}
