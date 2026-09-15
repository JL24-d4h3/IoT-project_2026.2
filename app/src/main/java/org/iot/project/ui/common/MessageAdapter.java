package org.iot.project.ui.common;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.iot.project.R;
import org.iot.project.databinding.ItemMessageBinding;
import org.iot.project.models.Message;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Conversacion cliente-hotel (§44, RF-066).
 *
 * <p>Quien escribio cada mensaje se distingue por tres cosas a la vez —lado,
 * color y forma de la burbuja— y no solo por el color: el color por si solo
 * deja fuera a quien no lo percibe, y el lado por si solo se pierde cuando el
 * mensaje es tan largo que ocupa el ancho entero.
 */
public class MessageAdapter extends ListAdapter<Message, MessageAdapter.MessageViewHolder> {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private static final DiffUtil.ItemCallback<Message> COMPARADOR =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(@NonNull Message anterior, @NonNull Message nuevo) {
                    return anterior.getId().equals(nuevo.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Message anterior,
                                                  @NonNull Message nuevo) {
                    return anterior.isLeido() == nuevo.isLeido();
                }
            };

    /**
     * Lado que esta viendo la conversacion.
     *
     * <p>Es lo que decide de que lado cae cada burbuja, y por eso se recibe al
     * construir en vez de deducirse del mensaje: el mismo mensaje del hotel se
     * pinta a la derecha en la pantalla del hotel y a la izquierda en la del
     * cliente.
     */
    private final Message.Autor espectador;

    public MessageAdapter(@NonNull Message.Autor espectador) {
        super(COMPARADOR);
        this.espectador = espectador;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int viewType) {
        return new MessageViewHolder(ItemMessageBinding.inflate(
                LayoutInflater.from(padre.getContext()), padre, false), espectador);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int posicion) {
        holder.bind(getItem(posicion));
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemMessageBinding binding;
        private final Message.Autor espectador;

        MessageViewHolder(@NonNull ItemMessageBinding binding, @NonNull Message.Autor espectador) {
            super(binding.getRoot());
            this.binding = binding;
            this.espectador = espectador;
        }

        void bind(@NonNull Message mensaje) {
            boolean propio = mensaje.esDe(espectador);

            binding.messageBubble.setText(mensaje.getTexto());
            binding.messageBubble.setBackgroundResource(propio
                    ? R.drawable.bg_bubble_own
                    : R.drawable.bg_bubble_other);
            binding.messageBubble.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    propio ? R.color.colorOnPrimary : R.color.colorTextPrimary));

            binding.messageHora.setText(mensaje.getTimestamp().format(HORA));

            // La burbuja se pega a su lado y la hora la acompania. El conjunto
            // va en una columna, asi que basta con mover la gravedad.
            binding.messageRow.setGravity(propio ? Gravity.END : Gravity.START);
        }
    }
}
