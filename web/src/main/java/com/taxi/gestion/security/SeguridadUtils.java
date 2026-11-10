package com.taxi.gestion.security;

import com.taxi.gestion.exception.AccesoNoAutorizadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Punto unico para leer al taxista autenticado y verificar propiedad del recurso. */
@Component
public class SeguridadUtils {

    /** Id del taxista due&ntilde;o del token con el que se hizo la peticion. */
    public Long taxistaAutenticadoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new AccesoNoAutorizadoException(
                    "Esta accion requiere iniciar sesion como taxista");
        }
        return id;
    }

    /** Lanza 403 si el taxista autenticado no es el dueño del recurso indicado. */
    public void exigirPropioTaxista(Long idDelRecurso) {
        if (!taxistaAutenticadoId().equals(idDelRecurso)) {
            throw new AccesoNoAutorizadoException(
                    "No puedes modificar datos de otro taxista");
        }
    }
}
