package org.iot.project.ui.admin.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentAdminHomeBinding;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.Hotel;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.models.ReservaDeHotel;
import org.iot.project.models.ResumenHotel;
import org.iot.project.ui.admin.mensajes.ConversacionAdapter;
import org.iot.project.ui.components.IngresoListView;
import org.iot.project.utils.InsetUtils;
import org.iot.project.utils.PriceFormatter;

import java.util.Collections;
import java.util.List;

/**
 * Portada del administrador de hotel (§43).
 *
 * <p>Es lo primero que ve al entrar y responde a dos preguntas: qué pasa hoy en
 * mi hotel y a dónde voy desde aquí. Las tres cifras del día van arriba, el
 * índice de acciones rápidas justo debajo y lo que se consulta despacio —las
 * estadías en curso, los ingresos por servicios y las conversaciones
 * recientes— al final.
 *
 * <p>El índice no es decorativo: la barra inferior lleva cinco secciones y
 * cinco pantallas más —datos del hotel, clientes, cobros, mensajes y
 * reportes— no se alcanzan de ninguna otra forma. Por eso los rótulos de las
 * piezas salen de las etiquetas del grafo y no de cadenas propias: la pieza dice
 * el nombre con el que la pantalla de destino se anuncia a sí misma, y así las
 * dos no pueden llamarla de dos maneras.
 *
 * <p>No hay estado vacío. Un hotel sin huéspedes no deja la pantalla en blanco:
 * deja el tablero en cero, que es una respuesta. Cada bloque sí tiene su propia
 * línea de vacío, porque de un bloque vacío sí hay algo que decir.
 */
public class AdminHomeFragment extends Fragment {

    /**
     * Reserva de la conversación o de la estadía que se va a abrir.
     *
     * <p>El nombre del argumento es cosa del grafo —{@code nav_hotel_admin}, que
     * es quien lo declara— y no de esta pantalla, que solo lo rellena.
     */
    private static final String ARG_BOOKING_ID = "bookingId";

