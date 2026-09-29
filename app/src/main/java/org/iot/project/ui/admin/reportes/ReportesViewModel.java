package org.iot.project.ui.admin.reportes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.IngresoPorServicio;
import org.iot.project.models.Periodicidad;
import org.iot.project.models.PeriodoDeVentas;

import java.util.List;

/**
 * Estado de la pantalla de reportes del hotel (§45, RF-055 a RF-061).
 *
 * <p>Reune los dos reportes de la seccion: las reservas y ventas agrupadas por
 * periodo (RF-055 a RF-058) y los ingresos por servicios adicionales (RF-060,
 * RF-061). Son dos consultas porque son dos preguntas distintas, y la pantalla
 * las enseña en dos bloques separados.
 *
 * <p><b>Las dos se piden siempre, tambien al cambiar de periodo.</b> El reporte
 * de servicios no depende de la granularidad —se calcula sobre toda la historia
 * del hotel—, asi que volver a pedirlo es trabajo redundante; a cambio, la carga
 * tiene un solo camino en vez de dos. Con un camino por cada pieza habria que
 * llevar la cuenta de cual esta en vuelo, y un reporte a medias no se puede
 * enseñar: el bloque que faltara se leeria como un cero.
 */
public class ReportesViewModel extends ViewModel {

    /** Lo que la pantalla pinta: las dos listas y las cifras de encabezado. */
    public static final class Reporte {

        @NonNull
        public final Periodicidad periodicidad;

        /** Una fila por periodo, del mas reciente al mas antiguo. */
        @NonNull
        public final List<PeriodoDeVentas> periodos;

        /** Ingresos por servicio, de menor a mayor monto (RF-061). */
        @NonNull
        public final List<IngresoPorServicio> ingresos;

        /** Reservas sumadas en todos los periodos. */
        public final int reservas;

        /** Noches vendidas en esas reservas. */
        public final long noches;

        /** Lo que facturan, con sus consumos incluidos. */
        public final double ventas;

        /** Lo que suman los servicios adicionales, que es parte de lo anterior. */
        public final double servicios;

        Reporte(@NonNull Periodicidad periodicidad,
                @NonNull List<PeriodoDeVentas> periodos,
                @NonNull List<IngresoPorServicio> ingresos) {
            this.periodicidad = periodicidad;
            this.periodos = periodos;
            this.ingresos = ingresos;

            int cuantas = 0;
            long nochesVendidas = 0;
            double facturado = 0d;
            for (PeriodoDeVentas periodo : periodos) {
                cuantas += periodo.getReservas();
                nochesVendidas += periodo.getNoches();
                facturado += periodo.getMonto();
            }
            this.reservas = cuantas;
            this.noches = nochesVendidas;
            this.ventas = facturado;

            double porServicios = 0d;
            for (IngresoPorServicio ingreso : ingresos) {
                porServicios += ingreso.getMontoTotal();
            }
            this.servicios = porServicios;
        }

        /**
         * Si hay servicios adicionales que reportar.
         *
         * <p>Un hotel puede tener ventas y ningun servicio adicional cobrado: no
         * es un error ni una pantalla vacia, es una seccion sin filas.
         */
        public boolean hayIngresos() {
            return !ingresos.isEmpty();
        }
    }

    /** Cuantas consultas hay que reunir antes de poder pintar. */
    private static final int PIEZAS = 2;

    private final MutableLiveData<UiState<Reporte>> reporte = new MutableLiveData<>();

    /**
     * Granularidad con la que se esta calculando el reporte.
     *
     * <p>Vive aqui y no en el boton marcado porque es lo que sobrevive a un giro
     * de pantalla: al reconstruirse la vista, el selector se vuelve a dibujar a
     * partir de este valor, y asi el boton marcado y el reporte que se enseña
     * debajo no pueden acabar diciendo cosas distintas.
     *
     * <p>Por defecto, mensual: es el periodo que un hotel mira sin que nadie se
     * lo pida. El dia es demasiado corto para ver una tendencia y el año
     * demasiado largo para verla cambiar.
     */
    @NonNull
    private Periodicidad periodicidad = Periodicidad.MES;

