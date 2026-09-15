package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.iot.project.R;
import org.iot.project.models.Vehicle;

/**
 * Vehiculo del conductor (§37, §58).
 *
 * <p>Lo que el cliente necesita para reconocer el auto cuando llegue son dos
 * cosas: como es y su placa. Por eso la placa va sola en su linea y con
 * espaciado entre letras: se lee de un vistazo desde la vereda, que es
 * exactamente cuando hace falta.
 *
 * <p>Es un componente suelto y no un trozo de la tarjeta del conductor porque
 * el panel del conductor (§46) enseña su propio vehiculo sin conductor
 * alrededor.
 */
public class VehicleCardView extends LinearLayout {

    private final ImageView foto;
    private final TextView descripcion;
    private final TextView placa;

    public VehicleCardView(@NonNull Context context) {
        this(context, null);
    }

    public VehicleCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VehicleCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        LayoutInflater.from(context).inflate(R.layout.view_vehicle_card, this, true);

        foto = findViewById(R.id.vehicle_photo);
        descripcion = findViewById(R.id.vehicle_description);
        placa = findViewById(R.id.vehicle_plate);
    }

    public void bind(@Nullable Vehicle vehiculo) {
        if (vehiculo == null) {
            // Un conductor sin vehiculo registrado no deberia poder prestar
            // servicios, pero la pantalla no puede quedarse con los datos del
            // conductor anterior por no contemplarlo.
            descripcion.setText(R.string.vehiculo_sin_datos);
            placa.setVisibility(GONE);
            foto.setVisibility(GONE);
            return;
        }

        descripcion.setText(vehiculo.getDescripcion());
        placa.setText(vehiculo.getPlaca());
        placa.setVisibility(VISIBLE);

        boolean hayFoto = vehiculo.getFotoUrl() != null && !vehiculo.getFotoUrl().isEmpty();
        foto.setVisibility(hayFoto ? VISIBLE : GONE);
        if (hayFoto) {
            Glide.with(this)
                    .load(vehiculo.getFotoUrl())
                    .placeholder(R.drawable.bg_skeleton)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(foto);
        }
    }
}
