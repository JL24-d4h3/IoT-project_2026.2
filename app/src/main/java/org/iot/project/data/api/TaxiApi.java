package org.iot.project.data.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Ubicacion;
import org.iot.project.utils.Distancia;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente de la REST API del sistema de taxistas (RT-006, RT-023, RT-117).
 *
 * <p>Usa solo {@link HttpURLConnection} y {@code org.json}, que ya vienen con
 * Android: no agrega dependencias. Cada llamada corre en un hilo de fondo y
 * responde en el hilo principal, como los demas repositorios.
 *
 * <p>Si la API no responde, el error llega ya redactado para el usuario y no
 * se lanza nada: hoteles y reservas siguen funcionando (RC-019, RC-020).
 */
public final class TaxiApi {

    static final String MSG_SIN_CONEXION = "No pudimos conectar con el servicio de taxis. "
            + "Las reservas y los hoteles siguen funcionando con normalidad.";
    private static final String MSG_RESPUESTA = "El servicio de taxis respondió algo inesperado. "
            + "Inténtalo de nuevo en un momento.";
    private static final String MSG_SESION = "Tu sesión de taxista cambió o venció. "
            + "Cierra sesión y vuelve a entrar.";

    private static final TaxiApi INSTANCIA = new TaxiApi();

    public static TaxiApi get() {
        return INSTANCIA;
    }

    private final ExecutorService hilo = Executors.newFixedThreadPool(2);
    private final Handler principal = new Handler(Looper.getMainLooper());

    private TaxiApi() {
    }

    // =====================================================================
    //  Inicio de sesion del taxista
    // =====================================================================

    /** Resultado del inicio de sesion contra la API. */
    public interface LoginCallback {
        void onExito(@NonNull Cuenta cuenta);

        /** La API conoce al taxista pero no lo deja entrar (en revision o desactivado). */
        void onRechazado(@NonNull String mensaje);

        /** Credenciales que la API no reconoce, sin conexion u otro fallo. */
        void onNoReconocido(@NonNull String mensaje);
    }

    /** POST /api/auth/login. Si sale bien, guarda el token en {@link ApiSesion}. */
    public void loginConductor(@NonNull String correo, @NonNull String contrasena,
                               @NonNull LoginCallback callback) {
        final String correoLimpio = correo.trim();
        hilo.execute(() -> {
            try {
                JSONObject cuerpo = new JSONObject()
                        .put("correo", correoLimpio).put("password", contrasena);
                Respuesta r = ejecutar("POST", "/api/auth/login", cuerpo, false);
                if (r.codigo == 200) {
                    JSONObject o = new JSONObject(r.cuerpo);
                    JSONObject taxista = o.getJSONObject("taxista");
                    String id = String.valueOf(taxista.getLong("id"));
                    String nombre = taxista.optString("nombreCompleto", "").trim();
                    ApiSesion.abrir(o.getString("token"), id, o.getLong("expiraEnMinutos"));
                    Cuenta cuenta = new Cuenta(Role.CONDUCTOR, id,
                            nombre.isEmpty() ? correoLimpio : nombre, correoLimpio);
                    principal.post(() -> callback.onExito(cuenta));
                } else if (r.codigo == 403) {
                    String mensaje = mensajeDeError(r);
                    principal.post(() -> callback.onRechazado(mensaje));
                } else {
                    String mensaje = mensajeDeError(r);
                    principal.post(() -> callback.onNoReconocido(mensaje));
                }
            } catch (IOException e) {
                principal.post(() -> callback.onNoReconocido(MSG_SIN_CONEXION));
            } catch (JSONException | RuntimeException e) {
                principal.post(() -> callback.onNoReconocido(MSG_RESPUESTA));
            }
        });
    }

    // =====================================================================
    //  Lado del conductor (las usa TaxiRepository)
    // =====================================================================

