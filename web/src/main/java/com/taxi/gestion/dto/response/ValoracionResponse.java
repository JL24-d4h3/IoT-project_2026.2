package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resultado del registro de una valoracion")
public record ValoracionResponse(
        Long valoracionId,
        Long taxistaId,
        Integer puntuacionRegistrada,
        @Schema(example = "4.72") BigDecimal valoracionPromedio,
        @Schema(example = "39") Integer totalValoraciones
) {}
