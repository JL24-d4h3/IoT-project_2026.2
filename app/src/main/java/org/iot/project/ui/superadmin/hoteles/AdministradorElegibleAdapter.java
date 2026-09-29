package org.iot.project.ui.superadmin.hoteles;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.databinding.ItemAdministradorElegibleBinding;
import org.iot.project.models.User;

/**
 * Cuentas que se pueden asignar a un hotel (RF-008).
 *
 * <p>La fila se queda con el {@link User} entero y no con su nombre suelto: la
 * hoja trabaja sobre el ViewModel del grafo, asi que no hay nada que
 * serializar en argumentos de navegacion.
 */
public class AdministradorElegibleAdapter
        extends ListAdapter<User, AdministradorElegibleAdapter.Fila> {

    /** Lo que la hoja hace cuando se elige un candidato. */
    public interface AlElegir {
        void elegir(@NonNull User candidato);
    }

    private final AlElegir alElegir;

    public AdministradorElegibleAdapter(@NonNull AlElegir alElegir) {
        super(DIFF);
        this.alElegir = alElegir;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemAdministradorElegibleBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        User candidato = getItem(posicion);
        fila.binding.saCandidatoNombre.setText(candidato.getNombreCompleto());
        fila.binding.getRoot().setOnClickListener(v -> alElegir.elegir(candidato));
    }

    static class Fila extends RecyclerView.ViewHolder {

        private final ItemAdministradorElegibleBinding binding;

        Fila(@NonNull ItemAdministradorElegibleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<User> DIFF =
            new DiffUtil.ItemCallback<User>() {
                @Override
                public boolean areItemsTheSame(@NonNull User a, @NonNull User b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull User a, @NonNull User b) {
                    return a.getNombreCompleto().equals(b.getNombreCompleto());
                }
            };
}
