package org.iot.project.data.rest;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SesionTaxistaRestTest {
    @Before public void preparar() { SesionTaxistaRest.cerrar(); }
    @After public void limpiar() { SesionTaxistaRest.cerrar(); }

    @Test public void noConfundeIdNumericoConUidFirebase() {
        Cuenta cuenta = SesionTaxistaRest.abrir(respuesta(), "taxista@example.test");
        assertEquals(Role.CONDUCTOR, cuenta.getRol());
        assertEquals("taxista:42", cuenta.getIdentidadId());
        assertEquals("42", SesionTaxistaRest.getId());
        assertEquals("token-ficticio", SesionTaxistaRest.getToken());
        assertEquals("taxista@example.test", cuenta.getEmail());
    }

    @Test public void cerrarRetiraTokenYCuenta() {
        SesionTaxistaRest.abrir(respuesta(), "taxista@example.test");
        SesionTaxistaRest.cerrar();
        assertFalse(SesionTaxistaRest.estaActiva());
        assertNull(SesionTaxistaRest.getToken());
        assertNull(SesionTaxistaRest.getCuenta());
    }

    @Test public void rechazaConductorDeshabilitadoYTokenVacio() {
        JsonObject respuesta = respuesta();
        respuesta.getAsJsonObject("taxista").addProperty("cuentaActiva", false);
        assertThrows(IllegalArgumentException.class,
                () -> SesionTaxistaRest.abrir(respuesta, "taxista@example.test"));
        assertNull(SesionTaxistaRest.getToken());
        JsonObject sinToken = respuesta();
        sinToken.addProperty("token", "");
        assertThrows(IllegalArgumentException.class,
                () -> SesionTaxistaRest.abrir(sinToken, "taxista@example.test"));
    }

    @Test public void rechazaCaducidadInvalidaYTipoDistinto() {
        JsonObject expirada = respuesta();
        expirada.addProperty("expiraEnMinutos", 0);
        assertThrows(IllegalArgumentException.class,
                () -> SesionTaxistaRest.abrir(expirada, "taxista@example.test"));
        JsonObject otroTipo = respuesta();
        otroTipo.addProperty("tipoToken", "Basic");
        assertThrows(IllegalArgumentException.class,
                () -> SesionTaxistaRest.abrir(otroTipo, "taxista@example.test"));
    }

    private static JsonObject respuesta() {
        return JsonParser.parseString("{\"token\":\"token-ficticio\",\"tipoToken\":\"Bearer\","
                + "\"expiraEnMinutos\":720,\"taxista\":{\"id\":42,\"nombres\":\"Ana\","
                + "\"apellidos\":\"Prueba\",\"estadoHabilitacion\":\"HABILITADO\",\"cuentaActiva\":true}}")
                .getAsJsonObject();
    }
}
