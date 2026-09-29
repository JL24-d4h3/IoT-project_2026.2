package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.core.FuenteUbicacion;
import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.Driver;
import org.iot.project.models.LogEntry;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Ubicacion;
import org.iot.project.models.User;
import org.iot.project.utils.Distancia;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementacion simulada del servicio de taxi.
 *
 * <p>Las reglas del flujo no las decide este repositorio: se delegan en
 * {@link TaxiService}, que es quien conoce los estados exactos —SOLICITADO,
 * ASIGNADO, EN_CAMINO, EN_TRASLADO, FINALIZADO— y rechaza los saltos
 * (RF-111, RC-016), quien decide si un conductor puede quedarse con un pedido
 * (RF-090) y quien comprueba el codigo (RF-103).
 *
 * <p>Aqui queda lo que el servicio no puede saber por si solo: que conductor
 * esta pidiendo, si ya tiene otro viaje en curso, a que distancia le queda el
 * recojo (RF-088) y donde esta (RF-098, {@link FuenteUbicacion}).
 */
public class MockTaxiRepository extends MockRepository implements TaxiRepository {

    /** Umbral de cercania (RF-088). Ver {@link #estaCerca}. */
    private static final double RADIO_ATENCION_M = 100_000d;

    /**
     * De donde salen las coordenadas del conductor (RF-098).
     *
     * <p>Se inyecta para poder sustituirla por una fuente real sin tocar este
     * repositorio; el constructor sin argumentos es el que usa la aplicacion.
     */
    private final FuenteUbicacion ubicacion;

    private int correlativo = 800;

    public MockTaxiRepository() {
        this(new FuenteUbicacionSimulada());
    }

    public MockTaxiRepository(FuenteUbicacion ubicacion) {
        this.ubicacion = ubicacion;
    }

    @Override
    public void servicioActivo(@NonNull String clienteId,
                               @NonNull ResultCallback<TaxiService> callback) {
        entregarDato(callback, () -> {
            for (TaxiService servicio : MockData.TAXIS) {
                if (servicio.getClienteId().equals(clienteId)
                        && servicio.getEstado().isActive()) {
                    return servicio;
                }
            }
            return null;
        }, "No tienes ningún servicio de taxi en curso.");
    }

    @Override
    public void historial(@NonNull String clienteId,
                          @NonNull ResultCallback<List<TaxiService>> callback) {
        entregarLista(callback, () -> {
            List<TaxiService> resultado = new ArrayList<>();
            for (TaxiService servicio : MockData.TAXIS) {
                if (servicio.getClienteId().equals(clienteId)) {
                    resultado.add(servicio);
                }
            }
            resultado.sort(Comparator.comparing(TaxiService::getFecha).reversed());
            return resultado;
        });
    }

