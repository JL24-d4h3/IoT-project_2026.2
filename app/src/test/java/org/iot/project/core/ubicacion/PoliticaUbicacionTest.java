package org.iot.project.core.ubicacion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PoliticaUbicacionTest {
    @Test
    public void aceptaUnPuntoGpsRecienteConPrecisionUtil() {
        assertTrue(PoliticaUbicacion.posicionValida(-12.0464, -77.0428, 8.5f, 5_000L));
        assertTrue(PoliticaUbicacion.posicionValida(0d, 0d, 100f, 120_000L));
        assertTrue(PoliticaUbicacion.posicionValida(0d, 0d, 1f, 0L));
    }

    @Test
    public void aceptaLosLimitesGeograficosYRechazaElPrimerValorFuera() {
        assertTrue(PoliticaUbicacion.posicionValida(-90d, -180d, 10f, 0L));
        assertTrue(PoliticaUbicacion.posicionValida(90d, 180d, 10f, 0L));
        assertFalse(PoliticaUbicacion.posicionValida(Math.nextDown(-90d), 0d, 10f, 0L));
        assertFalse(PoliticaUbicacion.posicionValida(Math.nextUp(90d), 0d, 10f, 0L));
        assertFalse(PoliticaUbicacion.posicionValida(0d, Math.nextDown(-180d), 10f, 0L));
        assertFalse(PoliticaUbicacion.posicionValida(0d, Math.nextUp(180d), 10f, 0L));
    }

    @Test
    public void rechazaCoordenadasQueNoRepresentanUnNumeroFinito() {
        for (double valor : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(PoliticaUbicacion.posicionValida(valor, -77d, 10f, 0L));
            assertFalse(PoliticaUbicacion.posicionValida(-12d, valor, 10f, 0L));
        }
    }

    @Test
    public void unaUbicacionImprecisaOConPrecisionAusenteNoSeTransmite() {
        for (float valor : new float[]{0f, -1f, Math.nextUp(100f), Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(PoliticaUbicacion.posicionValida(-12d, -77d, valor, 1_000L));
        }
    }

    @Test
    public void unaEdadNegativaOUnPuntoAntiguoNoSeAceptanAunqueLasCoordenadasSeanValidas() {
        assertFalse(PoliticaUbicacion.posicionValida(-12d, -77d, 10f, -1L));
        assertFalse(PoliticaUbicacion.posicionValida(-12d, -77d, 10f, 120_001L));
        assertFalse(PoliticaUbicacion.posicionValida(-12d, -77d, 10f, Long.MAX_VALUE));
    }

    @Test
    public void revocarPermisoPrecisoOApagarGpsRetiraLasOfertasAunqueElUltimoEnvioSeaReciente() {
        assertTrue(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, 5_000L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(false, true, true, 5_000L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(true, false, true, 5_000L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(false, false, true, 5_000L));
    }

    @Test
    public void concederPermisoYActivarGpsNoBastanHastaConfirmarElEnvio() {
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(true, true, false, 0L));
        assertTrue(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, 0L));
    }

    @Test
    public void lasOfertasCaducanYUnNuevoReporteConfirmadoLasVuelveAHabilitar() {
        assertTrue(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, 120_000L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, 120_001L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, -1L));
        assertFalse(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, Long.MAX_VALUE));
        assertTrue(PoliticaUbicacion.puedeMostrarOfertas(true, true, true, 1_000L));
    }
}
