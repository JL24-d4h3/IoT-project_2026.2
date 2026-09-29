package org.iot.project.ui.superadmin.hoteles;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.iot.project.R;
import org.iot.project.core.UiState;
import org.iot.project.databinding.FragmentSuperadminAltaHotelBinding;
import org.iot.project.models.Hotel;
import org.iot.project.utils.InsetUtils;

/**
 * Registrar un hotel (RF-007).
 *
 * <p>El alta la pide el dueño por fuera de la aplicacion y la ejecuta el
 * superadministrador, que es quien responde por lo que entra en el catalogo. Al
 * terminar, el hotel queda <b>sin publicar y sin administrador</b>: el recorrido
 * sigue con asignarle quien lo lleve, que lo complete y que lo publique.
 *
 * <p>Se validan los campos aqui ademas de en el repositorio porque el error de
 * un formulario tiene que salir en el campo que lo causo, y porque volver del
 * repositorio con "ponle un nombre al hotel" deja al superadministrador sin
 * saber cual de los seis campos es. El repositorio lo comprueba de todas formas:
 * una pantalla se puede saltar.
 */
public class AltaHotelFragment extends Fragment {

    private FragmentSuperadminAltaHotelBinding binding;
    private AltaHotelViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflador, @Nullable ViewGroup contenedor,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSuperadminAltaHotelBinding.inflate(inflador, contenedor, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        InsetUtils.applyTopPadding(binding.saHeader);
        binding.saHeader.setTitulo(R.string.sa_titulo_alta_hotel);
        binding.saHeader.mostrarVolver(v -> salir());

        viewModel = new ViewModelProvider(this).get(AltaHotelViewModel.class);
        viewModel.getAlta().observe(getViewLifecycleOwner(), this::pintar);

        binding.saAltaGuardar.setOnClickListener(v -> guardar());
    }

    // ----------------------------------------------------------------- Guardar

    /**
     * Registra el hotel, si el formulario esta completo.
     *
     * <p>Se leen los seis campos antes de decidir, en vez de parar en el primero
     * que falle: estan todos a la vista y marcarlos de una vez evita el ir y
     * venir de guardar, corregir, guardar.
     *
     * <p>La direccion se pide aunque el repositorio no la exija: la direccion del
     * hotel viaja al detalle del cliente y al punto de recogida del traslado, y
     * un hotel sin ella no se puede enseñar entero. El alta es el unico momento
     * en que quien la sabe esta al telefono.
     */
    private void guardar() {
        limpiarErrores();

        String nombre = texto(binding.saAltaNombreCampo, binding.saAltaNombre,
                R.string.sa_alta_nombre_obligatorio);
        String ciudad = texto(binding.saAltaCiudadCampo, binding.saAltaCiudad,
                R.string.sa_alta_ciudad_obligatoria);
        String distrito = texto(binding.saAltaDistritoCampo, binding.saAltaDistrito,
                R.string.sa_alta_distrito_obligatorio);
        String direccion = texto(binding.saAltaDireccionCampo, binding.saAltaDireccion,
                R.string.sa_alta_direccion_obligatoria);
        Double latitud = coordenada(binding.saAltaLatitudCampo, binding.saAltaLatitud,
                R.string.sa_alta_latitud_obligatoria);
        Double longitud = coordenada(binding.saAltaLongitudCampo, binding.saAltaLongitud,
                R.string.sa_alta_longitud_obligatoria);

        if (nombre == null || ciudad == null || distrito == null || direccion == null
                || latitud == null || longitud == null) {
            return;
        }
        viewModel.registrar(nombre, ciudad, distrito, direccion, latitud, longitud);
    }

    private void limpiarErrores() {
        binding.saAltaNombreCampo.setError(null);
        binding.saAltaCiudadCampo.setError(null);
        binding.saAltaDistritoCampo.setError(null);
        binding.saAltaDireccionCampo.setError(null);
        binding.saAltaLatitudCampo.setError(null);
        binding.saAltaLongitudCampo.setError(null);
    }

    // ------------------------------------------------------ Lectura de campos

    /** El texto de un campo obligatorio, o {@code null} si esta vacio. */
    @Nullable
    private String texto(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                         @StringRes int errorVacio) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            campo.setError(getString(errorVacio));
            return null;
        }
        return valor;
    }

    /**
     * Una coordenada obligatoria.
     *
     * <p>Se admite el signo y la coma decimal: medio pais esta al sur del
     * ecuador, y la coma es lo que ofrece el teclado en español. Que las dos
     * coordenadas no sean (0, 0) lo comprueba el repositorio, que es donde vive
     * esa regla y donde se puede decir por que.
     */
    @Nullable
    private Double coordenada(@NonNull TextInputLayout campo, @NonNull TextInputEditText entrada,
                              @StringRes int errorVacio) {
        CharSequence contenido = entrada.getText();
        String valor = contenido != null ? contenido.toString().trim() : "";
        if (valor.isEmpty()) {
            campo.setError(getString(errorVacio));
            return null;
        }
        try {
            return Double.parseDouble(valor.replace(',', '.'));
        } catch (NumberFormatException e) {
            campo.setError(getString(R.string.sa_alta_coordenada_invalida));
            return null;
        }
    }

    // ------------------------------------------------------------------ Estado

    private void pintar(@Nullable UiState<Hotel> estado) {
        if (estado == null) {
            return;
        }
        switch (estado.getStatus()) {
            case LOADING:
                // El guardado tarda lo que tarda el repositorio, y un segundo
                // toque registraria el hotel dos veces.
                binding.saAltaGuardar.setEnabled(false);
                break;
            case SUCCESS:
                binding.saAltaGuardar.setEnabled(true);
                volverCon(estado.requireData());
                break;
            case EMPTY:
            case ERROR:
            default:
                binding.saAltaGuardar.setEnabled(true);
                avisar(estado.getMessage());
                // El aviso ya se leyo: si se queda ahi, volver a esta pantalla
                // —o girar el telefono— lo repetiria.
                viewModel.limpiar();
                break;
        }
    }

    /**
     * Vuelve a la lista dejando dicho que hotel se acaba de registrar.
     *
     * <p>El aviso no se da aqui: esta pantalla se va, y un aviso sobre una vista
     * que desaparece no lo lee nadie. Se deja escrito en la entrada de la lista,
     * que es quien tiene que enseñar el hotel recien creado y quien sabe como se
     * llama la cuenta que acaba de llegar.
     */
    private void volverCon(@NonNull Hotel hotel) {
        NavController controlador = Navigation.findNavController(requireView());
        NavBackStackEntry lista = controlador.getPreviousBackStackEntry();
        if (lista != null) {
            lista.getSavedStateHandle().set(
                    SuperadminHotelesFragment.CLAVE_HOTEL_REGISTRADO, hotel.getNombre());
        }
        controlador.popBackStack();
    }

    private void salir() {
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void avisar(@Nullable String mensaje) {
        if (mensaje == null || mensaje.isEmpty()) {
            return;
        }
        Snackbar.make(binding.getRoot(), mensaje, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
