package org.iot.project.data.rest;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.iot.project.BuildConfig;
import org.iot.project.core.ResultCallback;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adaptador Android: HTTP fuera del hilo de la interfaz y callbacks en el principal. */
public final class TaxistasApi {
    public interface Trabajo<T> { T ejecutar() throws Exception; }
    private static final TaxistasApi INSTANCIA = new TaxistasApi();
    private final ExecutorService trabajadores = Executors.newFixedThreadPool(3);
    private HttpJsonTaxistas transporte;

    public static TaxistasApi getInstancia() { return INSTANCIA; }

    private synchronized HttpJsonTaxistas transporte() throws ApiException {
        if (transporte == null) transporte = new HttpJsonTaxistas(
                BuildConfig.TAXISTAS_API_BASE_URL, BuildConfig.DEBUG);
        return transporte;
    }

    public JsonObject objeto(String metodo, String ruta, JsonObject cuerpo, String token)
            throws ApiException {
        JsonElement respuesta = transporte().solicitar(metodo, ruta, cuerpo, token);
        if (!respuesta.isJsonObject()) throw new ApiException(0, "La API no devolvió un objeto válido.");
        return respuesta.getAsJsonObject();
    }

    public JsonArray lista(String ruta, String token) throws ApiException {
        JsonElement respuesta = transporte().solicitar("GET", ruta, null, token);
        if (!respuesta.isJsonArray()) throw new ApiException(0, "La API no devolvió una lista válida.");
        return respuesta.getAsJsonArray();
    }

    public static <T> void ejecutar(Trabajo<T> trabajo, ResultCallback<T> callback) {
        INSTANCIA.trabajadores.execute(() -> {
            try {
                T resultado = trabajo.ejecutar();
                new Handler(Looper.getMainLooper()).post(() -> callback.onExito(resultado));
            } catch (Exception error) {
                String mensaje = error instanceof ApiException || error instanceof IllegalArgumentException
                        ? error.getMessage() : "No pudimos completar la operación del sistema de taxistas.";
                String aviso = mensaje == null ? "Revisa la conexión con el sistema de taxistas." : mensaje;
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(aviso));
            }
        });
    }

    /** Las fotos relativas de Spring se cargan desde el mismo servidor configurado. */
    public static String resolverUrl(String ruta) {
        if (ruta == null || ruta.isBlank()) return null;
        try {
            URI url = URI.create(ruta);
            if (!url.isAbsolute()) url = URI.create(BuildConfig.TAXISTAS_API_BASE_URL).resolve(url);
            return ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null && url.getUserInfo() == null ? url.toString() : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
