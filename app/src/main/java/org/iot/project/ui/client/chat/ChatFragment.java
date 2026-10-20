package org.iot.project.ui.client.chat;

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
import org.iot.project.models.Conversation;
import org.iot.project.models.Message;
import org.iot.project.ui.common.MessageAdapter;
import org.iot.project.utils.InsetUtils;

import java.util.ArrayList;
import java.util.List;

/** Chat de la reserva activa, actualizado desde Firestore mientras la pantalla está abierta. */
public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adaptador;
    private LinearLayoutManager gestor;
    private String errorConexion;
    private long versionLista;
    private boolean listaPresentada;

    @Nullable @Override
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
        gestor = new LinearLayoutManager(requireContext());
        gestor.setStackFromEnd(true);
        binding.chatMensajes.setLayoutManager(gestor);
        binding.chatMensajes.setAdapter(adaptador);
        binding.chatMensajes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                marcarVisibles();
            }
        });

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        binding.chatEnviar.setOnClickListener(v -> enviar());
        binding.chatTexto.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEND) { enviar(); return true; }
            return false;
        });
        binding.chatTexto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.actualizarBorrador(s.toString());
                actualizarEntrada();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        binding.chatReintentar.setOnClickListener(v -> {
            if (errorConexion != null) { viewModel.recargar(); }
            else if (viewModel.getErrorLectura().getValue() != null) { viewModel.reintentarLectura(); }
            else { enviar(); }
        });

        viewModel.getConversacion().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.getEnvio().observe(getViewLifecycleOwner(), this::pintarEnvio);
        viewModel.getPuedeEscribir().observe(getViewLifecycleOwner(), valor -> actualizarEntrada());
        viewModel.getErrorLectura().observe(getViewLifecycleOwner(), valor -> {
            actualizarAviso();
            if (valor == null && binding != null) { binding.chatMensajes.post(this::marcarVisibles); }
        });
        viewModel.getTitulo().observe(getViewLifecycleOwner(), titulo -> {
            if (binding != null) {
                binding.header.setTitulo(titulo == null || titulo.trim().isEmpty()
                        ? getString(R.string.titulo_chat) : titulo);
            }
        });
        viewModel.getBorrador().observe(getViewLifecycleOwner(), texto -> {
            if (binding != null && !binding.chatTexto.getText().toString().equals(texto)) {
                binding.chatTexto.setText(texto == null ? "" : texto);
                binding.chatTexto.setSelection(binding.chatTexto.length());
            }
            actualizarEntrada();
        });

        String bookingId = getArguments() == null ? null : getArguments().getString("bookingId");
        if (bookingId == null || bookingId.trim().isEmpty()) {
            Navigation.findNavController(vista).navigateUp();
            return;
        }
        viewModel.cargar(bookingId);
    }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) { viewModel.iniciar(); }
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null) { viewModel.setVisible(true); }
        if (binding != null) { binding.chatMensajes.post(this::marcarVisibles); }
    }

    @Override public void onPause() {
        if (viewModel != null) { viewModel.setVisible(false); }
        super.onPause();
    }

    @Override public void onStop() {
        if (viewModel != null) { viewModel.detener(); }
        super.onStop();
    }

    private void pintar(@Nullable UiState<Conversation> estado) {
        if (binding == null || estado == null) { return; }
        if (estado.isLoading()) {
            errorConexion = null;
            binding.chatEsqueleto.setVisibility(View.VISIBLE);
            binding.chatMensajes.setVisibility(View.GONE);
            binding.chatVacio.setVisibility(View.GONE);
            binding.chatError.setVisibility(View.GONE);
            binding.chatEntrada.setVisibility(View.GONE);
        } else if (estado.isSuccess()) {
            errorConexion = null;
            mostrar(estado.requireData());
        } else {
            errorConexion = estado.getMessage();
            Conversation ultima = viewModel.getUltimaConversacion();
            if (ultima != null) {
                mostrar(ultima);
            } else {
                binding.chatEsqueleto.setVisibility(View.GONE);
                binding.chatMensajes.setVisibility(View.GONE);
                binding.chatVacio.setVisibility(View.GONE);
                binding.chatEntrada.setVisibility(View.GONE);
                binding.chatError.setVisibility(View.VISIBLE);
                binding.chatError.conReintento(estado.getMessage(), v -> viewModel.recargar());
                versionLista++;
                listaPresentada = false;
                adaptador.submitList(new ArrayList<>());
            }
        }
        actualizarEntrada();
        actualizarAviso();
    }

    private void mostrar(@NonNull Conversation conversacion) {
        binding.chatEsqueleto.setVisibility(View.GONE);
        binding.chatError.setVisibility(View.GONE);
        List<Message> mensajes = new ArrayList<>(conversacion.getMensajes());
        boolean vacia = mensajes.isEmpty();
        binding.chatVacio.setVisibility(vacia ? View.VISIBLE : View.GONE);
        binding.chatMensajes.setVisibility(vacia ? View.GONE : View.VISIBLE);
        binding.chatEntrada.setVisibility(View.VISIBLE);
        if (vacia) {
            binding.chatVacio.conIcono(R.drawable.ic_chat)
                    .conTitulo(R.string.chat_vacio_titulo).conMensaje(R.string.chat_vacio);
        }
        final FragmentChatBinding vistaActual = binding;
        final MessageAdapter adaptadorActual = adaptador;
        final long versionActual = ++versionLista;
        listaPresentada = false;
        final boolean alFinal = adaptador.getItemCount() == 0
                || gestor.findLastVisibleItemPosition() >= adaptador.getItemCount() - 2;
        final int cantidadAnterior = adaptador.getItemCount();
        adaptador.submitList(mensajes, () -> {
            if (binding != vistaActual || adaptador != adaptadorActual || versionActual != versionLista) { return; }
            if (alFinal && !mensajes.isEmpty() && mensajes.size() != cantidadAnterior) {
                binding.chatMensajes.scrollToPosition(mensajes.size() - 1);
            }
            binding.chatMensajes.post(() -> {
                if (binding == vistaActual && adaptador == adaptadorActual && versionActual == versionLista) {
                    listaPresentada = true;
                    marcarVisibles();
                }
            });
        });
    }

    private void marcarVisibles() {
        if (binding == null || adaptador == null || gestor == null || viewModel == null || !listaPresentada
                || !getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        if (!binding.chatMensajes.isShown() || adaptador.getItemCount() == 0) { return; }
        // Durante un diff las posiciones todavía pueden corresponder a las filas anteriores.
        if (binding.chatMensajes.isComputingLayout() || binding.chatMensajes.hasPendingAdapterUpdates()) {
            binding.chatMensajes.postOnAnimation(this::marcarVisibles);
            return;
        }
        Rect ventana = new Rect();
        if (!binding.chatMensajes.getGlobalVisibleRect(ventana)) { return; }
        List<Message> mensajes = adaptador.getCurrentList();
        List<Message> visibles = new ArrayList<>();
        for (int i = 0; i < binding.chatMensajes.getChildCount(); i++) {
            View burbuja = binding.chatMensajes.getChildAt(i);
            int posicion = binding.chatMensajes.getChildAdapterPosition(burbuja);
            Rect area = new Rect();
            if (posicion >= 0 && posicion < mensajes.size()
                    && burbuja.getGlobalVisibleRect(area) && Rect.intersects(ventana, area)) {
                visibles.add(mensajes.get(posicion));
            }
        }
        viewModel.marcarEntregados(visibles);
    }

    private void enviar() {
        if (binding == null) { return; }
        viewModel.actualizarBorrador(binding.chatTexto.getText().toString());
        viewModel.enviar();
        actualizarEntrada();
    }

    private void pintarEnvio(@Nullable UiState<Conversation> estado) {
        if (binding == null) { return; }
        actualizarEntrada();
        if (estado != null && estado.isError()) {
            Snackbar.make(binding.getRoot(), estado.getMessage(), Snackbar.LENGTH_LONG)
                    .setAction(R.string.chat_cliente_reintentar, v -> enviar()).show();
            viewModel.limpiarEnvio();
        } else if (estado != null && estado.isSuccess()) {
            // El historial solo se pinta desde el listener, nunca desde el acuse del envío.
            viewModel.limpiarEnvio();
        }
    }

    private void actualizarEntrada() {
        if (binding == null || viewModel == null) { return; }
        boolean disponible = Boolean.TRUE.equals(viewModel.getPuedeEscribir().getValue());
        binding.chatTexto.setEnabled(disponible && !viewModel.tieneEnvioPendiente());
        binding.chatEnviar.setEnabled(disponible && !viewModel.estaEnviando()
                && !binding.chatTexto.getText().toString().trim().isEmpty());
        binding.chatEnviar.setContentDescription(getString(viewModel.tieneEnvioPendiente()
                ? R.string.chat_cliente_reintentar_envio : R.string.cd_enviar_mensaje));
        actualizarAviso();
    }

    private void actualizarAviso() {
        if (binding == null || viewModel == null) { return; }
        String aviso = errorConexion;
        boolean reintentar = aviso != null;
        if (aviso == null && viewModel.getErrorLectura().getValue() != null) {
            aviso = getString(R.string.chat_cliente_lectura_error, viewModel.getErrorLectura().getValue());
            reintentar = true;
        }
        if (aviso == null && viewModel.tieneEnvioPendiente()) {
            aviso = getString(viewModel.estaEnviando()
                    ? R.string.chat_cliente_enviando : R.string.chat_cliente_envio_pendiente);
            reintentar = !viewModel.estaEnviando()
                    && Boolean.TRUE.equals(viewModel.getPuedeEscribir().getValue());
        }
        binding.chatEstado.setVisibility(aviso == null ? View.GONE : View.VISIBLE);
        binding.chatAviso.setText(aviso == null ? "" : aviso);
        binding.chatReintentar.setVisibility(reintentar ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() {
        versionLista++;
        listaPresentada = false;
        if (binding != null) { binding.chatMensajes.setAdapter(null); }
        adaptador = null;
        gestor = null;
        binding = null;
        errorConexion = null;
        super.onDestroyView();
    }
}
