package org.iot.project.ui.admin.mensajes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminMensajesBinding;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.utils.InsetUtils;

import java.util.List;

/**
 * Bandeja de mensajes del hotel (§44, RF-064 a RF-068).
 *
 * <p>Es la única pantalla desde la que el hotel habla con sus huéspedes, y por
 * eso no cuelga de una reserva sino que las reúne todas: el administrador
 * atiende varias a la vez y necesita ver de un vistazo cuáles tienen algo
 * esperando.
 *
 * <p>Los resúmenes, el orden y los contadores se actualizan desde Firestore
 * mientras esta pantalla está visible.
 */
public class MensajesFragment extends Fragment {

    /**
     * Reserva de la conversación que se va a abrir.
     *
     * <p>El nombre del argumento es cosa del grafo —{@code nav_hotel_admin}, que
     * es quien lo declara— y no de esta pantalla, que solo lo rellena.
     */
    private static final String ARG_BOOKING_ID = "bookingId";

    private FragmentAdminMensajesBinding binding;
    private MensajesViewModel viewModel;
    private ConversacionAdapter adaptador;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminMensajesBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminMensajesHeader);

        binding.adminMensajesHeader.setTitulo(R.string.nav_mensajes);
        // Vuelve aunque la barra inferior siga visible: esta pantalla no es una
        // de sus secciones, y sin flecha la única salida sería adivinar qué
        // pestaña lleva de vuelta al panel.
        binding.adminMensajesHeader.mostrarVolver(
                v -> Navigation.findNavController(v).navigateUp());

        adaptador = new ConversacionAdapter(this::abrir);
        binding.adminMensajesLista.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.adminMensajesLista.setAdapter(adaptador);

        viewModel = new ViewModelProvider(this).get(MensajesViewModel.class);
        viewModel.getConversaciones().observe(getViewLifecycleOwner(), this::pintar);
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.iniciarSeguimiento();
    }

    @Override
    public void onStop() {
        viewModel.detenerSeguimiento();
        super.onStop();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<List<ConversacionDeHotel>> estado) {
        if (binding == null) return;
        switch (estado.getStatus()) {
            case LOADING:
                binding.adminMensajesEsqueleto.setVisibility(View.VISIBLE);
                binding.adminMensajesLista.setVisibility(View.GONE);
                binding.adminMensajesVacio.setVisibility(View.GONE);
                binding.adminMensajesError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                List<ConversacionDeHotel> conversaciones = estado.getData();
                if (conversaciones == null) {
                    // Un éxito sin datos no es un éxito: se trata como error
                    // para no dejar la pantalla en blanco sin explicación.
                    pintarError(getString(R.string.estado_error_descripcion));
                    return;
                }
                binding.adminMensajesEsqueleto.setVisibility(View.GONE);
                binding.adminMensajesVacio.setVisibility(View.GONE);
                binding.adminMensajesError.setVisibility(View.GONE);
                binding.adminMensajesLista.setVisibility(View.VISIBLE);
                adaptador.submitList(conversaciones);
                break;
            case EMPTY:
                pintarVacio();
                break;
            case ERROR:
            default:
                pintarError(estado.getMessage());
                break;
        }
    }

    private void pintarVacio() {
        binding.adminMensajesEsqueleto.setVisibility(View.GONE);
        binding.adminMensajesLista.setVisibility(View.GONE);
        binding.adminMensajesError.setVisibility(View.GONE);
        binding.adminMensajesVacio.setVisibility(View.VISIBLE);
        binding.adminMensajesVacio.conIcono(R.drawable.ic_chat);
        binding.adminMensajesVacio.conTitulo(R.string.admin_mensajes_vacio_titulo);
        binding.adminMensajesVacio.conMensaje(R.string.admin_chat_firebase_vacio_reservas);
        binding.adminMensajesVacio.conAccion(R.string.nav_reservas, v -> {
            NavController navegacion = Navigation.findNavController(v);
            if (navegacion.getCurrentDestination() != null
                    && navegacion.getCurrentDestination().getId() == R.id.adminMensajesFragment) {
                navegacion.navigate(R.id.adminReservasFragment);
            }
        });
    }

    private void pintarError(@Nullable String mensaje) {
        adaptador.submitList(null);
        binding.adminMensajesEsqueleto.setVisibility(View.GONE);
        binding.adminMensajesLista.setVisibility(View.GONE);
        binding.adminMensajesVacio.setVisibility(View.GONE);
        binding.adminMensajesError.setVisibility(View.VISIBLE);
        binding.adminMensajesError.conReintento(mensaje, v -> viewModel.recargar());
    }

    // ----------------------------------------------------------------- Abrir

    private void abrir(@NonNull ConversacionDeHotel conversacion) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_BOOKING_ID, conversacion.getBookingId());
        Navigation.findNavController(requireView())
                .navigate(R.id.adminChatFragment, argumentos);
    }

    @Override
    public void onDestroyView() {
        viewModel.detenerSeguimiento();
        super.onDestroyView();
        // El adaptador se suelta antes que el binding: si el RecyclerView
        // conservara las vistas, seguirían apuntando a un binding ya liberado.
        binding.adminMensajesLista.setAdapter(null);
        adaptador = null;
        binding = null;
    }
}
