package org.iot.project.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;

import org.iot.project.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Galería de fotografías de un hotel o de una habitación.
 *
 * <p>Usa un {@link ViewPager2} con sus puntos indicadores. Se eligió paginador
 * y no una tira desplazable porque las fotografías son el argumento principal
 * de la ficha (RF-013): conviene verlas grandes y de una en una.
 *
 * <p>Con una sola fotografía los puntos desaparecen: un indicador de una sola
 * posición no informa de nada.
 */
public class HotelImageGalleryView extends LinearLayout {

    private final ViewPager2 paginador;
    private final LinearLayout indicadores;
    private final TextView contador;

    private final List<String> fotos = new ArrayList<>();

    public HotelImageGalleryView(@NonNull Context context) {
        this(context, null);
    }

    public HotelImageGalleryView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HotelImageGalleryView(@NonNull Context context, @Nullable AttributeSet attrs,
                                 int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_image_gallery, this, true);

        paginador = findViewById(R.id.gallery_pager);
        indicadores = findViewById(R.id.gallery_dots);
        contador = findViewById(R.id.gallery_counter);

        paginador.setAdapter(new GaleriaAdapter());
        paginador.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int posicion) {
                actualizarIndicadores(posicion);
            }
        });
    }

    /** Carga las fotografías. Con la lista vacía se muestra un marcador. */
    public void setFotos(@NonNull List<String> urls) {
        fotos.clear();
        fotos.addAll(urls);
        paginador.getAdapter().notifyDataSetChanged();
        paginador.setCurrentItem(0, false);
        construirIndicadores();
        actualizarIndicadores(0);
    }

    private void construirIndicadores() {
        indicadores.removeAllViews();

        boolean hayVarias = fotos.size() > 1;
        indicadores.setVisibility(hayVarias ? VISIBLE : GONE);
        contador.setVisibility(hayVarias ? VISIBLE : GONE);
        if (!hayVarias) {
            return;
        }

        int lado = getResources().getDimensionPixelSize(R.dimen.gallery_dot);
        int separacion = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < fotos.size(); i++) {
            View punto = new View(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(lado, lado);
            params.setMarginStart(separacion);
            params.setMarginEnd(separacion);
            punto.setLayoutParams(params);
            punto.setBackgroundResource(R.drawable.bg_gallery_dot);
            indicadores.addView(punto);
        }
    }

    private void actualizarIndicadores(int posicion) {
        if (fotos.size() <= 1) {
            return;
        }
        for (int i = 0; i < indicadores.getChildCount(); i++) {
            indicadores.getChildAt(i).setSelected(i == posicion);
        }
        contador.setText(getContext().getString(R.string.galeria_contador,
                posicion + 1, fotos.size()));
    }

    /** Adaptador interno: las páginas son ImageView a pantalla del paginador. */
    private class GaleriaAdapter extends RecyclerView.Adapter<GaleriaAdapter.PaginaViewHolder> {

        @NonNull
        @Override
        public PaginaViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
            ImageView imagen = new ImageView(padre.getContext());
            imagen.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imagen.setContentDescription(getContext().getString(R.string.cd_imagen_hotel));
            return new PaginaViewHolder(imagen);
        }

        @Override
        public void onBindViewHolder(@NonNull PaginaViewHolder holder, int posicion) {
            Glide.with(holder.imagen)
                    .load(fotos.get(posicion))
                    .placeholder(R.drawable.bg_skeleton)
                    .error(R.drawable.ic_search_off)
                    .centerCrop()
                    .into(holder.imagen);
        }

        @Override
        public int getItemCount() {
            return fotos.size();
        }

        class PaginaViewHolder extends RecyclerView.ViewHolder {
            final ImageView imagen;

            PaginaViewHolder(@NonNull ImageView imagen) {
                super(imagen);
                this.imagen = imagen;
            }
        }
    }
}
