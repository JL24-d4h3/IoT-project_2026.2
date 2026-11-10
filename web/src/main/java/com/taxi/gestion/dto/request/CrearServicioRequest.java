package com.taxi.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Solicitud de un nuevo servicio de taxi")
public class CrearServicioRequest {

    @Size(max = 60)
    @Schema(description = "Identificador del cliente en la app movil", example = "cli-8842")
    private String clienteId;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 120)
    private String nombreCliente;

    @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "Telefono invalido")
    private String telefonoCliente;

    @NotBlank(message = "La direccion de origen es obligatoria")
    @Size(max = 200)
    @Schema(example = "Av. Javier Prado Este 4200, Surco")
    private String direccionOrigen;

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitudOrigen;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitudOrigen;

    @NotBlank(message = "La direccion de destino es obligatoria")
    @Size(max = 200)
    @Schema(example = "Aeropuerto Jorge Chavez, Callao")
    private String direccionDestino;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double latitudDestino;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double longitudDestino;

    @DecimalMin(value = "0.0", inclusive = false, message = "La tarifa debe ser mayor a cero")
    @Schema(example = "38.50")
    private BigDecimal tarifa;
}
