package com.taxi.gestion.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificadorSuperadminFirebaseTest {

    private final TokensFirebaseAdmin tokens = mock(TokensFirebaseAdmin.class);

    @Test
    void proyectoVacioFallaCerradoSinInicializarFirebase() {
        var verificador = new VerificadorSuperadminFirebase(" ", "uid-1", tokens);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(FalloAutenticacionAdmin.class, () -> verificador.verificar("token")).getStatus());
        verifyNoInteractions(tokens);
    }

    @Test
    void allowlistVaciaFallaCerradoSinInicializarFirebase() {
        var verificador = new VerificadorSuperadminFirebase("proyecto-test", " , ", tokens);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(FalloAutenticacionAdmin.class, () -> verificador.verificar("token")).getStatus());
        verifyNoInteractions(tokens);
    }

    @Test
    void tokenVacioNoSeEnvuelveNiVerifica() {
        var verificador = new VerificadorSuperadminFirebase("proyecto-test", "uid-1", tokens);
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(FalloAutenticacionAdmin.class, () -> verificador.verificar(" ")).getStatus());
        verifyNoInteractions(tokens);
    }

    @Test
    void uidAutorizadoSoloSeAceptaTrasVerificarTokenEnProyectoConfigurado() {
        var verificador = new VerificadorSuperadminFirebase(" proyecto-test ", " uid-1, uid-2,uid-1 ", tokens);
        when(tokens.verificarUid("proyecto-test", "token")).thenReturn("uid-2");
        assertEquals("uid-2", verificador.verificar("token"));
        verify(tokens).verificarUid("proyecto-test", "token");
    }

    @Test
    void usuarioValidoAjenoNoSeVuelveAdministradorPorSuCorreoORolCliente() {
        var verificador = new VerificadorSuperadminFirebase("proyecto-test", "uid-1", tokens);
        when(tokens.verificarUid("proyecto-test", "token")).thenReturn("uid-ajeno");
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(FalloAutenticacionAdmin.class, () -> verificador.verificar("token")).getStatus());
    }

    @Test
    void falloDeRevocacionNoSeConvierteEnPermisoPorEstarEnAllowlist() {
        var verificador = new VerificadorSuperadminFirebase("proyecto-test", "uid-1", tokens);
        when(tokens.verificarUid("proyecto-test", "revocado"))
                .thenThrow(FalloAutenticacionAdmin.noAutenticado());
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(FalloAutenticacionAdmin.class, () -> verificador.verificar("revocado")).getStatus());
    }
}
