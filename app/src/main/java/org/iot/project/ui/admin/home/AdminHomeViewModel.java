package org.iot.project.ui.admin.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.SessionManager;
import org.iot.project.core.UiState;
import org.iot.project.models.ConversacionDeHotel;
import org.iot.project.models.ResumenHotel;

import java.util.List;

/**
 * Portada del administrador de hotel (§43).
 *
 * <p>Reune el resumen del hotel —que ya trae sus estadias en curso y sus
 * ingresos por servicios— con la bandeja de mensajes. Son dos consultas de dos
 * repositorios distintos porque el chat no pertenece al hotel: es su propia
 * conversacion, y mezclarlo en el resumen obligaria al hotel a saber de
 * mensajes.
 *
 * <p><b>Si una falla, falla la portada entera.</b> Es deliberado. La
 * alternativa era pintar cada bloque por su cuenta y dejar el que no llego en
 * blanco, pero un tablero con un hueco se lee como un cero: el administrador
 * veria "S/ 0 en servicios adicionales" cuando lo que pasa es que el reporte no
 * llego. Ante la duda, decir que no se pudo cargar es mas honesto que enseñar
 * un numero inventado, y el reintento esta a un toque.
 */
public class AdminHomeViewModel extends ViewModel {

    /** Lo que la portada muestra, ya reunido. */
    public static final class Contenido {

        @NonNull
        public final ResumenHotel resumen;

        /**
         * Bandeja del hotel, la mas reciente primero.
         *
         * <p>Viene ya cruzada con la estadia de cada conversacion: la portada
         * enseña quien escribe, y el nombre no esta en la conversacion.
         */
        @NonNull
        public final List<ConversacionDeHotel> conversaciones;

        /** Mensajes de clientes sin leer, sumando todas las conversaciones. */
        public final int mensajesSinLeer;

        Contenido(@NonNull ResumenHotel resumen, @NonNull List<ConversacionDeHotel> conversaciones,
                  int mensajesSinLeer) {
            this.resumen = resumen;
            this.conversaciones = conversaciones;
            this.mensajesSinLeer = mensajesSinLeer;
        }

        /** Si hay algo que atender en la bandeja. */
        public boolean hayMensajes() {
            return !conversaciones.isEmpty();
        }

        /**
         * Las conversaciones que la portada destaca, la mas reciente primero.
         *
         * <p>Solo unas pocas: la portada es un tablero, y una lista que crece
         * sin limite deja de serlo. El resto siguen enteras en la bandeja, a un
         * toque desde el bloque de mensajes.
         */
        @NonNull
        public List<ConversacionDeHotel> getConversacionesDestacadas() {
            return conversaciones.size() <= CONVERSACIONES_EN_PORTADA
                    ? conversaciones
                    : conversaciones.subList(0, CONVERSACIONES_EN_PORTADA);
        }

        /** Si hay mas conversaciones de las que la portada enseña. */
        public boolean hayMasConversaciones() {
            return conversaciones.size() > CONVERSACIONES_EN_PORTADA;
        }
    }

    /** Cuantas conversaciones enseña la portada; el resto quedan en la bandeja. */
    private static final int CONVERSACIONES_EN_PORTADA = 3;

    /** Cuantas consultas hay que reunir antes de poder pintar. */
    private static final int PIEZAS = 2;

    private final MutableLiveData<UiState<Contenido>> contenido = new MutableLiveData<>();

    @Nullable
    private ResumenHotel resumen;
    @Nullable
    private List<ConversacionDeHotel> conversaciones;

    /**
     * Identifica la tanda de peticiones en vuelo.
     *
     * <p>La portada se recarga sola al volver a ella, y esa recarga puede
     * cruzarse con una anterior: sin este numero, las dos tandas compartirian el
     * contador de piezas y la primera en llegar publicaria un tablero armado con
     * la mitad de una y la mitad de la otra.
     */
    private int tanda;

    private int recibidas;
    private boolean fallo;
    private boolean cargando;
    private boolean cargado;

    public LiveData<UiState<Contenido>> getContenido() {
        return contenido;
    }

    /** Hotel administrado por la sesion, o {@code null} si el rol no administra. */
    @Nullable
    public String getHotelId() {
        return SessionManager.getHotelAdministrado();
    }

