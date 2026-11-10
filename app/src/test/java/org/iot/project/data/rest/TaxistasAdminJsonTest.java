package org.iot.project.data.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.iot.project.models.TaxistaAdministrado;
import org.junit.Test;

import java.util.List;

public class TaxistasAdminJsonTest {
    @Test
    public void aprobacionNoSePierdeAlDesactivarLaCuenta() {
        TaxistaAdministrado dato = TaxistasAdminJson.taxista(ficha("HABILITADO", false));

        assertTrue(dato.isHabilitado());
        assertFalse(dato.isCuentaActiva());
        assertFalse(dato.getPerfil().isHabilitado());
    }

    @Test
    public void cuentaActivaNoApruebaUnTaxistaPendiente() {
        TaxistaAdministrado dato = TaxistasAdminJson.taxista(ficha("PENDIENTE", true));

        assertFalse(dato.isHabilitado());
        assertTrue(dato.isCuentaActiva());
        assertFalse(dato.getPerfil().isHabilitado());
    }

    @Test
    public void perfilOperativoSoloQuedaHabilitadoConAmbasDecisiones() {
        assertTrue(TaxistasAdminJson.taxista(ficha("HABILITADO", true)).getPerfil().isHabilitado());
        TaxistaAdministrado pendiente = TaxistasAdminJson.taxista(ficha("PENDIENTE", false));
        assertFalse(pendiente.isHabilitado());
        assertFalse(pendiente.isCuentaActiva());
        assertFalse(pendiente.getPerfil().isHabilitado());
    }

    @Test
    public void mapeaCorreoYDocumentoEnmascaradoSinUsarDatosPrivadosExtras() {
        JsonObject respuesta = ficha("HABILITADO", true);
        respuesta.addProperty("numeroDocumento", "documento-completo-ignorado");
        respuesta.addProperty("password", "campo-que-no-se-mapea");

        TaxistaAdministrado dato = TaxistasAdminJson.taxista(respuesta);

        assertEquals("7", dato.getId());
        assertEquals("Ana Pérez", dato.getPerfil().getNombreCompleto());
        assertEquals("ana@example.test", dato.getPerfil().getEmail());
        assertEquals("****1234", dato.getPerfil().getNumeroDocumento());
        assertNull(dato.getPerfil().getDireccion());
    }

    @Test
    public void cuentaActivaEsObligatoriaYDebeSerBooleanReal() {
        JsonObject sinCuenta = ficha("HABILITADO", true);
        sinCuenta.remove("cuentaActiva");
        rechaza(() -> TaxistasAdminJson.taxista(sinCuenta));
        JsonObject texto = ficha("HABILITADO", true);
        texto.addProperty("cuentaActiva", "false");
        rechaza(() -> TaxistasAdminJson.taxista(texto));
        JsonObject numero = ficha("HABILITADO", true);
        numero.addProperty("cuentaActiva", 1);
        rechaza(() -> TaxistasAdminJson.taxista(numero));
    }

    @Test
    public void noSuponePendienteAnteEstadoAusenteODesconocido() {
        JsonObject ausente = ficha("PENDIENTE", true);
        ausente.remove("estadoHabilitacion");
        rechaza(() -> TaxistasAdminJson.taxista(ausente));
        rechaza(() -> TaxistasAdminJson.taxista(ficha("APROBADO", true)));
        rechaza(() -> TaxistasAdminJson.taxista(ficha("habilitado", true)));
    }

    @Test
    public void rechazaIdsQuePuedenAlterarLaRutaOExcedenUnLong() {
        for (String id : new String[]{null, "", "0", "-1", "01", "+7", "7.0", "7e1",
                "7/ubicacion", "../7", " 7", "9223372036854775808"}) {
            rechaza(() -> TaxistasAdminJson.validarId(id));
        }
        assertEquals("7", TaxistasAdminJson.validarId("7"));
        assertEquals("9223372036854775807", TaxistasAdminJson.validarId("9223372036854775807"));
    }

