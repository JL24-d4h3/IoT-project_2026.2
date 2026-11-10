package org.iot.project.ui.client.taxi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

public class TaxiViewModelSeleccionTest {

    @Test
    public void ultimoFinalizadoSeVeSinConvertirseEnServicioActivo() {
        TaxiService antiguo = servicio("1", TaxiStatus.FINALIZADO, 20, 10);
        TaxiService ultimo = servicio("2", TaxiStatus.FINALIZADO, 22, 12);
        TaxiService intermedio = servicio("3", TaxiStatus.FINALIZADO, 21, 17);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(antiguo, ultimo, intermedio), null);

        assertNull(estado.activo);
        assertFalse(estado.tieneActivo());
        assertSame(ultimo, estado.principal);
        assertTrue(estado.tienePrincipal());
        assertEquals(3, estado.anteriores.size());
        assertTrue(estado.anteriores.contains(ultimo));
        assertTrue(estado.pendientesEnvio.isEmpty());
    }

    @Test
    public void detalleFinalizadoEnfocadoConservaActivoRealYValoracionEnHistorial() {
        TaxiService activo = servicio("4", TaxiStatus.EN_CAMINO, 23, 9);
        TaxiService terminado = servicio("5", TaxiStatus.FINALIZADO, 20, 9);
        terminado.valorar(8f);
        TaxiService otroTerminado = servicio("6", TaxiStatus.FINALIZADO, 22, 9);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(activo, terminado, otroTerminado), terminado.getId());

        assertSame(terminado, estado.principal);
        assertSame(activo, estado.activo);
        assertTrue(estado.tieneActivo());
        assertTrue(estado.anteriores.contains(terminado));
        assertTrue(estado.anteriores.contains(otroTerminado));
        assertFalse(estado.anteriores.contains(activo));
        assertEquals(8f, terminado.getRatingCliente(), 0f);
    }

    @Test
    public void focoEnOtroActivoLoSeleccionaSinPerderElViajeMasAvanzado() {
        TaxiService enTraslado = servicio("7", TaxiStatus.EN_TRASLADO, 22, 10);
        TaxiService enfocado = servicio("8", TaxiStatus.ASIGNADO, 23, 15);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(enTraslado, enfocado), enfocado.getId());

        assertSame(enfocado, estado.activo);
        assertSame(enfocado, estado.principal);
        assertTrue(estado.anteriores.contains(enTraslado));
        assertFalse(estado.anteriores.contains(enfocado));
    }

    @Test
    public void solicitudSinVinculoRestNoApareceComoPrincipalAunqueEsteEnfocada() {
        TaxiService pendiente = servicio("9", TaxiStatus.SOLICITADO, 22, 10).withApiId(null);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Collections.singletonList(pendiente), pendiente.getId());

        assertNull(estado.activo);
        assertNull(estado.principal);
        assertFalse(estado.tienePrincipal());
        assertEquals(Collections.singletonList(pendiente), estado.pendientesEnvio);
        assertTrue(estado.anteriores.isEmpty());
    }

    @Test
    public void sinFocoSePriorizaElEstadoActivoMasAvanzadoSobreLaFecha() {
        TaxiService solicitado = servicio("10", TaxiStatus.SOLICITADO, 20, 8);
        TaxiService asignado = servicio("11", TaxiStatus.ASIGNADO, 20, 9);
        TaxiService enCamino = servicio("12", TaxiStatus.EN_CAMINO, 21, 10);
        TaxiService enTraslado = servicio("13", TaxiStatus.EN_TRASLADO, 23, 17);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(enCamino, solicitado, enTraslado, asignado), null);

        assertSame(enTraslado, estado.activo);
        assertSame(enTraslado, estado.principal);
        assertEquals(3, estado.anteriores.size());
    }

    @Test
    public void entreActivosDelMismoEstadoSePriorizaLaProgramacionMasProxima() {
        TaxiService diaPosterior = servicio("14", TaxiStatus.EN_CAMINO, 23, 8);
        TaxiService horaPosterior = servicio("15", TaxiStatus.EN_CAMINO, 22, 16);
        TaxiService primero = servicio("16", TaxiStatus.EN_CAMINO, 22, 9);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(diaPosterior, horaPosterior, primero), null);

        assertSame(primero, estado.activo);
        assertSame(primero, estado.principal);
        assertTrue(estado.anteriores.contains(diaPosterior));
        assertTrue(estado.anteriores.contains(horaPosterior));
    }

    @Test
    public void focoInexistenteVuelveAlActivoAntesQueAlFinalizado() {
        TaxiService activo = servicio("17", TaxiStatus.SOLICITADO, 21, 8);
        TaxiService finalizado = servicio("18", TaxiStatus.FINALIZADO, 23, 20);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(finalizado, activo), "viaje-que-no-existe");

        assertSame(activo, estado.activo);
        assertSame(activo, estado.principal);
        assertTrue(estado.anteriores.contains(finalizado));
    }

    @Test
    public void focoInexistenteSinActivoVuelveAlUltimoFinalizado() {
        TaxiService antiguo = servicio("19", TaxiStatus.FINALIZADO, 22, 8);
        TaxiService ultimo = servicio("20", TaxiStatus.FINALIZADO, 22, 19);

        TaxiViewModel.Estado estado = TaxiViewModel.separarServicios(
                Arrays.asList(ultimo, antiguo), "viaje-que-no-existe");

        assertNull(estado.activo);
        assertSame(ultimo, estado.principal);
        assertEquals(2, estado.anteriores.size());
    }

    private static TaxiService servicio(String id, TaxiStatus estado, int dia, int hora) {
        Driver conductor = estado == TaxiStatus.SOLICITADO ? null : new Driver("3", "Ana", "Pérez");
        return new TaxiService("taxi-local-" + id, "", "reserva-" + id, "cliente-prueba")
                .withApiId(id)
                .withProgramacion(LocalDate.of(2026, 10, dia), LocalTime.of(hora, 0), false)
                .withEstadoPersistido(estado, conductor, 0f);
    }
}
