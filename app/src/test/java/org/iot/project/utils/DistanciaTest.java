package org.iot.project.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DistanciaTest {

    // Coordenadas reales de los datos sembrados (MockData).
    private static final double H1_LAT = -12.1219, H1_LNG = -77.0297;  // Casa del Mar, Lima
    private static final double H4_LAT = -13.5156, H4_LNG = -71.9785;  // Posada Cusco
    private static final double BASE_D1_LAT = -12.1060, BASE_D1_LNG = -77.0360;

    @Test
    public void laDistanciaDeUnPuntoASiMismoEsCero() {
        assertEquals(0d, Distancia.metrosEntre(H1_LAT, H1_LNG, H1_LAT, H1_LNG), 0.5d);
    }

    /**
     * Lima y Cusco estan a 569 km. Es el numero que justifica el umbral de
     * cercania: si esta prueba cambiara, el umbral habria que revisarlo.
     */
    @Test
    public void limaACuscoRondaLosQuinientosSetentaKilometros() {
        double km = Distancia.metrosEntre(H1_LAT, H1_LNG, H4_LAT, H4_LNG) / 1000d;
        assertTrue("esperado ~569 km, fue " + km, km > 560d && km < 580d);
    }

    @Test
    public void dosPuntosDeLaMismaCiudadQuedanMuyPorDebajoDeCienKilometros() {
        double km = Distancia.metrosEntre(BASE_D1_LAT, BASE_D1_LNG, H1_LAT, H1_LNG) / 1000d;
        assertTrue("esperado ~1,9 km, fue " + km, km < 5d);
    }

    @Test
    public void porDebajoDeUnKilometroSeLeeEnMetrosRedondeadosADiez() {
        assertEquals("850 m", Distancia.legible(847d));
        assertEquals("0 m", Distancia.legible(3d));
    }

    @Test
    public void aPartirDeUnKilometroSeLeeEnKilometrosConUnaDecimal() {
        assertEquals("1,9 km", Distancia.legible(1896d));
        assertEquals("1,0 km", Distancia.legible(1000d));
    }
}
