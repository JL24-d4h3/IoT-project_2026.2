package com.taxi.gestion.rest;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.dto.request.LoginRequest;
import com.taxi.gestion.dto.response.ApiErrorResponse;
import com.taxi.gestion.dto.response.LoginResponse;
import com.taxi.gestion.mapper.TaxistaMapper;
import com.taxi.gestion.security.JwtService;
import com.taxi.gestion.service.TaxistaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puerta de entrada de la app movil: aqui es donde "se integra" con el taxista
 * ya aprobado. Sin un login correcto, el taxista no puede usar ningun endpoint
 * que modifique sus propios datos o el servicio que tiene asignado.
 */
@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Inicio de sesion de la app movil del taxista")
public class AuthController {

    private final TaxistaService taxistaService;
    private final JwtService jwtService;
    private final TaxistaMapper mapper;

    @PostMapping(value = "/login", consumes = "application/json")
    @Operation(summary = "Iniciar sesion como taxista",
            description = "Solo un taxista HABILITADO por el Superadmin y con la cuenta "
                    + "activa puede iniciar sesion. El token que devuelve se envia como "
                    + "'Authorization: Bearer <token>' en los endpoints que reportan "
                    + "ubicacion, aceptan o avanzan un servicio, o actualizan datos propios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion iniciada"),
            @ApiResponse(responseCode = "400", description = "Correo o contrasena ausentes",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Correo o contrasena incorrectos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Credenciales correctas pero la cuenta esta PENDIENTE o desactivada",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Taxista taxista = taxistaService.autenticar(request.getCorreo(), request.getPassword());
        String token = jwtService.generarToken(taxista.getId(), taxista.getCorreo());
        return new LoginResponse(token, "Bearer", jwtService.expiracionMinutos(),
                mapper.aDetalle(taxista));
    }
}