    /** RF-096: GET /api/taxistas/{id}. */
    public void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback) {
        llamar(() -> {
            exigirNumero(conductorId);
            return TaxiApiMapper.taxista(leerObjeto("GET", "/api/taxistas/" + conductorId, null, false));
        }, callback);
    }

    /**
     * RF-088, RF-089: GET /api/servicios/pendientes, con la distancia desde la
     * ultima posicion que el taxista reporto (la mas cercana primero).
     */
    public void disponibles(@NonNull String conductorId,
                            @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        llamar(() -> {
            exigirConductor(conductorId);
            List<TaxiService> pendientes = servicios(leerArreglo("GET", "/api/servicios/pendientes", false));
            Ubicacion posicion = posicionDe(conductorId);
            List<OfertaDeTaxi> ofertas = new ArrayList<>();
            for (TaxiService s : pendientes) {
                double metros = posicion == null ? -1d : Distancia.metrosEntre(
                        posicion.getLatitud(), posicion.getLongitud(), s.getLatRecojo(), s.getLngRecojo());
                ofertas.add(new OfertaDeTaxi(s, metros));
            }
            // Distancia conocida de menor a mayor; las que no se pueden medir, al final.
            ofertas.sort(Comparator.comparingDouble(
                    (OfertaDeTaxi o) -> o.getDistanciaM() < 0 ? Double.MAX_VALUE : o.getDistanciaM()));
            return ofertas;
        }, callback);
    }

    /** RF-097: servicios sin finalizar de este taxista (GET /api/servicios/taxista/{id}). */
    public void serviciosEnCursoDe(@NonNull String conductorId,
                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        llamar(() -> {
            exigirConductor(conductorId);
            List<TaxiService> enCurso = new ArrayList<>();
            for (TaxiService s : servicios(leerArreglo("GET", "/api/servicios/taxista/" + conductorId, true))) {
                if (s.getEstado().isActive()) {
                    enCurso.add(s);
                }
            }
            return enCurso;
        }, callback);
    }

    /** RF-090 a RF-092, RF-107: PUT /api/servicios/{id}/aceptar. */
    public void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                        @NonNull ResultCallback<TaxiService> callback) {
        llamar(() -> {
            exigirConductor(conductorId);
            exigirNumero(taxiId);
            leerTexto("PUT", "/api/servicios/" + taxiId + "/aceptar", null, true);
            return detalle(taxiId);
        }, callback);
    }

    /** RF-108, RF-109: ASIGNADO -> EN_CAMINO -> EN_TRASLADO (PUT .../estado). */
    public void avanzar(@NonNull String taxiId, @NonNull String conductorId,
                        @NonNull ResultCallback<TaxiService> callback) {
        llamar(() -> {
            exigirConductor(conductorId);
            exigirNumero(taxiId);
            TaxiStatus actual = detalle(taxiId).getEstado();
            if (actual == TaxiStatus.SOLICITADO) {
                throw new ErrorApi("Primero acepta el servicio.");
            }
            TaxiStatus siguiente = actual.next();
            if (siguiente == actual || siguiente == TaxiStatus.FINALIZADO) {
                throw new ErrorApi("El servicio se cierra validando el código del cliente.");
            }
            leerTexto("PUT", "/api/servicios/" + taxiId + "/estado",
                    new JSONObject().put("estado", siguiente.name()), true);
            return detalle(taxiId);
        }, callback);
    }

    /** RF-102 a RF-104, RF-110: PUT /api/servicios/{id}/finalizar con el codigo QR. */
    public void finalizar(@NonNull String taxiId, @NonNull String conductorId, @NonNull String codigo,
                          @NonNull ResultCallback<TaxiService> callback) {
        llamar(() -> {
            exigirConductor(conductorId);
            exigirNumero(taxiId);
            JSONObject cuerpo = new JSONObject().put("codigoQr", codigo.trim());
            return TaxiApiMapper.servicio(
                    leerObjeto("PUT", "/api/servicios/" + taxiId + "/finalizar", cuerpo, true));
        }, callback);
    }

    // =====================================================================
    //  Piezas internas
    // =====================================================================

    private TaxiService detalle(String taxiId) throws IOException, JSONException, ErrorApi {
        return TaxiApiMapper.servicio(leerObjeto("GET", "/api/servicios/" + taxiId, null, false));
    }

    /** Ultima posicion reportada por el taxista, o {@code null} si nunca reporto una. */
    @Nullable
    private Ubicacion posicionDe(String conductorId) {
        try {
            JSONObject o = leerObjeto("GET", "/api/taxistas/" + conductorId + "/ubicacion", null, false);
            if (o.isNull("latitud") || o.isNull("longitud")) {
                return null;
            }
            return new Ubicacion(o.getDouble("latitud"), o.getDouble("longitud"));
        } catch (IOException | JSONException | ErrorApi e) {
            return null; // sin posicion no hay distancia, pero la lista sigue sirviendo
        }
    }

    private static List<TaxiService> servicios(JSONArray arreglo) throws JSONException {
        List<TaxiService> lista = new ArrayList<>();
        for (int i = 0; i < arreglo.length(); i++) {
            lista.add(TaxiApiMapper.servicio(arreglo.getJSONObject(i)));
        }
        return lista;
    }

    /** El taxista del token debe ser el mismo que pide la operacion (RC-009). */
    private static void exigirConductor(String conductorId) throws ErrorApi {
        exigirNumero(conductorId);
        if (!conductorId.equals(ApiSesion.taxistaId())) {
            throw new ErrorApi(MSG_SESION);
        }
    }

    /** Los ids de la API son numericos: evita colar texto en la ruta. */
    private static void exigirNumero(String id) throws ErrorApi {
        if (id == null || !id.matches("\\d{1,18}")) {
            throw new ErrorApi("No encontramos un identificador válido para este servicio.");
        }
    }

    private interface Tarea<T> {
        T ejecutar() throws IOException, JSONException, ErrorApi;
    }

    private <T> void llamar(Tarea<T> tarea, ResultCallback<T> callback) {
        hilo.execute(() -> {
            try {
                T resultado = tarea.ejecutar();
                principal.post(() -> callback.onExito(resultado));
            } catch (ErrorApi e) {
                principal.post(() -> callback.onError(e.getMessage()));
            } catch (IOException e) {
                principal.post(() -> callback.onError(MSG_SIN_CONEXION));
            } catch (JSONException | RuntimeException e) {
                principal.post(() -> callback.onError(MSG_RESPUESTA));
            }
        });
    }

    private JSONObject leerObjeto(String metodo, String ruta, @Nullable JSONObject cuerpo, boolean conToken)
            throws IOException, JSONException, ErrorApi {
        return new JSONObject(leerTexto(metodo, ruta, cuerpo, conToken));
    }

    private JSONArray leerArreglo(String metodo, String ruta, boolean conToken)
            throws IOException, JSONException, ErrorApi {
        return new JSONArray(leerTexto(metodo, ruta, null, conToken));
    }

    /** Ejecuta la peticion y devuelve el cuerpo si fue 2xx; si no, lanza {@link ErrorApi}. */
    private String leerTexto(String metodo, String ruta, @Nullable JSONObject cuerpo, boolean conToken)
            throws IOException, ErrorApi {
        if (conToken && !ApiSesion.activa()) {
            throw new ErrorApi(MSG_SESION);
        }
        Respuesta r = ejecutar(metodo, ruta, cuerpo, conToken);
        if (r.codigo < 200 || r.codigo >= 300) {
            throw new ErrorApi(mensajeDeError(r));
        }
        return r.cuerpo;
    }

    private static Respuesta ejecutar(String metodo, String ruta, @Nullable JSONObject cuerpo,
                                      boolean conToken) throws IOException {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(ApiConfig.BASE_URL + ruta).openConnection();
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(ApiConfig.TIMEOUT_CONEXION_MS);
            conexion.setReadTimeout(ApiConfig.TIMEOUT_LECTURA_MS);
            conexion.setRequestProperty("Accept", "application/json");
            if (conToken) {
                String token = ApiSesion.token();
                if (token != null) {
                    conexion.setRequestProperty("Authorization", "Bearer " + token);
                }
            }
            if (cuerpo != null) {
                byte[] datos = cuerpo.toString().getBytes(StandardCharsets.UTF_8);
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conexion.setFixedLengthStreamingMode(datos.length);
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(datos);
                }
            }
            int codigo = conexion.getResponseCode();
            InputStream entrada = codigo >= 400 ? conexion.getErrorStream() : conexion.getInputStream();
            return new Respuesta(codigo, entrada == null ? "" : leer(entrada));
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private static String leer(InputStream entrada) throws IOException {
        try (InputStream in = entrada; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bloque = new byte[4096];
            int n;
            while ((n = in.read(bloque)) != -1) {
                buffer.write(bloque, 0, n);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Mensaje para el usuario segun el codigo HTTP; usa el de la API cuando lo trae. */
    private static String mensajeDeError(Respuesta r) {
        String api = TaxiApiMapper.mensajeDeError(r.cuerpo);
        switch (r.codigo) {
            case 400:
                return api != null ? api : "Revisa los datos enviados.";
            case 401:
                return api != null ? api : "Correo o contraseña incorrectos.";
            case 403:
                // Sin cuerpo: Spring rechazo la peticion por falta de token valido.
                return api != null ? api : MSG_SESION;
            case 404:
                return api != null ? api : "No encontramos lo que buscabas.";
            case 409:
                return api != null ? api : "El servicio cambió de estado. Actualiza e inténtalo de nuevo.";
            default:
                return "El servicio de taxis tuvo un problema. Inténtalo de nuevo en un momento.";
        }
    }

    private static final class Respuesta {
        final int codigo;
        final String cuerpo;

        Respuesta(int codigo, String cuerpo) {
            this.codigo = codigo;
            this.cuerpo = cuerpo;
        }
    }

    /** Error ya redactado para el usuario. */
    private static final class ErrorApi extends Exception {
        ErrorApi(String mensaje) {
            super(mensaje);
        }
    }
}
