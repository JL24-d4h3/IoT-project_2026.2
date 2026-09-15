package org.iot.project.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * RF-106 a RF-111: el flujo de estados del taxi es exacto y no admite saltos
 * ni retrocesos (RC-016).
 */
public class TaxiStatusTest {

    @Test
    public void elFlujoCompletoEsValidoPasoAPaso() {
        TaxiStatus estado = TaxiStatus.SOLICITADO;

        for (TaxiStatus siguiente : new TaxiStatus[]{
                TaxiStatus.ASIGNADO,
                TaxiStatus.EN_CAMINO,
                TaxiStatus.EN_TRASLADO,
                TaxiStatus.FINALIZADO}) {
            assertTrue(estado + " -> " + siguiente, estado.canTransitionTo(siguiente));
            estado = siguiente;
        }
    }

    @Test
    public void saltarseUnEstadoSeRechaza() {
        assertFalse(TaxiStatus.SOLICITADO.canTransitionTo(TaxiStatus.EN_CAMINO));
        assertFalse(TaxiStatus.SOLICITADO.canTransitionTo(TaxiStatus.EN_TRASLADO));
        assertFalse(TaxiStatus.SOLICITADO.canTransitionTo(TaxiStatus.FINALIZADO));
        assertFalse(TaxiStatus.ASIGNADO.canTransitionTo(TaxiStatus.EN_TRASLADO));
    }

    @Test
    public void retrocederSeRechaza() {
        assertFalse(TaxiStatus.ASIGNADO.canTransitionTo(TaxiStatus.SOLICITADO));
        assertFalse(TaxiStatus.FINALIZADO.canTransitionTo(TaxiStatus.EN_TRASLADO));
    }

    @Test
    public void unEstadoNoTransicionaASiMismo() {
        for (TaxiStatus estado : TaxiStatus.values()) {
            assertFalse(estado.name(), estado.canTransitionTo(estado));
        }
    }

    @Test
    public void elUltimoEstadoNoTieneSiguiente() {
        assertEquals(TaxiStatus.FINALIZADO, TaxiStatus.FINALIZADO.next());
        assertEquals(TaxiStatus.ASIGNADO, TaxiStatus.SOLICITADO.next());
    }

    @Test
    public void elQrSoloSeMuestraCuandoElConductorEstaEnCaminoOEnTraslado() {
        assertFalse(TaxiStatus.SOLICITADO.allowsQrDisplay());
        assertFalse(TaxiStatus.ASIGNADO.allowsQrDisplay());
        assertTrue(TaxiStatus.EN_CAMINO.allowsQrDisplay());
        assertTrue(TaxiStatus.EN_TRASLADO.allowsQrDisplay());

        // RF-110: FINALIZADO se alcanza por el QR, no se muestra QR estando ahi.
        assertFalse(TaxiStatus.FINALIZADO.allowsQrDisplay());
    }

    @Test
    public void elServicioSigueActivoHastaFinalizar() {
        assertTrue(TaxiStatus.ASIGNADO.isActive());
        assertTrue(TaxiStatus.EN_TRASLADO.isActive());
        assertFalse(TaxiStatus.FINALIZADO.isActive());
    }
}
