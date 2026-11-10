package com.taxi.gestion.rest;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.dto.request.ActualizarTaxistaRequest;
import com.taxi.gestion.dto.request.RegistroTaxistaRequest;
import com.taxi.gestion.dto.request.UbicacionRequest;
import com.taxi.gestion.dto.request.ValoracionRequest;
import com.taxi.gestion.dto.response.ApiErrorResponse;
import com.taxi.gestion.dto.response.TaxistaResponse;
import com.taxi.gestion.dto.response.TaxistaResumenResponse;
import com.taxi.gestion.dto.response.UbicacionResponse;
import com.taxi.gestion.dto.response.ValoracionResponse;
import com.taxi.gestion.mapper.TaxistaMapper;
import com.taxi.gestion.security.SeguridadUtils;
import com.taxi.gestion.service.TaxistaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Endpoints de taxistas consumidos por la app movil. */
@RestController
@RequestMapping(value = "/api/taxistas", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Taxistas", description = "Consulta y actualizacion de taxistas desde la app movil")
public class TaxistaRestController {

    private final TaxistaService taxistaService;
    private final TaxistaMapper mapper;
    private final SeguridadUtils seguridadUtils;

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @GetMapping("/disponibles")
    @Operation(summary = "Listar taxistas disponibles",
            description = "Devuelve los taxistas HABILITADOS, con cuenta activa y "
                    + "disponibilidad DISPONIBLE, ordenados por mejor valoracion. "
                    + "Es la lista que la app muestra en el mapa al pedir un taxi.")
    @ApiResponse(responseCode = "200", description = "Listado obtenido (puede venir vacio)")
    public List<TaxistaResumenResponse> disponibles() {
        return mapper.aResumenes(taxistaService.listarDisponibles());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener el detalle de un taxista",
            description = "Datos personales basicos, datos del vehiculo y valoracion. "
                    + "Nunca incluye la contrasena y el documento viaja enmascarado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taxista encontrado"),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TaxistaResponse detalle(
            @Parameter(description = "Id del taxista", example = "1") @PathVariable Long id) {
        return mapper.aDetalle(taxistaService.obtener(id));
    }

    @GetMapping("/{id}/ubicacion")
    @Operation(summary = "Obtener la ubicacion actual del taxista",
            description = "Ultimas coordenadas reportadas. La app del cliente consulta "
                    + "este endpoint por polling mientras el taxi va en camino.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ubicacion obtenida"),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UbicacionResponse ubicacion(@PathVariable Long id) {
        return mapper.aUbicacion(taxistaService.obtener(id));
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @PutMapping(value = "/{id}/ubicacion", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualizar la ubicacion del taxista",
            description = "La app movil lo llama periodicamente mientras hay un servicio activo. "
                    + "Requiere el token del login: un taxista solo puede reportar su propia posicion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ubicacion actualizada"),
            @ApiResponse(responseCode = "400", description = "Coordenadas fuera de rango",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token ausente o de otro taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Taxista no habilitado o cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UbicacionResponse actualizarUbicacion(@PathVariable Long id,
                                                 @Valid @RequestBody UbicacionRequest request) {
        seguridadUtils.exigirPropioTaxista(id);
        return mapper.aUbicacion(taxistaService.actualizarUbicacion(id, request));
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Actualizar los datos del propio taxista",
            description = "Permite editar nombres, contacto, domicilio, fotos y placa. "
                    + "El documento, la habilitacion y el estado de la cuenta no son editables aqui. "
                    + "Requiere el token del login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos actualizados"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token ausente o de otro taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Correo o placa en uso por otra cuenta",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TaxistaResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ActualizarTaxistaRequest request) {
        seguridadUtils.exigirPropioTaxista(id);
        return mapper.aDetalle(taxistaService.actualizarDatos(id, request));
    }

    @PutMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reemplazar las fotos del taxista",
            description = "Sube archivos (multipart/form-data) en las partes 'fotoPerfil' y/o "
                    + "'fotoVehiculo'. Solo JPG o PNG de hasta 5 MB; se valida el contenido real "
                    + "del archivo. Requiere el token del login y solo aplica a las fotos propias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fotos actualizadas"),
            @ApiResponse(responseCode = "400", description = "Sin archivos, formato no permitido o mas de 5 MB",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token ausente o de otro taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TaxistaResponse actualizarFotos(
            @PathVariable Long id,
            @RequestPart(value = "fotoPerfil", required = false) MultipartFile fotoPerfil,
            @RequestPart(value = "fotoVehiculo", required = false) MultipartFile fotoVehiculo) {
        seguridadUtils.exigirPropioTaxista(id);
        return mapper.aDetalle(taxistaService.actualizarFotos(id, fotoPerfil, fotoVehiculo));
    }

    @PostMapping(value = "/{id}/valoracion", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una valoracion del cliente",
            description = "Guarda la calificacion (1 a 5) y recalcula el promedio del taxista. "
                    + "Si se envia servicioId, el servicio debe estar FINALIZADO y no haber "
                    + "sido valorado antes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Valoracion registrada"),
            @ApiResponse(responseCode = "400", description = "Puntuacion fuera de rango o servicio ajeno al taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Taxista o servicio inexistente",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Servicio no finalizado o ya valorado",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ValoracionResponse valorar(@PathVariable Long id,
                                      @Valid @RequestBody ValoracionRequest request) {
        return taxistaService.registrarValoracion(id, request);
    }
}
