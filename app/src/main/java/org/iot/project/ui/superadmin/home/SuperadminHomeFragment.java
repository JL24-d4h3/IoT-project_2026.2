package org.iot.project.ui.superadmin.home;

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
import org.iot.project.databinding.FragmentSuperadminHomeBinding;
import org.iot.project.models.ResumenSuperadmin;
import org.iot.project.ui.superadmin.bitacora.BitacoraAdapter;
import org.iot.project.utils.InsetUtils;

import java.util.Collections;

/**
 * Portada del superadministrador (§47).
 *
 * <p>Es lo primero que ve al entrar y responde a dos preguntas: como esta la
 * plataforma y que espera una decision suya. Las cuatro cifras van arriba, el
 * aviso de pendientes justo debajo —es lo unico accionable de la pantalla— y
 * los ultimos movimientos al final.
 *
 * <p>Las cifras se leen como "activos de totales" y no como un numero suelto:
 * "12 usuarios" no dice si son todos o la mitad, y la mitad es precisamente lo
 * que el superadministrador necesita saber.
 *
 * <p>El aviso de pendientes no se esconde cuando no hay ninguno. Un bloque que
 * desaparece deja la duda de si no hay nada o si el tablero no cargo; la
 * tarjeta se queda, cambia el titulo y dice que no hay nada.
 *
 * <p>Es tambien la puerta a la bitacora, que no cabe en la barra: los ultimos
 * movimientos se leen aqui y el boton de debajo abre la lista entera. Quien
 * quiera el detalle de uno de esos movimientos no tiene que saber en que
 * seccion vive: lo tiene a la vista donde lo leyo.
 */
public class SuperadminHomeFragment extends Fragment {

