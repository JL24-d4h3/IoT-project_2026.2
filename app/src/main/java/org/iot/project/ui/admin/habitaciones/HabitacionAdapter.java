package org.iot.project.ui.admin.habitaciones;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemHabitacionAdminBinding;
import org.iot.project.models.Room;
import org.iot.project.utils.PriceFormatter;

/**
 * Lista de habitaciones del hotel (RF-014 a RF-018).
 *
 * <p>El interruptor de disponibilidad notifica hacia fuera y no cambia el
 * modelo: quien decide si el cambio se acepta es el repositorio, y si falla la
 * fila tiene que volver a como estaba. Dejar que el interruptor se quedara
 * marcado y que el modelo dijera otra cosa sería una pantalla mintiendo.
 */
public class HabitacionAdapter extends ListAdapter<Room, HabitacionAdapter.Fila> {

    /** Lo que la pantalla hace cuando el administrador mueve un interruptor. */
    public interface AlCambiarDisponibilidad {
        void onCambiar(@NonNull Room habitacion, boolean disponible);
    }

    /** Lo que la pantalla hace cuando se toca el lápiz de una fila. */
    public interface AlEditar {
        void onEditar(@NonNull Room habitacion);
    }

    @Nullable
    private AlCambiarDisponibilidad alCambiar;

    @Nullable
    private AlEditar alEditar;

    public HabitacionAdapter() {
        super(DIFF);
    }

    public void setAlCambiarDisponibilidad(@Nullable AlCambiarDisponibilidad oyente) {
        this.alCambiar = oyente;
    }

    public void setAlEditar(@Nullable AlEditar oyente) {
        this.alEditar = oyente;
    }

    /**
     * Dos habitaciones son la misma por su identificador, y cambian cuando
     * cambia algo de lo que la fila enseña.
     *
     * <p>La disponibilidad entra en el contenido y no solo en la identidad: es
     * justo lo que el administrador acaba de tocar, y sin ella la fila no se
     * repintaría.
     */
    private static final DiffUtil.ItemCallback<Room> DIFF =
            new DiffUtil.ItemCallback<Room>() {
                @Override
                public boolean areItemsTheSame(@NonNull Room anterior, @NonNull Room nueva) {
                    return anterior.getId().equals(nueva.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Room anterior, @NonNull Room nueva) {
                    return anterior.isDisponible() == nueva.isDisponible()
                            && anterior.getPrecioNoche() == nueva.getPrecioNoche()
                            && anterior.getCapacidadAdultos() == nueva.getCapacidadAdultos()
                            && anterior.getCapacidadNinos() == nueva.getCapacidadNinos()
                            && anterior.getAreaM2() == nueva.getAreaM2()
                            && anterior.getPiso() == nueva.getPiso()
                            && texto(anterior.getTipo()).equals(texto(nueva.getTipo()))
                            && texto(anterior.getNumero()).equals(texto(nueva.getNumero()));
                }

                @NonNull
                private static String texto(@Nullable String valor) {
                    return valor != null ? valor : "";
                }
            };

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemHabitacionAdminBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion));
    }

    /** Una fila de la lista. */
    class Fila extends RecyclerView.ViewHolder {

        private final ItemHabitacionAdminBinding binding;

        Fila(@NonNull ItemHabitacionAdminBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Room habitacion) {
            Context contexto = itemView.getContext();
            Resources recursos = contexto.getResources();

            binding.habitacionNumero.setText(habitacion.getNumero());
            binding.habitacionTipo.setText(habitacion.getTipo());
            binding.habitacionPrecio.setText(PriceFormatter.format(habitacion.getPrecioNoche()));
            binding.habitacionCapacidad.setText(describir(recursos, habitacion));

            // El aviso solo aparece en la habitación retirada: es el estado
            // excepcional, y el que hay que encontrar de un vistazo en una
            // lista larga. Las que están a la venta no llevan nada porque son
            // la mayoría y no hay nada que avisar.
            binding.habitacionRetirada.setVisibility(
                    habitacion.isDisponible() ? View.GONE : View.VISIBLE);

            // Se suelta el oyente antes de mover el interruptor: si no, rellenar
            // el estado de la fila se confundiría con el administrador
            // moviéndolo, y el reciclado de vistas dispararía cambios solos.
            binding.habitacionDisponible.setOnCheckedChangeListener(null);
            binding.habitacionDisponible.setChecked(habitacion.isDisponible());
            binding.habitacionDisponible.setOnCheckedChangeListener((boton, marcado) -> {
                if (alCambiar != null) {
                    alCambiar.onCambiar(habitacion, marcado);
                }
            });

            binding.habitacionEditar.setOnClickListener(v -> {
                if (alEditar != null) {
                    alEditar.onEditar(habitacion);
                }
            });
        }

        /**
         * Capacidad, superficie y piso en una línea.
         *
         * <p>Se omiten los datos que falten en vez de enseñar "0 m²": un dato
         * ausente no debe parecer un dato malo.
         */
        @NonNull
        private String describir(@NonNull Resources recursos, @NonNull Room habitacion) {
            StringBuilder texto = new StringBuilder(habitacion.getResumenCapacidad());
            if (habitacion.getAreaM2() > 0) {
                texto.append(" · ").append((int) habitacion.getAreaM2()).append(" m²");
            }
            if (habitacion.getPiso() > 0) {
                texto.append(" · ").append(
                        recursos.getString(R.string.habitacion_piso, habitacion.getPiso()));
            }
            return texto.toString();
        }
    }
}