    /**
     * Carga con esqueleto. Es la primera y la del reintento.
     *
     * <p>Al rotar, el ViewModel sobrevive con los datos ya reunidos: volver a
     * pasar por el esqueleto dejaria la pantalla en blanco un instante sin
     * motivo.
     */
    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<Contenido> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargarDeVerdad();
    }

    public void reintentar() {
        cargando = false;
        cargarDeVerdad();
    }

    /**
     * Recarga sin esqueleto, para lo que ya esta en pantalla.
     *
     * <p>El tablero se desactualiza solo: abrir un chat desde la portada marca
     * sus mensajes como leidos (RF-068) y volver atras tiene que apagar el
     * contador de esa fila. Volver a pasar por el esqueleto para eso dejaria el
     * tablero en blanco cada vez que el administrador entra y sale de una
     * conversacion.
     *
     * <p>No hace nada si no hay un tablero que conservar: sin una carga previa
     * —o con la pantalla en error— no hay nada que refrescar, y el error tiene
     * su propio reintento, que sí enseña el esqueleto porque no hay nada debajo.
     */
    public void refrescarEnSilencio() {
        UiState<Contenido> actual = contenido.getValue();
        if (cargando || actual == null || !actual.isSuccess()) {
            return;
        }
        pedir();
    }

    private void cargarDeVerdad() {
        cargando = true;
        cargado = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    /**
     * Lanza las dos consultas de la tanda en curso.
     *
     * <p>No toca el estado de carga: lo llaman tanto la carga con esqueleto como
     * la recarga silenciosa, y solo la primera tiene que pasar por el esqueleto.
     */
    private void pedir() {
        String hotelId = getHotelId();
        if (hotelId == null) {
            cargando = false;
            // Un administrador sin hotel no es un error: su cuenta esta bien y
            // lo que falta es un paso ajeno (RF-008). Se publica como vacio para
            // que la portada lo explique en lugar de ofrecer un reintento que no
            // arregla nada.
            contenido.setValue(UiState.empty());
            return;
        }

        int mia = ++tanda;
        recibidas = 0;
        fallo = false;
        resumen = null;
        conversaciones = null;

        ServiceLocator.gestion().resumen(hotelId, new ResultCallback<ResumenHotel>() {
            @Override
            public void onExito(@NonNull ResumenHotel dato) {
                if (mia != tanda) {
                    return;
                }
                resumen = dato;
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

        ServiceLocator.chats().conversacionesDeHotel(hotelId,
                new ResultCallback<List<ConversacionDeHotel>>() {
                    @Override
                    public void onExito(@NonNull List<ConversacionDeHotel> datos) {
                        if (mia != tanda) {
                            return;
                        }
                        conversaciones = datos;
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
     * Suma una pieza y publica cuando estan todas.
     *
     * <p>El contador se ignora despues de un fallo: si una consulta fallo, las
     * que lleguen tarde no deben resucitar la pantalla a medias.
     */
    private void piezaRecibida() {
        if (fallo) {
            return;
        }
        recibidas++;
        if (recibidas < PIEZAS) {
            return;
        }

        // Se copian a locales para que lo que se publica sean valores no nulos
        // de verdad y no campos que "ya deberian" estarlo.
        ResumenHotel resumenListo = resumen;
        List<ConversacionDeHotel> bandeja = conversaciones;
        if (resumenListo == null || bandeja == null) {
            // No deberia ocurrir: llegar aqui significa que las dos piezas
            // contestaron bien. Se comprueba igualmente porque el resultado de
            // no comprobarlo seria un tablero en blanco sin explicacion.
            fallar("No pudimos cargar el resumen de tu hotel.");
            return;
        }

        cargando = false;
        contenido.setValue(UiState.success(
                new Contenido(resumenListo, bandeja, contarSinLeer(bandeja))));
    }

    private void fallar(@NonNull String mensaje) {
        if (fallo) {
            return;
        }
        fallo = true;
        cargando = false;
        contenido.setValue(UiState.<Contenido>error(mensaje));
    }

    /** Mensajes del cliente que el hotel todavia no ha abierto. */
    private static int contarSinLeer(@NonNull List<ConversacionDeHotel> bandeja) {
        int sinLeer = 0;
        for (ConversacionDeHotel conversacion : bandeja) {
            // De que lado se cuenta lo decide el propio modelo: esta bandeja es
            // la del hotel, y al hotel le cuentan los mensajes del cliente.
            sinLeer += conversacion.getSinLeer();
        }
        return sinLeer;
    }
}
