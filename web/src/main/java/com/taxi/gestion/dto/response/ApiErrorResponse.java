package com.taxi.gestion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/** Cuerpo uniforme de error para toda la API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error devuelto por la API")
public record ApiErrorResponse(
        @Schema(example = "2026-09-20T10:15:30") LocalDateTime timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "El servicio 17 ya fue asignado a otro taxista") String mensaje,
        @Schema(example = "/api/servicios/17/aceptar") String ruta,
        @Schema(description = "Errores por campo en validaciones de formulario")
        Map<String, String> erroresCampo
) {
    public static ApiErrorResponse of(int status, String error, String mensaje, String ruta) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, mensaje, ruta, null);
    }
}
