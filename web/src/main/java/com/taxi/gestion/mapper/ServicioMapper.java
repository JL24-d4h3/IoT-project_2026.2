package com.taxi.gestion.mapper;

import com.taxi.gestion.domain.ServicioTaxi;
import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.EstadoServicio;
import com.taxi.gestion.dto.response.EstadoServicioResponse;
import com.taxi.gestion.dto.response.ServicioResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class ServicioMapper {

    /** Respuesta estandar: el codigo QR viaja en null. */
    public ServicioResponse aDetalle(ServicioTaxi s) {
        return construir(s, false);
    }

    /** Solo al crear el servicio: el cliente necesita el codigo para generar su QR. */
    public ServicioResponse aDetalleConCodigoQr(ServicioTaxi s) {
        return construir(s, true);
    }

    private ServicioResponse construir(ServicioTaxi s, boolean incluirQr) {
        Taxista t = s.getTaxista();
        return new ServicioResponse(
                s.getId(),
                s.getEstado().name(),
                s.getEstado().getEtiqueta(),
                t != null ? t.getId() : null,
                t != null ? t.getNombreCompleto() : null,
                t != null ? t.getPlacaVehiculo() : null,
                s.getNombreCliente(),
                s.getTelefonoCliente(),
                s.getDireccionOrigen(),
                s.getLatitudOrigen(),
                s.getLongitudOrigen(),
                s.getDireccionDestino(),
                s.getLatitudDestino(),
                s.getLongitudDestino(),
                s.getTarifa(),
                incluirQr ? s.getCodigoQr() : null,
                s.getFechaSolicitud(),
                s.getFechaAsignacion(),
                s.getFechaInicioTraslado(),
                s.getFechaFinalizacion()
        );
    }

    public EstadoServicioResponse aEstado(ServicioTaxi s) {
        EstadoServicio actual = s.getEstado();
        return new EstadoServicioResponse(
                s.getId(),
                actual.name(),
                actual.getEtiqueta(),
                s.getTaxista() != null ? s.getTaxista().getId() : null,
                actual.siguientesPosibles().stream().map(Enum::name).toList(),
                ultimoCambio(s)
        );
    }

    private LocalDateTime ultimoCambio(ServicioTaxi s) {
        return switch (s.getEstado()) {
            case SOLICITADO -> s.getFechaSolicitud();
            case ASIGNADO, EN_CAMINO -> Objects.requireNonNullElse(
                    s.getFechaAsignacion(), s.getFechaSolicitud());
            case EN_TRASLADO -> s.getFechaInicioTraslado();
            case FINALIZADO -> s.getFechaFinalizacion();
        };
    }
}
