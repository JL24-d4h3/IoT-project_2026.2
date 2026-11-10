package com.taxi.gestion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica las peticiones de la app movil a partir del token que recibio en
 * el login. Si el header falta o el token no es valido, la peticion sigue
 * como anonima: seran las reglas de {@code SecurityConfig} las que decidan
 * si esa ruta exige estar autenticado.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIJO)) {
            Long taxistaId = jwtService.validarYObtenerTaxistaId(header.substring(PREFIJO.length()));
            if (taxistaId != null) {
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        taxistaId, null, List.of(new SimpleGrantedAuthority("ROLE_TAXISTA")));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }
        chain.doFilter(request, response);
    }
}
