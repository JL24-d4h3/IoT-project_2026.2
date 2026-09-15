package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.TaxiService;

import java.util.List;

/**
 * Acceso al modulo de taxi al aeropuerto.
 *
 * <p>Este es el servicio que RC-019 a RC-022 marcan como susceptible de caerse:
 * cuando no responde, la aplicacion debe informarlo <em>sin romper</em> el
 * resto. Reservas y hoteles siguen funcionando.
 */
public interface TaxiRepository {

    /** Servicio en curso del cliente, o error si no tiene ninguno. */
    void servicioActivo(@NonNull String clienteId, @NonNull ResultCallback<TaxiService> callback);

    void historial(@NonNull String clienteId, @NonNull ResultCallback<List<TaxiService>> callback);

    void solicitar(@NonNull TaxiService solicitud, @NonNull ResultCallback<TaxiService> callback);

    void obtener(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback);

    /**
     * Avanza el servicio al estado siguiente del flujo (RF-111). Los estados
     * son exactos y no admiten saltos ni retrocesos: SOLICITADO, ASIGNADO,
     * EN_CAMINO, EN_TRASLADO, FINALIZADO.
     */
    void avanzar(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback);

    /**
     * RF-110: el servicio se cierra al escanear el QR. Llega a FINALIZADO sin
     * pasar por el avance manual.
     */
    void confirmarQr(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback);

    /** Solo tras finalizar. Escala 1 a 10. */
    void calificar(@NonNull String taxiId, float rating, @NonNull ResultCallback<TaxiService> callback);
}
