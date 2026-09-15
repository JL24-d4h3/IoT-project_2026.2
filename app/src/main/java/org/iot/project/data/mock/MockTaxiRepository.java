package org.iot.project.data.mock;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Booking;
import org.iot.project.models.Driver;
import org.iot.project.models.Hotel;
import org.iot.project.models.LogEntry;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementacion simulada del servicio de taxi.
 *
 * <p>El avance de estado no lo decide este repositorio: se delega en
 * {@link TaxiService#avanzarA(TaxiStatus)}, que es quien conoce el flujo
 * exacto SOLICITADO, ASIGNADO, EN_CAMINO, EN_TRASLADO, FINALIZADO y rechaza
 * cualquier salto (RF-111, RC-016).
 */
public class MockTaxiRepository extends MockRepository implements TaxiRepository {

    /**
     * Donde aparece el conductor la primera vez, medido desde el punto de
     * recojo. Algo menos de 1,2 km: lo bastante lejos para que el seguimiento
     * tenga recorrido que enseñar, y lo bastante cerca para entrar entero en el
     * plano sin salirse por el borde.
     */
    private static final double DESPLAZE_INICIAL_LAT = 0.009;
    private static final double DESPLAZE_INICIAL_LNG = 0.006;

    /** Cuanto de la distancia restante se recorta en cada reporte. */
    private static final double FRACCION_ACERCAMIENTO = 0.4;

    private int correlativo = 800;

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

    @Override
    public void avanzar(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicio(taxiId);
            TaxiStatus actual = servicio.getEstado();

            if (actual.isFinished()) {
                throw new IllegalStateException("Este servicio ya finalizó.");
            }

            TaxiStatus siguiente = actual.next();

            // Al pasar a ASIGNADO hay que darle conductor; sin él el estado no
            // tiene sentido y la pantalla de seguimiento no tendría a quién
            // mostrar (RF-108).
            if (siguiente == TaxiStatus.ASIGNADO) {
                Driver conductor = conductorDisponible();
                if (conductor == null) {
                    throw new IllegalStateException(
                            "No hay conductores disponibles en este momento. "
                                    + "Vuelve a intentarlo en unos minutos.");
                }
                servicio.asignarA(conductor);
            } else {
                servicio.avanzarA(siguiente);
            }

            reportarUbicacion(servicio);

            registrar("CAMBIO_ESTADO_TAXI", "El servicio " + servicio.getCodigo()
                    + " pasó de " + actual.getDisplayName() + " a "
                    + servicio.getEstado().getDisplayName() + ".");
            return servicio;
        });
    }

    @Override
    public void confirmarQr(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback) {
        ejecutar(callback, () -> {
            TaxiService servicio = exigirServicio(taxiId);
            if (!servicio.debeMostrarQr()) {
                throw new IllegalStateException(
                        "El código QR recién aparece cuando el conductor está en camino.");
            }
            // RF-110: escanear el QR cierra el servicio de una vez. El flujo
            // pasa por EN_TRASLADO antes de FINALIZADO, no se salta estados.
            if (servicio.getEstado() == TaxiStatus.EN_CAMINO) {
                servicio.avanzarA(TaxiStatus.EN_TRASLADO);
            }
            servicio.avanzarA(TaxiStatus.FINALIZADO);

            registrar("CAMBIO_ESTADO_TAXI", "El cliente confirmó el QR del servicio "
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
     * Simula el reporte de posicion del conductor (RC-025).
     *
     * <p>El primer reporte se siembra cerca del punto de recojo: si se partiera
     * de cero, el conductor apareceria en el golfo de Guinea. Los siguientes
     * recortan la distancia que queda, porque un conductor que se aleja del
     * punto de recojo no es un conductor que viene a recogerte — y el plano de
     * seguimiento (RF-099) dibuja exactamente esa distancia.
     */
    private void reportarUbicacion(TaxiService servicio) {
        if (!servicio.hasRecojo()) {
            return;
        }
        double recojoLat = servicio.getLatRecojo();
        double recojoLng = servicio.getLngRecojo();

        if (servicio.getUltimaActualizacionUbicacion() == null) {
            servicio.actualizarUbicacion(
                    recojoLat + DESPLAZE_INICIAL_LAT,
                    recojoLng + DESPLAZE_INICIAL_LNG,
                    LocalDateTime.now());
            return;
        }

        double lat = servicio.getLatConductor();
        double lng = servicio.getLngConductor();
        servicio.actualizarUbicacion(
                lat + (recojoLat - lat) * FRACCION_ACERCAMIENTO,
                lng + (recojoLng - lng) * FRACCION_ACERCAMIENTO,
                LocalDateTime.now());
    }

    /**
     * Conductor habilitado con menos servicios acumulados.
     *
     * <p>Se salta los deshabilitados —uno dado de baja no puede recibir
     * servicios (RF-077)— y los que ya estan en un viaje en curso.
     */
    private Driver conductorDisponible() {
        Driver elegido = null;
        for (Driver conductor : MockData.CONDUCTORES) {
            if (!conductor.isHabilitado() || estaOcupado(conductor.getId())) {
                continue;
            }
            if (elegido == null || conductor.getNumServicios() < elegido.getNumServicios()) {
                elegido = conductor;
            }
        }
        return elegido;
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
