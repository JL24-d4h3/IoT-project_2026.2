package com.taxi.gestion.exception;

import com.taxi.gestion.domain.enums.EstadoServicio;

/** 409: el recurso esta en un estado incompatible con la operacion pedida. */
public class ConflictoEstadoException extends RuntimeException {

    public ConflictoEstadoException(String mensaje) {
        super(mensaje);
    }

    public static ConflictoEstadoException transicionInvalida(Long servicioId,
                                                              EstadoServicio actual,
                                                              EstadoServicio destino) {
        String permitidas = actual.esTerminal()
                ? "ninguna (estado terminal)"
                : actual.siguientesPosibles().toString();
        return new ConflictoEstadoException(
                "El servicio " + servicioId + " esta en " + actual
                        + " y no puede pasar a " + destino
                        + ". Transiciones permitidas: " + permitidas);
    }
}
