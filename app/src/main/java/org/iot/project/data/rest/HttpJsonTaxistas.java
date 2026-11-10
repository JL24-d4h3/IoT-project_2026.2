package org.iot.project.data.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Transporte JSON sin reintentos de escritura ni redirecciones de credenciales. */
public final class HttpJsonTaxistas {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final URI base;

    public HttpJsonTaxistas(String baseUrl, boolean permitirHttp) throws ApiException {
        try {
            URI candidata = URI.create(baseUrl == null ? "" : baseUrl.trim());
            boolean https = "https".equalsIgnoreCase(candidata.getScheme());
            boolean http = "http".equalsIgnoreCase(candidata.getScheme());
            if ((!https && !(permitirHttp && http)) || candidata.getHost() == null
                    || candidata.getUserInfo() != null || candidata.getQuery() != null
                    || candidata.getFragment() != null) throw new IllegalArgumentException();
            base = URI.create(candidata.toString().endsWith("/")
                    ? candidata.toString() : candidata + "/");
        } catch (IllegalArgumentException error) {
            throw new ApiException(0, "Configura una URL válida para la API de taxistas. "
                    + "HTTP solo está habilitado en desarrollo.", false);
        }
    }

    public JsonElement solicitar(String metodo, String ruta, JsonObject cuerpo, String token)
            throws ApiException {
        HttpURLConnection conexion = null;
        boolean envioIniciado = false;
        try {
            if (!metodo.matches("GET|POST|PUT") || ruta == null || ruta.contains("..")
                    || ruta.contains(":") || ruta.startsWith("//")) {
                throw new ApiException(400, "La ruta de la API no es válida.");
            }
            URI relativa = URI.create(ruta.startsWith("/") ? ruta.substring(1) : ruta);
            if (relativa.isAbsolute() || relativa.getRawAuthority() != null) {
                throw new ApiException(400, "La ruta de la API no es válida.");
            }
            conexion = (HttpURLConnection) base.resolve(relativa).toURL().openConnection();
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(8000);
            conexion.setReadTimeout(12000);
            conexion.setInstanceFollowRedirects(false);
            conexion.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                if (token.contains("\n") || token.contains("\r")) {
                    throw new ApiException(401, "La sesión del taxista no es válida.");
                }
                conexion.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (cuerpo != null) {
                byte[] bytes = cuerpo.toString().getBytes(StandardCharsets.UTF_8);
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conexion.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream salida = conexion.getOutputStream()) {
                    envioIniciado = true;
                    salida.write(bytes);
                }
            } else {
                envioIniciado = true;
            }
            int status = conexion.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new ApiException(status, "La API redirigió la petición. Revisa su URL base.");
            }
            String contenido = leer(status >= 200 && status < 300
                    ? conexion.getInputStream() : conexion.getErrorStream());
            JsonElement json = null;
            try {
                if (!contenido.isBlank()) json = JsonParser.parseString(contenido);
            } catch (RuntimeException formato) {
                if (status >= 200 && status < 300) {
                    throw new ApiException(0, "La API respondió con un formato inesperado. "
                            + "Comprueba el estado antes de repetir la operación.");
                }
            }
            if (status < 200 || status >= 300) throw new ApiException(status, mensaje(status, json));
            if (json == null || (!json.isJsonObject() && !json.isJsonArray())) {
                throw new ApiException(0, "La API no devolvió el resultado de la operación.");
            }
            return json;
        } catch (ApiException error) {
            throw error;
        } catch (IOException error) {
            throw new ApiException(0, "No pudimos contactar con la API de taxistas. "
                    + "Comprueba que el backend esté iniciado y la dirección de localhost sea accesible.",
                    envioIniciado);
        } catch (IllegalArgumentException error) {
            throw new ApiException(400, "La petición a la API no es válida.");
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    private static String leer(InputStream stream) throws IOException, ApiException {
        if (stream == null) return "";
        try (InputStream entrada = stream; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int cantidad;
            while ((cantidad = entrada.read(buffer)) != -1) {
                if (salida.size() + cantidad > MAX_BYTES) {
                    throw new ApiException(0, "La respuesta de la API es demasiado grande.");
                }
                salida.write(buffer, 0, cantidad);
            }
            return salida.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String mensaje(int status, JsonElement json) {
        if (status < 500 && json != null && json.isJsonObject()) {
            JsonElement mensaje = json.getAsJsonObject().get("mensaje");
            if (mensaje != null && mensaje.isJsonPrimitive() && mensaje.getAsJsonPrimitive().isString()) {
                String texto = mensaje.getAsString().trim();
                if (!texto.isEmpty() && texto.length() <= 500) return texto;
            }
        }
        switch (status) {
            case 400: return "Revisa los datos enviados al sistema de taxistas.";
            case 401: return "El acceso no es válido o la sesión del taxista venció. Inicia sesión de nuevo.";
            case 403: return "El sistema de taxistas no permite esta operación con tu cuenta.";
            case 404: return "No encontramos el recurso en el sistema de taxistas.";
            case 409: return "El servicio cambió o ya fue tomado. Actualiza antes de continuar.";
            default: return "El sistema de taxistas no está disponible. Inténtalo más tarde.";
        }
    }
}
