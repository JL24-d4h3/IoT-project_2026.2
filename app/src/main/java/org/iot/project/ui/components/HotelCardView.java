package org.iot.project.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.card.MaterialCardView;

import org.iot.project.R;
import org.iot.project.models.Hotel;
import org.iot.project.models.HotelService;
import org.iot.project.models.Service;
import org.iot.project.utils.PriceFormatter;

/**
 * Tarjeta de hotel.
 *
 * <p>Es el componente que demuestra la reutilización de §58: la misma clase se
 * usa sin cambios en Inicio, en Buscar, en Recomendados y en Favoritos. Lo que
 * cambia entre una pantalla y otra son solo sus opciones —si enseña el precio,
 * si enseña los servicios, si el corazón está activo—, nunca su estructura.
 *
 * <p>Por eso no hay una tarjeta "de inicio" y otra "de búsqueda": si el diseño
 * de la tarjeta cambia, cambia en un solo sitio.
 */
public class HotelCardView extends MaterialCardView {

    private static final int MAX_SERVICIOS_VISIBLES = 4;

    private final ImageView imagen;
    private final ImageButton favorito;
    private final TextView disponibilidad;
    private final TextView nombre;
    private final TextView ubicacion;
    private final TextView precio;
    private final LinearLayout filaServicios;
    private final RatingBadgeView calificacion;

    private Hotel hotel;
    private boolean esFavorito;
    private OnFavoriteClickListener oyenteFavorito;

    /** Se avisa con el estado ya cambiado, para que quien escuche solo guarde. */
    public interface OnFavoriteClickListener {
        void onFavoritoCambiado(@NonNull Hotel hotel, boolean esFavorito);
    }

    public HotelCardView(@NonNull Context context) {
        this(context, null);
    }

    public HotelCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HotelCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.view_hotel_card, this, true);

        imagen = findViewById(R.id.hotel_image);
        favorito = findViewById(R.id.hotel_favorite);
        disponibilidad = findViewById(R.id.hotel_availability);
        nombre = findViewById(R.id.hotel_name);
        ubicacion = findViewById(R.id.hotel_location);
        precio = findViewById(R.id.hotel_price);
        filaServicios = findViewById(R.id.hotel_services);
        calificacion = findViewById(R.id.hotel_rating);

        favorito.setOnClickListener(v -> alternarFavorito());
    }

    /**
     * Vuelca un hotel en la tarjeta.
     *
     * <p>Se encarga de que no queden datos del hotel anterior: al reciclarse
     * una tarjeta en un RecyclerView, olvidar un campo hace que aparezca el
     * precio de un hotel sobre el nombre de otro.
     */
    public void bind(@NonNull Hotel hotel) {
        this.hotel = hotel;

        nombre.setText(hotel.getNombre());
        ubicacion.setText(hotel.getUbicacionCorta());
        calificacion.setRating(hotel.getRating());
        precio.setText(getContext().getString(R.string.hotel_precio_desde,
                PriceFormatter.format(hotel.getPrecioDesde())));

        mostrarServicios(hotel);

        // El distintivo de disponibilidad lo enciende quien usa la tarjeta,
        // porque solo tiene sentido cuando la búsqueda llevó fechas.
        disponibilidad.setVisibility(GONE);

        Glide.with(this)
                .load(hotel.getFotos().isEmpty() ? null : hotel.getFotos().get(0))
                .placeholder(R.drawable.bg_skeleton)
                .error(R.drawable.ic_search_off)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imagen);

        aplicarEstadoFavorito();
    }

    /**
     * Dibuja hasta cuatro iconos de servicio. Un hotel sin servicios no
     * muestra la fila en absoluto (es el caso de uno de los hoteles de prueba,
     * así que este camino se recorre de verdad).
     */
    private void mostrarServicios(@NonNull Hotel hotel) {
        filaServicios.removeAllViews();

        if (hotel.getServicios().isEmpty()) {
            filaServicios.setVisibility(GONE);
            return;
        }

        int mostrados = 0;
        for (HotelService hs : hotel.getServicios()) {
            if (mostrados >= MAX_SERVICIOS_VISIBLES) {
                break;
            }
            Service servicio = buscarServicio(hs.getServiceId());
            if (servicio == null) {
                continue;
            }
            ImageView icono = new ImageView(getContext());
            int lado = getResources().getDimensionPixelSize(R.dimen.icon_sm);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(lado, lado);
            params.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.space_sm));
            icono.setLayoutParams(params);
            icono.setImageResource(servicio.getIconRes());
            icono.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(getContext(),
                            hs.isIncluded() ? R.color.colorPrimary : R.color.colorTextDisabled)));
            icono.setContentDescription(servicio.getName());
            filaServicios.addView(icono);
            mostrados++;
        }

        // Si ningún servicio del hotel estaba en el catálogo, es mejor no
        // enseñar una fila vacía.
        filaServicios.setVisibility(mostrados > 0 ? VISIBLE : GONE);
    }

    /**
     * El catálogo global se pide al repositorio a través del contrato de
     * servicio, no se copia aquí: el administrador solo puede elegir servicios
     * del catálogo (§13, §17).
     */
    @Nullable
    private Service buscarServicio(String serviceId) {
        return org.iot.project.core.ServiceLocator.hoteles().servicio(serviceId);
    }

    /** Muestra u oculta el distintivo verde de disponibilidad. */
    public void setDisponible(boolean disponible) {
        disponibilidad.setVisibility(disponible ? VISIBLE : GONE);
    }

    /** Permite ocultar el precio en contextos donde no aplica, como Favoritos. */
    public void setMostrarPrecio(boolean mostrar) {
        precio.setVisibility(mostrar ? VISIBLE : GONE);
    }

    public void setEsFavorito(boolean esFavorito) {
        this.esFavorito = esFavorito;
        aplicarEstadoFavorito();
    }

    public boolean isEsFavorito() {
        return esFavorito;
    }

    public void setOnFavoriteClickListener(@Nullable OnFavoriteClickListener oyente) {
        this.oyenteFavorito = oyente;
    }

    @Nullable
    public Hotel getHotel() {
        return hotel;
    }

    private void alternarFavorito() {
        esFavorito = !esFavorito;
        aplicarEstadoFavorito();
        if (oyenteFavorito != null && hotel != null) {
            oyenteFavorito.onFavoritoCambiado(hotel, esFavorito);
        }
    }

    /**
     * El corazón lleno se pinta en ámbar solo cuando está activo. El color de
     * relleno lo da el propio vector, así que basta con alternar el tinte.
     */
    private void aplicarEstadoFavorito() {
        favorito.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(getContext(),
                        esFavorito ? R.color.colorAccent : R.color.colorSurface)));
        favorito.setContentDescription(getContext().getString(
                esFavorito ? R.string.cd_quitar_favorito : R.string.cd_favorito));
    }

    /** Acceso a la imagen para casos que necesiten ajustar la carga. */
    @NonNull
    public ImageView getImagen() {
        return imagen;
    }

    @NonNull
    public View getContenido() {
        return this;
    }
}
