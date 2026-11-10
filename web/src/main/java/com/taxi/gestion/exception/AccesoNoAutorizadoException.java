package com.taxi.gestion.exception;

/** 403: token ausente/invalido, o el taxista intenta actuar sobre un recurso ajeno. */
public class AccesoNoAutorizadoException extends RuntimeException {
    public AccesoNoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
