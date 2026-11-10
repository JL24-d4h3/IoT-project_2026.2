package com.taxi.gestion.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.gestion.dto.response.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Se instancia exclusivamente en la cadena /api/admin/**; no es un filtro servlet global. */
public class FiltroSuperadminFirebase extends OncePerRequestFilter {

    private final VerificadorSuperadminFirebase verificador;
    private final ObjectMapper json;

    public FiltroSuperadminFirebase(VerificadorSuperadminFirebase verificador, ObjectMapper json) {
        this.verificador = verificador;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String header = request.getHeader("Authorization");
        String uid;
        try {
            if (header == null || !header.startsWith("Bearer ")
                    || header.substring(7).isBlank()) {
                throw FalloAutenticacionAdmin.noAutenticado();
            }
            uid = verificador.verificar(header.substring(7));
            if (uid == null || uid.isBlank()) throw FalloAutenticacionAdmin.noAutenticado();
        } catch (FalloAutenticacionAdmin fallo) {
            SecurityContextHolder.clearContext();
            responder(json, request, response, fallo.getStatus(), fallo.getMessage());
            return;
        }
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(
                "firebase:" + uid, null, List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))));
        SecurityContextHolder.setContext(contexto);
        chain.doFilter(request, response);
    }

    public static void responder(ObjectMapper json, HttpServletRequest request,
                                 HttpServletResponse response, HttpStatus status,
                                 String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getWriter(), ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), mensaje, request.getRequestURI()));
    }
}
