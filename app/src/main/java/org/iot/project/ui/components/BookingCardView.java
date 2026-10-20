package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
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
 * <p>Usa el hotel del catálogo si está disponible y, en su ausencia, los datos
 * conservados en la reserva para mostrar también hoteles retirados del catálogo.
 */
public class BookingCardView extends MaterialCardView {

    private final ImageView imagen;
    private final TextView hotel;
    private final TextView ubicacion;
    private final BookingStatusBadgeView estado;
    private final TextView codigo;
    private final TextView fechas;
    private final TextView total;
    private final View chat;

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
        chat = findViewById(R.id.booking_chat);
    }

    public void bind(@NonNull Booking reserva) {
        mostrarChat(null);
        Hotel alojamiento = ServiceLocator.hoteles().hotel(reserva.getHotelId());

        String nombre = alojamiento != null
                ? alojamiento.getNombre() : reserva.getHotelNombre();
        hotel.setText(nombre.isEmpty()
                ? getContext().getString(R.string.reserva_hotel_desconocido) : nombre);

        String lugar;
        if (alojamiento != null) {
            lugar = alojamiento.getUbicacionCorta();
        } else if (reserva.getHotelDistrito().isEmpty()) {
            lugar = reserva.getHotelCiudad();
        } else if (reserva.getHotelCiudad().isEmpty()) {
            lugar = reserva.getHotelDistrito();
        } else {
            lugar = reserva.getHotelDistrito() + " · " + reserva.getHotelCiudad();
        }
        ubicacion.setText(lugar);
        ubicacion.setVisibility(lugar == null || lugar.isEmpty() ? GONE : VISIBLE);

        String foto = alojamiento != null && !alojamiento.getFotos().isEmpty()
                ? alojamiento.getFotos().get(0) : reserva.getHotelFotoUrl();
        Glide.with(this)
                .load(foto.isEmpty() ? null : foto)
                .placeholder(R.drawable.bg_skeleton)
                .error(R.drawable.ic_search_off)
                .fallback(R.drawable.ic_search_off)
                .centerCrop()
                .into(imagen);

        estado.setEstado(reserva.getEstado());
        codigo.setText(reserva.getCodigo());
        fechas.setText(DateFormatter.rango(reserva.getFechaEntrada(), reserva.getFechaSalida()));
        total.setText(PriceFormatter.format(reserva.getTotal()));
    }

    /** La pantalla conecta esta acción solo para reservas con chat disponible. */
    public void mostrarChat(@Nullable View.OnClickListener accion) {
        chat.setOnClickListener(accion);
        chat.setVisibility(accion == null ? GONE : VISIBLE);
    }
}
