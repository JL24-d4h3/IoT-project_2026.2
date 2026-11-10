package com.taxi.gestion.rest;

import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.dto.request.EstadoCuentaTaxistaRequest;
import com.taxi.gestion.dto.request.HabilitacionTaxistaRequest;
import com.taxi.gestion.dto.response.TaxistaAdminResponse;
import com.taxi.gestion.mapper.TaxistaMapper;
import com.taxi.gestion.service.TaxistaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/admin/taxistas", produces = "application/json")
@RequiredArgsConstructor
@SecurityRequirement(name = "firebaseAdminAuth")
@Tag(name = "Administracion de taxistas")
public class AdminTaxistaRestController {

    private final TaxistaService servicio;
    private final TaxistaMapper mapper;

    @GetMapping
    public List<TaxistaAdminResponse> listar() {
        return servicio.listarTodos().stream().map(mapper::aAdministrativo).toList();
    }

    @PutMapping(value = "/{id}/habilitacion", consumes = "application/json")
    public TaxistaAdminResponse habilitacion(@PathVariable Long id,
            @Valid @RequestBody HabilitacionTaxistaRequest request) {
        return mapper.aAdministrativo(servicio.cambiarHabilitacion(id,
                request.habilitado() ? EstadoHabilitacion.HABILITADO : EstadoHabilitacion.PENDIENTE));
    }

    @PutMapping(value = "/{id}/cuenta", consumes = "application/json")
    public TaxistaAdminResponse cuenta(@PathVariable Long id,
            @Valid @RequestBody EstadoCuentaTaxistaRequest request) {
        return mapper.aAdministrativo(servicio.cambiarEstadoCuenta(id, request.activa()));
    }
}
