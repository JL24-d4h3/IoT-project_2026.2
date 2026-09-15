package org.iot.project.ui.client.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.OrdenBusqueda;
import org.iot.project.models.Pagina;
import org.iot.project.models.SearchQuery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estado de la busqueda: la consulta, los resultados y las paginas (§13 a §19).
 *
 * <p>Vive en el grafo de navegacion del cliente, no en la pantalla de busqueda
 * (vease {@link org.iot.project.core.ViewModelGrafo}). Es lo que permite que
 * elegir el destino en una pantalla, las fechas en una hoja y los filtros en
 * otra acabe produciendo una sola busqueda coherente.
 *
 * <p>Tiene dos modos y los distingue explicitamente: <b>formulario</b>, cuando
 * el usuario esta componiendo la busqueda, y <b>resultados</b>, cuando ya la
 * lanzo. No es un detalle de la vista: en el formulario el boton dice "Buscar
 * hoteles" y aqui abajo esta el resumen con los filtros, y confundir los dos
 * modos es lo que hace que un cambio de fecha lance una busqueda a medias.
 */
public class SearchViewModel extends ViewModel {

    /**
     * Cuantas busquedas recientes se recuerdan.
     *
     * <p>Cuatro: mas que eso deja de ser un atajo y se convierte en un
     * historial que hay que leer. Se pierden al cerrar la aplicacion, porque
     * sin backend no hay donde guardarlas.
     */
    private static final int MAX_RECIENTES = 4;

    private final MutableLiveData<SearchQuery> consulta =
            new MutableLiveData<>(new SearchQuery());
    private final MutableLiveData<Boolean> enResultados = new MutableLiveData<>(false);
    private final MutableLiveData<UiState<List<Hotel>>> resultados = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cargandoMas = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hayMas = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> recientes =
            new MutableLiveData<>(new ArrayList<>());

    /** Resultados acumulados de todas las paginas pedidas. */
    private final List<Hotel> acumulado = new ArrayList<>();

    private int paginaActual = 0;

    /** Evita que un segundo toque en "Ver más" dispare dos peticiones. */
    private boolean pidiendo = false;

    // ------------------------------------------------------------------
    //  Estado
    // ------------------------------------------------------------------

    @NonNull
    public LiveData<SearchQuery> getConsulta() {
        return consulta;
    }

    /** true cuando ya se lanzo una busqueda y toca enseñar la lista. */
    @NonNull
    public LiveData<Boolean> getEnResultados() {
        return enResultados;
    }

    @NonNull
    public LiveData<UiState<List<Hotel>>> getResultados() {
        return resultados;
    }

    @NonNull
    public LiveData<Boolean> getCargandoMas() {
        return cargandoMas;
    }

    @NonNull
    public LiveData<Boolean> getHayMas() {
        return hayMas;
    }

    /** Textos de las busquedas recientes, la mas nueva primero. */
    @NonNull
    public LiveData<List<String>> getRecientes() {
        return recientes;
    }

    /**
     * Destinos que el usuario puede elegir: ciudades y distritos.
     *
     * <p>Sale del repositorio y no de una lista escrita a mano porque el
     * selector tiene que ofrecer exactamente lo que la busqueda entiende
     * (RF-017 compara el destino contra ciudad y contra distrito). Un destino
     * propuesto que la busqueda no reconoce llevaria a una pantalla de "sin
     * resultados" sin explicar por que.
     *
     * <p>Se pide de forma sincrona: es una tabla de referencia que la
     * aplicacion ya tiene en memoria, no una consulta de red.
     */
    @NonNull
    public List<String> getCiudades() {
        return ServiceLocator.hoteles().ciudadesDisponibles();
    }

    /** Distritos donde hay alojamiento, para el buscador de destino. */
    @NonNull
    public List<String> getDistritos() {
        return ServiceLocator.hoteles().distritosDisponibles();
    }

    // ------------------------------------------------------------------
    //  Edicion de la consulta
    // ------------------------------------------------------------------

    public void setDestino(@Nullable String destino) {
        exigirConsulta().setDestino(destino);
        registrarReciente(destino);
        notificarConsulta();
    }

    public void setFechas(@Nullable LocalDate entrada, @Nullable LocalDate salida) {
        SearchQuery actual = exigirConsulta();
        if (entrada == null) {
            actual.limpiarFechas();
        } else {
            actual.setFechas(entrada, salida);
        }
        notificarConsulta();
    }

    public void setHuespedes(int adultos, int ninos, int habitaciones) {
        SearchQuery actual = exigirConsulta();
        actual.setAdultos(adultos);
        actual.setNinos(ninos);
        actual.setHabitaciones(habitaciones);
        notificarConsulta();
    }

    /**
     * Vuelca los filtros de una consulta editada aparte sobre la vigente.
     *
     * <p>Solo se copian los filtros: el destino, las fechas y los huespedes no
     * se tocan porque la hoja de filtros no los edita, y reemplazar la consulta
     * entera los borraria.
     *
     * <p>Si ya se estaban viendo resultados, se vuelven a pedir. El boton de la
     * hoja dice "Ver resultados", asi que el usuario espera verlos al cerrarse.
     */
    public void aplicarFiltros(@NonNull SearchQuery borrador) {
        SearchQuery actual = exigirConsulta();
        actual.limpiarFiltros();
        actual.setRangoPrecio(borrador.getPrecioMin(), borrador.getPrecioMax());
        actual.setRatingMinimo(borrador.getRatingMinimo());
        actual.setTipoHabitacion(borrador.getTipoHabitacion());
        for (String serviceId : borrador.getServiciosSeleccionados()) {
            actual.toggleServicio(serviceId);
        }
        notificarConsulta();
        refrescarSiHayResultados();
    }

