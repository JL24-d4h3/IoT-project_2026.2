package org.iot.project.data.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Vehicle;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Traduce el JSON de la API a los modelos de la app. Sin red ni Android, para
 * poder probarlo con JSON de ejemplo.
 */
final class TaxiApiMapper {

    private TaxiApiMapper() {
    }

    /** {@code ServicioResponse} de la API -> {@link TaxiService}. */
    @NonNull
    static TaxiService servicio(@NonNull JSONObject o) throws JSONException {
        String id = String.valueOf(o.getLong("id"));
        TaxiStatus estado = TaxiStatus.valueOf(o.getString("estado"));

        LocalDateTime solicitado = fechaHora(o, "fechaSolicitud");
        LocalDate fecha = o.isNull("fechaProgramada")
                ? (solicitado != null ? solicitado.toLocalDate() : LocalDate.now())
                : LocalDate.parse(o.getString("fechaProgramada"));
        LocalTime hora = o.isNull("horaProgramada")
                ? (solicitado != null ? solicitado.toLocalTime().withSecond(0).withNano(0) : LocalTime.MIDNIGHT)
                : LocalTime.parse(o.getString("horaProgramada"));

        Driver conductor = null;
        if (!o.isNull("taxistaId")) {
            conductor = new Driver(String.valueOf(o.getLong("taxistaId")), texto(o, "taxistaNombre"), "")
                    .withVehiculo(new Vehicle(texto(o, "taxistaPlaca")));
        }

        // El codigo que se muestra en la tarjeta es solo una etiqueta del servicio.
        // El codigo QR real nunca viaja aqui: la API lo entrega una sola vez, al cliente.
        return new TaxiService(id, "SRV-" + id, texto(o, "bookingId"), "")
                .withRuta(texto(o, "direccionOrigen"), texto(o, "direccionDestino"))
                .withRecojo(o.getDouble("latitudOrigen"), o.getDouble("longitudOrigen"))
                .withProgramacion(fecha, hora, !o.isNull("idaYVuelta") && o.getBoolean("idaYVuelta"))
                .withPasajeros(o.isNull("numPasajeros") ? 1 : o.getInt("numPasajeros"))
                .withPrecio(o.isNull("tarifa") ? 0d : o.getDouble("tarifa"))
                .withEstadoPersistido(estado, conductor, 0f);
    }

    /** {@code TaxistaResponse} de la API -> {@link Driver}. */
    @NonNull
    static Driver taxista(@NonNull JSONObject o) throws JSONException {
        Vehicle vehiculo = new Vehicle(texto(o, "placaVehiculo"))
                .withFoto(textoONulo(o, "fotoVehiculoUrl"));
        double promedio = o.isNull("valoracionPromedio") ? 0d : o.getDouble("valoracionPromedio");
        Driver conductor = new Driver(String.valueOf(o.getLong("id")),
                texto(o, "nombres"), texto(o, "apellidos"))
                .withDocumento(texto(o, "tipoDocumento"), texto(o, "documentoEnmascarado"))
                .withContacto("", texto(o, "telefono"), "")
                .withFoto(textoONulo(o, "fotoUrl"))
                .withVehiculo(vehiculo)
                .withRating((float) (promedio * ApiConfig.FACTOR_VALORACION),
                        o.isNull("totalValoraciones") ? 0 : o.getInt("totalValoraciones"));
        conductor.setHabilitado("HABILITADO".equals(texto(o, "estadoHabilitacion")));
        return conductor;
    }

    /** Mensaje de un {@code ApiErrorResponse}, o {@code null} si el cuerpo no lo trae. */
    @Nullable
    static String mensajeDeError(@Nullable String cuerpo) {
        if (cuerpo == null || cuerpo.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(cuerpo);
            String mensaje = o.isNull("mensaje") ? "" : o.getString("mensaje").trim();
            return mensaje.isEmpty() ? null : mensaje;
        } catch (JSONException ignorado) {
            return null;
        }
    }

    @NonNull
    private static String texto(JSONObject o, String clave) {
        return o.isNull(clave) ? "" : o.optString(clave, "");
    }

    @Nullable
    private static String textoONulo(JSONObject o, String clave) {
        String valor = texto(o, clave);
        return valor.isEmpty() ? null : valor;
    }

    @Nullable
    private static LocalDateTime fechaHora(JSONObject o, String clave) {
        if (o.isNull(clave)) {
            return null;
        }
        try {
            return LocalDateTime.parse(o.getString(clave));
        } catch (JSONException | RuntimeException ignorado) {
            return null;
        }
    }
}
