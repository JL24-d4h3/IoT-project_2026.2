package com.taxi.gestion.dto.request;

import com.taxi.gestion.domain.enums.EstadoServicio;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nuevo estado del servicio dentro del flujo permitido")
public class CambioEstadoRequest {

    @NotNull(message = "El nuevo estado es obligatorio")
    @Schema(description = "ASIGNADO -> EN_CAMINO -> EN_TRASLADO. "
            + "FINALIZADO se alcanza solo por /finalizar con el codigo QR.",
            example = "EN_CAMINO")
    private EstadoServicio estado;
}
