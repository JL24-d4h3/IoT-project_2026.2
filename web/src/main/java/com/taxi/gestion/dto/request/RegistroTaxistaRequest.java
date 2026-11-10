package com.taxi.gestion.dto.request;

import com.taxi.gestion.domain.enums.TipoDocumento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** Datos del autoregistro. Se usa tanto en el formulario web como en la API. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de autoregistro de un taxista")
public class RegistroTaxistaRequest {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 80)
    @Schema(example = "Luis Alberto")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 80)
    @Schema(example = "Ramirez Quispe")
    private String apellidos;

    @NotNull(message = "Selecciona el tipo de documento")
    private TipoDocumento tipoDocumento;

    @NotBlank(message = "El numero de documento es obligatorio")
    @Schema(example = "45781236", description = "8 digitos si es DNI; alfanumerico de 6 a 20 "
            + "caracteres para pasaporte o carnet de extranjeria")
    private String numeroDocumento;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(example = "1990-05-14")
    private LocalDate fechaNacimiento;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo invalido")
    @Size(max = 120)
    @Schema(example = "luis.ramirez@correo.com")
    private String correo;

    @NotBlank(message = "El telefono es obligatorio")
    @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "Telefono invalido")
    @Schema(example = "987654321")
    private String telefono;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Schema(example = "MiClaveSegura1")
    private String password;

    @NotBlank(message = "Repite la contrasena")
    private String confirmacionPassword;

    @NotBlank(message = "El domicilio es obligatorio")
    @Size(max = 200)
    @Schema(example = "Av. Arequipa 1234, Lince, Lima")
    private String domicilio;

    /** Foto del taxista: archivo JPG o PNG de hasta 5 MB (se valida en ValidadorImagen). */
    private MultipartFile fotoPerfil;

    @NotBlank(message = "La placa es obligatoria")
    @Pattern(regexp = "^[A-Za-z0-9\\-]{5,10}$", message = "Placa invalida")
    @Schema(example = "ABC-123")
    private String placaVehiculo;

    /** Foto del vehiculo: archivo JPG o PNG de hasta 5 MB. */
    private MultipartFile fotoVehiculo;

    @AssertTrue(message = "Las contrasenas no coinciden")
    public boolean isPasswordConfirmada() {
        return password != null && password.equals(confirmacionPassword);
    }

    /**
     * El formato del documento depende del tipo: el DNI peruano son 8 cifras
     * exactas; pasaporte y carnet de extranjeria admiten letras y numeros.
     * Si falta el tipo o el numero, deja que @NotNull/@NotBlank lo reporten.
     */
    @AssertTrue(message = "Documento invalido: el DNI debe tener 8 digitos; "
            + "pasaporte o carnet de extranjeria, de 6 a 20 caracteres alfanumericos")
    public boolean isDocumentoValido() {
        if (tipoDocumento == null || numeroDocumento == null || numeroDocumento.isBlank()) {
            return true;
        }
        String doc = numeroDocumento.trim();
        return switch (tipoDocumento) {
            case DNI -> doc.matches("\\d{8}");
            case PASAPORTE, CARNET_EXTRANJERIA -> doc.matches("[A-Za-z0-9]{6,20}");
        };
    }
}
