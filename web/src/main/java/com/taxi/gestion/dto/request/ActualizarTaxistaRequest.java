package com.taxi.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que el propio taxista puede modificar desde la app.
 * El documento de identidad, la habilitacion y el estado de la cuenta no
 * son editables por el taxista.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Datos editables por el propio taxista")
public class ActualizarTaxistaRequest {

    @NotBlank
    @Size(max = 80)
    private String nombres;

    @NotBlank
    @Size(max = 80)
    private String apellidos;

    @NotBlank
    @Email
    @Size(max = 120)
    private String correo;

    @NotBlank
    @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "Telefono invalido")
    private String telefono;

    @NotBlank
    @Size(max = 200)
    private String domicilio;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9\\-]{5,10}$", message = "Placa invalida")
    private String placaVehiculo;

    /** Opcional: si viene, se rehashea con BCrypt. Si es null, no se toca. */
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Schema(description = "Nueva contrasena. Omitir para conservar la actual.")
    private String nuevaPassword;
}
