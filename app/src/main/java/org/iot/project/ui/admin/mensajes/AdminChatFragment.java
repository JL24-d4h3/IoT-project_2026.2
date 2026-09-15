package org.iot.project.ui.admin.mensajes;

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
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentChatBinding;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.ui.common.MessageAdapter;
import org.iot.project.utils.DateFormatter;
import org.iot.project.utils.InsetUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversación del hotel con un huésped (§44, RF-064 a RF-068).
 *
 * <p>Reutiliza la pantalla del chat del cliente —el mismo layout y el mismo
 * adaptador— porque es la misma conversación: lo único que cambia es de qué lado
 * caen las burbujas, y eso lo decide el lado que se le pasa al adaptador.
 *
 * <p>La cabecera lleva el nombre del huésped y las fechas de su estadía, que es
 * lo que el administrador necesita mientras escribe: con quién habla y de qué
 * días. El nombre no está en la conversación —solo su identificador—, así que
 * sale del cruce que devuelve el repositorio.
 */
public class AdminChatFragment extends Fragment {

    /**
     * Reserva de la conversación que se está abriendo.
     *
     * <p>Es el mismo argumento que espera el chat del cliente y lo declara el
     * grafo, no esta pantalla.
     */
    private static final String ARG_BOOKING_ID = "bookingId";

    private FragmentChatBinding binding;
    private AdminChatViewModel viewModel;
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

        // El lado del hotel: la misma conversación que ve el cliente, con las
        // burbujas del hotel a la derecha.
        adaptador = new MessageAdapter(Message.Autor.HOTEL);
        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
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

        viewModel = new ViewModelProvider(this).get(AdminChatViewModel.class);
        viewModel.getConversacion().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintarEnvio);

        String bookingId = getArguments() != null
                ? getArguments().getString(ARG_BOOKING_ID) : null;
        if (bookingId == null) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ConversacionDeHotel> estado) {
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
                // Sin conversación no hay a quién responderle.
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

    private void mostrar(@NonNull ConversacionDeHotel detalle) {
        pintarTitulo(detalle.getEstadia());

        List<Message> mensajes = new ArrayList<>(detalle.getConversacion().getMensajes());
        boolean vacia = mensajes.isEmpty();

        binding.chatVacio.setVisibility(vacia ? View.VISIBLE : View.GONE);
        binding.chatMensajes.setVisibility(vacia ? View.GONE : View.VISIBLE);
        // Se puede escribir aunque no haya mensajes: un huésped que todavía no
        // ha escrito es justo a quien el hotel saluda primero.
        binding.chatEntrada.setVisibility(View.VISIBLE);

        if (vacia) {
            binding.chatVacio.conIcono(R.drawable.ic_chat)
                    .conTitulo(R.string.chat_vacio_titulo)
                    .conMensaje(R.string.admin_chat_vacio);
        }
        publicar(mensajes);
    }

    /**
     * "Lucía Quispe Ramos" y debajo "20 set – 24 set · 4 noches".
     *
     * <p>Las fechas van en el subtítulo y no se omiten cuando la estancia es de
     * un solo día: el administrador puede tener dos conversaciones abiertas con
     * el mismo huésped y es lo que distingue una de otra.
     */
    private void pintarTitulo(@NonNull ReservaDeHotel estadia) {
        binding.header.setTitulo(estadia.getClienteNombre(
                getString(R.string.reserva_huesped_no_disponible)));
        binding.header.setSubtitulo(DateFormatter.rangoConNoches(
                estadia.getReserva().getFechaEntrada(),
                estadia.getReserva().getFechaSalida(),
                estadia.getReserva().getNumNoches()));
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

    // --------------------------------------------------------------- Responde

    private void enviar() {
        String texto = binding.chatTexto.getText().toString();
        if (texto.trim().isEmpty()) {
            return;
        }
        binding.chatEnviar.setEnabled(false);
        viewModel.responder(texto);
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
        binding.chatMensajes.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
