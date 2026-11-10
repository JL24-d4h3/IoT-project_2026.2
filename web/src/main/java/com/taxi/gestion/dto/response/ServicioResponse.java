package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Detalle de un servicio de taxi")
public record ServicioResponse(
        Long id,
        @Schema(example = "ASIGNADO") String estado,
        String estadoDescripcion,
        Long taxistaId,
        String taxistaNombre,
        String taxistaPlaca,
        String nombreCliente,
        String telefonoCliente,
        String direccionOrigen,
        Double latitudOrigen,
        Double longitudOrigen,
        String direccionDestino,
        Double latitudDestino,
        Double longitudDestino,
        BigDecimal tarifa,
        @Schema(description = "Solo se devuelve al crear el servicio; en el resto de "
                + "respuestas viaja null para no filtrar el codigo de cierre.")
        String codigoQr,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaAsignacion,
        LocalDateTime fechaInicioTraslado,
        LocalDateTime fechaFinalizacion
) {}
