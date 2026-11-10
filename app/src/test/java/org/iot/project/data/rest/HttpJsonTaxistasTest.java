package org.iot.project.data.rest;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Contrato HTTP real en loopback; sin cuentas, servidor web ni Firebase. */
public class HttpJsonTaxistasTest {
    private ServidorPrueba servidor;
    private String base;

    @Before public void iniciar() throws Exception {
        servidor = new ServidorPrueba();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/";
        servidor.start();
    }

    @After public void cerrar() { if (servidor != null) servidor.stop(0); }

    @Test public void enviaJsonYBearerEnUnaSolaPeticion() throws Exception {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        AtomicInteger peticiones = new AtomicInteger();
        servidor.createContext("/api/servicios/2/estado", exchange -> {
            peticiones.incrementAndGet();
            autorizacion.set(exchange.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] respuesta = "{\"estado\":\"EN_CAMINO\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.close();
        });
        JsonObject datos = new JsonObject();
        datos.addProperty("estado", "EN_CAMINO");
        assertEquals("EN_CAMINO", new HttpJsonTaxistas(base, true)
                .solicitar("PUT", "/api/servicios/2/estado", datos, "token-ficticio")
                .getAsJsonObject().get("estado").getAsString());
        assertEquals("Bearer token-ficticio", autorizacion.get());
        assertEquals(datos.toString(), cuerpo.get());
        assertEquals(1, peticiones.get());
    }

    @Test public void conservaErrorDeNegocioSinReintentar() throws Exception {
        AtomicInteger peticiones = new AtomicInteger();
        servidor.createContext("/api/servicios", exchange -> {
            peticiones.incrementAndGet();
            byte[] respuesta = "{\"mensaje\":\"El servicio ya fue tomado\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.close();
        });
        ApiException error = assertThrows(ApiException.class, () -> new HttpJsonTaxistas(base, true)
                .solicitar("POST", "api/servicios", new JsonObject(), null));
        assertEquals(409, error.getStatus());
        assertEquals("El servicio ya fue tomado", error.getMessage());
        assertFalse(error.esIncierto());
        assertEquals(1, peticiones.get());
    }

