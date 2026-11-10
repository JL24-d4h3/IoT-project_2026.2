package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de un login correcto")
public record LoginResponse(
        @Schema(description = "Token a enviar como 'Authorization: Bearer <token>' "
                + "en los endpoints que modifican datos propios del taxista")
        String token,
        @Schema(example = "Bearer") String tipoToken,
        @Schema(example = "720") long expiraEnMinutos,
        TaxistaResponse taxista
) {}
