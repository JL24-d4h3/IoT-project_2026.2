package com.taxi.gestion.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emite y valida el token que recibe la app movil al iniciar sesion.
 * El token solo transporta el id del taxista y su correo; nunca la contrasena.
 */
@Component
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMinutos;

    public JwtService(@Value("${app.jwt.secret}") String secreto,
                       @Value("${app.jwt.expiracion-minutos}") long expiracionMinutos) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
    }

    public String generarToken(Long taxistaId, String correo) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(taxistaId))
                .claim("correo", correo)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionMinutos * 60)))
                .signWith(clave)
                .compact();
    }

    public long expiracionMinutos() {
        return expiracionMinutos;
    }

    /** Devuelve el id del taxista si el token es autentico y no expiro; null en cualquier otro caso. */
    public Long validarYObtenerTaxistaId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build()
                    .parseSignedClaims(token).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
