package org.iot.project.data.repository;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
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

    /** Solo tras finalizar. Escala 1 a 10. */
    void calificar(@NonNull String taxiId, float rating, @NonNull ResultCallback<TaxiService> callback);

    // ------------------------------------------------------------------
    //  Lado del conductor (RF-085 a RF-111)
    // ------------------------------------------------------------------

    /** RF-096: el perfil del conductor: su vehiculo, su nota y sus servicios. */
    void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback);

    /** RF-088, RF-089: solicitudes que este conductor puede atender, por cercania. */
    void disponibles(@NonNull String conductorId,
                     @NonNull ResultCallback<List<OfertaDeTaxi>> callback);

    /** RF-097: los servicios en curso de este conductor (ninguno, o uno). */
    void serviciosEnCursoDe(@NonNull String conductorId,
                            @NonNull ResultCallback<List<TaxiService>> callback);

    /** RF-090, RF-091, RF-092, RF-107: aceptar un pedido. */
    void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                 @NonNull ResultCallback<TaxiService> callback);

    /** RF-108, RF-109: avanzar el servicio. Nunca a FINALIZADO (RF-110). */
    void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                              @NonNull ResultCallback<TaxiService> callback);

    /** RF-102, RF-103, RF-104, RF-110: cerrar el servicio validando el codigo. */
    void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                       @NonNull String codigo, @NonNull ResultCallback<TaxiService> callback);
}
