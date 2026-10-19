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
import org.iot.project.models.PeriodoDeVentas;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ficha de un hotel para el superadministrador (§47): sus datos, su
 * administrador, su publicacion y sus ventas (RF-059).
 *
 * <p>El hotel y las cuentas de administrador se consultan en paralelo. El
 * reporte tiene una migracion independiente. La asignacion y la publicacion
 * esperan la confirmacion del servidor antes de recargar la ficha.
 */
public class SuperadminHotelViewModel extends ViewModel {

    /** Consultas que hay que reunir antes de poder pintar la ficha. */
    private static final int PIEZAS = 2;

    /** Que se acaba de hacer, para que la pantalla sepa que decir. */
    public enum Operacion {
        ASIGNAR,
        PUBLICAR,
        RETIRAR
    }

    /** El resultado de la ultima operacion, con lo que hace falta para contarlo. */
    public static final class Cambio {

        @NonNull
        public final Hotel hotel;

        @NonNull
        public final Operacion operacion;

        Cambio(@NonNull Hotel hotel, @NonNull Operacion operacion) {
            this.hotel = hotel;
            this.operacion = operacion;
        }
    }

    /** Todo lo que la ficha enseña. */
    public static final class Contenido {

        @NonNull
        public final Hotel hotel;

        /**
         * Nombre de quien administra el hotel, o {@code null}.
         *
         * <p>Un nulo con {@link #sinAsignar} en falso no significa "no tiene
         * nadie": significa que la cuenta asignada ya no esta activa.
         */
        @Nullable
        public final String administrador;

        /** Si al hotel todavia le falta el paso de RF-008. */
        public final boolean sinAsignar;

        /** Cuentas que se le pueden asignar, ya sin la que tiene. */
        @NonNull
        public final List<User> candidatos;

        @NonNull
        public final List<PeriodoDeVentas> ventas;

        Contenido(@NonNull Hotel hotel, @Nullable String administrador, boolean sinAsignar,
                  @NonNull List<User> candidatos, @NonNull List<PeriodoDeVentas> ventas) {
            this.hotel = hotel;
            this.administrador = administrador;
            this.sinAsignar = sinAsignar;
            this.candidatos = candidatos;
            this.ventas = ventas;
        }

        /** Si el hotel reune lo minimo para ofrecerse al cliente (RF-013, RF-014). */
        public boolean esPublicable() {
            return hotel.aptoParaPublicar() && !sinAsignar && administrador != null;
        }
    }

    private final MutableLiveData<UiState<Contenido>> contenido = new MutableLiveData<>();
    private final MutableLiveData<UiState<Cambio>> cambio = new MutableLiveData<>();

    @Nullable
    private String hotelId;

    private Hotel hotel;
    private List<User> cuentas;

    private int tanda;
    private int recibidas;
    private boolean fallo;
    private boolean cargando;
    private boolean modificando;
    @Nullable
    private Operacion operacionPendiente;

    public LiveData<UiState<Contenido>> getContenido() {
        return contenido;
    }

    public LiveData<UiState<Cambio>> getCambio() {
        return cambio;
    }

    public boolean estaModificando() {
        return modificando;
    }

    @Nullable
    public Operacion getOperacionPendiente() {
        return operacionPendiente;
    }

    public boolean puedeAsignar() {
        UiState<Contenido> actual = contenido.getValue();
        return !cargando && !modificando && actual != null && actual.isSuccess();
    }

    public boolean puedeCambiarPublicacion() {
        UiState<Contenido> actual = contenido.getValue();
        return puedeAsignar() && actual != null && actual.getData() != null
                && (actual.getData().hotel.isPublicado() || actual.getData().esPublicable());
    }

    /**
     * Abre la ficha de un hotel.
     *
     * <p>El ViewModel es el del grafo —lo comparte con la hoja de asignacion— y
     * por eso sobrevive a la pantalla: al abrir otro hotel, lo que hubiera
     * cargado era de otro y se tira antes de pedir nada.
     */
    public void abrir(@NonNull String hotelId) {
        boolean otroHotel = !hotelId.equals(this.hotelId);
        this.hotelId = hotelId;
        if (otroHotel) {
            cargando = false;
            contenido.setValue(UiState.loading());
            cambio.setValue(null);
        } else if (!cargando && !modificando) {
            // La lista puede haberse actualizado desde la ultima visita a esta ficha.
            contenido.setValue(UiState.loading());
        }
        cargar();
    }

