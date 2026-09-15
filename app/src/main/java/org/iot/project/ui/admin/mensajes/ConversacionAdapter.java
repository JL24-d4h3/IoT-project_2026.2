package org.iot.project.ui.admin.mensajes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemConversacionAdminBinding;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Message;
import org.iot.project.utils.DateFormatter;

import java.time.LocalDateTime;

/**
 * Bandeja de mensajes del hotel (§44, RF-064, RF-068).
 *
 * <p>Trabaja sobre el cruce que ya hizo el repositorio y no sobre
 * {@code Conversation}: el nombre del huésped y la habitación de la que habla no
 * están en la conversación, y resolverlos aquí obligaría al adaptador a conocer
 * los almacenes de usuarios y hoteles (reglas 33-35).
 */
public class ConversacionAdapter
        extends ListAdapter<ConversacionDeHotel, ConversacionAdapter.Fila> {

    /** Quien escucha abre la conversación que se tocó. */
    public interface AlAbrir {
        void onAbrir(@NonNull ConversacionDeHotel conversacion);
    }

    private static final DiffUtil.ItemCallback<ConversacionDeHotel> COMPARADOR =
            new DiffUtil.ItemCallback<ConversacionDeHotel>() {
                @Override
                public boolean areItemsTheSame(@NonNull ConversacionDeHotel anterior,
                                               @NonNull ConversacionDeHotel nueva) {
                    // Una conversación se identifica por su reserva: es lo que
                    // la hace única, y es lo que se le pasa a la pantalla del
                    // chat.
                    return anterior.getBookingId().equals(nueva.getBookingId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ConversacionDeHotel anterior,
                                                  @NonNull ConversacionDeHotel nueva) {
                    // De una conversación ya abierta solo cambian sus mensajes:
                    // se añaden por el final y lo leído solo pasa de falso a
                    // verdadero. Cuántos hay y cuántos faltan por leer cubre las
                    // dos cosas, y son valores copiados al cruzar la bandeja y
                    // no lecturas del objeto vivo: es lo que permite que dos
                    // cruces de la misma conversación se distingan.
                    return anterior.getNumMensajes() == nueva.getNumMensajes()
                            && anterior.getSinLeer() == nueva.getSinLeer();
                }
            };

    @NonNull
    private final AlAbrir oyente;

    public ConversacionAdapter(@NonNull AlAbrir oyente) {
        super(COMPARADOR);
        this.oyente = oyente;
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipoVista) {
        return new Fila(ItemConversacionAdminBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila titular, int posicion) {
        titular.bind(getItem(posicion), oyente);
    }

    /**
     * Una fila de la bandeja.
     *
     * <p>Publica porque este adaptador se usa tambien desde la portada del
     * administrador (§43), que enseña las conversaciones mas recientes en su
     * bloque de mensajes: son las mismas filas que la bandeja, y repetirlas alli
     * seria arriesgarse a que la portada y la bandeja describieran la misma
     * conversacion de dos maneras.
     */
    public static class Fila extends RecyclerView.ViewHolder {

        private final ItemConversacionAdminBinding binding;

        Fila(@NonNull ItemConversacionAdminBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ConversacionDeHotel conversacion, @NonNull AlAbrir oyente) {
            binding.conversacionNombre.setText(conversacion.getEstadia().getClienteNombre(
                    itemView.getContext().getString(R.string.reserva_huesped_no_disponible)));

            // "EST-2026-0420 · Hab. 302", el mismo formato que la fila de una
            // reserva: es la misma estadía descrita en los dos sitios.
            binding.conversacionEstadia.setText(itemView.getContext().getString(
                    R.string.admin_reserva_linea,
                    conversacion.getEstadia().getReserva().getCodigo(),
                    conversacion.getEstadia().getHabitacionNumero()));

            binding.conversacionPrevia.setText(previa(conversacion));

            Message ultimo = conversacion.getUltimoMensaje();
            binding.conversacionHora.setText(DateFormatter.relativo(
                    ultimo != null ? ultimo.getTimestamp() : null, LocalDateTime.now()));

            pintarSinLeer(conversacion.getSinLeer());

            binding.getRoot().setOnClickListener(v -> oyente.onAbrir(conversacion));
        }

        /**
         * Lo último que se dijo, con de quién es.
         *
         * <p>El "Tú:" no es un adorno: en una bandeja se hojea la columna entera
         * de previas, y sin él las palabras del propio hotel se leerían como si
         * las hubiera dicho el huésped, que es justo lo contrario de lo que hay
         * que contestar.
         */
        @NonNull
        private CharSequence previa(@NonNull ConversacionDeHotel conversacion) {
            Message ultimo = conversacion.getUltimoMensaje();
            if (ultimo == null) {
                return itemView.getContext().getString(R.string.admin_mensajes_sin_mensajes);
            }
            return ultimo.esDe(Message.Autor.HOTEL)
                    ? itemView.getContext().getString(
                            R.string.admin_mensajes_propio, ultimo.getTexto())
                    : ultimo.getTexto();
        }

        /**
         * Contador de mensajes sin leer, oculto cuando no hay ninguno.
         *
         * <p>Se anuncia con la frase entera y no con la cifra: quien usa un lector
         * de pantalla oiría un "3" suelto, sin saber de qué.
         */
        private void pintarSinLeer(int sinLeer) {
            boolean hay = sinLeer > 0;
            binding.conversacionSinLeer.setVisibility(hay ? View.VISIBLE : View.GONE);
            if (!hay) {
                return;
            }
            binding.conversacionSinLeer.setText(String.valueOf(sinLeer));
            binding.conversacionSinLeer.setContentDescription(
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.admin_mensajes_sin_leer, sinLeer, sinLeer));
        }
    }
}
