package org.iot.project.data.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.models.Ubicacion;
import org.iot.project.models.Vehicle;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Lo que el sistema de taxistas sabe hoy de un servicio: estado real, conductor
 * y su ultima posicion. La app guarda en Firestore la solicitud del cliente; el
 * estado y el conductor se leen siempre de aqui (RT-006).
 */
final class TaxiVivo {

    final long servicioId;
    @NonNull final TaxiStatus estado;
    @Nullable final String taxistaId;
    @Nullable final Driver conductor;
    @Nullable final Ubicacion posicion;
    @Nullable final LocalDateTime posicionEn;

    private TaxiVivo(long servicioId, @NonNull TaxiStatus estado, @Nullable String taxistaId,
                     @Nullable Driver conductor, @Nullable Ubicacion posicion,
                     @Nullable LocalDateTime posicionEn) {
        this.servicioId = servicioId;
        this.estado = estado;
        this.taxistaId = taxistaId;
        this.conductor = conductor;
        this.posicion = posicion;
        this.posicionEn = posicionEn;
    }

    /**
     * @param servicio  {@code ServicioResponse} de la API
     * @param conductor detalle del taxista; si no se pudo leer se arma con los datos del servicio
     * @param ubicacion {@code UbicacionResponse}, o {@code null}
     */
    @NonNull
    static TaxiVivo desde(@NonNull JSONObject servicio, @Nullable Driver conductor,
                          @Nullable JSONObject ubicacion) throws JSONException {
        TaxiStatus estado = TaxiStatus.valueOf(servicio.getString("estado"));
        String taxistaId = servicio.isNull("taxistaId") ? null : String.valueOf(servicio.getLong("taxistaId"));
        Driver quien = conductor;
        if (quien == null && taxistaId != null) {
            quien = new Driver(taxistaId, servicio.optString("taxistaNombre", ""), "")
                    .withVehiculo(new Vehicle(servicio.optString("taxistaPlaca", "")));
        }
        Ubicacion posicion = null;
        LocalDateTime posicionEn = null;
        // Solo mientras el taxista atiende el servicio (RT-037); nunca antes ni despues.
        boolean siguiendo = estado == TaxiStatus.ASIGNADO || estado == TaxiStatus.EN_CAMINO
                || estado == TaxiStatus.EN_TRASLADO;
        if (siguiendo && ubicacion != null && !ubicacion.isNull("latitud") && !ubicacion.isNull("longitud")) {
            posicion = new Ubicacion(ubicacion.getDouble("latitud"), ubicacion.getDouble("longitud"));
            posicionEn = aHoraLocal(ubicacion.isNull("actualizadaEn") ? null : ubicacion.getString("actualizadaEn"));
        }
        return new TaxiVivo(servicio.getLong("id"), estado, taxistaId, quien, posicion, posicionEn);
    }

    /**
     * Copia de la solicitud guardada por el cliente con el estado, el conductor y la
     * posicion reales. La nota del cliente solo cuenta cuando el servicio termino.
     */
    @NonNull
    static TaxiService aplicar(@NonNull TaxiService base, @NonNull TaxiVivo vivo) {
        float nota = vivo.estado == TaxiStatus.FINALIZADO ? base.getRatingCliente() : 0f;
        TaxiService copia = new TaxiService(base.getId(), base.getCodigo(), base.getBookingId(), base.getClienteId())
                .withRuta(base.getOrigen(), base.getDestino());
        if (base.hasRecojo()) {
            copia.withRecojo(base.getLatRecojo(), base.getLngRecojo());
        }
        copia.withProgramacion(base.getFecha(), base.getHora(), base.isIdaYVuelta())
                .withPasajeros(base.getNumPasajeros())
                .withPrecio(base.getPrecio())
                .withEstadoPersistido(vivo.estado, vivo.conductor, nota);
        if (vivo.posicion != null) {
            copia.actualizarUbicacion(vivo.posicion.getLatitud(), vivo.posicion.getLongitud(),
                    vivo.posicionEn != null ? vivo.posicionEn : LocalDateTime.now());
        }
        return copia;
    }

    /** Hora del telefono a partir de una fecha ISO con o sin zona horaria; {@code null} si no sirve. */
    @Nullable
    static LocalDateTime aHoraLocal(@Nullable String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException conZona) {
            try {
                return LocalDateTime.parse(iso);
            } catch (RuntimeException sinZona) {
                return null;
            }
        }
    }
}
