package com.taxi.gestion.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/** Adaptador lazy: arrancar la web no exige credenciales ni llamadas a Firebase. */
@Component
public class TokensFirebaseAdmin {

    private FirebaseApp app;
    private FirebaseAuth auth;

    public String verificarUid(String proyecto, String token) {
        final FirebaseAuth autenticador;
        try {
            autenticador = autenticador(proyecto);
        } catch (IOException | RuntimeException fallo) {
            // Un fallo de ADC/configuracion es del servidor, no de la sesion Android.
            throw FalloAutenticacionAdmin.noDisponible();
        }
        try {
            // true consulta revocacion y usuario deshabilitado; no basta la firma del JWT.
            return autenticador.verifyIdToken(token, true).getUid();
        } catch (FirebaseAuthException fallo) {
            if (fallo.getAuthErrorCode() != null) {
                switch (fallo.getAuthErrorCode().name()) {
                    case "INVALID_ID_TOKEN", "EXPIRED_ID_TOKEN", "REVOKED_ID_TOKEN",
                            "USER_DISABLED", "USER_NOT_FOUND" ->
                            throw FalloAutenticacionAdmin.noAutenticado();
                    default -> { /* Red, certificados o permisos del servidor: 503. */ }
                }
            }
            throw FalloAutenticacionAdmin.noDisponible();
        } catch (IllegalArgumentException fallo) {
            throw FalloAutenticacionAdmin.noAutenticado();
        } catch (RuntimeException fallo) {
            throw FalloAutenticacionAdmin.noDisponible();
        }
    }

    private synchronized FirebaseAuth autenticador(String proyecto) throws IOException {
        if (auth == null) {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setProjectId(proyecto)
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            app = FirebaseApp.initializeApp(opciones,
                    "taxistas-superadmin-" + UUID.randomUUID());
            auth = FirebaseAuth.getInstance(app);
        }
        return auth;
    }

    @PreDestroy
    synchronized void cerrar() {
        if (app != null) {
            app.delete();
            app = null;
            auth = null;
        }
    }
}
