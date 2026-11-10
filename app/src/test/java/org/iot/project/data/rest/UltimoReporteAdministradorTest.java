package org.iot.project.data.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.junit.Test;

import java.time.LocalDateTime;

/** Una posición observada de este viaje no desaparece ni se mezcla con otro al finalizar. */
public class UltimoReporteAdministradorTest {
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 26, 10, 15);

    @Test public void conservaUltimoPuntoDelTrasladoAlLlegarFinalizadoSinPosicion() {
        TaxiService traslado = taxi("taxi-1", "25", "73", TaxiStatus.EN_TRASLADO);
        traslado.actualizarUbicacion(-12.1, -77.1, HORA);
        TaxiApiRepository.UltimoReporteAdministrador reporte = new TaxiApiRepository.UltimoReporteAdministrador(traslado);
        TaxiService finalizado = taxi("taxi-1", "25", "73", TaxiStatus.FINALIZADO);
        reporte.conservarEn(finalizado);
        assertEquals(TaxiStatus.FINALIZADO, finalizado.getEstado());
        assertEquals(HORA, finalizado.getUltimaActualizacionUbicacion());
        assertEquals(-12.1, finalizado.getLatConductor(), 0d);
        assertEquals(-77.1, finalizado.getLngConductor(), 0d);
    }

    @Test public void reporteMasNuevoDeServidorNoRetrocedePorLaCache() {
        TaxiService previo = taxi("taxi-1", "25", "73", TaxiStatus.EN_CAMINO);
        previo.actualizarUbicacion(-12.1, -77.1, HORA);
        TaxiApiRepository.UltimoReporteAdministrador reporte = new TaxiApiRepository.UltimoReporteAdministrador(previo);
        TaxiService nuevo = taxi("taxi-1", "25", "73", TaxiStatus.EN_TRASLADO);
        nuevo.actualizarUbicacion(-12.2, -77.2, HORA.plusSeconds(10));
        reporte.conservarEn(nuevo);
        assertEquals(HORA.plusSeconds(10), nuevo.getUltimaActualizacionUbicacion());
        assertEquals(-12.2, nuevo.getLatConductor(), 0d);
    }

    @Test public void lecturaViejaNoSustituyeElUltimoReporteConfirmado() {
        TaxiService previo = taxi("taxi-1", "25", "73", TaxiStatus.EN_TRASLADO);
        previo.actualizarUbicacion(-12.1, -77.1, HORA);
        TaxiApiRepository.UltimoReporteAdministrador reporte = new TaxiApiRepository.UltimoReporteAdministrador(previo);
        TaxiService viejo = taxi("taxi-1", "25", "73", TaxiStatus.FINALIZADO);
        viejo.actualizarUbicacion(-12.0, -77.0, HORA.minusSeconds(10));
        reporte.conservarEn(viejo);
        assertEquals(HORA, viejo.getUltimaActualizacionUbicacion());
        assertEquals(-12.1, viejo.getLatConductor(), 0d);
    }

    @Test public void noReutilizaUbicacionDeOtroViajeNiDeOtroConductor() {
        TaxiService previo = taxi("taxi-1", "25", "73", TaxiStatus.EN_CAMINO);
        previo.actualizarUbicacion(-12.1, -77.1, HORA);
        TaxiApiRepository.UltimoReporteAdministrador reporte = new TaxiApiRepository.UltimoReporteAdministrador(previo);
        TaxiService[] otros = {
                taxi("taxi-2", "25", "73", TaxiStatus.EN_CAMINO),
                taxi("taxi-1", "26", "73", TaxiStatus.EN_CAMINO),
                taxi("taxi-1", "25", "74", TaxiStatus.EN_CAMINO),
                taxi("taxi-1", null, "73", TaxiStatus.EN_CAMINO),
                taxi("taxi-1", "25", "73", TaxiStatus.SOLICITADO).withEstadoPersistido(TaxiStatus.SOLICITADO, null, 0)
        };
        for (TaxiService otro : otros) {
            reporte.conservarEn(otro);
            assertNull(otro.getUltimaActualizacionUbicacion());
        }
    }

    private static TaxiService taxi(String id, String apiId, String driverId, TaxiStatus estado) {
        return new TaxiService(id, "", "reserva-1", "cliente-1").withApiId(apiId)
                .withEstadoPersistido(estado, new Driver(driverId, "Julio", "Soto"), 0);
    }
}