    @Override
    public void solicitar(@NonNull TaxiService solicitud,
                          @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            validarSolicitud(solicitud);

            TaxiService nuevo = new TaxiService(
                    "T" + (MockData.TAXIS.size() + 1),
                    codigoNuevo(),
                    solicitud.getBookingId(),
                    solicitud.getClienteId());
            nuevo.withRuta(solicitud.getOrigen(), solicitud.getDestino())
                    .withRecojo(solicitud.getLatRecojo(), solicitud.getLngRecojo())
                    .withProgramacion(solicitud.getFecha(), solicitud.getHora(),
                            solicitud.isIdaYVuelta())
                    .withPasajeros(solicitud.getNumPasajeros())
                    .withPrecio(solicitud.getPrecio());
            MockData.TAXIS.add(nuevo);

            registrar("CAMBIO_ESTADO_TAXI", "Solicitó el servicio " + nuevo.getCodigo()
                    + " hacia " + nuevo.getDestino() + ".");
            return nuevo;
        });
    }

    @Override
    public void obtener(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback) {
        entregarDato(callback, () -> MockData.taxi(taxiId),
                "No encontramos este servicio de taxi.");
    }

    // ------------------------------------------------------------------
    //  Lado del conductor (RF-085 a RF-111)
    // ------------------------------------------------------------------

    @Override
    public void perfilDe(@NonNull String conductorId,
                         @NonNull ResultCallback<Driver> callback) {
        entregarDato(callback, () -> MockData.conductor(conductorId),
                "No encontramos esta cuenta de conductor.");
    }

    @Override
    public void disponibles(@NonNull String conductorId,
                            @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        entregarLista(callback, () -> {
            Driver conductor = MockData.conductor(conductorId);
            Ubicacion base = conductor == null ? null : ubicacion.posicion(conductor, null);
            // Sin conductor conocido no hay a quien ofrecerle nada, y sin base no
            // se puede decir a qué distancia le queda: en ambos casos, vacío. Los
            // datos sembrados dan siempre las dos cosas.
            if (base == null) {
                return Collections.emptyList();
            }

            List<OfertaDeTaxi> resultado = new ArrayList<>();
            for (TaxiService servicio : MockData.TAXIS) {
                if (servicio.puedeAceptarlo(conductor) && estaCerca(servicio, base)) {
                    resultado.add(new OfertaDeTaxi(servicio, distanciaAlRecojo(servicio, base)));
                }
            }
            // Lo más cercano primero: es lo que el conductor quiere ver arriba.
            resultado.sort(Comparator.comparingDouble(OfertaDeTaxi::getDistanciaM));
            return resultado;
        });
    }

    @Override
    public void serviciosEnCursoDe(@NonNull String conductorId,
                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        entregarLista(callback, () -> {
            for (TaxiService servicio : MockData.TAXIS) {
                Driver asignado = servicio.getDriver();
                if (asignado != null && asignado.getId().equals(conductorId)
                        && servicio.getEstado().isActive()) {
                    return Collections.singletonList(servicio);
                }
            }
            // Vacío es la respuesta correcta, no un fallo: significa que el
            // conductor está libre y la portada muestra su cara libre.
            return Collections.emptyList();
        });
    }

    @Override
    public void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                        @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            Driver conductor = exigirConductor(conductorId);
            TaxiService servicio = exigirServicio(taxiId);

            if (!servicio.puedeAceptarlo(conductor)) {
                throw new IllegalStateException(
                        "Este servicio ya no está disponible. Actualiza la lista.");
            }
            if (estaOcupado(conductor.getId())) {
                throw new IllegalStateException(
                        "Ya tienes un servicio en curso. Termínalo antes de aceptar otro.");
            }

            servicio.asignarA(conductor);
            reportarUbicacion(servicio);

            registrar("CAMBIO_ESTADO_TAXI", "Aceptó el servicio " + servicio.getCodigo()
                    + " hacia " + servicio.getDestino() + ".");
            return servicio;
        });
    }

    @Override
    public void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                                     @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicioDe(taxiId, conductorId);
            TaxiStatus actual = servicio.getEstado();

            // avanzarPorConductor rechaza FINALIZADO (RF-110) y los saltos (RF-111).
            servicio.avanzarPorConductor(actual.next());
            reportarUbicacion(servicio);

            registrar("CAMBIO_ESTADO_TAXI", "El servicio " + servicio.getCodigo()
                    + " pasó de " + actual.getDisplayName() + " a "
                    + servicio.getEstado().getDisplayName() + ".");
            return servicio;
        });
    }

    @Override
    public void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                              @NonNull String codigo,
                              @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicioDe(taxiId, conductorId);

            // RF-103: se valida antes de tocar el estado. Un código que no es el
            // de este servicio no deja el traslado a medias.
            if (!servicio.validarCodigo(codigo)) {
                throw new IllegalArgumentException(
                        "Ese código no corresponde a este servicio. "
                                + "Pídeselo otra vez al cliente.");
            }
            if (!servicio.debeMostrarQr()) {
                throw new IllegalStateException(
                        "El servicio todavía no tiene un código que validar.");
            }

            // RF-110 y RF-111: se llega a FINALIZADO sin saltarse EN_TRASLADO.
            if (servicio.getEstado() == TaxiStatus.EN_CAMINO) {
                servicio.avanzarA(TaxiStatus.EN_TRASLADO);
            }
            servicio.avanzarA(TaxiStatus.FINALIZADO);

            registrar("CAMBIO_ESTADO_TAXI", "Validó el código del servicio "
                    + servicio.getCodigo() + "; el servicio finalizó.");
            return servicio;
        });
    }

    @Override
    public void calificar(@NonNull String taxiId, float rating,
                          @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicio(taxiId);
            if (!servicio.getEstado().isFinished()) {
                throw new IllegalStateException(
                        "Solo puedes calificar un servicio que ya terminó.");
            }
            if (rating < 1f || rating > 10f) {
                throw new IllegalArgumentException("La calificación va de 1 a 10.");
            }
            // RF-105. Se guarda la nota del cliente ademas de promediarla en el
            // conductor: el promedio es del conductor, y sin la nota propia el
            // servicio pareceria sin valorar y la pantalla volveria a pedirla.
            servicio.valorar(rating);

            Driver conductor = servicio.getDriver();
            if (conductor != null) {
                // Promedio incremental: se pondera por el numero de servicios
                // previos para que una calificacion nueva no borre el historial.
                int previos = conductor.getNumServicios();
                float promedio = (conductor.getRating() * previos + rating) / (previos + 1);
                conductor.setRating(Math.round(promedio * 10f) / 10f);
                conductor.setNumServicios(previos + 1);
            }
            registrar("CAMBIO_ESTADO_TAXI", "Calificó el servicio " + servicio.getCodigo()
                    + " con " + (int) rating + "/10.");
            return servicio;
        });
    }

    // ------------------------------------------------------------------
    //  Reglas
    // ------------------------------------------------------------------

    private void validarSolicitud(TaxiService solicitud) {
        if (esVacio(solicitud.getOrigen()) || esVacio(solicitud.getDestino())) {
            throw new IllegalArgumentException("Indica el punto de recojo y el destino.");
        }
        if (!solicitud.hasRecojo()) {
            throw new IllegalArgumentException(
                    "No pudimos ubicar el punto de recojo en el mapa.");
        }
        if (solicitud.getFecha() == null || solicitud.getHora() == null) {
            throw new IllegalArgumentException("Elige la fecha y la hora del servicio.");
        }
        if (solicitud.getNumPasajeros() <= 0) {
            throw new IllegalArgumentException("Indica cuántos pasajeros viajan.");
        }
        // RF-101: el taxi al aeropuerto se ofrece dentro de una reserva.
        Booking reserva = MockData.reserva(solicitud.getBookingId());
        if (reserva == null) {
            throw new IllegalArgumentException(
                    "El servicio de taxi se solicita desde una reserva.");
        }
        if (tieneServicioActivo(solicitud.getClienteId())) {
            throw new IllegalStateException(
                    "Ya tienes un servicio de taxi en curso. Termínalo antes de pedir otro.");
        }
    }

    private boolean tieneServicioActivo(String clienteId) {
        for (TaxiService servicio : MockData.TAXIS) {
            if (servicio.getClienteId().equals(clienteId) && servicio.getEstado().isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deja constancia de la posicion del conductor (RF-098, RC-025).
     *
     * <p>Quien decide donde esta es {@code FuenteUbicacion}: aqui solo se
     * guarda el resultado. El plano de seguimiento del cliente (RF-099) dibuja
     * exactamente esta posicion, y por eso el conductor se acerca al punto de
     * recojo en lugar de alejarse.
     */
    private void reportarUbicacion(TaxiService servicio) {
        Driver conductor = servicio.getDriver();
        if (conductor == null || !servicio.hasRecojo()) {
            return;
        }
        Ubicacion siguiente = ubicacion.posicion(conductor, servicio);
        servicio.actualizarUbicacion(siguiente.getLatitud(), siguiente.getLongitud(),
                LocalDateTime.now());
    }

    /**
     * Si el conductor puede llegar a este recojo (RF-088).
     *
     * <p>El umbral esta entre los dos extremos que separan "mi ciudad" de "otra
     * ciudad": en los datos sembrados, Lima y Cusco estan a 569 km, y dos puntos
     * de la misma ciudad, a menos de 2. Cien kilometros cae holgadamente entre
     * los dos, y deja margen para un traslado interprovincial corto sin que se
     * cuele una solicitud al otro extremo del pais.
     */
    private boolean estaCerca(TaxiService servicio, Ubicacion base) {
        return servicio.hasRecojo()
                && distanciaAlRecojo(servicio, base) <= RADIO_ATENCION_M;
    }

    private static double distanciaAlRecojo(TaxiService servicio, Ubicacion base) {
        return Distancia.metrosEntre(base.getLatitud(), base.getLongitud(),
                servicio.getLatRecojo(), servicio.getLngRecojo());
    }

    /** RF-093: el conductor existe en el sistema de gestion de taxistas. */
    private Driver exigirConductor(String conductorId) {
        Driver conductor = MockData.conductor(conductorId);
        if (conductor == null) {
            throw new IllegalArgumentException("No encontramos esta cuenta de conductor.");
        }
        return conductor;
    }

    /**
     * El servicio, exigiendo ademas que sea de este conductor.
     *
     * <p>Sin esta comprobacion, un conductor podria avanzar o cerrar el traslado
     * de otro con solo conocer su identificador.
     */
    private TaxiService exigirServicioDe(String taxiId, @NonNull String conductorId) {
        TaxiService servicio = exigirServicio(taxiId);
        Driver asignado = servicio.getDriver();
        if (asignado == null || !asignado.getId().equals(conductorId)) {
            throw new IllegalStateException("Este servicio no está asignado a ti.");
        }
        return servicio;
    }

    private boolean estaOcupado(String driverId) {
        for (TaxiService servicio : MockData.TAXIS) {
            Driver asignado = servicio.getDriver();
            if (asignado != null && asignado.getId().equals(driverId)
                    && servicio.getEstado().isActive()) {
                return true;
            }
        }
        return false;
    }

    private TaxiService exigirServicio(String taxiId) {
        TaxiService servicio = MockData.taxi(taxiId);
        if (servicio == null) {
            throw new IllegalArgumentException("No encontramos este servicio de taxi.");
        }
        return servicio;
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }

    private String codigoNuevo() {
        correlativo++;
        return String.format("TAX-2026-%04d", correlativo);
    }

    private void registrar(String tipo, String detalle) {
        User usuario = MockData.usuario(SessionManager.getUsuarioIdSeguro());
        MockData.BITACORA.add(new LogEntry(
                LocalDateTime.now(),
                usuario != null ? usuario.getNombreCompleto() : "Sistema",
                LogEntry.Evento.valueOf(tipo),
                detalle));
    }
}
