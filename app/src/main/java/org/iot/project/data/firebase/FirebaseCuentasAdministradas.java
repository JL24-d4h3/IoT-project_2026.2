package org.iot.project.data.firebase;

import com.google.firebase.firestore.DocumentSnapshot;

import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.Map;

/** Directorio administrativo; este mapeo no autentica ni concede un rol de sesión. */
final class FirebaseCuentasAdministradas {

    private FirebaseCuentasAdministradas() { }

    static User desde(DocumentSnapshot documento) {
        String uid = documento.getId();
        Map<String, Object> datos = documento.getData();
        if (!documento.exists() || datos == null) {
            throw new IllegalArgumentException("No encontramos el perfil " + uid + ".");
        }
        if (!(datos.get("activo") instanceof Boolean)) {
            throw invalido(uid, "activo", "Debe ser un booleano.");
        }
        Role rol;
        try {
            Object valor = datos.get("rol");
            if (!(valor instanceof String)) throw new IllegalArgumentException();
            rol = Role.valueOf((String) valor);
        } catch (IllegalArgumentException error) {
            throw invalido(uid, "rol", "Selecciona un rol válido del sistema.");
        }
        String email = texto(uid, datos, "email");
        if (email.isEmpty()) throw invalido(uid, "email", "Escribe el correo de la cuenta.");
        User usuario = new User(uid, texto(uid, datos, "nombres"),
                texto(uid, datos, "apellidos"), rol);
        usuario.setEmail(email);
        if (usuario.getNombreCompleto().trim().isEmpty()) usuario.setNombres(email);
        usuario.setActivo((Boolean) datos.get("activo"));
        usuario.setTelefono(texto(uid, datos, "telefono"));
        usuario.setDireccion(texto(uid, datos, "direccion"));
        usuario.withFoto(texto(uid, datos, "fotoUrl"));
        usuario.withDocumento(texto(uid, datos, "tipoDocumento"), texto(uid, datos, "numeroDocumento"));
        usuario.withNacimiento(texto(uid, datos, "fechaNacimiento"));
        String hotelId = texto(uid, datos, "hotelId");
        if (hotelId.contains("/")) throw invalido(uid, "hotelId", "El identificador no es válido.");
        usuario.setHotelId(hotelId.isEmpty() ? null : hotelId);
        return usuario;
    }

    private static String texto(String uid, Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return "";
        if (!(valor instanceof String)) throw invalido(uid, campo, "Debe ser texto.");
        return ((String) valor).trim();
    }

    private static IllegalArgumentException invalido(String uid, String campo, String detalle) {
        return new IllegalArgumentException("El perfil " + uid + " tiene un campo inválido: "
                + campo + ". " + detalle);
    }
}
