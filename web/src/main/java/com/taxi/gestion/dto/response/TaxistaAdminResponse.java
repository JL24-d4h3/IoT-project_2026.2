package com.taxi.gestion.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Contrato administrativo plano: detalle publico mas correo, sin entidad ni hash. */
public record TaxistaAdminResponse(
        Long id, String nombres, String apellidos, String nombreCompleto,
        String documentoEnmascarado, String tipoDocumento, String telefono,
        String fotoUrl, String placaVehiculo, String fotoVehiculoUrl,
        String estadoHabilitacion, Boolean cuentaActiva, BigDecimal valoracionPromedio,
        Integer totalValoraciones, String disponibilidad, Double latitud, Double longitud,
        LocalDateTime fechaRegistro, String correo) {}
