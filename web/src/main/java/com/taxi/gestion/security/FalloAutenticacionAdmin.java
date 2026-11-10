package com.taxi.gestion.security;

import org.springframework.http.HttpStatus;

/** Fallos seguros para el cliente: nunca incluyen tokens ni detalles de credenciales. */
public final class FalloAutenticacionAdmin extends RuntimeException {

    private final HttpStatus status;

    private FalloAutenticacionAdmin(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static FalloAutenticacionAdmin noAutenticado() {
        return new FalloAutenticacionAdmin(HttpStatus.UNAUTHORIZED,
                "Inicia sesion de nuevo para gestionar los taxistas");
    }

    public static FalloAutenticacionAdmin noAutorizado() {
        return new FalloAutenticacionAdmin(HttpStatus.FORBIDDEN,
                "Tu cuenta no esta autorizada para gestionar los taxistas");
    }

    public static FalloAutenticacionAdmin noDisponible() {
        return new FalloAutenticacionAdmin(HttpStatus.SERVICE_UNAVAILABLE,
                "La verificacion del superadministrador no esta disponible");
    }
}