    private FragmentSuperadminHomeBinding binding;
    private SuperadminHomeViewModel viewModel;
    private BitacoraAdapter ultimos;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminHomeBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);

        binding.saHeader.setTitulo(R.string.sa_titulo_inicio);

        // Esta cabecera no lleva accion: el cerrar sesion vive en el perfil,
        // con los otros roles, y no hace falta tenerlo dos veces. Antes estaba
        // aqui porque no habia perfil, y eso dejaba sin salida a quien estuviera
        // en cualquiera de las otras cuatro secciones.

        ultimos = new BitacoraAdapter();
        binding.saUltimos.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.saUltimos.setAdapter(ultimos);

        binding.saPendientesConductoresAccion.setOnClickListener(
                v -> irA(R.id.superadminConductoresFragment));
        binding.saPendientesHotelesAccion.setOnClickListener(
                v -> irA(R.id.superadminHotelesFragment));
        binding.saVerAuditoria.setOnClickListener(v -> irA(R.id.superadminBitacoraFragment));

        viewModel = new ViewModelProvider(this).get(SuperadminHomeViewModel.class);
        viewModel.getContenido().observe(getViewLifecycleOwner(), this::pintar);
        viewModel.cargar();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Las cifras cambian desde las otras pantallas del panel —desactivar una
        // cuenta, habilitar un conductor, dar de alta un hotel— y volver a Inicio
        // tiene que enseñarlas al dia. Sin esqueleto: el tablero ya esta pintado
        // y no hay motivo para borrarlo mientras llega el refresco.
        viewModel.refrescarEnSilencio();
    }

    // ------------------------------------------------------------------ Pinta

    private void pintar(@NonNull UiState<ResumenSuperadmin> estado) {
        switch (estado.getStatus()) {
            case LOADING:
                mostrar(false, true, false);
                break;
            case SUCCESS:
                mostrar(true, false, false);
                llenar(estado.requireData());
                break;
            case EMPTY:
                // Una plataforma sin nada dentro sigue siendo una plataforma: las
                // cifras son ceros y la bitacora esta vacia, pero el tablero se
                // dibuja igual. Por eso este caso se pinta como el exito.
                mostrar(true, false, false);
                llenar(estado.requireData());
                break;
            case ERROR:
            default:
                mostrar(false, false, true);
                binding.saError.conReintento(estado.getMessage(), v -> viewModel.reintentar());
                break;
        }
    }

    private void mostrar(boolean contenido, boolean esqueleto, boolean error) {
        binding.saContenido.setVisibility(contenido ? View.VISIBLE : View.GONE);
        binding.saEsqueleto.setVisibility(esqueleto ? View.VISIBLE : View.GONE);
        binding.saError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void llenar(@NonNull ResumenSuperadmin resumen) {
        binding.saStatUsuarios.setDato(R.string.sa_stat_usuarios,
                deTotales(resumen.getUsuariosActivos(), resumen.getUsuariosTotales()));
        binding.saStatConductores.setDato(R.string.sa_stat_conductores,
                deTotales(resumen.getConductoresHabilitados(), resumen.getConductoresTotales()));
        binding.saStatHoteles.setDato(R.string.sa_stat_hoteles,
                deTotales(resumen.getHotelesPublicados(), resumen.getHotelesTotales()));
        binding.saStatPendientes.setDato(R.string.sa_stat_pendientes,
                String.valueOf(resumen.getConductoresPendientes()));

        pintarPendientes(resumen);
        pintarUltimos(resumen);
    }

    @NonNull
    private CharSequence deTotales(int parte, int total) {
        return getString(R.string.sa_de_total, parte, total);
    }

    /**
     * El aviso de pendientes, con una fila por cada cosa que espera.
     *
     * <p>Cada fila lleva su propio acceso porque son dos destinos distintos:
     * mandar a Conductores a quien tiene un hotel sin administrador seria
     * mandarlo a un sitio donde no esta lo que le falta.
     */
    private void pintarPendientes(@NonNull ResumenSuperadmin resumen) {
        int conductores = resumen.getConductoresPendientes();
        int hoteles = resumen.getHotelesSinAdministrador();
        boolean hayConductores = conductores > 0;
        boolean hayHoteles = hoteles > 0;

        binding.saPendientesTitulo.setText(hayConductores || hayHoteles
                ? R.string.sa_pendientes_titulo
                : R.string.sa_sin_pendientes_titulo);

        binding.saPendientesConductores.setVisibility(hayConductores ? View.VISIBLE : View.GONE);
        binding.saPendientesConductoresTexto.setText(getResources()
                .getQuantityString(R.plurals.sa_conductores_pendientes, conductores, conductores));

        binding.saPendientesHoteles.setVisibility(hayHoteles ? View.VISIBLE : View.GONE);
        binding.saPendientesHotelesTexto.setText(getResources()
                .getQuantityString(R.plurals.sa_hoteles_sin_administrador, hoteles, hoteles));

        binding.saPendientesVacio.setVisibility(
                hayConductores || hayHoteles ? View.GONE : View.VISIBLE);
    }

    /**
     * Los ultimos movimientos.
     *
     * <p>Cuantos son un adelanto y cuantos la lista entera lo decide el
     * repositorio, que es quien sabe que la portada solo enseña unos pocos.
     * Recortar aqui seria tener la misma regla escrita en dos sitios.
     *
     * <p>Sin ninguno, la lista se va y queda solo el titulo: la frase de vacio no
     * hace falta, porque "Ultimos movimientos" sobre una lista vacia ya dice que
     * no ha pasado nada todavia.
     */
    private void pintarUltimos(@NonNull ResumenSuperadmin resumen) {
        boolean hay = !resumen.getUltimos().isEmpty();
        binding.saUltimos.setVisibility(hay ? View.VISIBLE : View.GONE);
        ultimos.submitList(hay ? resumen.getUltimos() : Collections.emptyList());
    }

    private void irA(@IdRes int destino) {
        Navigation.findNavController(requireView()).navigate(destino);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // El adaptador se suelta antes que el binding: si el RecyclerView
        // conservara las vistas, seguirian apuntando a un binding ya liberado.
        binding.saUltimos.setAdapter(null);
        ultimos = null;
        binding = null;
    }
}
