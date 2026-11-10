package com.taxi.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Vista ligera para listados (por ejemplo, taxistas disponibles en el mapa).
 * No incluye correo, documento ni domicilio.
 */
@Schema(description = "Resumen publico de un taxista disponible")
public record TaxistaResumenResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "Luis Alberto Ramirez Quispe") String nombreCompleto,
        @Schema(example = "ABC-123") String placaVehiculo,
        String fotoUrl,
        String fotoVehiculoUrl,
        @Schema(example = "4.72") BigDecimal valoracionPromedio,
        @Schema(example = "38") Integer totalValoraciones,
        @Schema(example = "-12.096540") Double latitud,
        @Schema(example = "-77.036110") Double longitud,
        @Schema(example = "DISPONIBLE") String disponibilidad
) {}
