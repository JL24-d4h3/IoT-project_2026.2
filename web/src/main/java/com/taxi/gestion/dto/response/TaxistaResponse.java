package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle de un taxista. Nunca incluye el hash de la contrasena ni el
 * numero de documento completo.
 */
@Schema(description = "Detalle de un taxista")
public record TaxistaResponse(
        Long id,
        String nombres,
        String apellidos,
        String nombreCompleto,
        @Schema(description = "Documento enmascarado", example = "****1236") String documentoEnmascarado,
        String tipoDocumento,
        String telefono,
        String fotoUrl,
        String placaVehiculo,
        String fotoVehiculoUrl,
        @Schema(example = "HABILITADO") String estadoHabilitacion,
        Boolean cuentaActiva,
        BigDecimal valoracionPromedio,
        Integer totalValoraciones,
        @Schema(example = "DISPONIBLE") String disponibilidad,
        Double latitud,
        Double longitud,
        LocalDateTime fechaRegistro
) {}
