package com.taxi.gestion.exception;

/** 401: el correo no existe o la contrasena no coincide. */
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