    @Nullable
    private List<PeriodoDeVentas> periodos;
    @Nullable
    private List<IngresoPorServicio> ingresos;

    private int recibidas;
    private boolean fallo;
    private boolean cargado;

    /**
     * Identifica la tanda de peticiones en vuelo.
     *
     * <p>Cambiar de periodo con una carga a medias dejaria dos respuestas
     * compitiendo por el mismo contador, y la primera en llegar publicaria un
     * reporte con la mitad de los datos. Cada respuesta comprueba que sigue
     * siendo la suya antes de tocar nada.
     */
    private int tanda;

    public LiveData<UiState<Reporte>> getReporte() {
        return reporte;
    }

    /** Hotel administrado por la sesion, o {@code null} si el rol no administra. */
    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    /** La granularidad que el selector tiene que mostrar marcada. */
    @NonNull
    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        pedirTodo();
    }

    /** Cambia la granularidad del reporte y lo vuelve a calcular. */
    public void cambiarPeriodicidad(@NonNull Periodicidad nueva) {
        if (nueva == periodicidad) {
            return;
        }
        periodicidad = nueva;
        pedirTodo();
    }

    /** Reintenta la carga completa tras un error. */
    public void reintentar() {
        pedirTodo();
    }

    private void pedirTodo() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            fallo = true;
            reporte.setValue(UiState.<Reporte>error("Todavía no tienes un hotel asignado."));
            return;
        }

        fallo = false;
        recibidas = 0;
        periodos = null;
        ingresos = null;
        reporte.setValue(UiState.loading());

        final int mia = ++tanda;

        ServiceLocator.gestion().ventas(hotelId, periodicidad,
                new ResultCallback<List<PeriodoDeVentas>>() {
                    @Override
                    public void onExito(@NonNull List<PeriodoDeVentas> datos) {
                        if (mia != tanda) {
                            return;
                        }
                        periodos = datos;
                        piezaRecibida();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (mia != tanda) {
                            return;
                        }
                        fallar(mensaje);
                    }
                });

        ServiceLocator.gestion().ingresosPorServicios(hotelId,
                new ResultCallback<List<IngresoPorServicio>>() {
                    @Override
                    public void onExito(@NonNull List<IngresoPorServicio> datos) {
                        if (mia != tanda) {
                            return;
                        }
                        ingresos = datos;
                        piezaRecibida();
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        if (mia != tanda) {
                            return;
                        }
                        fallar(mensaje);
                    }
                });
    }

    /**
     * Suma una pieza y publica cuando estan las dos.
     *
     * <p>El contador se ignora despues de un fallo: si una consulta fallo, la
     * que llegue tarde no debe resucitar la pantalla a medias.
     */
    private void piezaRecibida() {
        if (fallo) {
            return;
        }
        recibidas++;
        if (recibidas < PIEZAS) {
            return;
        }

        List<PeriodoDeVentas> listaPeriodos = periodos;
        List<IngresoPorServicio> listaIngresos = ingresos;
        if (listaPeriodos == null || listaIngresos == null) {
            // No deberia ocurrir: llegar aqui significa que las dos piezas
            // contestaron bien. Se comprueba igualmente porque el resultado de
            // no comprobarlo seria una pantalla en blanco sin explicacion.
            fallar("No pudimos armar el reporte de tu hotel.");
            return;
        }

        // Sin periodos no hay nada que reportar. Y si no hay periodos tampoco
        // hay ingresos: el reporte de servicios solo cuenta reservas que
        // generan ingreso, las mismas que forman los periodos.
        reporte.setValue(listaPeriodos.isEmpty()
                ? UiState.<Reporte>empty()
                : UiState.success(new Reporte(periodicidad, listaPeriodos, listaIngresos)));
    }

    private void fallar(@NonNull String mensaje) {
        if (fallo) {
            return;
        }
        fallo = true;
        reporte.setValue(UiState.<Reporte>error(mensaje));
    }
}
