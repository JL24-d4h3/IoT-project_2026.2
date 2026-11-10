package com.taxi.gestion.rest;

import com.taxi.gestion.domain.ServicioTaxi;
import com.taxi.gestion.dto.request.CambioEstadoRequest;
import com.taxi.gestion.dto.request.CrearServicioRequest;
import com.taxi.gestion.dto.request.FinalizarServicioRequest;
import com.taxi.gestion.dto.response.ApiErrorResponse;
import com.taxi.gestion.dto.response.EstadoServicioResponse;
import com.taxi.gestion.dto.response.ServicioResponse;
import com.taxi.gestion.mapper.ServicioMapper;
import com.taxi.gestion.security.SeguridadUtils;
import com.taxi.gestion.service.ServicioTaxiService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Ciclo de vida del servicio de taxi expuesto a la app movil. */
@RestController
@RequestMapping(value = "/api/servicios", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Servicios de taxi",
        description = "Creacion, asignacion y avance de estado de los viajes")
public class ServicioTaxiRestController {

    private final ServicioTaxiService servicioService;
    private final ServicioMapper mapper;
    private final SeguridadUtils seguridadUtils;

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @GetMapping("/{id}/estado")
    @Operation(summary = "Consultar el estado de un servicio",
            description = "Respuesta ligera pensada para polling desde la app. "
                    + "Incluye las transiciones validas desde el estado actual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado obtenido"),
            @ApiResponse(responseCode = "404", description = "El servicio no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public EstadoServicioResponse estado(
            @Parameter(description = "Id del servicio", example = "1") @PathVariable Long id) {
        return mapper.aEstado(servicioService.obtener(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener el detalle de un servicio")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio encontrado"),
            @ApiResponse(responseCode = "404", description = "El servicio no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ServicioResponse detalle(@PathVariable Long id) {
        return mapper.aDetalle(servicioService.obtener(id));
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Listar servicios en SOLICITADO",
            description = "Bolsa de viajes sin taxista asignado que la app ofrece a los "
                    + "taxistas conectados.")
    public List<ServicioResponse> pendientes() {
        return servicioService.listarPendientes().stream().map(mapper::aDetalle).toList();
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Crear un servicio",
            description = "Nace en estado SOLICITADO. Esta es la unica respuesta que "
                    + "devuelve el codigoQr: el cliente lo convierte en QR y el taxista "
                    + "lo escanea al terminar el viaje.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Servicio creado en SOLICITADO"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ServicioResponse> crear(@Valid @RequestBody CrearServicioRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        ServicioTaxi creado = servicioService.crear(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/servicios/{id}").buildAndExpand(creado.getId()).toUri())
                .body(mapper.aDetalleConCodigoQr(creado));
    }

    @PutMapping("/{id}/aceptar")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Aceptar un servicio",
            description = "El taxista toma el viaje: SOLICITADO -> ASIGNADO. El taxista "
                    + "queda identificado por el token del login, no por un parametro: "
                    + "asi ningun taxista puede aceptar un viaje a nombre de otro. "
                    + "Pasa a OCUPADO y deja de aparecer entre los disponibles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio asignado"),
            @ApiResponse(responseCode = "403", description = "Token ausente o invalido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Servicio o taxista inexistente",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "El servicio ya fue tomado, o el taxista esta ocupado, "
                            + "no habilitado o con la cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ServicioResponse aceptar(@PathVariable Long id) {
        Long taxistaId = seguridadUtils.taxistaAutenticadoId();
        return mapper.aDetalle(servicioService.aceptar(id, taxistaId));
    }

    @PutMapping(value = "/{id}/estado", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Avanzar el estado del servicio",
            description = "Transiciones permitidas: ASIGNADO -> EN_CAMINO -> EN_TRASLADO. "
                    + "Cualquier salto fuera de secuencia se rechaza con 409. "
                    + "FINALIZADO no se alcanza por aqui: usa /finalizar con el codigo QR. "
                    + "Solo el taxista asignado (segun el token) puede avanzarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400",
                    description = "Estado ausente, o se intento ASIGNADO/FINALIZADO por esta via",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Token ausente, o el servicio no esta asignado a ese taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El servicio no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Transicion fuera del flujo permitido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public EstadoServicioResponse cambiarEstado(@PathVariable Long id,
                                                @Valid @RequestBody CambioEstadoRequest request) {
        Long taxistaId = seguridadUtils.taxistaAutenticadoId();
        return mapper.aEstado(servicioService.cambiarEstado(id, request.getEstado(), taxistaId));
    }

    @PutMapping(value = "/{id}/finalizar", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Finalizar el servicio validando el QR",
            description = "Solo desde EN_TRASLADO, y solo el taxista asignado. Si el codigo "
                    + "no coincide se responde 400 y el servicio permanece abierto. Al "
                    + "cerrarlo, el taxista vuelve a DISPONIBLE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Servicio finalizado"),
            @ApiResponse(responseCode = "400", description = "Codigo QR invalido o ausente",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "Token ausente, o el servicio no esta asignado a ese taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El servicio no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El servicio no esta en EN_TRASLADO",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ServicioResponse finalizar(@PathVariable Long id,
                                      @Valid @RequestBody FinalizarServicioRequest request) {
        Long taxistaId = seguridadUtils.taxistaAutenticadoId();
        return mapper.aDetalle(servicioService.finalizar(id, request.getCodigoQr(), taxistaId));
    }

    @GetMapping("/taxista/{taxistaId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Historial de servicios de un taxista",
            description = "Requiere el token del login: cada taxista solo ve su propio historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial obtenido"),
            @ApiResponse(responseCode = "403", description = "Token ausente o de otro taxista",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El taxista no existe",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ServicioResponse> historial(@PathVariable Long taxistaId) {
        Long solicitante = seguridadUtils.taxistaAutenticadoId();
        return servicioService.listarPorTaxista(taxistaId, solicitante).stream()
                .map(mapper::aDetalle).toList();
    }
}
