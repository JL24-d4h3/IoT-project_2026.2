package org.iot.project.data.firebase;

import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;

import java.util.HashMap;
import java.util.Map;

/** Mapeo explicito del perfil; los permisos se validan tambien en Firestore. */
public final class PerfilFirebase {

    private PerfilFirebase() {
    }

    public static User usuario(String uid, String email, Map<String, Object> datos) {
        exigirUid(uid);
        if (datos == null) {
            throw new IllegalArgumentException("No encontramos el perfil de esta cuenta.");
        }
        Role rol = rol(datos.get("rol"));
        if (rol == Role.CONDUCTOR) {
            throw new IllegalArgumentException("Esta cuenta corresponde a un taxista, no a un perfil de cliente o administrador.");
        }
        exigirActivo(datos);
        User usuario = new User(uid, texto(datos, "nombres"), texto(datos, "apellidos"), rol);
        usuario.setHotelId(hotelId(datos));
        usuario.setEmail(email == null ? "" : email);
        usuario.setTelefono(texto(datos, "telefono"));
        usuario.setDireccion(texto(datos, "direccion"));
        usuario.withFoto(texto(datos, "fotoUrl"));
        usuario.withDocumento(texto(datos, "tipoDocumento"), texto(datos, "numeroDocumento"));
        usuario.withNacimiento(texto(datos, "fechaNacimiento"));
        usuario.setActivo(true);
        return usuario;
    }

    public static Cuenta cuenta(String uid, String email, Map<String, Object> datos) {
        exigirUid(uid);
        if (datos == null) {
            throw new IllegalArgumentException("No encontramos el perfil de esta cuenta.");
        }
        // El perfil de acceso del taxista no requiere vehículo ni conexión con su API.
        // Conserva su UID de Auth como identidad y nunca se trata como un User.
        if (rol(datos.get("rol")) == Role.CONDUCTOR) {
            exigirActivo(datos);
            String correo = email == null ? "" : email;
            String nombre = (texto(datos, "nombres") + " " + texto(datos, "apellidos")).trim();
            return new Cuenta(Role.CONDUCTOR, uid, nombre.isEmpty() ? correo : nombre, correo);
        }
        User usuario = usuario(uid, email, datos);
        String nombre = usuario.getNombreCompleto().trim();
        return new Cuenta(usuario.getRol(), uid, nombre.isEmpty() ? usuario.getEmail() : nombre,
                usuario.getEmail(), hotelId(datos));
    }

    /** El alta publica siempre crea clientes, aunque el borrador diga otro rol. */
    public static Map<String, Object> nuevoCliente(User borrador, String email) {
        Map<String, Object> datos = actualizacion(borrador);
        datos.put("email", email);
        datos.put("rol", Role.CLIENTE.name());
        datos.put("activo", true);
        return datos;
    }

    /** Lista explicita: nunca incluye rol, habilitacion, UID, correo o hotel. */
    public static Map<String, Object> actualizacion(User usuario) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombres", obligatorio(usuario.getNombres(), "Escribe tu nombre."));
        datos.put("apellidos", obligatorio(usuario.getApellidos(), "Escribe tus apellidos."));
        datos.put("telefono", limpio(usuario.getTelefono()));
        datos.put("direccion", limpio(usuario.getDireccion()));
        datos.put("fotoUrl", limpio(usuario.getFotoUrl()));
        return datos;
    }

    private static Role rol(Object valor) {
        if (!(valor instanceof String)) {
            throw new IllegalArgumentException("Tu cuenta no tiene un rol válido. Contacta al administrador.");
        }
        final Role rol;
        try {
            rol = Role.valueOf((String) valor);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Tu cuenta no tiene un rol válido. Contacta al administrador.");
        }
        return rol;
    }

    private static void exigirActivo(Map<String, Object> datos) {
        if (!Boolean.TRUE.equals(datos.get("activo"))) {
            throw new IllegalArgumentException("Tu cuenta está deshabilitada. Contacta al administrador.");
        }
    }

    private static String hotelId(Map<String, Object> datos) {
        String id = texto(datos, "hotelId");
        if (id.contains("/")) {
            throw new IllegalArgumentException("La asignación de hotel no es válida.");
        }
        return id.isEmpty() ? null : id;
    }

    private static void exigirUid(String uid) {
        if (uid == null || uid.trim().isEmpty() || uid.contains("/")) {
            throw new IllegalArgumentException("No hay una identidad autenticada válida.");
        }
    }

    private static String texto(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) {
            return "";
        }
        if (!(valor instanceof String)) {
            throw new IllegalArgumentException("El perfil tiene un campo inválido: " + campo + ".");
        }
        return ((String) valor).trim();
    }

    private static String obligatorio(String valor, String mensaje) {
        String resultado = limpio(valor);
        if (resultado.isEmpty()) {
            throw new IllegalArgumentException(mensaje);
        }
        return resultado;
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
