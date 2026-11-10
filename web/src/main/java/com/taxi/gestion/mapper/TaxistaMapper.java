package com.taxi.gestion.mapper;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.dto.response.TaxistaResponse;
import com.taxi.gestion.dto.response.TaxistaAdminResponse;
import com.taxi.gestion.dto.response.TaxistaResumenResponse;
import com.taxi.gestion.dto.response.UbicacionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Unico punto donde la entidad se convierte en salida publica. */
@Component
public class TaxistaMapper {

    public TaxistaAdminResponse aAdministrativo(Taxista t) {
        TaxistaResponse d = aDetalle(t);
        return new TaxistaAdminResponse(d.id(), d.nombres(), d.apellidos(), d.nombreCompleto(),
                d.documentoEnmascarado(), d.tipoDocumento(), d.telefono(), d.fotoUrl(),
                d.placaVehiculo(), d.fotoVehiculoUrl(), d.estadoHabilitacion(), d.cuentaActiva(),
                d.valoracionPromedio(), d.totalValoraciones(), d.disponibilidad(),
                d.latitud(), d.longitud(), d.fechaRegistro(), t.getCorreo());
    }

    public TaxistaResumenResponse aResumen(Taxista t) {
        return new TaxistaResumenResponse(
                t.getId(),
                t.getNombreCompleto(),
                t.getPlacaVehiculo(),
                t.getFotoUrl(),
                t.getFotoVehiculoUrl(),
                t.getValoracionPromedio(),
                t.getTotalValoraciones(),
                t.getLatitud(),
                t.getLongitud(),
                t.getDisponibilidad().name()
        );
    }

    public List<TaxistaResumenResponse> aResumenes(List<Taxista> taxistas) {
        return taxistas.stream().map(this::aResumen).toList();
    }

    public TaxistaResponse aDetalle(Taxista t) {
        return new TaxistaResponse(
                t.getId(),
                t.getNombres(),
                t.getApellidos(),
                t.getNombreCompleto(),
                enmascarar(t.getNumeroDocumento()),
                t.getTipoDocumento().name(),
                t.getTelefono(),
                t.getFotoUrl(),
                t.getPlacaVehiculo(),
                t.getFotoVehiculoUrl(),
                t.getEstadoHabilitacion().name(),
                t.getCuentaActiva(),
                t.getValoracionPromedio(),
                t.getTotalValoraciones(),
                t.getDisponibilidad().name(),
                t.getLatitud(),
                t.getLongitud(),
                t.getFechaRegistro()
        );
    }

    public UbicacionResponse aUbicacion(Taxista t) {
        return new UbicacionResponse(
                t.getId(), t.getLatitud(), t.getLongitud(), t.getUbicacionActualizadaEn());
    }

    /** Deja visibles solo los ultimos 4 caracteres del documento. */
    private String enmascarar(String documento) {
        if (documento == null || documento.length() <= 4) {
            return "****";
        }
        return "****" + documento.substring(documento.length() - 4);
    }
}
