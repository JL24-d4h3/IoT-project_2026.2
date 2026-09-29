package org.iot.project.ui.superadmin.conductores;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemConductorSaBinding;
import org.iot.project.models.Driver;
import org.iot.project.models.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Filas de la lista de conductores (RF-077).
 *
 * <p>Misma fila que la de cuentas, con el vehiculo en lugar del correo: son dos
 * listas del mismo panel y se leen igual a proposito.
 *
 * <p>Un conductor que nunca estuvo habilitado y uno al que se le retiro la
 * habilitacion se ven igual —"Pendiente"— porque desde la plataforma son el
 * mismo estado: ninguno de los dos puede recibir solicitudes. Distinguirlos
 * pediria un campo nuevo en {@link Driver} y ningun requisito lo pide.
 *
 * <p><b>La lista guarda filas resueltas y no conductores.</b> El por que esta en
 * {@link Conductor}.
 */
public class ConductorSaAdapter
        extends ListAdapter<ConductorSaAdapter.Conductor, ConductorSaAdapter.Fila> {

    /**
     * Lo que la pantalla hace cuando se pulsa el boton de un conductor.
     *
     * <p>Van el identificador y el nombre, y no el conductor entero, porque la
     * fila no tiene el conductor: el nombre es lo que necesita el aviso, y el
     * identificador es lo unico que hace falta para encontrar al conductor de
     * verdad.
     */
    public interface AlAccionar {
        void accionar(@NonNull String conductorId, @NonNull String nombre, boolean habilitado);
    }

    /**
     * Una fila: lo que se pinta, copiado al publicar la lista.
     *
     * <p>No es un {@link Driver} porque el comparador de la lista no puede mirar
     * al modelo. Los modelos son mutables y el repositorio entrega sus propias
     * instancias, asi que al habilitar a un conductor la lista anterior y la
     * nueva apuntan al mismo objeto: comparar sus campos seria comparar al
     * objeto consigo mismo, saldria "iguales" y la fila se quedaria en
     * "Pendiente" con el aviso de que ya puede recibir solicitudes.
     *
     * <p>{@code vehiculo} es nulo cuando el conductor no tiene ninguno; la fila
     * decide con eso que frase enseñar, porque el texto vive en los recursos y
     * el adaptador no es quien para elegirlo al copiar.
     */
    static final class Conductor {

        private final String id;
        private final String nombre;
        private final CharSequence contacto;
        @Nullable
        private final CharSequence vehiculo;
        private final boolean habilitado;

        Conductor(@NonNull Driver conductor) {
            this.id = conductor.getId();
            this.nombre = conductor.getNombreCompleto();
            this.contacto = concatenar(conductor.getEmail(), conductor.getTelefono());
            Vehicle vehiculo = conductor.getVehiculo();
            this.vehiculo = vehiculo == null
                    ? null : concatenar(vehiculo.getDescripcion(), vehiculo.getPlaca());
            this.habilitado = conductor.isHabilitado();
        }
    }

    private final AlAccionar alAccionar;

    public ConductorSaAdapter(@NonNull AlAccionar alAccionar) {
        super(DIFF);
        this.alAccionar = alAccionar;
    }

    /**
     * Publica una lista de conductores.
     *
     * <p>La conversion a filas se hace aqui y no en la pantalla: la pantalla
     * sigue hablando de {@link Driver}, que es lo que le entrega el ViewModel, y
     * quien sabe que hace falta para pintar una fila es la fila.
     */
    public void mostrar(@NonNull List<Driver> conductores) {
        List<Conductor> filas = new ArrayList<>(conductores.size());
        for (Driver conductor : conductores) {
            filas.add(new Conductor(conductor));
        }
        submitList(filas);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        return new Fila(ItemConductorSaBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.bind(getItem(posicion));
    }

    class Fila extends RecyclerView.ViewHolder {

        private final ItemConductorSaBinding binding;

        Fila(@NonNull ItemConductorSaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Conductor conductor) {
            binding.saConductorNombre.setText(conductor.nombre);
            binding.saConductorContacto.setText(conductor.contacto);
            binding.saConductorVehiculo.setText(conductor.vehiculo != null
                    ? conductor.vehiculo
                    : itemView.getContext().getString(R.string.sa_conductor_sin_vehiculo));

            binding.saConductorInsigniaEstado.setText(conductor.habilitado
                    ? R.string.sa_conductor_habilitado : R.string.sa_conductor_pendiente);
            pintarEstado(conductor.habilitado);

            binding.saConductorAccion.setText(conductor.habilitado
                    ? R.string.sa_retirar_habilitacion : R.string.sa_habilitar);
            binding.saConductorAccion.setOnClickListener(v ->
                    alAccionar.accionar(conductor.id, conductor.nombre, !conductor.habilitado));
        }

        private void pintarEstado(boolean habilitado) {
            fondo(habilitado ? R.drawable.bg_badge_success : R.drawable.bg_badge_warning);
            binding.saConductorInsigniaEstado.setTextColor(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), habilitado
                            ? R.color.colorOnSuccessContainer : R.color.colorOnWarningContainer)));
        }

        private void fondo(@DrawableRes int fondo) {
            binding.saConductorInsigniaEstado.setBackground(
                    ContextCompat.getDrawable(itemView.getContext(), fondo));
        }
    }

    /**
     * Une dos datos con el punto medio, saltandose el que falte.
     *
     * <p>Un "·" suelto o un "null" en medio de la fila se lee como un fallo de
     * la aplicacion, y basta con no escribirlo.
     */
    @NonNull
    static CharSequence concatenar(@Nullable String primero, @Nullable String segundo) {
        boolean hayPrimero = primero != null && !primero.isEmpty();
        boolean haySegundo = segundo != null && !segundo.isEmpty();
        if (hayPrimero && haySegundo) {
            return primero + " · " + segundo;
        }
        return hayPrimero ? primero : (haySegundo ? segundo : "");
    }

    private static final DiffUtil.ItemCallback<Conductor> DIFF =
            new DiffUtil.ItemCallback<Conductor>() {
                @Override
                public boolean areItemsTheSame(@NonNull Conductor a, @NonNull Conductor b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Conductor a, @NonNull Conductor b) {
                    return a.habilitado == b.habilitado
                            && a.nombre.equals(b.nombre)
                            && mismoTexto(a.contacto, b.contacto)
                            && mismoTexto(a.vehiculo, b.vehiculo);
                }
            };

    private static boolean mismoTexto(@Nullable CharSequence a, @Nullable CharSequence b) {
        return a == null ? b == null : a.toString().contentEquals(b);
    }
}
