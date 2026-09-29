package org.iot.project.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TaxiConductorTest {

    private static TaxiService servicioSolicitado() {
        return new TaxiService("T9", "TAX-2026-9999", "B1", "U1");
    }

    private static Driver conductorHabilitado() {
        Driver d = new Driver("D9", "Ana", "Prueba");
        d.setHabilitado(true);
        return d;
    }

    private static Driver conductorNoHabilitado() {
        Driver d = new Driver("D8", "Beto", "Prueba");
        d.setHabilitado(false);
        return d;
    }

    // ---------------------------------------------------------- Aceptar

    @Test
    public void unConductorHabilitadoPuedeAceptarUnServicioSolicitado() {
        assertTrue(servicioSolicitado().puedeAceptarlo(conductorHabilitado()));
    }

    /** RF-077, RT-014: sin la aprobacion del Superadmin no se presta servicio. */
    @Test
    public void unConductorNoHabilitadoNoPuedeAceptar() {
        assertFalse(servicioSolicitado().puedeAceptarlo(conductorNoHabilitado()));
    }

    /** RF-092: un servicio ya asignado deja de estar disponible. */
    @Test
    public void unServicioYaAsignadoNoSePuedeAceptarOtraVez() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        assertFalse(servicio.puedeAceptarlo(conductorHabilitado()));
    }

    @Test
    public void unServicioYaIniciadoNoSePuedeAceptar() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarA(TaxiStatus.EN_CAMINO);
        assertFalse(servicio.puedeAceptarlo(conductorHabilitado()));
    }

    @Test
    public void sinConductorNoSePuedeAceptar() {
        assertFalse(servicioSolicitado().puedeAceptarlo(null));
    }

    // ------------------------------------------------- Avanzar como conductor

    @Test
    public void elConductorAvanzaDeAsignadoAEnCaminoYDeAhiAEnTraslado() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarPorConductor(TaxiStatus.EN_CAMINO);
        servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
        assertTrue(servicio.getEstado() == TaxiStatus.EN_TRASLADO);
    }

    /**
     * RF-110: FINALIZADO solo se alcanza validando el codigo. Sin este rechazo,
     * canTransitionTo lo permitiria desde EN_TRASLADO y la regla quedaria solo
     * en la pantalla, que es donde no se puede probar.
     */
    @Test
    public void elConductorNoPuedeFinalizarAMano() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        servicio.avanzarPorConductor(TaxiStatus.EN_CAMINO);
        servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
        try {
            servicio.avanzarPorConductor(TaxiStatus.FINALIZADO);
            fail("deberia haber rechazado la finalizacion manual");
        } catch (IllegalStateException esperado) {
            assertTrue(servicio.getEstado() == TaxiStatus.EN_TRASLADO);
        }
    }

    /** RF-111: los saltos siguen prohibidos para el conductor. */
    @Test
    public void elConductorNoPuedeSaltarseEstados() {
        TaxiService servicio = servicioSolicitado();
        servicio.asignarA(conductorHabilitado());
        try {
            servicio.avanzarPorConductor(TaxiStatus.EN_TRASLADO);
            fail("deberia haber rechazado el salto");
        } catch (IllegalStateException esperado) {
            assertTrue(servicio.getEstado() == TaxiStatus.ASIGNADO);
        }
    }

    // ------------------------------------------------- Validar el codigo

    @Test
    public void elCodigoCorrectoValida() {
        assertTrue(servicioSolicitado().validarCodigo("TAX-2026-9999"));
    }

    @Test
    public void elCodigoSeAceptaConEspaciosYEnMinusculas() {
        assertTrue(servicioSolicitado().validarCodigo("  tax-2026-9999  "));
    }

    @Test
    public void unCodigoDeOtroServicioNoValida() {
        assertFalse(servicioSolicitado().validarCodigo("TAX-2026-0733"));
    }

    @Test
    public void unCodigoVacioNoValida() {
        assertFalse(servicioSolicitado().validarCodigo(null));
        assertFalse(servicioSolicitado().validarCodigo(""));
        assertFalse(servicioSolicitado().validarCodigo("   "));
    }
}