    /** Quita todos los filtros. Es la accion del estado vacio de resultados. */
    public void limpiarFiltros() {
        exigirConsulta().limpiarFiltros();
        notificarConsulta();
        refrescarSiHayResultados();
    }

    public void setOrden(@NonNull OrdenBusqueda orden) {
        exigirConsulta().setOrden(orden);
        notificarConsulta();
        refrescarSiHayResultados();
    }

    /**
     * Vuelve al formulario conservando lo escrito.
     *
     * <p>No se limpia la consulta: editar la busqueda es retocarla, no
     * empezarla de cero. Para empezar de cero esta borrar el destino.
     */
    public void volverAlFormulario() {
        enResultados.setValue(false);
        resultados.setValue(null);
        acumulado.clear();
        paginaActual = 0;
        pidiendo = false;
        cargandoMas.setValue(false);
        hayMas.setValue(false);
    }

    // ------------------------------------------------------------------
    //  Busqueda
    // ------------------------------------------------------------------

    /** Primera pagina. Es lo que dispara el boton "Buscar hoteles". */
    public void buscar() {
        if (!exigirConsulta().isReadyToSearch()) {
            // La pantalla deshabilita el boton, pero el estado no puede
            // depender de que la vista se acuerde: una busqueda sin destino ni
            // fechas devolveria el catalogo entero y no es lo que se pidio.
            return;
        }
        enResultados.setValue(true);
        acumulado.clear();
        paginaActual = 0;
        cargarPagina(0, false);
    }

    /** Siguiente pagina, anexada a lo que ya hay (§17). */
    public void verMas() {
        if (pidiendo || !Boolean.TRUE.equals(hayMas.getValue())) {
            return;
        }
        cargarPagina(paginaActual + 1, true);
    }

    /** Reintento tras un error: se vuelve a pedir la primera pagina. */
    public void reintentar() {
        acumulado.clear();
        paginaActual = 0;
        cargarPagina(0, false);
    }

    private void cargarPagina(int numero, boolean anexar) {
        pidiendo = true;

        if (anexar) {
            cargandoMas.setValue(true);
        } else {
            resultados.setValue(UiState.loading());
        }

        ServiceLocator.hoteles().buscar(exigirConsulta(), numero,
                new ResultCallback<Pagina<Hotel>>() {
                    @Override
                    public void onExito(@NonNull Pagina<Hotel> datos) {
                        pidiendo = false;
                        cargandoMas.setValue(false);
                        paginaActual = datos.getNumero();
                        hayMas.setValue(datos.isHayMas());
                        acumulado.addAll(datos.getItems());

                        // Una lista vacia no es un error: es un estado vacio, y
                        // la pantalla lo dibuja distinto (§50). Con paginas
                        // anteriores ya cargadas, el vacio se decide sobre el
                        // acumulado, no sobre la pagina que acaba de llegar.
                        resultados.setValue(acumulado.isEmpty()
                                ? UiState.empty()
                                : UiState.success(new ArrayList<>(acumulado)));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        pidiendo = false;
                        cargandoMas.setValue(false);
                        hayMas.setValue(false);

                        // Si ya habia resultados en pantalla, un fallo al pedir
                        // mas no puede borrarlos: el usuario perderia lo que
                        // estaba mirando por un fallo de la pagina siguiente.
                        if (acumulado.isEmpty()) {
                            resultados.setValue(UiState.error(mensaje));
                        }
                    }
                });
    }

    private void refrescarSiHayResultados() {
        if (Boolean.TRUE.equals(enResultados.getValue())) {
            buscar();
        }
    }

    // ------------------------------------------------------------------
    //  Interno
    // ------------------------------------------------------------------

    @NonNull
    private SearchQuery exigirConsulta() {
        SearchQuery actual = consulta.getValue();
        if (actual == null) {
            actual = new SearchQuery();
            consulta.setValue(actual);
        }
        return actual;
    }

    /**
     * Avisa a la pantalla de que la consulta cambio.
     *
     * <p>Vuelve a publicar la <em>misma</em> instancia a proposito. La consulta
     * es un formulario que se edita en el sitio —el usuario abre la hoja de
     * fechas y le pone fecha—, no un valor inmutable que se reemplace. LiveData
     * no compara el valor nuevo con el anterior: incrementa su version y avisa,
     * asi que republicar la misma instancia es la forma de decir "esto de
     * dentro cambio".
     */
    private void notificarConsulta() {
        consulta.setValue(consulta.getValue());
    }

    /** Guarda un destino al frente del historial, sin repetidos. */
    private void registrarReciente(@Nullable String destino) {
        if (destino == null || destino.trim().isEmpty()) {
            return;
        }
        List<String> lista = new ArrayList<>(
                recientes.getValue() != null ? recientes.getValue() : Collections.emptyList());
        lista.remove(destino);
        lista.add(0, destino);
        while (lista.size() > MAX_RECIENTES) {
            lista.remove(lista.size() - 1);
        }
        recientes.setValue(lista);
    }

    /** Quita una busqueda reciente. Lo pide la pantalla de destino (§14). */
    public void quitarReciente(@NonNull String destino) {
        List<String> lista = new ArrayList<>(
                recientes.getValue() != null ? recientes.getValue() : Collections.emptyList());
        if (lista.remove(destino)) {
            recientes.setValue(lista);
        }
    }
}
