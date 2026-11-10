package com.taxi.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Credenciales de inicio de sesion del taxista")
public class LoginRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo invalido")
    @Schema(example = "luis.ramirez@correo.com")
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Schema(example = "Clave12345")
    private String password;
}
