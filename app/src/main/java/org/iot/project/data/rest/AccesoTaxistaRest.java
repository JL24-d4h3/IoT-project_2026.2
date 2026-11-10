package org.iot.project.data.rest;

import com.google.gson.JsonObject;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Cuenta;

import java.util.Locale;

/** Solo se usa cuando el usuario elige explícitamente el acceso de taxista. */
public final class AccesoTaxistaRest {
    private long operacion;

    public void iniciarSesion(String correo, String password, ResultCallback<Cuenta> callback) {
        String email = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || password == null || password.isEmpty()) {
            callback.onError("Escribe el correo y la contraseña de tu cuenta del sistema web de taxistas.");
            return;
        }
        long solicitud = ++operacion;
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("correo", email);
        cuerpo.addProperty("password", password);
        TaxistasApi.ejecutar(() -> TaxistasApi.getInstancia().objeto(
                "POST", "api/auth/login", cuerpo, null), new ResultCallback<JsonObject>() {
            @Override public void onExito(JsonObject respuesta) {
                if (solicitud != operacion) return;
                try {
                    Cuenta cuenta = SesionTaxistaRest.abrir(respuesta, email);
                    callback.onExito(cuenta);
                } catch (IllegalArgumentException error) {
                    callback.onError(error.getMessage());
                }
            }
            @Override public void onError(String mensaje) {
                if (solicitud == operacion) callback.onError(mensaje);
            }
        });
    }

    public void cancelar() { operacion++; }
}
