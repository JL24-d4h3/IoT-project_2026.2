package com.taxi.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Calificacion que el cliente deja al taxista")
public class ValoracionRequest {

    @NotNull(message = "La puntuacion es obligatoria")
    @Min(value = 1, message = "La puntuacion minima es 1")
    @Max(value = 5, message = "La puntuacion maxima es 5")
    @Schema(example = "5")
    private Integer puntuacion;

    @Size(max = 300)
    @Schema(example = "Llego puntual y condujo con mucho cuidado.")
    private String comentario;

    @Schema(description = "Servicio al que corresponde la valoracion. "
            + "Si se envia, debe estar FINALIZADO y sin valoracion previa.",
            example = "17")
    private Long servicioId;
}
