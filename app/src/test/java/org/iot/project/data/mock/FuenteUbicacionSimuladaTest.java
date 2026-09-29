package org.iot.project.data.mock;

import static org.junit.Assert.assertTrue;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Ubicacion;
import org.iot.project.utils.Distancia;
import org.junit.Test;

public class FuenteUbicacionSimuladaTest {

    private final FuenteUbicacionSimulada fuente = new FuenteUbicacionSimulada();

    private static Driver conductor(String id) {
        return MockData.conductor(id);
    }

    /** Sin viaje en curso, el conductor esta en su base. */
    @Test
    public void unConductorLibreEstaEnSuBase() {
        Driver d1 = conductor("D1");
        Ubicacion base = fuente.posicion(d1, null);
        assertTrue("la base de D1 deberia estar en Lima",
                base.getLatitud() < -12d && base.getLatitud() > -13d);
    }

    /**
     * Durante un servicio, el conductor se mueve hacia el punto de recojo: un
     * conductor que se aleja no es un conductor que viene a recogerte.
     */
    @Test
    public void duranteUnServicioSeAcercaAlPuntoDeRecojo() {
        Driver d1 = conductor("D1");
        TaxiService servicio = new TaxiService("T99", "TAX-2026-9998", "B1", "U1");
        servicio.withRecojo(-12.1219, -77.0297).asignarA(d1);
        servicio.avanzarA(TaxiStatus.EN_CAMINO);

        Ubicacion primera = fuente.posicion(d1, servicio);
        servicio.actualizarUbicacion(primera.getLatitud(), primera.getLongitud(),
                java.time.LocalDateTime.now());
        Ubicacion segunda = fuente.posicion(d1, servicio);

        double antes = Distancia.metrosEntre(
                primera.getLatitud(), primera.getLongitud(), -12.1219, -77.0297);
        double despues = Distancia.metrosEntre(
                segunda.getLatitud(), segunda.getLongitud(), -12.1219, -77.0297);
        assertTrue("deberia acercarse: " + antes + " -> " + despues, despues < antes);
    }

    @Test
    public void nuncaDevuelveCeroCero() {
        Ubicacion base = fuente.posicion(conductor("D3"), null);
        assertTrue(base.getLatitud() != 0d || base.getLongitud() != 0d);
    }

    @Test
    public void todosLosConductoresSembradosTienenBase() {
        for (Driver d : MockData.CONDUCTORES) {
            assertTrue("sin base: " + d.getId(), fuente.posicion(d, null) != null);
        }
    }
}
