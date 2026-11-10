package org.iot.project.data.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxistaAdministrado;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contrato administrativo puro: aprobación y acceso de la cuenta son decisiones independientes. */
public final class TaxistasAdminJson {
    private TaxistasAdminJson() { }

    public static TaxistaAdministrado taxista(JsonObject datos) {
        if (datos == null) throw invalido("taxista");
        JsonElement identificador = datos.get("id");
        if (identificador == null || !identificador.isJsonPrimitive()
                || identificador.getAsJsonPrimitive().isBoolean()) throw invalido("id");
        String id = validarId(identificador.getAsString());
        String estado = textoRequerido(datos, "estadoHabilitacion", 20);
        if (!"HABILITADO".equals(estado) && !"PENDIENTE".equals(estado)) {
            throw invalido("estadoHabilitacion");
        }
        boolean cuentaActiva = booleanoRequerido(datos, "cuentaActiva");
        String correo = textoRequerido(datos, "correo", 254);
        Driver perfil = TaxistasJson.conductor(datos);
        if (!id.equals(perfil.getId())) throw invalido("id");
        perfil.withContacto(correo, perfil.getTelefono(), perfil.getDireccion());
        // Driver conserva su semántica operativa: aprobado Y cuenta activa.
        return new TaxistaAdministrado(perfil, "HABILITADO".equals(estado), cuentaActiva);
    }

    public static List<TaxistaAdministrado> listar(JsonArray datos) {
        if (datos == null) throw invalido("lista de taxistas");
        List<TaxistaAdministrado> resultado = new ArrayList<>();
        Set<String> identificadores = new HashSet<>();
        for (JsonElement fila : datos) {
            if (fila == null || !fila.isJsonObject()) throw invalido("taxista");
            TaxistaAdministrado taxista = taxista(fila.getAsJsonObject());
            if (!identificadores.add(taxista.getId())) throw invalido("id duplicado");
            resultado.add(taxista);
        }
        return resultado;
    }

    public static String validarId(String id) {
        try {
            if (id == null || !id.matches("[1-9][0-9]*") || Long.parseLong(id) <= 0L) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException error) {
            throw invalido("id");
        }
    }

    public static JsonObject cuerpoHabilitacion(boolean habilitado) {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("habilitado", habilitado);
        return cuerpo;
    }

    public static JsonObject cuerpoCuenta(boolean activa) {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("activa", activa);
        return cuerpo;
    }

    public static TaxistaAdministrado confirmarHabilitacion(JsonObject respuesta, String id,
                                                            boolean habilitado) {
        TaxistaAdministrado dato = confirmarIdentidad(respuesta, id);
        if (dato.isHabilitado() != habilitado) throw cambioNoConfirmado();
        return dato;
    }

    public static TaxistaAdministrado confirmarCuenta(JsonObject respuesta, String id,
                                                      boolean activa) {
        TaxistaAdministrado dato = confirmarIdentidad(respuesta, id);
        if (dato.isCuentaActiva() != activa) throw cambioNoConfirmado();
        return dato;
    }

    private static TaxistaAdministrado confirmarIdentidad(JsonObject respuesta, String id) {
        String esperado = validarId(id);
        TaxistaAdministrado dato = taxista(respuesta);
        if (!esperado.equals(dato.getId())) throw cambioNoConfirmado();
        return dato;
    }

    private static String textoRequerido(JsonObject datos, String campo, int maximo) {
        JsonElement valor = datos.get(campo);
        if (valor == null || !valor.isJsonPrimitive()
                || !valor.getAsJsonPrimitive().isString()) throw invalido(campo);
        String texto = valor.getAsString().trim();
        if (texto.isEmpty() || texto.length() > maximo) throw invalido(campo);
        return texto;
    }

    private static boolean booleanoRequerido(JsonObject datos, String campo) {
        JsonElement valor = datos.get(campo);
        if (valor == null || !valor.isJsonPrimitive()
                || !valor.getAsJsonPrimitive().isBoolean()) throw invalido(campo);
        return valor.getAsBoolean();
    }

    private static IllegalArgumentException invalido(String campo) {
        return new IllegalArgumentException("El sistema web devolvió un dato administrativo inválido: " + campo + ".");
    }

    private static IllegalArgumentException cambioNoConfirmado() {
        return new IllegalArgumentException("El sistema web no confirmó el cambio para el taxista seleccionado. Actualiza la lista.");
    }
}
