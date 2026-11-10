package com.taxi.gestion.exception;

/** 404: el recurso solicitado no existe. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException taxista(Long id) {
        return new RecursoNoEncontradoException("No existe un taxista con id " + id);
    }

    public static RecursoNoEncontradoException servicio(Long id) {
        return new RecursoNoEncontradoException("No existe un servicio con id " + id);
    }
}
