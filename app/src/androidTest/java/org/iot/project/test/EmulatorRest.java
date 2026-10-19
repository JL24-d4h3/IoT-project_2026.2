package org.iot.project.test;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Fixtures y comprobaciones exclusivamente en endpoints locales constantes. */
final class EmulatorRest {
    private static final String AUTH = "http://10.0.2.2:19099";
    private static final String FIRESTORE = "http://10.0.2.2:18080";
    private static final String DOCUMENTOS = "/v1/projects/demo-iot-hoteles/databases/(default)/documents/";
    static final String PASSWORD = "SoloEmulador42!";

    static final class CuentaPrueba {
        final String uid;
        final String email;
        final String nombreCompleto;

        CuentaPrueba(String uid, String email, String nombreCompleto) {
            this.uid = uid;
            this.email = email;
            this.nombreCompleto = nombreCompleto;
        }
    }

    private EmulatorRest() {
    }

    static void limpiarFirestoreDemo() throws Exception {
        solicitar("DELETE", FIRESTORE + "/emulator/v1/projects/demo-iot-hoteles/databases/(default)/documents", null, true);
    }

    static CuentaPrueba crearCuenta(String prefijo, String rol) throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String email = prefijo + "-" + sufijo + "@example.test";
        JSONObject alta = new JSONObject().put("email", email).put("password", PASSWORD)
                .put("returnSecureToken", true);
        JSONObject creada = solicitar("POST", AUTH + "/identitytoolkit.googleapis.com/v1/accounts:signUp?key="
                + FirebaseEmulatorApplication.API_KEY, alta, false);
        String uid = creada.getString("localId");
        String nombres = "SUPERADMIN".equals(rol) ? "Superadmin" : "Administrador";
        String apellidos = "E2E-" + sufijo;
        String ahora = Instant.now().toString();
        JSONObject campos = new JSONObject()
                .put("nombres", texto(nombres)).put("apellidos", texto(apellidos))
                .put("email", texto(email)).put("rol", texto(rol))
                .put("activo", new JSONObject().put("booleanValue", true))
                .put("hotelId", new JSONObject().put("nullValue", JSONObject.NULL))
                .put("creadoEn", new JSONObject().put("timestampValue", ahora))
                .put("actualizadoEn", new JSONObject().put("timestampValue", ahora));
        solicitar("PATCH", FIRESTORE + DOCUMENTOS + "usuarios/" + uid,
                new JSONObject().put("fields", campos), true);
        return new CuentaPrueba(uid, email, nombres + " " + apellidos);
    }

    static JSONObject documento(String ruta) throws Exception {
        if (!ruta.matches("(usuarios|hoteles|auditoria)/[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("La ruta de prueba no esta permitida.");
        }
        return solicitar("GET", FIRESTORE + DOCUMENTOS + ruta, null, true).getJSONObject("fields");
    }

    static String textoCampo(JSONObject campos, String campo) throws Exception {
        return campos.getJSONObject(campo).getString("stringValue");
    }

    private static JSONObject texto(String valor) throws Exception {
        return new JSONObject().put("stringValue", valor);
    }

    private static JSONObject solicitar(String metodo, String destino, JSONObject datos, boolean propietario)
            throws Exception {
        FirebaseEmulatorApplication.exigirProyectoDemo();
        URL url = new URL(destino);
        if (!"http".equals(url.getProtocol()) || !FirebaseEmulatorApplication.HOST.equals(url.getHost())
                || (url.getPort() != FirebaseEmulatorApplication.AUTH_PORT
                && url.getPort() != FirebaseEmulatorApplication.FIRESTORE_PORT)
                || (url.getPort() == FirebaseEmulatorApplication.FIRESTORE_PORT
                && !url.getPath().contains("/projects/demo-iot-hoteles/"))) {
            throw new IllegalStateException("Prueba cancelada: endpoint fuera de los emuladores demo.");
        }
        HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
        conexion.setConnectTimeout(10000);
        conexion.setReadTimeout(10000);
        conexion.setInstanceFollowRedirects(false);
        conexion.setRequestMethod(metodo);
        if (propietario) conexion.setRequestProperty("Authorization", "Bearer owner");
        if (datos != null) {
            conexion.setDoOutput(true);
            conexion.setRequestProperty("Content-Type", "application/json");
            try (OutputStream salida = conexion.getOutputStream()) {
                salida.write(datos.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int codigo = conexion.getResponseCode();
            InputStream entrada = codigo >= 200 && codigo < 300
                    ? conexion.getInputStream() : conexion.getErrorStream();
            String contenido = "";
            if (entrada != null) {
                try (InputStream flujo = entrada; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    for (int n; (n = flujo.read(buffer)) != -1;) bytes.write(buffer, 0, n);
                    contenido = bytes.toString(StandardCharsets.UTF_8.name());
                }
            }
            if (codigo < 200 || codigo >= 300) {
                // No imprimir bodies Auth: pueden contener tokens aun siendo ficticios.
                throw new AssertionError("Emulador local respondio HTTP " + codigo + " para " + metodo + " " + url.getPath());
            }
            return contenido.isEmpty() ? new JSONObject() : new JSONObject(contenido);
        } finally {
            conexion.disconnect();
        }
    }
}
