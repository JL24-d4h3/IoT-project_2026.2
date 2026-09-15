package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.models.Room;
import org.iot.project.utils.PriceFormatter;

/**
 * Tarjeta de habitación.
 *
 * <p>Muestra lo que decide la elección: tipo, capacidad, superficie, piso y
 * precio por noche. El precio por noche —y no el total— es lo que se compara
 * entre habitaciones; el total con noches se ve en el resumen de la reserva.
 */
public class RoomCardView extends MaterialCardView {

    private final ImageView imagen;
    private final TextView nombre;
    private final TextView capacidad;
    private final TextView precio;
    private final MaterialButton accion;

    public RoomCardView(@NonNull Context context) {
        this(context, null);
    }

    public RoomCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoomCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_room_card, this, true);

        imagen = findViewById(R.id.room_image);
        nombre = findViewById(R.id.room_name);
        capacidad = findViewById(R.id.room_capacity);
        precio = findViewById(R.id.room_price);
        accion = findViewById(R.id.room_action);
    }

    public void bind(@NonNull Room room) {
        nombre.setText(room.getTipo());
        precio.setText(PriceFormatter.format(room.getPrecioNoche()));
        capacidad.setText(describirCapacidad(room));

        Glide.with(this)
                .load(room.getFotos().isEmpty() ? null : room.getFotos().get(0))
                .placeholder(R.drawable.bg_skeleton)
                .error(R.drawable.ic_search_off)
                .centerCrop()
                .into(imagen);
    }

    /**
     * Junta capacidad, superficie y piso en una sola línea.
     *
     * <p>Se omiten los datos que el modelo no tenga, en vez de enseñar "0 m²":
     * un dato ausente no debe parecer un dato malo.
     */
    private String describirCapacidad(@NonNull Room room) {
        StringBuilder texto = new StringBuilder(room.getResumenCapacidad());
        if (room.getAreaM2() > 0) {
            texto.append(" · ").append((int) room.getAreaM2()).append(" m²");
        }
        if (room.getPiso() > 0) {
            texto.append(" · ").append(getContext().getString(R.string.habitacion_piso,
                    room.getPiso()));
        }
        return texto.toString();
    }

    public void setOnActionClickListener(@Nullable OnClickListener oyente) {
        accion.setOnClickListener(oyente);
    }

    public void setTextoAccion(@NonNull CharSequence texto) {
        accion.setText(texto);
    }

    public void setAccionHabilitada(boolean habilitada) {
        accion.setEnabled(habilitada);
    }
}