    private FragmentAdminHomeBinding binding;
    private AdminHomeViewModel viewModel;
    private EstadiaAdapter estadias;
    private ConversacionAdapter mensajes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminHomeBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.adminHeader);

        binding.adminHeader.setTitulo(R.string.titulo_panel_admin);

        estadias = new EstadiaAdapter(this::abrirReserva);
        binding.adminEstadias.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.adminEstadias.setAdapter(estadias);

        mensajes = new ConversacionAdapter(this::abrirChat);
        binding.adminHomeMensajes.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.adminHomeMensajes.setAdapter(mensajes);

        atarAccionesRapidas();

        viewModel = new ViewModelProvider(this).get(AdminHomeViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // La primera vez no hace nada —la carga con esqueleto acaba de salir de
        // onViewCreated— y a partir de ahí refresca el tablero sin borrarlo: al
        // volver de una conversación, sus mensajes ya están leídos y el contador
        // de esa fila tiene que apagarse.
        viewModel.refrescarEnSilencio();
    }

    /**
     * Cuelga el destino de cada acceso rápido.
     *
     * <p>El grafo no declara acciones —{@code nav_hotel_admin} solo enumera
     * destinos—, así que se navega por identificador. Es lo mismo que hacen las
     * demás pantallas del rol y evita tener que tocar el grafo cada vez que una
     * portada aprende un camino nuevo.
     */
    private void atarAccionesRapidas() {
        binding.adminAccionHotel.setAccion(R.drawable.ic_edit, R.string.nav_hotel_datos,
                v -> irA(R.id.adminHotelFragment));
        binding.adminAccionClientes.setAccion(R.drawable.ic_person_group, R.string.nav_clientes,
                v -> irA(R.id.adminClientesFragment));
        binding.adminAccionCobros.setAccion(R.drawable.ic_credit_card, R.string.nav_cargos,
                v -> irA(R.id.adminCargosFragment));
        binding.adminAccionMensajes.setAccion(R.drawable.ic_chat, R.string.nav_mensajes,
                v -> irA(R.id.adminMensajesFragment));
        binding.adminAccionReportes.setAccion(R.drawable.ic_receipt, R.string.nav_reportes,
                v -> irA(R.id.adminReportesFragment));
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<AdminHomeViewModel.Contenido> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                llenar(estado.requireData());
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.adminError.conReintento(estado.getMessage(),
                        v -> viewModel.reintentar());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.adminContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.adminEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.adminError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void llenar(@NonNull AdminHomeViewModel.Contenido contenido) {
        ResumenHotel resumen = contenido.resumen;
        Hotel hotel = resumen.getHotel();

        binding.adminHotelNombre.setText(hotel.getNombre());
        binding.adminHotelUbicacion.setText(getString(R.string.admin_home_ubicacion,
                hotel.getDistrito(), hotel.getCiudad(), hotel.getDireccion()));

        binding.adminStatActivas.setDato(R.string.admin_home_stat_activas,
                String.valueOf(resumen.getEstadias().size()));
        binding.adminStatSalidas.setDato(R.string.admin_home_stat_salidas,
                String.valueOf(resumen.getSalidasDeHoy()));
        binding.adminStatIngresos.setDato(R.string.admin_home_stat_ingresos,
                PriceFormatter.format(resumen.getTotalServicios()));

        // Sin estadías, la lista no se dibuja y en su lugar va una frase que
        // explica qué aparecerá aquí. Un encabezado con el vacío debajo deja al
        // administrador sin saber si no hay nadie o si algo falló.
        boolean hayEstadias = !resumen.getEstadias().isEmpty();
        binding.adminEstadias.setVisibility(hayEstadias ? View.VISIBLE : View.GONE);
        binding.adminEstadiasNota.setVisibility(hayEstadias ? View.VISIBLE : View.GONE);
        binding.adminEstadiasVacio.setVisibility(hayEstadias ? View.GONE : View.VISIBLE);
        estadias.submitList(resumen.getEstadias());

        pintarIngresos(resumen.getIngresos());
        pintarMensajes(contenido);
    }

    /**
     * El desglose de ingresos, o la frase que explica por qué no lo hay.
     *
     * <p>"Ver reporte" no se toca: no se esconde aunque este bloque esté vacío,
     * porque el reporte no es esta lista. Aquí solo se ve lo cobrado por
     * servicios adicionales; allí se ven además las reservas y las ventas por
     * día, mes y año, que son otro dato y pueden tener contenido igualmente.
     */
    private void pintarIngresos(@NonNull List<IngresoPorServicio> ingresos) {
        boolean hay = IngresoListView.hayIngresos(ingresos);
        binding.adminHomeIngresos.setIngresos(hay ? ingresos : null);
        binding.adminHomeIngresosVacio.setVisibility(hay ? View.GONE : View.VISIBLE);
    }

    private void pintarMensajes(@NonNull AdminHomeViewModel.Contenido contenido) {
        boolean hay = contenido.hayMensajes();
        binding.adminHomeMensajes.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.adminHomeMensajesNota.setVisibility(hay ? View.VISIBLE : View.GONE);
        binding.adminHomeMensajesVacio.setVisibility(hay ? View.GONE : View.VISIBLE);

        // "Ver todos" solo cuando hay más de los que caben: con tres o menos, la
        // bandeja enseñaría exactamente las mismas filas que ya están aquí, y el
        // acceso rápido de Mensajes lleva allí sin prometer nada de más.
        binding.adminHomeVerMensajes.setVisibility(
                contenido.hayMasConversaciones() ? View.VISIBLE : View.GONE);

        if (!hay) {
            mensajes.submitList(Collections.emptyList());
            return;
        }
        binding.adminHomeMensajesNota.setText(avisoDeSinLeer(contenido.mensajesSinLeer));
        mensajes.submitList(contenido.getConversacionesDestacadas());
    }

    /**
     * Cuántos mensajes esperan respuesta, o que no espera ninguno.
     *
     * <p>Decirlo también cuando no hay ninguno es deliberado: la línea aparece
     * bajo un título que sí se ve, y dejarla en blanco haría pensar que el
     * contador no cargó.
     */
    @NonNull
    private CharSequence avisoDeSinLeer(int sinLeer) {
        if (sinLeer <= 0) {
            return getString(R.string.admin_home_mensajes_al_dia);
        }
        return getResources().getQuantityString(
                R.plurals.admin_mensajes_sin_leer, sinLeer, sinLeer);
    }

    // ----------------------------------------------------------------- Abrir

    private void abrirReserva(@NonNull ReservaDeHotel estadia) {
        abrirDesdeReserva(R.id.adminReservaDetalleFragment, estadia.getReserva().getId());
    }

    private void abrirChat(@NonNull ConversacionDeHotel conversacion) {
        abrirDesdeReserva(R.id.adminChatFragment, conversacion.getBookingId());
    }

    /**
     * Abre la pantalla que cuelga de una reserva.
     *
     * <p>Las dos —el detalle y el chat— se identifican por la reserva y se
     * alcanzan desde aquí, así que el argumento se arma en un solo sitio: el
     * nombre {@code bookingId} lo fija el grafo, y escrito dos veces el día que
     * cambie se cambia en una.
     */
    private void abrirDesdeReserva(@IdRes int destino, @NonNull String bookingId) {
        Bundle argumentos = new Bundle();
        argumentos.putString(ARG_BOOKING_ID, bookingId);
        Navigation.findNavController(requireView()).navigate(destino, argumentos);
    }

    private void irA(@IdRes int destino) {
        Navigation.findNavController(requireView()).navigate(destino);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Los adaptadores se sueltan antes que el binding: si los RecyclerView
        // conservaran las vistas, seguirían apuntando a un binding ya liberado.
        binding.adminEstadias.setAdapter(null);
        binding.adminHomeMensajes.setAdapter(null);
        estadias = null;
        mensajes = null;
        binding = null;
    }
}
