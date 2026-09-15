package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.models.Driver;

/**
 * Conductor asignado a un servicio de taxi (§37).
 *
 * <p>Es la tarjeta que responde a "quien viene a buscarme". Enseña lo que el
 * cliente necesita para reconocerlo en la puerta —cara, nombre y auto— y su
 * valoracion, que es el aval de que el servicio es de fiar (RF-081).
 *
 * <p>La valoracion va en {@link RatingBadgeView} y no en un texto suelto por el
 * mismo motivo que en las tarjetas de hotel: la escala es 1 a 10 (§10, regla
 * 9) y el ambar sobre blanco no cumple contraste (§56). Teniendolo en una sola
 * clase, es imposible pintarlo al reves.
 */
public class DriverCardView extends MaterialCardView {

    private final TextView iniciales;
    private final ImageView foto;
    private final TextView nombre;
    private final TextView servicios;
    private final RatingBadgeView valoracion;
    private final VehicleCardView vehiculo;

    public DriverCardView(@NonNull Context context) {
        this(context, null);
    }

    public DriverCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DriverCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.view_driver_card, this, true);

        iniciales = findViewById(R.id.driver_initials);
        foto = findViewById(R.id.driver_photo);
        nombre = findViewById(R.id.driver_name);
        servicios = findViewById(R.id.driver_services);
        valoracion = findViewById(R.id.driver_rating);
        vehiculo = findViewById(R.id.driver_vehicle);
    }

    public void bind(@Nullable Driver conductor) {
        if (conductor == null) {
            return;
        }

        nombre.setText(conductor.getNombreCompleto());
        iniciales.setText(conductor.getIniciales());
        valoracion.setRating(conductor.getRating());
        servicios.setText(getContext().getResources().getQuantityString(
                R.plurals.taxi_servicios_conductor,
                conductor.getNumServicios(), conductor.getNumServicios()));

        // La API del sistema de taxis no siempre trae fotografia: cuando no la
        // hay, el circulo con las iniciales ya esta puesto debajo.
        boolean hayFoto = conductor.getFotoUrl() != null && !conductor.getFotoUrl().isEmpty();
        foto.setVisibility(hayFoto ? VISIBLE : GONE);
        if (hayFoto) {
            Glide.with(this)
                    .load(conductor.getFotoUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_circulo_primario)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(foto);
        }

        vehiculo.bind(conductor.getVehiculo());
    }
}