    @Test
    public void identificadorDeRespuestaDebeSerIntegralPositivoYCanonico() {
        JsonObject decimal = ficha("PENDIENTE", true);
        decimal.addProperty("id", 7.5);
        rechaza(() -> TaxistasAdminJson.taxista(decimal));
        JsonObject booleano = ficha("PENDIENTE", true);
        booleano.addProperty("id", true);
        rechaza(() -> TaxistasAdminJson.taxista(booleano));
        JsonObject texto = ficha("PENDIENTE", true);
        texto.addProperty("id", "7");
        assertEquals("7", TaxistasAdminJson.taxista(texto).getId());
    }

    @Test
    public void cuerposPutSeparanAprobacionDeActivacionDeCuenta() {
        assertEquals(JsonParser.parseString("{\"habilitado\":true}"), TaxistasAdminJson.cuerpoHabilitacion(true));
        assertEquals(JsonParser.parseString("{\"habilitado\":false}"), TaxistasAdminJson.cuerpoHabilitacion(false));
        assertEquals(JsonParser.parseString("{\"activa\":true}"), TaxistasAdminJson.cuerpoCuenta(true));
        assertEquals(JsonParser.parseString("{\"activa\":false}"), TaxistasAdminJson.cuerpoCuenta(false));
    }

    @Test
    public void respuestaDebeConfirmarElTaxistaYLaDecisionSolicitada() {
        JsonObject otro = ficha("HABILITADO", true);
        otro.addProperty("id", 8);
        rechaza(() -> TaxistasAdminJson.confirmarHabilitacion(otro, "7", true));
        rechaza(() -> TaxistasAdminJson.confirmarCuenta(otro, "7", true));
        rechaza(() -> TaxistasAdminJson.confirmarHabilitacion(ficha("PENDIENTE", true), "7", true));
        rechaza(() -> TaxistasAdminJson.confirmarCuenta(ficha("HABILITADO", true), "7", false));
        TaxistaAdministrado dato = TaxistasAdminJson.confirmarCuenta(ficha("HABILITADO", false), "7", false);
        assertTrue(dato.isHabilitado());
        assertFalse(dato.isCuentaActiva());
    }

    @Test
    public void listaAceptaVacioYRechazaFilasInvalidasOIdsDuplicados() {
        assertTrue(TaxistasAdminJson.listar(new JsonArray()).isEmpty());
        JsonArray duplicados = new JsonArray();
        duplicados.add(ficha("HABILITADO", true));
        duplicados.add(ficha("PENDIENTE", false));
        rechaza(() -> TaxistasAdminJson.listar(duplicados));
        JsonArray invalida = new JsonArray();
        invalida.add("no-es-taxista");
        rechaza(() -> TaxistasAdminJson.listar(invalida));
        JsonArray valida = new JsonArray();
        valida.add(ficha("HABILITADO", false));
        List<TaxistaAdministrado> datos = TaxistasAdminJson.listar(valida);
        assertEquals(1, datos.size());
        assertTrue(datos.get(0).isHabilitado());
        assertFalse(datos.get(0).isCuentaActiva());
    }

    @Test
    public void correoAdministrativoDebeVenirEnLaRespuestaCompleta() {
        JsonObject sinCorreo = ficha("HABILITADO", true);
        sinCorreo.remove("correo");
        rechaza(() -> TaxistasAdminJson.taxista(sinCorreo));
        JsonObject invalido = ficha("HABILITADO", true);
        invalido.addProperty("correo", false);
        rechaza(() -> TaxistasAdminJson.taxista(invalido));
    }

    private static JsonObject ficha(String habilitacion, boolean activa) {
        JsonObject datos = new JsonObject();
        datos.addProperty("id", 7);
        datos.addProperty("nombres", "Ana");
        datos.addProperty("apellidos", "Pérez");
        datos.addProperty("tipoDocumento", "DNI");
        datos.addProperty("documentoEnmascarado", "****1234");
        datos.addProperty("correo", "ana@example.test");
        datos.addProperty("estadoHabilitacion", habilitacion);
        datos.addProperty("cuentaActiva", activa);
        return datos;
    }

    private static void rechaza(Runnable operacion) {
        try {
            operacion.run();
            fail("El contrato administrativo debía rechazar este dato.");
        } catch (IllegalArgumentException esperado) {
            // El mensaje no incluye valores recibidos ni credenciales.
            assertTrue(esperado.getMessage() != null && !esperado.getMessage().isEmpty());
        }
    }
}
