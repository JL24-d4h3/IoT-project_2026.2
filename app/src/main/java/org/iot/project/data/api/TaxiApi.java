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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Servicios ya cerrados: no cambian, asi que se consultan una sola vez por sesion. */
    private static final Map<String, TaxiVivo> FINALIZADOS = new ConcurrentHashMap<>();
    /** Notas del cliente que la API ya tiene (o que ya reconocio como repetidas). */
    private static final Set<String> VALORADAS = ConcurrentHashMap.newKeySet();
    private static final long VIGENCIA_CONDUCTOR_MS = 60_000L;
    private final Map<String, CacheConductor> conductores = new ConcurrentHashMap<>();

    private final ExecutorService hilo = Executors.newFixedThreadPool(3);
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
    //  Lado del cliente (las usa FirebaseTaxiRepository)
    // =====================================================================

    /** Resultado de contrastar las solicitudes del cliente con el sistema de taxistas. */
    public static final class Enriquecimiento {
        /** Las mismas solicitudes, en el mismo orden, con el estado real. */
        @NonNull public final List<TaxiService> servicios;
        /** Reservas cuya solicitud todavia no existe en la API: la app debe registrarlas. */
        @NonNull public final Set<String> sinRegistrar;
        /** {@code true} si la API no respondio: se conserva lo que guardo Firestore. */
        public final boolean apiCaida;

        Enriquecimiento(@NonNull List<TaxiService> servicios, @NonNull Set<String> sinRegistrar, boolean apiCaida) {
            this.servicios = servicios;
            this.sinRegistrar = sinRegistrar;
            this.apiCaida = apiCaida;
        }
    }

    /**
     * RF-113 a RF-116: para cada solicitud del cliente consulta su estado, su taxista
     * asignado y su ubicacion. Si la API no responde no falla: devuelve las solicitudes
     * tal como estaban, para que reservas y hoteles sigan funcionando (RC-019, RC-020).
     */
    public void enriquecer(@NonNull List<TaxiService> base, @NonNull ResultCallback<Enriquecimiento> callback) {
        hilo.execute(() -> {
            List<TaxiService> salida = new ArrayList<>(base.size());
            Set<String> sinRegistrar = new LinkedHashSet<>();
            boolean caida = false;
            for (TaxiService taxi : base) {
                String reserva = taxi.getBookingId();
                TaxiVivo cerrado = FINALIZADOS.get(reserva);
                if (cerrado != null) {
                    salida.add(TaxiVivo.aplicar(taxi, cerrado));
                    continue;
                }
                boolean completo = taxi.getEstado() == TaxiStatus.FINALIZADO && taxi.getDriver() != null;
                if (caida || completo) {
                    salida.add(taxi);
                    continue;
                }
                try {
                    TaxiVivo vivo = vivo(reserva);
                    if (vivo == null) {
                        sinRegistrar.add(reserva);
                        salida.add(taxi);
                        continue;
                    }
                    if (vivo.estado == TaxiStatus.FINALIZADO) {
                        FINALIZADOS.put(reserva, vivo);
                        reenviarNota(taxi, vivo);
                    }
                    salida.add(TaxiVivo.aplicar(taxi, vivo));
                } catch (IOException e) {
                    caida = true;
                    salida.add(taxi);
                } catch (ErrorApi | JSONException | RuntimeException e) {
                    salida.add(taxi);
                }
            }
            final boolean apiCaida = caida;
            principal.post(() -> callback.onExito(new Enriquecimiento(salida, sinRegistrar, apiCaida)));
        });
    }

    /**
     * RF-083: registra en el sistema de taxistas la solicitud que el cliente ya guardo.
     * Si la reserva ya tenia servicio, tambien es exito: repetirlo es seguro.
     */
    public void registrar(@NonNull TaxiService taxi, @NonNull String hotelId, @NonNull String nombreCliente,
                          @Nullable String telefono, @NonNull ResultCallback<Boolean> callback) {
        llamar(() -> {
            String nombre = nombreCliente.trim().isEmpty() ? "Cliente de Estadía" : nombreCliente.trim();
            JSONObject o = new JSONObject()
                    .put("clienteId", taxi.getClienteId())
                    .put("nombreCliente", recortar(nombre, 120))
                    .put("direccionOrigen", recortar(taxi.getOrigen(), 200))
                    .put("latitudOrigen", taxi.getLatRecojo())
                    .put("longitudOrigen", taxi.getLngRecojo())
                    .put("direccionDestino", recortar(taxi.getDestino(), 200))
                    .put("latitudDestino", ApiConfig.AEROPUERTO_LATITUD)
                    .put("longitudDestino", ApiConfig.AEROPUERTO_LONGITUD)
                    .put("tarifa", taxi.getPrecio())
                    .put("bookingId", taxi.getBookingId())
                    .put("numPasajeros", taxi.getNumPasajeros())
                    .put("fechaProgramada", taxi.getFecha().toString())
                    .put("horaProgramada", taxi.getHora().withSecond(0).withNano(0).toString())
                    .put("idaYVuelta", taxi.isIdaYVuelta())
                    .put("codigoQr", taxi.getCodigo());
            if (!hotelId.isEmpty()) {
                o.put("hotelId", recortar(hotelId, 60));
            }
            if (telefono != null && telefono.trim().matches("^[0-9+\\- ]{6,20}$")) {
                o.put("telefonoCliente", telefono.trim());
            }
            Respuesta r = ejecutar("POST", "/api/servicios", o, false);
            if ((r.codigo >= 200 && r.codigo < 300) || r.codigo == 409) {
                return true;
            }
            throw new ErrorApi(mensajeDeError(r));
        }, callback);
    }

    /**
     * RF-105: manda la nota del cliente al taxista del servicio. La API acepta una sola
     * por servicio, asi que reintentarla es seguro. La nota llega de 1 a 10 y se
     * convierte a la escala de la API en {@link TaxiApiMapper#puntosApi(float)}.
     */
    public void valorar(@NonNull String bookingId, float nota, @NonNull ResultCallback<Boolean> callback) {
        llamar(() -> {
            TaxiVivo vivo = vivo(bookingId);
            if (vivo == null || vivo.taxistaId == null) {
                throw new ErrorApi("Este traslado todavía no tiene un taxista asignado.");
            }
            enviarNota(vivo, nota);
            VALORADAS.add(bookingId);
            return true;
        }, callback);
    }

    private void enviarNota(TaxiVivo vivo, float nota) throws IOException, JSONException, ErrorApi {
        JSONObject cuerpo = new JSONObject().put("puntuacion", TaxiApiMapper.puntosApi(nota)).put("servicioId", vivo.servicioId);
        Respuesta r = ejecutar("POST", "/api/taxistas/" + vivo.taxistaId + "/valoracion", cuerpo, false);
        boolean yaRegistrada = r.codigo == 409;
        if ((r.codigo < 200 || r.codigo >= 300) && !yaRegistrada) {
            throw new ErrorApi(mensajeDeError(r));
        }
    }

    /** Si el cliente valoro pero la nota no llego a la API (estaba caida), se reenvia aqui. */
    private void reenviarNota(TaxiService taxi, TaxiVivo vivo) {
        if (!taxi.isValorado() || vivo.taxistaId == null || !VALORADAS.add(taxi.getBookingId())) {
            return;
        }
        try {
            enviarNota(vivo, taxi.getRatingCliente());
        } catch (IOException | ErrorApi | JSONException | RuntimeException e) {
            VALORADAS.remove(taxi.getBookingId()); // se intentara de nuevo en la proxima consulta
        }
    }

    /** GET /api/servicios/reserva/{id} + taxista + ubicacion; {@code null} si la reserva no tiene servicio. */
    @Nullable
    private TaxiVivo vivo(String bookingId) throws IOException, JSONException, ErrorApi {
        Respuesta r = ejecutar("GET", "/api/servicios/reserva/" + URLEncoder.encode(bookingId, "UTF-8"), null, false);
        if (r.codigo == 404) {
            return null;
        }
        if (r.codigo < 200 || r.codigo >= 300) {
            throw new ErrorApi(mensajeDeError(r));
        }
        JSONObject servicio = new JSONObject(r.cuerpo);
        Driver conductor = null;
        JSONObject ubicacion = null;
        if (!servicio.isNull("taxistaId")) {
            String taxistaId = String.valueOf(servicio.getLong("taxistaId"));
            conductor = conductor(taxistaId);
            String estado = servicio.optString("estado", "");
            if (estado.equals("ASIGNADO") || estado.equals("EN_CAMINO") || estado.equals("EN_TRASLADO")) {
                try {
                    ubicacion = leerObjeto("GET", "/api/taxistas/" + taxistaId + "/ubicacion", null, false);
                } catch (ErrorApi | JSONException sinPosicion) {
                    ubicacion = null;
                }
            }
        }
        return TaxiVivo.desde(servicio, conductor, ubicacion);
    }

    /** Ficha del taxista con una vigencia corta: no cambia entre un sondeo y el siguiente. */
    @Nullable
    private Driver conductor(String taxistaId) throws IOException {
        CacheConductor guardado = conductores.get(taxistaId);
        long ahora = System.currentTimeMillis();
        if (guardado != null && ahora - guardado.momento < VIGENCIA_CONDUCTOR_MS) {
            return guardado.conductor;
        }
        try {
            Driver nuevo = TaxiApiMapper.taxista(leerObjeto("GET", "/api/taxistas/" + taxistaId, null, false));
            conductores.put(taxistaId, new CacheConductor(nuevo, ahora));
            return nuevo;
        } catch (ErrorApi | JSONException e) {
            return guardado != null ? guardado.conductor : null; // TaxiVivo lo arma con los datos del servicio
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static final class CacheConductor {
        final Driver conductor;
        final long momento;

        CacheConductor(Driver conductor, long momento) {
            this.conductor = conductor;
            this.momento = momento;
        }
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
