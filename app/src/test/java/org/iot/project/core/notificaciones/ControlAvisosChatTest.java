package org.iot.project.core.notificaciones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.AppNotification;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ControlAvisosChatTest {

    private final ControlAvisosChat control = new ControlAvisosChat();

    @Test
    public void primeraCargaNoPublicaElHistorialAunqueElLlamadorNoMarqueInicial() {
        AppNotification historico = mensaje("historico", "reserva");

        assertTrue(control.recibir(Collections.singletonList(historico), false, null).isEmpty());
        assertTrue(control.recibir(Collections.singletonList(historico), false, null).isEmpty());
    }

    @Test
    public void cacheVaciaYPrimerServidorNoGeneranUnaRafagaDeHistorial() {
        AppNotification antiguo = mensaje("antiguo", "reserva");
        AppNotification nuevo = mensaje("nuevo", "reserva");

        assertTrue(control.recibir(Collections.emptyList(), true, null).isEmpty());
        assertTrue(control.recibir(Collections.singletonList(antiguo), true, null).isEmpty());
        assertEquals(Collections.singletonList(nuevo),
                control.recibir(Arrays.asList(nuevo, antiguo), false, null));
    }

    @Test
    public void mensajesQueYaVenianEnCacheNoSeAnuncianAlConfirmarElServidor() {
        AppNotification cache = mensaje("cache", "reserva");
        AppNotification servidor = mensaje("servidor", "reserva");

        assertTrue(control.recibir(Collections.singletonList(cache), true, null).isEmpty());
        assertTrue(control.recibir(Arrays.asList(cache, servidor), true, null).isEmpty());
        assertTrue(control.recibir(Arrays.asList(cache, servidor), false, null).isEmpty());
    }

    @Test
    public void consumeDuplicadosDentroDeUnLoteYEnActualizacionesPosteriores() {
        establecerBase();
        AppNotification nuevo = mensaje("mensaje", "reserva");

        assertEquals(Collections.singletonList(nuevo),
                control.recibir(Arrays.asList(nuevo, nuevo), false, null));
        assertTrue(control.recibir(Collections.singletonList(nuevo), false, null).isEmpty());
        assertFalse(nuevo.isLeida());
    }

    @Test
    public void losMensajesDelChatAbiertoNoAparecenDespuesDeCerrarEseChat() {
        establecerBase();
        AppNotification visible = mensaje("visible", "chat-abierto");
        AppNotification otro = mensaje("otro", "otro-chat");
        List<AppNotification> lote = Arrays.asList(visible, otro);

        assertEquals(Collections.singletonList(otro),
                control.recibir(lote, false, "chat-abierto"));
        assertTrue(control.recibir(lote, false, null).isEmpty());
    }

    @Test
    public void unAvisoLeidoNoSePublicaAunqueUnaActualizacionLoMarqueNoLeido() {
        establecerBase();
        AppNotification leido = mensaje("leido", "reserva");
        leido.setLeida(true);

        assertTrue(control.recibir(Collections.singletonList(leido), false, null).isEmpty());
        leido.setLeida(false);
        assertTrue(control.recibir(Collections.singletonList(leido), false, null).isEmpty());
    }

    @Test
    public void noConvierteOtrosEventosDeLaBandejaEnAvisosDeChat() {
        establecerBase();
        for (AppNotification.Tipo tipo : AppNotification.Tipo.values()) {
            if (tipo == AppNotification.Tipo.MENSAJE) continue;
            AppNotification otro = aviso(tipo.name(), tipo, "reserva");
            assertTrue(control.recibir(Collections.singletonList(otro), false, null).isEmpty());
        }
    }

    @Test
    public void descartaIdsODestinosAusentesOInvalidos() {
        establecerBase();
        int indice = 0;
        for (String invalido : Arrays.asList(null, "", "  ", ".", "..", "ruta/reserva",
                String.join("", Collections.nCopies(301, "x")))) {
            assertTrue(control.recibir(Arrays.asList(null, mensaje(invalido, "reserva"),
                    mensaje("aviso-" + indice++, invalido)), false, null).isEmpty());
        }
    }

    @Test
    public void concederPermisoNoReproduceCandidatosDescartadosSinPermiso() {
        establecerBase();
        AppNotification sinPermiso = mensaje("sin-permiso", "reserva");
        AppNotification conPermiso = mensaje("con-permiso", "reserva");

        // Android descarta el primer resultado si el permiso aún está denegado.
        control.recibir(Collections.singletonList(sinPermiso), false, null);
        assertEquals(Collections.singletonList(conPermiso), control.recibir(
                Arrays.asList(sinPermiso, conPermiso), false, null));
    }

    @Test
    public void noRepiteIdsQueDesaparecenTemporalmenteDeLaConsulta() {
        establecerBase();
        AppNotification aviso = mensaje("estable", "reserva");
        assertEquals(Collections.singletonList(aviso),
                control.recibir(Collections.singletonList(aviso), false, null));

        control.recibir(Collections.emptyList(), false, null);
        assertTrue(control.recibir(Collections.singletonList(aviso), false, null).isEmpty());
    }

    @Test
    public void cambiarCuentaReiniciaTantoBaseComoIdsProcesados() {
        establecerBase();
        AppNotification mismoId = mensaje("compartido", "reserva");
        control.recibir(Collections.singletonList(mismoId), false, null);

        control.reiniciar();
        AppNotification historicoOtraCuenta = mensaje("historico-otra-cuenta", "reserva");
        assertTrue(control.recibir(Collections.singletonList(historicoOtraCuenta),
                false, null).isEmpty());
        assertEquals(Collections.singletonList(mismoId), control.recibir(
                Arrays.asList(historicoOtraCuenta, mismoId), false, null));
    }

    @Test
    public void unResultadoNuloNoConvierteLaSiguienteCargaEnAvisosHistoricos() {
        assertTrue(control.recibir(null, false, null).isEmpty());
        assertTrue(control.recibir(Collections.singletonList(mensaje("antiguo", "reserva")),
                false, null).isEmpty());
    }

    private void establecerBase() {
        control.recibir(Collections.emptyList(), true, null);
    }

    private static AppNotification mensaje(String id, String bookingId) {
        return aviso(id, AppNotification.Tipo.MENSAJE, bookingId);
    }

    private static AppNotification aviso(String id, AppNotification.Tipo tipo, String bookingId) {
        return new AppNotification(id, tipo, "Mensaje del hotel", "Nuevo mensaje",
                LocalDateTime.of(2026, 10, 22, 12, 0), bookingId, null);
    }
}
