package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Ultima posicion conocida del taxista")
public record UbicacionResponse(
        @Schema(example = "7") Long taxistaId,
        @Schema(example = "-12.096540") Double latitud,
        @Schema(example = "-77.036110") Double longitud,
        @Schema(description = "Momento del ultimo reporte") LocalDateTime actualizadaEn
) {}