    @Test public void noSigueRedireccionNiFiltraBearer() throws Exception {
        AtomicInteger destino = new AtomicInteger();
        servidor.createContext("/redirigir", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "otro");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        servidor.createContext("/otro", exchange -> {
            destino.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        ApiException error = assertThrows(ApiException.class, () -> new HttpJsonTaxistas(base, true)
                .solicitar("GET", "redirigir", null, "token-ficticio"));
        assertEquals(302, error.getStatus());
        assertEquals(0, destino.get());
    }

    @Test public void respuestaInvalidaTrasPostTieneResultadoIncierto() throws Exception {
        servidor.createContext("/api/servicios", exchange -> {
            byte[] respuesta = "<html>sin JSON</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.close();
        });
        ApiException error = assertThrows(ApiException.class, () -> new HttpJsonTaxistas(base, true)
                .solicitar("POST", "api/servicios", new JsonObject(), null));
        assertTrue(error.esIncierto());
    }

    @Test public void releaseExigeHttpsYSinCredencialesEnUrl() {
        assertThrows(ApiException.class, () -> new HttpJsonTaxistas(base, false));
        assertThrows(ApiException.class, () -> new HttpJsonTaxistas("https://usuario:clave@example.com/", false));
        assertThrows(ApiException.class, () -> new HttpJsonTaxistas("https://example.com/?token=ejemplo", false));
        assertThrows(ApiException.class, () -> new HttpJsonTaxistas("", true));
    }

    @Test public void conexionRechazadaAntesDeEscribirPermiteReintentar() throws Exception {
        int puerto;
        try (ServerSocket libre = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            puerto = libre.getLocalPort();
        }
        HttpJsonTaxistas http = new HttpJsonTaxistas("http://127.0.0.1:" + puerto + "/", true);
        ApiException error = assertThrows(ApiException.class,
                () -> http.solicitar("POST", "api/servicios", new JsonObject(), null));
        assertEquals(0, error.getStatus());
        assertFalse(error.esIncierto());
    }

    @Test public void rechazaSaltarAUnHostDistintoDesdeLaRuta() throws Exception {
        HttpJsonTaxistas http = new HttpJsonTaxistas(base, true);
        assertThrows(ApiException.class, () -> http.solicitar("GET", "https://example.com/api", null, "x"));
        assertThrows(ApiException.class, () -> http.solicitar("GET", "//example.com/api", null, "x"));
        assertThrows(ApiException.class, () -> http.solicitar("GET", "../api", null, "x"));
    }

    /** Fixture HTTP mínimo sin módulos del JDK que no están en el SDK Android. */
    private static final class ServidorPrueba {
        private final ServerSocket socket;
        private final Map<String, Manejador> rutas = new ConcurrentHashMap<>();
        private volatile boolean cerrado;

        ServidorPrueba() throws IOException {
            socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        }
        InetSocketAddress getAddress() { return (InetSocketAddress) socket.getLocalSocketAddress(); }
        void createContext(String ruta, Manejador manejador) { rutas.put(ruta, manejador); }
        void start() {
            Thread hilo = new Thread(() -> {
                while (!cerrado) {
                    try (Socket cliente = socket.accept()) {
                        cliente.setSoTimeout(3000);
                        Intercambio intercambio = new Intercambio(cliente);
                        Manejador manejador = rutas.get(intercambio.ruta);
                        if (manejador == null) intercambio.sendResponseHeaders(404, -1);
                        else manejador.atender(intercambio);
                    } catch (IOException error) {
                        if (!cerrado) throw new RuntimeException(error);
                    }
                }
            }, "api-taxistas-fixture");
            hilo.setDaemon(true);
            hilo.start();
        }
        void stop(int ignorado) {
            cerrado = true;
            try { socket.close(); } catch (IOException ignorada) { }
        }
    }

    private interface Manejador { void atender(Intercambio intercambio) throws IOException; }

    private static final class Cabeceras extends TreeMap<String, String> {
        Cabeceras() { super(String.CASE_INSENSITIVE_ORDER); }
        String getFirst(String nombre) { return get(nombre); }
        void add(String nombre, String valor) { put(nombre, valor); }
    }

    private static final class Intercambio {
        private final Socket socket;
        private final Cabeceras entrada = new Cabeceras();
        private final Cabeceras salida = new Cabeceras();
        private final InputStream cuerpo;
        final String ruta;

        Intercambio(Socket socket) throws IOException {
            this.socket = socket;
            InputStream datos = socket.getInputStream();
            String[] primera = linea(datos).split(" ");
            ruta = primera[1];
            String campo;
            while (!(campo = linea(datos)).isEmpty()) {
                int separador = campo.indexOf(':');
                entrada.put(campo.substring(0, separador), campo.substring(separador + 1).trim());
            }
            int cantidad = Integer.parseInt(entrada.getOrDefault("Content-Length", "0"));
            cuerpo = new java.io.ByteArrayInputStream(datos.readNBytes(cantidad));
        }
        Cabeceras getRequestHeaders() { return entrada; }
        Cabeceras getResponseHeaders() { return salida; }
        InputStream getRequestBody() { return cuerpo; }
        OutputStream getResponseBody() throws IOException { return socket.getOutputStream(); }
        void sendResponseHeaders(int status, long largo) throws IOException {
            StringBuilder cabecera = new StringBuilder("HTTP/1.1 " + status + " Resultado\r\n");
            cabecera.append("Content-Length: ").append(Math.max(0, largo)).append("\r\n");
            cabecera.append("Connection: close\r\nContent-Type: application/json\r\n");
            salida.forEach((nombre, valor) -> cabecera.append(nombre).append(": ").append(valor).append("\r\n"));
            cabecera.append("\r\n");
            socket.getOutputStream().write(cabecera.toString().getBytes(StandardCharsets.US_ASCII));
        }
        void close() throws IOException { socket.close(); }
        private static String linea(InputStream datos) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int valor;
            while ((valor = datos.read()) != -1 && valor != '\n') {
                if (valor != '\r') bytes.write(valor);
            }
            return bytes.toString(StandardCharsets.US_ASCII.name());
        }
    }
}
