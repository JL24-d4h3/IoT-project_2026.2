package org.iot.project.ui.admin.mensajes;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

/** Conversación en tiempo real con el huésped de una reserva activa del hotel. */
public class AdminChatFragment extends Fragment {
    private static final String ARG_BOOKING_ID = "bookingId";

    private FragmentChatBinding binding;
    private AdminChatViewModel viewModel;
    private MessageAdapter adaptador;
    private long versionLista;
    private boolean listaPresentada;

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

        adaptador = new MessageAdapter(Message.Autor.HOTEL);
        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
        gestor.setStackFromEnd(true);
        binding.chatMensajes.setLayoutManager(gestor);
        binding.chatMensajes.setAdapter(adaptador);
        binding.chatMensajes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) {
                marcarVisibles();
            }
        });

        viewModel = new ViewModelProvider(this).get(AdminChatViewModel.class);
        String bookingId = getArguments() != null
                ? getArguments().getString(ARG_BOOKING_ID) : null;
        if (bookingId == null || bookingId.trim().isEmpty()) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
        viewModel.getConversacion().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintarEnvio);
        viewModel.getBorrador().observe(getViewLifecycleOwner(), texto -> {
            if (binding == null) return;
            if (texto != null && !texto.equals(binding.chatTexto.getText().toString())) {
                binding.chatTexto.setText(texto);
                binding.chatTexto.setSelection(texto.length());
            }
            actualizarEntrada();
        });
        viewModel.getErrorLectura().observe(getViewLifecycleOwner(), mensaje -> {
            if (binding == null) return;
            actualizarEntrada();
            if (mensaje == null) binding.chatMensajes.post(this::marcarVisibles);
        });
        binding.chatTexto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio,
                                                    int antes, int despues) { }
            @Override public void onTextChanged(CharSequence texto, int inicio,
                                               int antes, int despues) {
                viewModel.actualizarBorrador(texto.toString());
                actualizarEntrada();
            }
            @Override public void afterTextChanged(Editable texto) { }
        });
        binding.chatEnviar.setOnClickListener(v -> enviar());
        binding.chatReintentar.setOnClickListener(v -> {
            if (viewModel.getErrorLectura().getValue() != null) {
                viewModel.reintentarLectura();
                marcarVisibles();
            } else {
                enviar();
            }
        });
        binding.chatTexto.setOnEditorActionListener((v, accion, evento) -> {
            if (accion != EditorInfo.IME_ACTION_SEND) return false;
            enviar();
            return true;
        });
    }

    @Override public void onStart() {
        super.onStart();
        viewModel.iniciarSeguimiento();
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null) binding.chatMensajes.post(this::marcarVisibles);
    }

    @Override public void onStop() {
        viewModel.detenerSeguimiento();
        super.onStop();
    }

    private void pintar(@NonNull UiState<ConversacionDeHotel> estado) {
        if (binding == null) return;
        switch (estado.getStatus()) {
            case LOADING:
                versionLista++;
                listaPresentada = false;
                binding.header.setTitulo(R.string.titulo_chat);
                binding.header.setSubtitulo("");
                binding.chatEsqueleto.setVisibility(View.VISIBLE);
                binding.chatMensajes.setVisibility(View.GONE);
                binding.chatVacio.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.GONE);
                binding.chatEntrada.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.chatEsqueleto.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.GONE);
                mostrar(estado.requireData());
                break;
            default:
                versionLista++;
                listaPresentada = false;
                binding.header.setTitulo(R.string.titulo_chat);
                binding.header.setSubtitulo("");
                adaptador.submitList(null);
                binding.chatEsqueleto.setVisibility(View.GONE);
                binding.chatMensajes.setVisibility(View.GONE);
                binding.chatVacio.setVisibility(View.GONE);
                binding.chatEntrada.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.VISIBLE);
                binding.chatError.conReintento(estado.getMessage(), v -> viewModel.recargar());
                break;
        }
        actualizarEntrada();
    }

    private void mostrar(@NonNull ConversacionDeHotel detalle) {
        ReservaDeHotel estadia = detalle.getEstadia();
        binding.header.setTitulo(estadia.getClienteNombre(
                getString(R.string.reserva_huesped_no_disponible)));
        binding.header.setSubtitulo(DateFormatter.rangoConNoches(
                estadia.getReserva().getFechaEntrada(), estadia.getReserva().getFechaSalida(),
                estadia.getReserva().getNumNoches()));

        List<Message> mensajes = new ArrayList<>(detalle.getConversacion().getMensajes());
        boolean vacia = mensajes.isEmpty();
        binding.chatVacio.setVisibility(vacia ? View.VISIBLE : View.GONE);
        binding.chatMensajes.setVisibility(vacia ? View.GONE : View.VISIBLE);
        binding.chatEntrada.setVisibility(View.VISIBLE);
        if (vacia) {
            binding.chatVacio.conIcono(R.drawable.ic_chat)
                    .conTitulo(R.string.chat_vacio_titulo)
                    .conMensaje(R.string.admin_chat_vacio);
        }
        LinearLayoutManager gestor = (LinearLayoutManager) binding.chatMensajes.getLayoutManager();
        boolean seguirFinal = adaptador.getItemCount() == 0 || (gestor != null
                && gestor.findLastVisibleItemPosition() >= adaptador.getItemCount() - 2);
        int cantidadAnterior = adaptador.getItemCount();
        final FragmentChatBinding vistaActual = binding;
        final long versionActual = ++versionLista;
        listaPresentada = false;
        MessageAdapter actual = adaptador;
        actual.submitList(mensajes, () -> {
            if (binding != vistaActual || actual != adaptador || versionActual != versionLista) return;
            if (seguirFinal && !mensajes.isEmpty() && cantidadAnterior != mensajes.size()) {
                binding.chatMensajes.scrollToPosition(mensajes.size() - 1);
            }
            binding.chatMensajes.post(() -> {
                if (binding == vistaActual && actual == adaptador && versionActual == versionLista) {
                    listaPresentada = true;
                    marcarVisibles();
                }
            });
        });
    }

    /** No acusa lectura al descargar: solo las burbujas presentes en la ventana visible. */
    private void marcarVisibles() {
        if (binding == null || adaptador == null || !listaPresentada || !binding.chatMensajes.isShown()
                || !getViewLifecycleOwner().getLifecycle().getCurrentState()
                .isAtLeast(Lifecycle.State.RESUMED)) return;
        if (binding.chatMensajes.isComputingLayout() || binding.chatMensajes.hasPendingAdapterUpdates()) {
            binding.chatMensajes.postOnAnimation(this::marcarVisibles);
            return;
        }
        Rect ventana = new Rect();
        if (!binding.chatMensajes.getGlobalVisibleRect(ventana)) return;
        List<Message> visibles = new ArrayList<>();
        List<Message> actuales = adaptador.getCurrentList();
        for (int i = 0; i < binding.chatMensajes.getChildCount(); i++) {
            View burbuja = binding.chatMensajes.getChildAt(i);
            int posicion = binding.chatMensajes.getChildAdapterPosition(burbuja);
            Rect area = new Rect();
            if (posicion >= 0 && posicion < actuales.size()
                    && burbuja.getGlobalVisibleRect(area) && Rect.intersects(ventana, area)) {
                visibles.add(actuales.get(posicion));
            }
        }
        viewModel.marcarVisibles(visibles);
    }

    private void enviar() {
        if (binding == null) return;
        viewModel.responder(binding.chatTexto.getText().toString());
    }

    private void pintarEnvio(@Nullable UiState<Conversation> estado) {
        if (binding == null) return;
        actualizarEntrada();
        if (estado == null || estado.isLoading()) return;
        if (estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG)
                    .setAction(R.string.admin_chat_firebase_reintentar, v -> enviar()).show();
        }
        // El callback confirma el envío; el historial completo siempre viene de la escucha.
        viewModel.limpiarEnvio();
    }

    private void actualizarEntrada() {
        if (binding == null || viewModel == null) return;
        UiState<ConversacionDeHotel> chat = viewModel.getConversacion().getValue();
        UiState<Conversation> envio = viewModel.getEnvio().getValue();
        boolean abierto = chat != null && chat.isSuccess();
        boolean enviando = envio != null && envio.isLoading();
        boolean pendiente = viewModel.tieneEnvioPendiente();
        binding.chatTexto.setEnabled(abierto && !pendiente && !enviando);
        binding.chatEnviar.setEnabled(abierto && !enviando
                && !binding.chatTexto.getText().toString().trim().isEmpty());
        binding.chatEnviar.setContentDescription(getString(pendiente
                ? R.string.chat_cliente_reintentar_envio : R.string.cd_enviar_mensaje));
        String errorLectura = viewModel.getErrorLectura().getValue();
        String aviso = errorLectura != null
                ? getString(R.string.chat_cliente_lectura_error, errorLectura)
                : pendiente ? getString(enviando ? R.string.admin_chat_firebase_enviando
                : R.string.admin_chat_firebase_envio_pendiente) : null;
        binding.chatEstado.setVisibility(abierto && aviso != null ? View.VISIBLE : View.GONE);
        binding.chatAviso.setText(aviso == null ? "" : aviso);
        binding.chatReintentar.setVisibility(abierto && (errorLectura != null || pendiente && !enviando)
                ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() {
        versionLista++;
        listaPresentada = false;
        viewModel.detenerSeguimiento();
        binding.chatMensajes.setAdapter(null);
        adaptador = null;
        binding = null;
        super.onDestroyView();
    }
}
