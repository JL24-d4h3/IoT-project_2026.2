package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Respuesta ligera que la app movil consulta por polling. */
@Schema(description = "Estado actual de un servicio")
public record EstadoServicioResponse(
        Long servicioId,
        @Schema(example = "EN_CAMINO") String estado,
        String estadoDescripcion,
        Long taxistaId,
        @Schema(description = "Transiciones validas desde el estado actual")
        List<String> siguientesEstadosPosibles,
        LocalDateTime actualizadoEn
) {}