    public void cargar() {
        if (cargando || modificando || hotelId == null) {
            return;
        }
        UiState<Contenido> actual = contenido.getValue();
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

    private void pedir() {
        int mia = ++tanda;
        recibidas = 0;
        fallo = false;
        hotel = null;
        cuentas = null;

        ServiceLocator.hoteles().obtener(hotelId, new ResultCallback<Hotel>() {
            @Override
            public void onExito(@NonNull Hotel dato) {
                if (mia != tanda) {
                    return;
                }
                hotel = dato;
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
                cuentas = datos;
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

    /** Una de las consultas esenciales ya llego. */
    private void pieza() {
        if (fallo) {
            return;
        }
        recibidas++;
        if (recibidas < PIEZAS) {
            return;
        }
        Hotel listo = hotel;
        List<User> activas = cuentas;
        if (listo == null || activas == null) {
            fallar("No pudimos cargar la ficha del hotel.");
            return;
        }

        cargando = false;
        contenido.setValue(UiState.success(new Contenido(listo,
                Administradores.nombreDe(listo, activas),
                Administradores.sinAsignar(listo),
                candidatosPara(listo, activas),
                Collections.emptyList())));
    }

    /**
     * Las cuentas que se le pueden asignar.
     *
     * <p>Sin la que ya tiene: la accion de la ficha es cambiar de administrador,
     * y ofrecer al mismo que esta no es un cambio.
     */
    @NonNull
    private static List<User> candidatosPara(@NonNull Hotel hotel, @NonNull List<User> activas) {
        List<User> libres = new ArrayList<>();
        for (User cuenta : activas) {
            String asignado = cuenta.getHotelId();
            boolean disponible = asignado == null || asignado.trim().isEmpty()
                    || hotel.getId().equals(asignado);
            if (cuenta.isActivo() && cuenta.getRol() == Role.ADMIN_HOTEL && disponible
                    && !cuenta.getId().equals(hotel.getAdministradorId())) {
                libres.add(cuenta);
            }
        }
        return libres;
    }

    private void fallar(@NonNull String mensaje) {
        if (fallo) {
            return;
        }
        fallo = true;
        cargando = false;
        contenido.setValue(UiState.<Contenido>error(mensaje));
    }

    // ------------------------------------------------------------------
    //  Acciones
    // ------------------------------------------------------------------

    /**
     * RF-008: asignar un administrador.
     *
     * <p>Recarga la ficha entera: el nombre que se enseña y la lista de
     * candidatos cambian los dos, y adelantar uno de los dos en memoria dejaria
     * la pantalla diciendo dos cosas distintas del mismo hotel.
     */
    public void asignar(@NonNull String usuarioId) {
        if (hotelId == null || !puedeAsignar()) {
            return;
        }
        String solicitado = hotelId;
        modificando = true;
        operacionPendiente = Operacion.ASIGNAR;
        cambio.setValue(UiState.loading());
        ServiceLocator.superadmin().asignarAdministrador(solicitado, usuarioId,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel dato) {
                        modificando = false;
                        operacionPendiente = null;
                        if (!solicitado.equals(hotelId)) {
                            cambio.setValue(null);
                            cargar();
                            return;
                        }
                        recargarSinEsqueleto();
                        cambio.setValue(UiState.success(new Cambio(dato, Operacion.ASIGNAR)));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        modificando = false;
                        operacionPendiente = null;
                        if (!solicitado.equals(hotelId)) {
                            cambio.setValue(null);
                            cargar();
                            return;
                        }
                        cambio.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Publica o retira con las mismas guardas de carga que la asignacion. */
    public void cambiarPublicacion(boolean publicado) {
        if (hotelId == null || !puedeCambiarPublicacion()) {
            return;
        }
        String solicitado = hotelId;
        Operacion operacion = publicado ? Operacion.PUBLICAR : Operacion.RETIRAR;
        modificando = true;
        operacionPendiente = operacion;
        cambio.setValue(UiState.loading());
        ServiceLocator.superadmin().cambiarPublicacion(solicitado, publicado,
                new ResultCallback<Hotel>() {
                    @Override
                    public void onExito(@NonNull Hotel dato) {
                        modificando = false;
                        operacionPendiente = null;
                        if (!solicitado.equals(hotelId)) {
                            cambio.setValue(null);
                            cargar();
                            return;
                        }
                        recargarSinEsqueleto();
                        cambio.setValue(UiState.success(new Cambio(dato, operacion)));
                    }

                    @Override
                    public void onError(@NonNull String mensaje) {
                        modificando = false;
                        operacionPendiente = null;
                        if (!solicitado.equals(hotelId)) {
                            cambio.setValue(null);
                            cargar();
                            return;
                        }
                        cambio.setValue(UiState.error(mensaje));
                    }
                });
    }

    /** Sin esqueleto: la ficha ya esta pintada y el cambio no justifica borrarla. */
    private void recargarSinEsqueleto() {
        cargando = true;
        pedir();
    }

    /** El aviso ya se leyo. */
    public void consumirCambio() {
        cambio.setValue(null);
    }
}
