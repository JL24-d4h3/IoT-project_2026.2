package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.iot.project.R;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;
import org.iot.project.utils.PriceFormatter;

/**
 * Chip de servicio.
 *
 * <p>Un servicio puede estar incluido o cobrarse aparte, y esa diferencia
 * tiene que verse de un vistazo: el incluido va en el color de la marca y sin
 * precio; el adicional va atenuado y con su importe. El precio vive en la
 * relación hotel-servicio, no en el servicio (§18, regla 20), así que el chip
 * se construye siempre a partir de un {@link HotelService}.
 */
public class ServiceChipView extends LinearLayout {

    private final ImageView icono;
    private final TextView texto;
    private final TextView precio;

    public ServiceChipView(@NonNull Context context) {
        this(context, null);
    }

    public ServiceChipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ServiceChipView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setBackground(ContextCompat.getDrawable(context, R.drawable.bg_chip_neutral));

        int h = getResources().getDimensionPixelSize(R.dimen.space_sm);
        int v = getResources().getDimensionPixelSize(R.dimen.space_xs);
        setPadding(h, v, h, v);

        LayoutInflater.from(context).inflate(R.layout.view_service_chip, this, true);

        icono = findViewById(R.id.chip_icon);
        texto = findViewById(R.id.chip_text);
        precio = findViewById(R.id.chip_price);
    }

    /**
     * @param servicio    entrada del catálogo global (§13, §17)
     * @param hotelServicio la relación con este hotel, que aporta el precio
     */
    public void bind(@NonNull Service servicio, @NonNull HotelService hotelServicio) {
        icono.setImageResource(servicio.getIconRes());
        texto.setText(servicio.getName());

        boolean incluido = hotelServicio.isIncluded();
        int colorIcono = incluido ? R.color.colorPrimary : R.color.colorTextSecondary;
        int colorTexto = incluido ? R.color.colorTextPrimary : R.color.colorTextSecondary;
        pintar(colorIcono, colorTexto);

        if (incluido) {
            precio.setVisibility(GONE);
        } else {
            precio.setText(getContext().getString(R.string.servicio_precio_extra,
                    PriceFormatter.format(hotelServicio.getPrice())));
            precio.setVisibility(VISIBLE);
        }
    }

    /** Variante suelta, para listas donde todavía no hay hotel asociado. */
    public void bind(@NonNull Service servicio) {
        icono.setImageResource(servicio.getIconRes());
        texto.setText(servicio.getName());
        precio.setVisibility(GONE);
        pintar(R.color.colorTextPrimary, R.color.colorTextPrimary);
    }

    private void pintar(@ColorRes int colorIcono, @ColorRes int colorTexto) {
        icono.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), colorIcono)));
        texto.setTextColor(ContextCompat.getColor(getContext(), colorTexto));
    }
}
