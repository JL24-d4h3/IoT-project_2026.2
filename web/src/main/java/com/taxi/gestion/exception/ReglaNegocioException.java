package com.taxi.gestion.exception;

/** 400: la peticion es sintacticamente valida pero rompe una regla de negocio. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
