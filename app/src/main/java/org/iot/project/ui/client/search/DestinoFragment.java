package org.iot.project.ui.client.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.iot.project.R;
import org.iot.project.core.ViewModelGrafo;
import org.iot.project.databinding.FragmentDestinoBinding;
import org.iot.project.ui.common.DestinoAdapter;
import org.iot.project.ui.common.DestinoItem;
import org.iot.project.utils.InsetUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eleccion del destino (§14).
 *
 * <p>Escribe en el {@link SearchViewModel} del grafo y vuelve: no devuelve el
 * destino por argumentos de navegacion, porque la busqueda es un objeto
 * compartido y pasarlo por el Bundle obligaria a serializarlo en cada salto.
 *
 * <p>Lo que ofrece es exactamente lo que la busqueda entiende —ciudades y
 * distritos (RF-017)—, para que elegir aqui y buscar alla no puedan discrepar.
 */
public class DestinoFragment extends Fragment {

    private FragmentDestinoBinding binding;
    private SearchViewModel viewModel;
    private DestinoAdapter adaptador;

    /** Texto que el usuario lleva escrito. Filtra la lista en cada tecla. */
    private String filtro = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDestinoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = ViewModelGrafo.de(this, R.id.nav_client, SearchViewModel.class);

        InsetUtils.applyTopPadding(binding.header);

        binding.header.setTitulo(R.string.titulo_destino);
        binding.header.mostrarVolver(v -> Navigation.findNavController(v).navigateUp());

        binding.campoDestino.setPista(R.string.destino_pista);
        binding.campoDestino.setOnQueryChangeListener(texto -> {
            filtro = texto;
            reconstruir();
        });

        binding.estadoSinResultados
                .conTitulo(R.string.destino_sin_resultados_titulo)
                .conMensaje(R.string.destino_sin_resultados);

        adaptador = new DestinoAdapter(this::elegir, viewModel::quitarReciente);
        binding.listaDestinos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listaDestinos.setAdapter(adaptador);

        // Se llega aqui para escribir: el campo arranca enfocado y con el
        // teclado puesto. Sin eso habria que tocar el campo antes de poder
        // hacer lo unico que se viene a hacer.
        binding.campoDestino.enfocar();

        viewModel.getRecientes().observe(getViewLifecycleOwner(), recientes -> reconstruir());
        reconstruir();
    }

    /**
     * Arma la lista con lo que corresponda al texto escrito.
     *
     * <p>Sin texto se proponen ciudades, que son los destinos que el usuario
     * reconoce de un vistazo, y se recuerdan sus busquedas. Con texto se
     * buscan ciudades <em>y</em> distritos, porque quien escribe "Miraflores"
     * esta nombrando un distrito y la busqueda lo acepta igual.
     */
    private void reconstruir() {
        if (binding == null) {
            return;
        }

        List<DestinoItem> filas = new ArrayList<>();
        List<String> recientes = viewModel.getRecientes().getValue();
        String buscado = filtro.trim().toLowerCase(Locale.ROOT);

        if (buscado.isEmpty()) {
            agregarRecientes(filas, recientes);
            agregarSeccion(filas, R.string.destino_sugerencias, viewModel.getCiudades());
        } else {
            // Con texto, el historial estorba: se busca, no se recuerda.
            List<String> coincidencias = new ArrayList<>();
            coincidencias.addAll(coinciden(viewModel.getCiudades(), buscado));
            coincidencias.addAll(coinciden(viewModel.getDistritos(), buscado));
            agregarSeccion(filas, R.string.destino_sugerencias, coincidencias);
        }

        adaptador.submitList(filas);

        boolean vacio = filas.isEmpty();
        binding.estadoSinResultados.setVisibility(vacio ? View.VISIBLE : View.GONE);
        binding.listaDestinos.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    private void agregarRecientes(@NonNull List<DestinoItem> filas, @Nullable List<String> recientes) {
        if (recientes == null || recientes.isEmpty()) {
            return;
        }
        filas.add(DestinoItem.encabezado(R.string.destino_recientes));
        for (String texto : recientes) {
            filas.add(DestinoItem.reciente(texto));
        }
    }

    /** Una seccion solo se dibuja si tiene filas: un titulo solo no dice nada. */
    private void agregarSeccion(@NonNull List<DestinoItem> filas, int tituloRes,
                                @NonNull List<String> destinos) {
        if (destinos.isEmpty()) {
            return;
        }
        filas.add(DestinoItem.encabezado(tituloRes));
        for (String destino : destinos) {
            filas.add(DestinoItem.sugerencia(destino));
        }
    }

    @NonNull
    private static List<String> coinciden(@NonNull List<String> destinos, @NonNull String buscado) {
        List<String> resultado = new ArrayList<>();
        for (String destino : destinos) {
            if (destino.toLowerCase(Locale.ROOT).contains(buscado)) {
                resultado.add(destino);
            }
        }
        return resultado;
    }

    private void elegir(@NonNull String destino) {
        viewModel.setDestino(destino);
        binding.campoDestino.ocultarTeclado();
        Navigation.findNavController(requireView()).navigateUp();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
