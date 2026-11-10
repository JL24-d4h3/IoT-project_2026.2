package org.iot.project.data.rest;

import com.google.gson.JsonObject;

import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.Role;

/** JWT solo en memoria. La cuenta web no se confunde con un UID de Firebase. */
public final class SesionTaxistaRest {
    private static volatile Datos datos;

    private SesionTaxistaRest() { }

    static synchronized Cuenta abrir(JsonObject respuesta, String correo) {
        try {
            String token = respuesta.get("token").getAsString();
            String tipo = respuesta.get("tipoToken").getAsString();
            long minutos = respuesta.get("expiraEnMinutos").getAsLong();
            Driver conductor = TaxistasJson.conductor(respuesta.getAsJsonObject("taxista"));
            if (token.isBlank() || token.length() > 16384 || token.contains("\r") || token.contains("\n")
                    || !"Bearer".equalsIgnoreCase(tipo) || minutos <= 0 || minutos > 525600
                    || !conductor.isHabilitado()) throw new IllegalArgumentException();
            long vencimiento = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(minutos, 60000));
            Cuenta cuenta = new Cuenta(Role.CONDUCTOR, "taxista:" + conductor.getId(),
                    conductor.getNombreCompleto(), correo);
            datos = new Datos(conductor.getId(), token, vencimiento, cuenta);
            return cuenta;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("El sistema web no devolvió una sesión válida de taxista.");
        }
    }

    public static boolean estaActiva() {
        return vigente(datos);
    }

    private static boolean vigente(Datos actual) {
        return actual != null && System.currentTimeMillis() < actual.vencimiento;
    }

    public static String getId() { Datos actual = datos; return vigente(actual) ? actual.id : null; }
    public static String getToken() { Datos actual = datos; return vigente(actual) ? actual.token : null; }
    public static Cuenta getCuenta() { Datos actual = datos; return vigente(actual) ? actual.cuenta : null; }
    public static synchronized void cerrar() { datos = null; }

    private static final class Datos {
        final String id;
        final String token;
        final long vencimiento;
        final Cuenta cuenta;
        Datos(String id, String token, long vencimiento, Cuenta cuenta) {
            this.id = id;
            this.token = token;
            this.vencimiento = vencimiento;
            this.cuenta = cuenta;
        }
    }
}
