package org.iot.project.models;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Criterios de busqueda de hoteles (§9). Es el objeto que viaja entre Home,
 * Destino, Fechas, Huespedes y Filtros sin que ninguna pantalla pierda el
 * estado de las demas.
 */
public class SearchQuery {

    private String destino;
    private LocalDate fechaEntrada;
    private LocalDate fechaSalida;
    private int adultos = 2;
    private int ninos = 0;
    private int habitaciones = 1;

    /** Tipo de habitacion pedido (§18). Nulo mientras no se elija ninguno. */
    private TipoHabitacion tipoHabitacion;

    private double precioMin;
    private double precioMax;
    private float ratingMinimo;

    /** Orden de los resultados (§19). Recomendados mientras no se elija otro. */
    private OrdenBusqueda orden = OrdenBusqueda.RECOMENDADOS;

    /** Servicios del catalogo global usados como filtro (regla 21). */
    private final Set<String> serviciosSeleccionados = new LinkedHashSet<>();

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public LocalDate getFechaEntrada() {
        return fechaEntrada;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    /**
     * Fija el rango de fechas. Si la salida no es posterior a la entrada, se
     * corrige a entrada + 1 noche en vez de dejar el rango invalido, que es la
     * validacion que pide §53.
     */
    public void setFechas(LocalDate entrada, LocalDate salida) {
        this.fechaEntrada = entrada;
        this.fechaSalida = (salida == null || !salida.isAfter(entrada))
                ? entrada.plusDays(1)
                : salida;
    }

    /** Deja la busqueda sin fechas. */
    public void limpiarFechas() {
        this.fechaEntrada = null;
        this.fechaSalida = null;
    }

    public int getAdultos() {
        return adultos;
    }

    public void setAdultos(int adultos) {
        this.adultos = Math.max(1, adultos);
    }

    public int getNinos() {
        return ninos;
    }

    public void setNinos(int ninos) {
        this.ninos = Math.max(0, ninos);
    }

    public int getHabitaciones() {
        return habitaciones;
    }

    public void setHabitaciones(int habitaciones) {
        this.habitaciones = Math.max(1, habitaciones);
    }

    /** Tipo de habitacion pedido, o {@code null} si no se filtra por tipo. */
    public TipoHabitacion getTipoHabitacion() {
        return tipoHabitacion;
    }

    public void setTipoHabitacion(TipoHabitacion tipoHabitacion) {
        this.tipoHabitacion = tipoHabitacion;
    }

    public boolean hasTipoHabitacion() {
        return tipoHabitacion != null;
    }

    public double getPrecioMin() {
        return precioMin;
    }

    public double getPrecioMax() {
        return precioMax;
    }

    public void setRangoPrecio(double min, double max) {
        this.precioMin = min;
        this.precioMax = max;
    }

    public float getRatingMinimo() {
        return ratingMinimo;
    }

    public void setRatingMinimo(float ratingMinimo) {
        this.ratingMinimo = ratingMinimo;
    }

    /** Orden de los resultados (§19). Nunca es nulo. */
    public OrdenBusqueda getOrden() {
        return orden;
    }

    public void setOrden(OrdenBusqueda orden) {
        this.orden = (orden == null) ? OrdenBusqueda.RECOMENDADOS : orden;
    }

    public Set<String> getServiciosSeleccionados() {
        return Collections.unmodifiableSet(serviciosSeleccionados);
    }

    /** Devuelve true si el servicio quedo seleccionado tras el toggle. */
    public boolean toggleServicio(String serviceId) {
        if (serviciosSeleccionados.contains(serviceId)) {
            serviciosSeleccionados.remove(serviceId);
            return false;
        }
        serviciosSeleccionados.add(serviceId);
        return true;
    }

    public boolean hasServiciosSeleccionados() {
        return !serviciosSeleccionados.isEmpty();
    }

    public int getNumHuespedes() {
        return adultos + ninos;
    }

    public long getNumNoches() {
        if (fechaEntrada == null || fechaSalida == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(fechaEntrada, fechaSalida);
    }

    public boolean hasFechas() {
        return fechaEntrada != null && fechaSalida != null;
    }

    /** "2 adultos · 1 nino · 1 habitacion" — el resumen del selector (§16). */
    public String getResumenHuespedes() {
        return resumenHuespedes(adultos, ninos, habitaciones);
    }

    /**
     * El mismo resumen a partir de los tres numeros sueltos.
     *
     * <p>Existe para que el selector de huespedes pueda enseñar el resumen de
     * lo que el usuario esta tocando sin tener que fabricar una busqueda entera
     * solo para formatear un texto.
     */
    public static String resumenHuespedes(int adultos, int ninos, int habitaciones) {
        StringBuilder sb = new StringBuilder();
        sb.append(adultos).append(adultos == 1 ? " adulto" : " adultos");
        if (ninos > 0) {
            sb.append(" · ").append(ninos).append(ninos == 1 ? " niño" : " niños");
        }
        sb.append(" · ").append(habitaciones)
                .append(habitaciones == 1 ? " habitación" : " habitaciones");
        return sb.toString();
    }

    /** true cuando ya hay destino y fechas: es lo que habilita el boton Buscar. */
    public boolean isReadyToSearch() {
        return destino != null && !destino.trim().isEmpty() && hasFechas();
    }

    public int getNumFiltrosActivos() {
        int total = serviciosSeleccionados.size();
        if (precioMax > 0) {
            total++;
        }
        if (ratingMinimo > 0) {
            total++;
        }
        if (tipoHabitacion != null) {
            total++;
        }
        return total;
    }

    public void limpiarFiltros() {
        serviciosSeleccionados.clear();
        precioMin = 0;
        precioMax = 0;
        ratingMinimo = 0;
        tipoHabitacion = null;
    }

    /**
     * Copia independiente de la consulta.
     *
     * <p>Existe para que la hoja de filtros pueda editar sin comprometer nada:
     * trabaja sobre su copia y solo la vuelca en la busqueda vigente cuando el
     * usuario confirma (§18). Sin copia, cerrar la hoja deslizando el dedo
     * dejaria aplicados los filtros que el usuario estaba probando.
     */
    @NonNull
    public SearchQuery copy() {
        SearchQuery copia = new SearchQuery();
        copia.destino = destino;
        copia.fechaEntrada = fechaEntrada;
        copia.fechaSalida = fechaSalida;
        copia.adultos = adultos;
        copia.ninos = ninos;
        copia.habitaciones = habitaciones;
        copia.tipoHabitacion = tipoHabitacion;
        copia.precioMin = precioMin;
        copia.precioMax = precioMax;
        copia.ratingMinimo = ratingMinimo;
        copia.orden = orden;
        copia.serviciosSeleccionados.addAll(serviciosSeleccionados);
        return copia;
    }
}
