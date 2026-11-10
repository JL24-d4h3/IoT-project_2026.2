package com.taxi.gestion.service;

import com.taxi.gestion.domain.ServicioTaxi;
import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoServicio;
import com.taxi.gestion.dto.request.CrearServicioRequest;
import com.taxi.gestion.exception.AccesoNoAutorizadoException;
import com.taxi.gestion.exception.ConflictoEstadoException;
import com.taxi.gestion.exception.RecursoNoEncontradoException;
import com.taxi.gestion.exception.ReglaNegocioException;
import com.taxi.gestion.repository.ServicioTaxiRepository;
import com.taxi.gestion.repository.TaxistaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ciclo de vida del servicio de taxi. Toda transicion pasa por aqui, de modo
 * que el flujo SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO
 * se valida en un unico sitio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioTaxiService {

    private final ServicioTaxiRepository servicioRepository;
    private final TaxistaRepository taxistaRepository;

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ServicioTaxi obtener(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.servicio(id));
    }

    @Transactional(readOnly = true)
    public List<ServicioTaxi> listarPendientes() {
        return servicioRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoServicio.SOLICITADO);
    }

    @Transactional(readOnly = true)
    public List<ServicioTaxi> listarPorTaxista(Long taxistaId, Long solicitanteTaxistaId) {
        if (!taxistaId.equals(solicitanteTaxistaId)) {
            throw new AccesoNoAutorizadoException("No puedes ver el historial de otro taxista");
        }
        if (!taxistaRepository.existsById(taxistaId)) {
            throw RecursoNoEncontradoException.taxista(taxistaId);
        }
        return servicioRepository.findByTaxistaIdOrderByFechaSolicitudDesc(taxistaId);
    }

    // ------------------------------------------------------------------
    // Creacion
    // ------------------------------------------------------------------

    /** Crea el servicio en SOLICITADO y genera el codigo que el cliente mostrara como QR. */
    @Transactional
    public ServicioTaxi crear(CrearServicioRequest req) {
        ServicioTaxi servicio = ServicioTaxi.builder()
                .clienteId(req.getClienteId())
                .nombreCliente(req.getNombreCliente().trim())
                .telefonoCliente(req.getTelefonoCliente())
                .direccionOrigen(req.getDireccionOrigen().trim())
                .latitudOrigen(req.getLatitudOrigen())
                .longitudOrigen(req.getLongitudOrigen())
                .direccionDestino(req.getDireccionDestino().trim())
                .latitudDestino(req.getLatitudDestino())
                .longitudDestino(req.getLongitudDestino())
                .tarifa(req.getTarifa())
                .estado(EstadoServicio.SOLICITADO)
                .codigoQr(generarCodigoQr())
                .build();

        ServicioTaxi guardado = servicioRepository.save(servicio);
        log.info("Servicio {} creado en estado SOLICITADO", guardado.getId());
        return guardado;
    }

    private void exigirTaxistaAsignado(ServicioTaxi servicio, Long solicitanteTaxistaId) {
        Taxista asignado = servicio.getTaxista();
        if (asignado == null || !asignado.getId().equals(solicitanteTaxistaId)) {
            throw new AccesoNoAutorizadoException(
                    "Este servicio no esta asignado a tu cuenta");
        }
    }

    private String generarCodigoQr() {
        return "QR-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Transiciones
    // ------------------------------------------------------------------

    /**
     * Un taxista toma el servicio: SOLICITADO -> ASIGNADO y el taxista pasa a
     * OCUPADO para que deje de aparecer entre los disponibles.
     */
    @Transactional
    public ServicioTaxi aceptar(Long servicioId, Long taxistaId) {
        ServicioTaxi servicio = servicioRepository.findByIdParaActualizar(servicioId)
                .orElseThrow(() -> RecursoNoEncontradoException.servicio(servicioId));
        Taxista taxista = taxistaRepository.findByIdParaActualizar(taxistaId)
                .orElseThrow(() -> RecursoNoEncontradoException.taxista(taxistaId));

        if (servicio.getEstado() != EstadoServicio.SOLICITADO) {
            throw new ConflictoEstadoException("El servicio " + servicioId + " ya no esta "
                    + "disponible: su estado actual es " + servicio.getEstado());
        }
        if (!taxista.estaOperativo()) {
            throw new ConflictoEstadoException(
                    "El taxista no esta habilitado o su cuenta esta desactivada");
        }
        if (taxista.getDisponibilidad() != Disponibilidad.DISPONIBLE) {
            throw new ConflictoEstadoException("El taxista ya esta atendiendo otro servicio");
        }

        servicio.setTaxista(taxista);
        servicio.setEstado(EstadoServicio.ASIGNADO);
        servicio.setFechaAsignacion(LocalDateTime.now());
        taxista.setDisponibilidad(Disponibilidad.OCUPADO);

        log.info("Servicio {} asignado al taxista {}", servicioId, taxistaId);
        return servicio;
    }

    /**
     * Avance del viaje: ASIGNADO -> EN_CAMINO -> EN_TRASLADO.
     * FINALIZADO queda reservado a {@link #finalizar(Long, String, Long)} porque exige el QR.
     *
     * @param solicitanteTaxistaId id del taxista autenticado que hace la peticion;
     *                             debe coincidir con el taxista asignado al servicio.
     */
    @Transactional
    public ServicioTaxi cambiarEstado(Long servicioId, EstadoServicio destino,
                                      Long solicitanteTaxistaId) {
        ServicioTaxi servicio = servicioRepository.findByIdParaActualizar(servicioId)
                .orElseThrow(() -> RecursoNoEncontradoException.servicio(servicioId));
        exigirTaxistaAsignado(servicio, solicitanteTaxistaId);

        if (destino == EstadoServicio.FINALIZADO) {
            throw new ReglaNegocioException("Para finalizar el servicio usa "
                    + "PUT /api/servicios/" + servicioId + "/finalizar con el codigo QR");
        }
        if (destino == EstadoServicio.ASIGNADO) {
            throw new ReglaNegocioException("Para asignar el servicio usa "
                    + "PUT /api/servicios/" + servicioId + "/aceptar");
        }

        EstadoServicio actual = servicio.getEstado();
        if (!actual.permiteTransicionA(destino)) {
            throw ConflictoEstadoException.transicionInvalida(servicioId, actual, destino);
        }

        servicio.setEstado(destino);
        if (destino == EstadoServicio.EN_TRASLADO) {
            servicio.setFechaInicioTraslado(LocalDateTime.now());
        }
        log.info("Servicio {}: {} -> {}", servicioId, actual, destino);
        return servicio;
    }

    /**
     * Cierre del viaje. Valida el codigo QR que el taxista escanea al cliente
     * y libera al taxista dejandolo DISPONIBLE otra vez.
     *
     * @param solicitanteTaxistaId id del taxista autenticado que hace la peticion;
     *                             debe coincidir con el taxista asignado al servicio.
     */
    @Transactional
    public ServicioTaxi finalizar(Long servicioId, String codigoQr, Long solicitanteTaxistaId) {
        ServicioTaxi servicio = servicioRepository.findByIdParaActualizar(servicioId)
                .orElseThrow(() -> RecursoNoEncontradoException.servicio(servicioId));
        exigirTaxistaAsignado(servicio, solicitanteTaxistaId);

        EstadoServicio actual = servicio.getEstado();
        if (!actual.permiteTransicionA(EstadoServicio.FINALIZADO)) {
            throw ConflictoEstadoException.transicionInvalida(
                    servicioId, actual, EstadoServicio.FINALIZADO);
        }
        if (!servicio.codigoQrCoincide(codigoQr)) {
            throw new ReglaNegocioException(
                    "El codigo QR no corresponde a este servicio");
        }

        servicio.setEstado(EstadoServicio.FINALIZADO);
        servicio.setFechaFinalizacion(LocalDateTime.now());

        Taxista taxista = servicio.getTaxista();
        if (taxista != null && Boolean.TRUE.equals(taxista.getCuentaActiva())) {
            taxista.setDisponibilidad(Disponibilidad.DISPONIBLE);
        }
        log.info("Servicio {} finalizado", servicioId);
        return servicio;
    }
}
