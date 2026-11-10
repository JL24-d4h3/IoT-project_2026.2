package com.taxi.gestion.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EstadoServicioTest {

    @Test
    @DisplayName("El flujo completo del viaje es valido paso a paso")
    void flujoCompletoEsValido() {
        assertTrue(EstadoServicio.SOLICITADO.permiteTransicionA(EstadoServicio.ASIGNADO));
        assertTrue(EstadoServicio.ASIGNADO.permiteTransicionA(EstadoServicio.EN_CAMINO));
        assertTrue(EstadoServicio.EN_CAMINO.permiteTransicionA(EstadoServicio.EN_TRASLADO));
        assertTrue(EstadoServicio.EN_TRASLADO.permiteTransicionA(EstadoServicio.FINALIZADO));
    }

    @Test
    @DisplayName("Los saltos fuera de secuencia se rechazan")
    void saltosSeRechazan() {
        assertFalse(EstadoServicio.SOLICITADO.permiteTransicionA(EstadoServicio.EN_TRASLADO));
        assertFalse(EstadoServicio.ASIGNADO.permiteTransicionA(EstadoServicio.FINALIZADO));
        assertFalse(EstadoServicio.EN_CAMINO.permiteTransicionA(EstadoServicio.ASIGNADO));
    }

    @Test
    @DisplayName("FINALIZADO es terminal")
    void finalizadoEsTerminal() {
        assertTrue(EstadoServicio.FINALIZADO.esTerminal());
        assertTrue(EstadoServicio.FINALIZADO.siguientesPosibles().isEmpty());
    }
}
