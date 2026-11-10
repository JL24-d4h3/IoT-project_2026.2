package com.taxi.gestion.exception;

/**
 * 403: las credenciales son correctas pero la cuenta todavia no puede operar
 * (esta PENDIENTE de habilitacion o el Superadmin la desactivo).
 */
public class CuentaNoOperativaException extends RuntimeException {
    public CuentaNoOperativaException(String mensaje) {
        super(mensaje);
    }
}
