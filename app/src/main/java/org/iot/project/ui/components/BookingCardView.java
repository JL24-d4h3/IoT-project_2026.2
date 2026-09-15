package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.models.Booking;
import org.iot.project.models.Hotel;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Tarjeta de reserva, compartida por "Mis reservas" y el historial de estadías.
 *
 * <p>Resuelve el hotel a partir del identificador que guarda la reserva, porque
 * quien pinta la lista solo tiene reservas. La búsqueda es sincrónica: el
 * catálogo de hoteles ya está en memoria (ver HotelRepository).
 */
public class BookingCardView extends MaterialCardView {

    private final ImageView imagen;
    private final TextView hotel;
    private final TextView ubicacion;
    private final BookingStatusBadgeView estado;
    private final TextView codigo;
    private final TextView fechas;
    private final TextView total;

    public BookingCardView(@NonNull Context context) {
        this(context, null);
    }

    public BookingCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BookingCardView(@NonNull Context context, @Nullable AttributeSet attrs,
                           int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.view_booking_card, this);

        imagen = findViewById(R.id.booking_image);
        hotel = findViewById(R.id.booking_hotel);
        ubicacion = findViewById(R.id.booking_ubicacion);
        estado = findViewById(R.id.booking_estado);
        codigo = findViewById(R.id.booking_codigo);
        fechas = findViewById(R.id.booking_fechas);
        total = findViewById(R.id.booking_total);
    }

    public void bind(@NonNull Booking reserva) {
        Hotel alojamiento = ServiceLocator.hoteles().hotel(reserva.getHotelId());

        if (alojamiento != null) {
            hotel.setText(alojamiento.getNombre());
            ubicacion.setText(alojamiento.getUbicacionCorta());
            Glide.with(this)
                    .load(alojamiento.getFotos().isEmpty() ? null : alojamiento.getFotos().get(0))
                    .placeholder(R.drawable.bg_skeleton)
                    .error(R.drawable.ic_search_off)
                    .centerCrop()
                    .into(imagen);
        } else {
            // Una reserva sin hotel conocido es un dato roto, pero la reserva
            // sigue siendo válida: se muestra lo que sí se sabe.
            hotel.setText(R.string.reserva_hotel_desconocido);
            ubicacion.setVisibility(GONE);
        }

        estado.setEstado(reserva.getEstado());
        codigo.setText(reserva.getCodigo());
        fechas.setText(DateFormatter.rango(reserva.getFechaEntrada(), reserva.getFechaSalida()));
        total.setText(PriceFormatter.format(reserva.getTotal()));
    }
}
