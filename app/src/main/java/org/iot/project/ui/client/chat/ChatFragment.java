package org.iot.project.ui.client.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import org.iot.project.R;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentChatBinding;
import org.iot.project.models.Conversation;
import org.iot.project.models.Hotel;
import org.iot.project.models.Message;
import org.iot.project.ui.common.MessageAdapter;
import org.iot.project.utils.InsetUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat entre el cliente y el alojamiento (RF-062 a RF-068).
 *
 * <p>El título de la cabecera es el nombre del hotel: es con quien se está
 * hablando, y sin él la pantalla no diría con quién. Sale de la propia
 * conversación, que ya sabe a qué hotel pertenece, así que no hace falta pedir
 * la reserva solo para eso.
 */
public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.header);
        InsetUtils.applyBottomPadding(binding.chatEntrada);

        binding.header.setTitulo(R.string.titulo_chat);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        adaptador = new MessageAdapter(Message.Autor.CLIENTE);
        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
        // Las conversaciones se leen de abajo hacia arriba: el mensaje recién
        // llegado tiene que quedar a la vista sin desplazar.
        gestor.setStackFromEnd(true);
        binding.chatMensajes.setLayoutManager(gestor);
        binding.chatMensajes.setAdapter(adaptador);

        binding.chatEnviar.setOnClickListener(v -> enviar());
        binding.chatTexto.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEND) {
                enviar();
                return true;
            }
            return false;
        });

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.getConversacion().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintarEnvio);

        String bookingId = getArguments() != null
                ? getArguments().getString("bookingId") : null;
        if (bookingId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<Conversation> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrarEsqueleto();
                break;
            case SUCCESS:
                binding.chatEsqueleto.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.GONE);
                mostrar(estado.requireData());
                break;
            default:
                binding.chatEsqueleto.setVisibility(View.GONE);
                binding.chatMensajes.setVisibility(View.GONE);
                binding.chatVacio.setVisibility(View.GONE);
                // Sin conversación no hay a quién escribirle.
                binding.chatEntrada.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.VISIBLE);
                binding.chatError.conReintento(estado.getMessage(), v -> viewModel.recargar());
                break;
        }
    }

    private void mostrarEsqueleto() {
        binding.chatEsqueleto.setVisibility(View.VISIBLE);
        binding.chatMensajes.setVisibility(View.GONE);
        binding.chatVacio.setVisibility(View.GONE);
        binding.chatError.setVisibility(View.GONE);
        binding.chatEntrada.setVisibility(View.GONE);
    }

    private void mostrar(@NonNull Conversation conversacion) {
        pintarTitulo(conversacion);

        List<Message> mensajes = new ArrayList<>(conversacion.getMensajes());
        boolean vacia = mensajes.isEmpty();

        binding.chatVacio.setVisibility(vacia ? View.VISIBLE : View.GONE);
        binding.chatMensajes.setVisibility(vacia ? View.GONE : View.VISIBLE);
        // Se puede escribir aunque no haya mensajes: ése es justo el caso de una
        // conversación recién abierta.
        binding.chatEntrada.setVisibility(View.VISIBLE);

        if (vacia) {
            binding.chatVacio.conIcono(R.drawable.ic_chat)
                    .conTitulo(R.string.chat_vacio_titulo)
                    .conMensaje(R.string.chat_vacio);
        }
        publicar(mensajes);
    }

    private void pintarTitulo(@NonNull Conversation conversacion) {
        Hotel hotel = ServiceLocator.hoteles().hotel(conversacion.getHotelId());
        binding.header.setTitulo(hotel != null
                ? hotel.getNombre()
                : getString(R.string.titulo_chat));
    }

    /**
     * Entrega la lista al adaptador.
     *
     * <p>Siempre una copia: {@code getMensajes()} devuelve una vista sobre la
     * lista viva, y DiffUtil la recorre en otro hilo. Con la vista en vez de una
     * copia, un mensaje que llegara mientras se compara la rompería.
     */
    private void publicar(@NonNull List<Message> mensajes) {
        adaptador.submitList(mensajes);
        if (!mensajes.isEmpty()) {
            binding.chatMensajes.scrollToPosition(mensajes.size() - 1);
        }
    }

    // ----------------------------------------------------------------- Enviar

    private void enviar() {
        String texto = binding.chatTexto.getText().toString();
        if (texto.trim().isEmpty()) {
            return;
        }
        binding.chatEnviar.setEnabled(false);
        viewModel.enviar(texto);
    }

    private void pintarEnvio(@Nullable UiState<Conversation> estado) {
        if (estado == null || estado.isLoading()) {
            return;
        }
        binding.chatEnviar.setEnabled(true);

        if (estado.isError()) {
            // El texto se queda en el campo: perderlo obligaría a escribirlo otra
            // vez por un fallo que no es del usuario.
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG).show();
            viewModel.limpiarEnvio();
            return;
        }

        binding.chatTexto.setText("");
        binding.chatVacio.setVisibility(View.GONE);
        binding.chatMensajes.setVisibility(View.VISIBLE);
        publicar(new ArrayList<>(estado.requireData().getMensajes()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // El adaptador se suelta antes que el binding: si el RecyclerView
        // conservara las vistas, seguirían apuntando a un binding ya liberado.
        binding.chatMensajes.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
