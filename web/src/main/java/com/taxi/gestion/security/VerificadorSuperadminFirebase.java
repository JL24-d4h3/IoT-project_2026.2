package com.taxi.gestion.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** El UID autorizado procede del servidor, nunca del rol enviado por Android o Firestore. */
@Component
public class VerificadorSuperadminFirebase {

    private final String proyecto;
    private final Set<String> uidsPermitidos;
    private final TokensFirebaseAdmin tokens;

    public VerificadorSuperadminFirebase(
            @Value("${FIREBASE_PROJECT_ID:}") String proyecto,
            @Value("${FIREBASE_SUPERADMIN_UIDS:}") String uids,
            TokensFirebaseAdmin tokens) {
        this.proyecto = proyecto == null ? "" : proyecto.trim();
        this.uidsPermitidos = Arrays.stream((uids == null ? "" : uids).split(","))
                .map(String::trim).filter(uid -> !uid.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.tokens = tokens;
    }

    public String verificar(String token) {
        if (proyecto.isEmpty() || uidsPermitidos.isEmpty()) {
            throw FalloAutenticacionAdmin.noDisponible();
        }
        if (token == null || token.isBlank()) {
            throw FalloAutenticacionAdmin.noAutenticado();
        }
        String uid = tokens.verificarUid(proyecto, token);
        if (uid == null || !uidsPermitidos.contains(uid)) {
            throw FalloAutenticacionAdmin.noAutorizado();
        }
        return uid;
    }
}
