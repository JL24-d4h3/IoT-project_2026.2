package com.taxi.gestion.exception;

/** 409: ya existe un registro con esa clave unica (correo, documento o placa). */
public class RecursoDuplicadoException extends RuntimeException {
    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
