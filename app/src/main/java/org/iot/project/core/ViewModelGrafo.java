package org.iot.project.core;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.fragment.NavHostFragment;

/**
 * ViewModel compartido por todas las pantallas de un grafo de navegacion.
 *
 * <p>La busqueda de §13 a §19 no cabe en una pantalla. El destino se elige en
 * una, las fechas en otra hoja, los filtros en otra, y los resultados viven en
 * la pantalla de busqueda: todas editan <em>la misma</em> consulta. Si cada una
 * tuviera su ViewModel, elegir fechas y volver dejaria los resultados sin
 * fechas, o habria que ir pasando el objeto por argumentos de navegacion
 * serializandolo en cada salto.
 *
 * <p>El dueno natural de ese estado es el grafo entero, que es lo que ofrece
 * {@code getViewModelStoreOwner(idDelGrafo)}: un unico ViewModel mientras el
 * grafo siga en la pila de navegacion, y olvidado cuando se sale del rol.
 *
 * <p>Se usa por identificador de grafo y no por fragmento padre porque las
 * hojas de dialogo no son hijas de la pantalla que las abre: son destinos
 * aparte, y {@code requireParentFragment()} no las alcanzaria.
 */
public final class ViewModelGrafo {

    private ViewModelGrafo() {
    }

    /**
     * El ViewModel del grafo, creandolo la primera vez.
     *
     * @param grafo identificador del grafo de navegacion, por ejemplo
     *              {@code R.id.nav_client}
     */
    @NonNull
    public static <T extends ViewModel> T de(@NonNull Fragment fragment,
                                             @IdRes int grafo,
                                             @NonNull Class<T> tipo) {
        ViewModelStoreOwner dueno =
                NavHostFragment.findNavController(fragment).getViewModelStoreOwner(grafo);
        return new ViewModelProvider(dueno).get(tipo);
    }
}
