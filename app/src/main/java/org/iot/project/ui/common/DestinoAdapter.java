package org.iot.project.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;

/**
 * Lista de destinos de la pantalla de destino (§14).
 *
 * <p>Es una lista con dos formas de fila —encabezado y destino— porque las dos
 * secciones que muestra la pantalla comparten el mismo desplazamiento. Usa
 * {@link ListAdapter} para que al escribir en el buscador la lista se filtre
 * sin parpadeo: las filas que siguen estando se conservan en lugar de
 * redibujarse enteras en cada tecla.
 */
public class DestinoAdapter extends ListAdapter<DestinoItem, RecyclerView.ViewHolder> {

    /** Qué hace la pantalla cuando el usuario elige un destino. */
    public interface OnDestinoClickListener {
        void onDestinoElegido(@NonNull String destino);
    }

    /** Qué hace la pantalla cuando el usuario descarta una búsqueda reciente. */
    public interface OnRecienteQuitarListener {
        void onRecienteQuitado(@NonNull String destino);
    }

    private static final int TIPO_ENCABEZADO = 0;
    private static final int TIPO_DESTINO = 1;

    private final OnDestinoClickListener oyenteSeleccion;
    private final OnRecienteQuitarListener oyenteQuitar;

    private static final DiffUtil.ItemCallback<DestinoItem> COMPARADOR =
            new DiffUtil.ItemCallback<DestinoItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull DestinoItem anterior,
                                               @NonNull DestinoItem nuevo) {
                    // Un encabezado se identifica por su recurso de título y un
                    // destino por su texto: dos "Lima" no pueden coexistir, y
                    // dos encabezados distintos tampoco.
                    return anterior.getTipo() == nuevo.getTipo()
                            && anterior.getTituloRes() == nuevo.getTituloRes()
                            && anterior.getTexto().equals(nuevo.getTexto());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DestinoItem anterior,
                                                  @NonNull DestinoItem nuevo) {
                    return anterior.getIcono() == nuevo.getIcono()
                            && anterior.isEliminable() == nuevo.isEliminable();
                }
            };

    public DestinoAdapter(@NonNull OnDestinoClickListener oyenteSeleccion,
                          @NonNull OnRecienteQuitarListener oyenteQuitar) {
        super(COMPARADOR);
        this.oyenteSeleccion = oyenteSeleccion;
        this.oyenteQuitar = oyenteQuitar;
    }

    @Override
    public int getItemViewType(int posicion) {
        return getItem(posicion).esEncabezado() ? TIPO_ENCABEZADO : TIPO_DESTINO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        LayoutInflater inflador = LayoutInflater.from(padre.getContext());
        if (viewType == TIPO_ENCABEZADO) {
            return new EncabezadoViewHolder((TextView) inflador.inflate(
                    R.layout.item_destino_encabezado, padre, false));
        }
        return new DestinoViewHolder(inflador.inflate(R.layout.item_destino, padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int posicion) {
        DestinoItem item = getItem(posicion);
        if (holder instanceof EncabezadoViewHolder) {
            ((EncabezadoViewHolder) holder).bind(item);
        } else if (holder instanceof DestinoViewHolder) {
            ((DestinoViewHolder) holder).bind(item);
        }
    }

    static class EncabezadoViewHolder extends RecyclerView.ViewHolder {

        private final TextView titulo;

        EncabezadoViewHolder(@NonNull TextView titulo) {
            super(titulo);
            this.titulo = titulo;
        }

        void bind(@NonNull DestinoItem item) {
            titulo.setText(item.getTituloRes());
        }
    }

    class DestinoViewHolder extends RecyclerView.ViewHolder {

        private final ImageView icono;
        private final TextView texto;
        private final ImageButton quitar;

        DestinoViewHolder(@NonNull View raiz) {
            super(raiz);
            icono = raiz.findViewById(R.id.destino_icono);
            texto = raiz.findViewById(R.id.destino_texto);
            quitar = raiz.findViewById(R.id.destino_quitar);

            raiz.setOnClickListener(v -> {
                int posicion = getBindingAdapterPosition();
                if (posicion != RecyclerView.NO_POSITION) {
                    oyenteSeleccion.onDestinoElegido(getItem(posicion).getTexto());
                }
            });
        }

        void bind(@NonNull DestinoItem item) {
            texto.setText(item.getTexto());
            icono.setImageResource(item.getIcono());

            // El aspa solo existe donde hay algo que descartar: un destino
            // sugerido no se borra, porque no lo puso el usuario.
            quitar.setVisibility(item.isEliminable() ? View.VISIBLE : View.GONE);
            if (item.isEliminable()) {
                quitar.setOnClickListener(v -> oyenteQuitar.onRecienteQuitado(item.getTexto()));
            } else {
                quitar.setOnClickListener(null);
            }
        }
    }
}
