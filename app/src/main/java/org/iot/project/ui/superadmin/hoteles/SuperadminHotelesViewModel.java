package org.iot.project.ui.superadmin.hoteles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.ServiceLocator;
import org.iot.project.core.UiState;
import org.iot.project.models.Hotel;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hoteles de la plataforma (RF-007, RF-008).
 *
 * <p>La lista no trae el nombre de quien administra cada hotel: el hotel guarda
 * un identificador y nada mas. Asi que son dos consultas —los hoteles y las
 * cuentas de administrador— y una fila se publica cuando han llegado las dos.
 *
 * <p>El filtro vive aqui y no en el repositorio, igual que en las otras dos
 * listas del panel: es mirar de nuevo una lista que ya esta en memoria, y
 * hacerlo pasar por el repositorio costaria la latencia simulada en cada toque
 * de chip.
 */
public class SuperadminHotelesViewModel extends ViewModel {

    /** Consultas que hay que reunir antes de poder publicar la lista. */
    private static final int PIEZAS = 2;

    /** Un hotel con el nombre de quien lo administra, ya cruzado. */
    public static final class FilaHotel {

        @NonNull
        public final Hotel hotel;

        /**
         * Nombre del administrador asignado y activo, o {@code null}.
         *
         * <p>Un nulo con {@link #sinAsignar} en falso no significa "no tiene
         * nadie": significa que la cuenta asignada ya no esta activa.
         */
        @Nullable
        public final String administrador;

        /** Si al hotel todavia le falta el paso de RF-008. */
        public final boolean sinAsignar;

        FilaHotel(@NonNull Hotel hotel, @Nullable String administrador, boolean sinAsignar) {
            this.hotel = hotel;
            this.administrador = administrador;
            this.sinAsignar = sinAsignar;
        }
    }

    /** Si el filtro enseña solo los que todavia no se ofrecen al cliente. */
    private boolean soloSinPublicar;

    private final MutableLiveData<UiState<List<FilaHotel>>> contenido = new MutableLiveData<>();

    private List<Hotel> hoteles = Collections.emptyList();
    private List<User> administradores = Collections.emptyList();

    private int tanda;
    private int recibidas;
    private boolean fallo;
    private boolean cargando;

    public LiveData<UiState<List<FilaHotel>>> getContenido() {
        return contenido;
    }

    /**
     * Que filtro esta puesto.
     *
     * <p>La pantalla lo necesita para elegir que decir cuando no hay nada que
     * enseñar: no es lo mismo "no queda ninguno sin publicar" que "no hay
     * hoteles registrados", y el filtro es quien decide cual de las dos.
     */
    public boolean isSoloSinPublicar() {
        return soloSinPublicar;
    }

    public void cargar() {
        if (cargando) {
            return;
        }
        UiState<List<FilaHotel>> actual = contenido.getValue();
        if (actual != null && actual.isSuccess()) {
            return;
        }
        cargando = true;
        contenido.setValue(UiState.loading());
        pedir();
    }

    public void reintentar() {
        cargando = false;
        cargar();
    }

    /**
     * Recarga sin esqueleto, al volver a la pantalla.
     *
     * <p>Hace falta porque la barra inferior guarda el estado de cada seccion:
     * volver a Hoteles devuelve el mismo ViewModel con la lista de la ultima
     * visita, y desde entonces puede haber pasado lo que se hace en las otras
     * pantallas del panel —dar de alta un hotel, publicarlo o retirarlo desde su
     * ficha—. Sin esto, el hotel recien registrado no apareceria hasta cerrar la
     * aplicacion.
     *
     * <p>El filtro no se toca: se vuelve a pedir y se vuelve a aplicar, que es lo
     * que el usuario dejo puesto.
     */
    public void refrescarEnSilencio() {
        UiState<List<FilaHotel>> actual = contenido.getValue();
        if (cargando || actual == null || !(actual.isSuccess() || actual.isEmpty())) {
            return;
        }
        cargando = true;
        pedir();
    }

    private void pedir() {
        int mia = ++tanda;
        recibidas = 0;
        fallo = false;
        hoteles = Collections.emptyList();
        administradores = Collections.emptyList();

        ServiceLocator.superadmin().hoteles(new ResultCallback<List<Hotel>>() {
            @Override
            public void onExito(@NonNull List<Hotel> datos) {
                if (mia != tanda) {
                    return;
                }
                hoteles = datos;
                pieza();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == tanda) {
                    fallar(mensaje);
                }
            }
        });

        ServiceLocator.superadmin().administradores(new ResultCallback<List<User>>() {
            @Override
            public void onExito(@NonNull List<User> datos) {
                if (mia != tanda) {
                    return;
                }
                administradores = datos;
                pieza();
            }

            @Override
            public void onError(@NonNull String mensaje) {
                if (mia == tanda) {
                    fallar(mensaje);
                }
            }
        });
    }

    /** Una de las dos consultas ya llego. */
    private void pieza() {
        if (fallo) {
            return;
        }
        recibidas++;
        if (recibidas < PIEZAS) {
            return;
        }
        cargando = false;
        publicar();
    }

    /** El filtro de la lista: todos, o solo los que no se ofrecen al cliente. */
    public void filtrar(boolean soloSinPublicar) {
        this.soloSinPublicar = soloSinPublicar;
        publicar();
    }

    private void publicar() {
        List<FilaHotel> visibles = new ArrayList<>();
        for (Hotel hotel : hoteles) {
            if (!soloSinPublicar || !hotel.isPublicado()) {
                visibles.add(new FilaHotel(hotel,
                        Administradores.nombreDe(hotel, administradores),
                        Administradores.sinAsignar(hotel)));
            }
        }
        contenido.setValue(visibles.isEmpty()
                ? UiState.<List<FilaHotel>>empty()
                : UiState.success(visibles));
    }

    private void fallar(@NonNull String mensaje) {
        if (fallo) {
            return;
        }
        fallo = true;
        cargando = false;
        contenido.setValue(UiState.error(mensaje));
    }
}
