package com.taxi.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Codigo QR que el taxista escanea al cliente para cerrar el viaje")
public class FinalizarServicioRequest {

    @NotBlank(message = "El codigo QR es obligatorio")
    @Size(max = 40)
    @Schema(example = "QR-9F3C21A8B4D6")
    private String codigoQr;
}
