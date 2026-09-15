package org.iot.project.ui.admin.clientes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemClienteAdminBinding;
import org.iot.project.models.ClienteDeHotel;
import org.iot.project.ui.admin.AdminFormato;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.PriceFormatter;

/**
 * Lista de clientes del hotel (§44).
 *
 * <p>Trabaja sobre el resumen que ya hizo el repositorio y no sobre
 * {@code Booking}: cuántas veces se alojó alguien y cuánto gastó no está en
 * ninguna reserva suelta, y contarlo aquí obligaría al adaptador a recorrer el
 * almacén de reservas entero (reglas 33-35).
 */
public class ClienteAdapter extends ListAdapter<ClienteDeHotel, ClienteAdapter.Fila> {

    private static final DiffUtil.ItemCallback<ClienteDeHotel> COMPARADOR =
            new DiffUtil.ItemCallback<ClienteDeHotel>() {
                @Override
                public boolean areItemsTheSame(@NonNull ClienteDeHotel anterior,
                                               @NonNull ClienteDeHotel nueva) {
                    return anterior.getCliente().getId().equals(nueva.getCliente().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ClienteDeHotel anterior,
                                                  @NonNull ClienteDeHotel nueva) {
                    // Lo que cambia de un cliente es su historial: una estancia
                    // más, o que ahora mismo esté alojado. El nombre y el
                    // contacto no se mueven solos.
                    return anterior.getNumEstancias() == nueva.getNumEstancias()
                            && anterior.getTotalGastado() == nueva.getTotalGastado()
                            && anterior.getUltimaEstancia().equals(nueva.getUltimaEstancia())
                            && anterior.estaEnCurso() == nueva.estaEnCurso();
                }
            };

    public ClienteAdapter() {
        super(COMPARADOR);
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemClienteAdminBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion));
    }

    /** Una fila de la lista. */
    static class Fila extends RecyclerView.ViewHolder {

        private final ItemClienteAdminBinding binding;

        Fila(@NonNull ItemClienteAdminBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ClienteDeHotel cliente) {
            binding.clienteNombre.setText(cliente.getCliente().getNombreCompleto());
            binding.clienteContacto.setText(contacto(cliente));
            binding.clienteEstado.setVisibility(cliente.estaEnCurso() ? View.VISIBLE : View.GONE);

            // "3 estadías · última el 20 set". La fecha es la de entrada, no la
            // de salida: es la que el administrador recuerda y la que buscaría
            // en el historial de reservas.
            binding.clienteEstancias.setText(itemView.getContext().getString(
                    R.string.admin_clientes_ultima,
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.admin_clientes_estancias,
                            cliente.getNumEstancias(), cliente.getNumEstancias()),
                    DateFormatter.fechaCorta(cliente.getUltimaEstancia())));

            binding.clienteTotal.setText(PriceFormatter.format(cliente.getTotalGastado()));
        }

        /**
         * "DNI 40918273 · lucia.quispe@correo.com", con lo que haya.
         *
         * <p>Los dos datos son opcionales en el modelo, y ninguno de los dos
         * basta por sí solo para identificar a alguien en recepción: el
         * documento distingue a dos personas con el mismo nombre, y el correo es
         * por donde se le escribe. Se enseña el que haya antes que dejar la
         * línea en blanco, que se leería como un fallo de carga.
         */
        @NonNull
        private CharSequence contacto(@NonNull ClienteDeHotel cliente) {
            CharSequence documento = AdminFormato.documento(cliente.getCliente());
            String correo = cliente.getCliente().getEmail();
            boolean hayCorreo = correo != null && !correo.trim().isEmpty();

            if (documento == null) {
                return hayCorreo
                        ? correo
                        : itemView.getContext().getString(R.string.admin_clientes_sin_contacto);
            }
            return hayCorreo
                    ? itemView.getContext().getString(
                            R.string.admin_clientes_contacto, documento, correo)
                    : documento;
        }
    }
}
