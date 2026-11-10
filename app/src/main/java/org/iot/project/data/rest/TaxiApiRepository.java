package org.iot.project.data.rest;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.iot.project.core.ResultCallback;
import org.iot.project.core.SessionManager;
import org.iot.project.data.firebase.FirebaseTaxiRepository;
import org.iot.project.data.repository.TaxiRepository;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Driver;
import org.iot.project.models.OfertaDeTaxi;
import org.iot.project.models.Role;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.iot.project.utils.Distancia;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * REST es la fuente del estado del viaje. Firestore conserva el vínculo de la
 * reserva, su propietario, programación, beneficio y QR, que la API no devuelve
 * en las consultas. No se envía una solicitud desde una lectura o un polling.
 */
public final class TaxiApiRepository implements TaxiRepository {
    private static final long INTERVALO_MS = 5_000L;
    private static final String ENVIO_INCIERTO = "No se pudo confirmar el envío al sistema de taxis. "
            + "No lo repetiremos para evitar duplicados; revisa el servicio en la web antes de solicitar otro.";

    private final FirebaseTaxiRepository locales;
    private final ConcurrentMap<String, TaxiService> creacionesConfirmadas = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Float> valoracionesConfirmadas = new ConcurrentHashMap<>();

    public TaxiApiRepository() {
        this(new FirebaseTaxiRepository());
    }

    public TaxiApiRepository(FirebaseTaxiRepository locales) {
        this.locales = locales;
    }

    @Override
    public void solicitar(@NonNull TaxiService solicitud, @NonNull ResultCallback<TaxiService> callback) {
        AccesoCliente acceso = cliente(solicitud.getClienteId(), callback);
        if (acceso == null) return;
        locales.solicitar(solicitud, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService local) {
                if (!clienteVigente(acceso, callback)) return;
                TaxiService confirmado = creacionesConfirmadas.get(clave(acceso, local));
                if (confirmado != null) {
                    guardarCreacion(acceso, confirmado, callback);
                    return;
                }
                if (local.getApiId() != null) {
                    actualizarCliente(acceso, local, callback);
                    return;
                }
                final JsonObject cuerpo;
                try {
                    cuerpo = TaxistasJson.solicitud(local, acceso.nombre, null);
                } catch (IllegalArgumentException error) {
                    callback.onError(error.getMessage());
                    return;
                }
                locales.reclamarEnvioApi(local.getId(), new ResultCallback<TaxiService>() {
                    @Override public void onExito(@NonNull TaxiService reclamado) {
                        if (!clienteVigente(acceso, callback)) return;
                        if (reclamado.getApiId() != null) {
                            actualizarCliente(acceso, reclamado, callback);
                            return;
                        }
                        enviarSolicitud(acceso, reclamado, cuerpo, callback);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (clienteVigente(acceso, callback)) callback.onError(mensaje);
                    }
                });
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    private void enviarSolicitud(AccesoCliente acceso, TaxiService local, JsonObject cuerpo,
                                 ResultCallback<TaxiService> callback) {
        TaxistasApi.ejecutar(() -> {
            if (!vigente(acceso)) return ResultadoPost.error(ENVIO_INCIERTO, false);
            try {
                JsonObject datos = api().objeto("POST", "/api/servicios", cuerpo, null);
                // Si se pierde el QR en esta respuesta no se podrá recuperar por GET.
                if (!presente(datos, "codigoQr") || datos.get("codigoQr").getAsString().trim().isEmpty()) {
                    return ResultadoPost.error(ENVIO_INCIERTO, false);
                }
                TaxiService confirmado = TaxistasJson.servicio(datos, local);
                creacionesConfirmadas.put(clave(acceso, local), confirmado);
                return ResultadoPost.exito(confirmado);
            } catch (ApiException error) {
                boolean rechazo = !error.esIncierto() && (error.getStatus() == 0
                        || (error.getStatus() >= 400 && error.getStatus() < 500 && error.getStatus() != 408));
                return ResultadoPost.error(rechazo ? error.getMensaje() : ENVIO_INCIERTO, rechazo);
            } catch (Exception error) {
                // También una respuesta ilegible puede haber creado el servicio.
                return ResultadoPost.error(ENVIO_INCIERTO, false);
            }
        }, new ResultCallback<ResultadoPost>() {
            @Override public void onExito(@NonNull ResultadoPost resultado) {
                if (!clienteVigente(acceso, callback)) return;
                if (resultado.taxi != null) {
                    guardarCreacion(acceso, resultado.taxi, callback);
                } else if (resultado.rechazo) {
                    locales.registrarRechazoApi(local.getId(), new ResultCallback<Boolean>() {
                        @Override public void onExito(@NonNull Boolean ignorado) {
                            if (clienteVigente(acceso, callback)) callback.onError(resultado.error);
                        }
                        @Override public void onError(@NonNull String mensaje) {
                            if (clienteVigente(acceso, callback)) callback.onError(resultado.error
                                    + " No pudimos guardar el rechazo; revisa el traslado antes de reenviarlo.");
                        }
                    });
                } else {
                    callback.onError(resultado.error);
                }
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(ENVIO_INCIERTO);
            }
        });
    }

    private void guardarCreacion(AccesoCliente acceso, TaxiService taxi,
                                 ResultCallback<TaxiService> callback) {
        locales.sincronizarDesdeApi(taxi, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService guardado) {
                creacionesConfirmadas.remove(clave(acceso, taxi));
                if (clienteVigente(acceso, callback)) callback.onExito(guardado);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(
                        "El sistema web creó el viaje, pero no pudimos guardar su vínculo con la reserva. "
                                + "Actualiza para volver a guardar el vínculo; no crearemos otro viaje.");
            }
        });
    }

    @Override
    public void historial(@NonNull String clienteId, @NonNull ResultCallback<List<TaxiService>> callback) {
        consultarHistorial(clienteId, () -> true, callback);
    }

    @Override
    public void serviciosDeReserva(@NonNull String hotelId, @NonNull String bookingId,
                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        AccesoAdministrador acceso = administrador(hotelId, bookingId, callback);
        if (acceso != null) consultarServiciosAdministrador(acceso, () -> true, callback);
    }

    @Override
    public Suscripcion escucharServiciosDeReserva(@NonNull String hotelId, @NonNull String bookingId,
                                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        AccesoAdministrador acceso = administrador(hotelId, bookingId, callback);
        if (acceso == null) return () -> { };
        SondeoAdministrador sondeo = new SondeoAdministrador(acceso, callback);
        sondeo.handler.post(sondeo);
        return sondeo;
    }

    private void consultarServiciosAdministrador(AccesoAdministrador acceso, Vigencia consulta,
                                                 ResultCallback<List<TaxiService>> callback) {
        if (!consulta.activa() || !administradorVigente(acceso, callback)) return;
        locales.autorizarReservaAdministrador(acceso.hotelId, acceso.bookingId,
                new ResultCallback<FirebaseTaxiRepository.ContextoAdministrador>() {
                    @Override public void onExito(@NonNull FirebaseTaxiRepository.ContextoAdministrador contexto) {
                        if (!consulta.activa() || !administradorVigente(acceso, callback)) return;
                        if (!contexto.uid.equals(acceso.uid) || !contexto.vigente()) {
                            callback.onError("La sesión o la asignación del administrador cambiaron durante la consulta.");
                            return;
                        }
                        locales.serviciosDeReservaAutorizados(contexto, new ResultCallback<List<TaxiService>>() {
                            @Override public void onExito(@NonNull List<TaxiService> localesReserva) {
                                if (!consulta.activa() || !administradorVigente(acceso, callback)) return;
                                if (localesReserva.isEmpty()) {
                                    entregarServiciosAdministrador(acceso, contexto, consulta, localesReserva, callback);
                                    return;
                                }
                                TaxistasApi.ejecutar(() -> {
                                    List<TaxiService> servicios = new ArrayList<>();
                                    Runnable comprobar = () -> exigirConsultaAdministrador(acceso, contexto, consulta);
                                    for (TaxiService local : localesReserva) {
                                        comprobar.run();
                                        // Un registro local pendiente no se publica como viaje web,
                                        // ni dispara creación o escritura desde el administrador.
                                        TaxiService servicio = local.getApiId() == null ? local
                                                : consultarRemotoAdministrador(local, comprobar);
                                        if (consulta instanceof SondeoAdministrador) {
                                            ((SondeoAdministrador) consulta).conservarUltimoReporte(servicio);
                                        }
                                        servicios.add(servicio);
                                    }
                                    return servicios;
                                }, new ResultCallback<List<TaxiService>>() {
                                    @Override public void onExito(@NonNull List<TaxiService> servicios) {
                                        if (consulta.activa() && administradorVigente(acceso, callback)) {
                                            entregarServiciosAdministrador(acceso, contexto, consulta, servicios, callback);
                                        }
                                    }
                                    @Override public void onError(@NonNull String mensaje) {
                                        if (consulta.activa() && administradorVigente(acceso, callback)) callback.onError(mensaje);
                                    }
                                });
                            }
                            @Override public void onError(@NonNull String mensaje) {
                                if (consulta.activa() && administradorVigente(acceso, callback)) callback.onError(mensaje);
                            }
                        });
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (consulta.activa() && administradorVigente(acceso, callback)) callback.onError(mensaje);
                    }
                });
    }

    private void entregarServiciosAdministrador(AccesoAdministrador acceso,
                                                 FirebaseTaxiRepository.ContextoAdministrador contexto,
                                                 Vigencia consulta, List<TaxiService> servicios,
                                                 ResultCallback<List<TaxiService>> callback) {
        if (!consulta.activa() || !administradorVigente(acceso, callback)) return;
        // El servidor vuelve a verificar asignación y propietario tras la red.
        // El administrador no guarda snapshots ni cambia el estado en Firestore.
        locales.revalidarReservaAdministrador(contexto, new ResultCallback<Boolean>() {
            @Override public void onExito(@NonNull Boolean autorizado) {
                if (!consulta.activa() || !administradorVigente(acceso, callback)) return;
                if (!autorizado || !contexto.vigente()) {
                    callback.onError("La reserva o la asignación del administrador cambiaron durante la consulta.");
                    return;
                }
                callback.onExito(servicios);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (consulta.activa() && administradorVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    /** Consulta únicamente el estado público, el perfil asignado y su posición real. */
    private TaxiService consultarRemotoAdministrador(TaxiService local, Runnable comprobar) throws Exception {
        comprobar.run();
        JsonObject estado = api().objeto("GET", "/api/servicios/" + local.getApiId() + "/estado", null, null);
        if (!presente(estado, "servicioId") || !local.getApiId().equals(estado.get("servicioId").getAsString())
                || !presente(estado, "estado")) {
            throw new IllegalArgumentException("La API no devolvió el estado del traslado de esta reserva.");
        }
        // Se seleccionan los campos permitidos: nunca se copia un codigoQr de
        // la respuesta, aunque una versión futura del backend lo incluyera.
        JsonObject datos = new JsonObject();
        datos.addProperty("id", local.getApiId());
        datos.add("estado", estado.get("estado"));
        Driver conductor = null;
        if (presente(estado, "taxistaId")) {
            String id = estado.get("taxistaId").getAsString();
            if (!id.matches("[1-9][0-9]*")) throw new IllegalArgumentException("La API no devolvió un conductor válido.");
            try { Long.parseLong(id); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("La API no devolvió un conductor válido."); }
            comprobar.run();
            conductor = perfil(api().objeto("GET", "/api/taxistas/" + id, null, null));
            if (!id.equals(conductor.getId())) throw new IllegalArgumentException("El perfil no corresponde al conductor asignado.");
            datos.addProperty("taxistaId", id);
            datos.addProperty("taxistaNombre", conductor.getNombreCompleto());
            if (conductor.getVehiculo() != null) datos.addProperty("taxistaPlaca", conductor.getVehiculo().getPlaca());
        }
        TaxiService taxi = TaxistasJson.servicio(datos, local).withEnvioApi(local.getEnvioApi());
        taxi.withEstadoPersistido(taxi.getEstado(), conductor, taxi.getRatingCliente());
        if (conductor != null && taxi.getEstado().isActive()) {
            comprobar.run();
            JsonObject ubicacion = api().objeto("GET", "/api/taxistas/" + conductor.getId() + "/ubicacion", null, null);
            if (!presente(ubicacion, "taxistaId") || !conductor.getId().equals(ubicacion.get("taxistaId").getAsString())) {
                throw new IllegalArgumentException("La ubicación no corresponde al conductor de este traslado.");
            }
            boolean latitud = presente(ubicacion, "latitud");
            boolean longitud = presente(ubicacion, "longitud");
            boolean fecha = presente(ubicacion, "actualizadaEn");
            if (latitud || longitud || fecha) {
                if (!latitud || !longitud || !fecha) {
                    throw new IllegalArgumentException("La API devolvió una posición incompleta. Se conserva el último reporte confirmado.");
                }
                double lat = coordenada(ubicacion, "latitud", 90d);
                double lng = coordenada(ubicacion, "longitud", 180d);
                LocalDateTime momento = LocalDateTime.parse(ubicacion.get("actualizadaEn").getAsString());
                if (taxi.getUltimaActualizacionUbicacion() == null
                        || !momento.isBefore(taxi.getUltimaActualizacionUbicacion())) {
                    taxi.actualizarUbicacion(lat, lng, momento);
                }
            }
        }
        comprobar.run();
        return taxi;
    }

    private final class SondeoAdministrador implements Suscripcion, Runnable, Vigencia {
        final Handler handler = new Handler(Looper.getMainLooper());
        final AccesoAdministrador acceso;
        final ResultCallback<List<TaxiService>> callback;
        final ConcurrentMap<String, UltimoReporteAdministrador> posicionesConfirmadas = new ConcurrentHashMap<>();
        volatile boolean cerrada;
        SondeoAdministrador(AccesoAdministrador acceso, ResultCallback<List<TaxiService>> callback) {
            this.acceso = acceso;
            this.callback = callback;
        }
        @Override public boolean activa() { return !cerrada && vigente(acceso); }
        @Override public void run() {
            if (!activa()) { cerrar(); return; }
            consultarServiciosAdministrador(acceso, this, new ResultCallback<List<TaxiService>>() {
                @Override public void onExito(@NonNull List<TaxiService> datos) {
                    if (!activa()) return;
                    for (TaxiService servicio : datos) {
                        if (servicio.getApiId() != null && servicio.getDriver() != null
                                && servicio.getUltimaActualizacionUbicacion() != null) {
                            posicionesConfirmadas.put(servicio.getId(), new UltimoReporteAdministrador(servicio));
                        } else {
                            posicionesConfirmadas.remove(servicio.getId());
                        }
                    }
                    callback.onExito(datos);
                    programar();
                }
                @Override public void onError(@NonNull String mensaje) {
                    if (!activa()) return;
                    callback.onError(mensaje);
                    programar();
                }
            });
        }
        void conservarUltimoReporte(TaxiService servicio) {
            if (!activa() || servicio.getApiId() == null || servicio.getDriver() == null) return;
            UltimoReporteAdministrador previo = posicionesConfirmadas.get(servicio.getId());
            if (previo != null) previo.conservarEn(servicio);
        }
        void programar() { if (activa()) handler.postDelayed(this, INTERVALO_MS); }
        @Override public void cerrar() {
            cerrada = true;
            handler.removeCallbacks(this);
            posicionesConfirmadas.clear();
        }
    }

    static final class UltimoReporteAdministrador {
        final String taxiId;
        final String apiId;
        final String conductorId;
        final double latitud;
        final double longitud;
        final LocalDateTime momento;
        UltimoReporteAdministrador(TaxiService servicio) {
            taxiId = servicio.getId();
            apiId = servicio.getApiId();
            conductorId = servicio.getDriver().getId();
            latitud = servicio.getLatConductor();
            longitud = servicio.getLngConductor();
            momento = servicio.getUltimaActualizacionUbicacion();
        }

        void conservarEn(TaxiService servicio) {
            if (!taxiId.equals(servicio.getId()) || !apiId.equals(servicio.getApiId())
                    || servicio.getDriver() == null
                    || !conductorId.equals(servicio.getDriver().getId())) return;
            if (servicio.getUltimaActualizacionUbicacion() == null
                    || momento.isAfter(servicio.getUltimaActualizacionUbicacion())) {
                servicio.actualizarUbicacion(latitud, longitud, momento);
            }
        }
    }

    private void consultarHistorial(String clienteId, Vigencia consulta,
                                    ResultCallback<List<TaxiService>> callback) {
        AccesoCliente acceso = cliente(clienteId, callback);
        if (acceso == null || !consulta.activa()) return;
        locales.historial(clienteId, new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> datos) {
                if (!consulta.activa() || !clienteVigente(acceso, callback)) return;
                TaxistasApi.ejecutar(() -> {
                    List<TaxiService> actualizados = new ArrayList<>();
                    for (TaxiService local : datos) {
                        exigirConsulta(acceso, consulta);
                        TaxiService confirmado = creacionesConfirmadas.get(clave(acceso, local));
                        if (confirmado != null) {
                            actualizados.add(confirmado);
                        } else if (local.getApiId() != null) {
                            actualizados.add(consultarRemoto(local, () -> exigirConsulta(acceso, consulta)));
                        } else {
                            // La UI los separa de los viajes confirmados y ofrece
                            // un reintento explícito solo si el marcador lo permite.
                            actualizados.add(local);
                        }
                    }
                    return actualizados;
                }, new ResultCallback<List<TaxiService>>() {
                    @Override public void onExito(@NonNull List<TaxiService> actualizados) {
                        if (consulta.activa() && clienteVigente(acceso, callback)) {
                            guardarLista(acceso, consulta, actualizados, 0, new ArrayList<>(), callback);
                        }
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (consulta.activa() && clienteVigente(acceso, callback)) callback.onError(mensaje);
                    }
                });
            }
            @Override public void onError(@NonNull String mensaje) {
                if (consulta.activa() && clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    private void guardarLista(AccesoCliente acceso, Vigencia consulta, List<TaxiService> lista, int indice,
                              List<TaxiService> guardados, ResultCallback<List<TaxiService>> callback) {
        if (!consulta.activa() || !clienteVigente(acceso, callback)) return;
        if (indice == lista.size()) {
            callback.onExito(guardados);
            return;
        }
        TaxiService taxi = lista.get(indice);
        if (taxi.getApiId() == null) {
            guardados.add(taxi);
            guardarLista(acceso, consulta, lista, indice + 1, guardados, callback);
            return;
        }
        locales.sincronizarDesdeApi(taxi, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService guardado) {
                creacionesConfirmadas.remove(clave(acceso, taxi));
                if (!consulta.activa() || !clienteVigente(acceso, callback)) return;
                guardados.add(guardado);
                guardarLista(acceso, consulta, lista, indice + 1, guardados, callback);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (consulta.activa() && clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    @Override
    public Suscripcion escucharHistorial(@NonNull String clienteId,
                                         @NonNull ResultCallback<List<TaxiService>> callback) {
        AccesoCliente acceso = cliente(clienteId, callback);
        if (acceso == null) return () -> { };
        Sondeo sondeo = new Sondeo(acceso, callback);
        sondeo.handler.post(sondeo);
        return sondeo;
    }

    private final class Sondeo implements Suscripcion, Runnable, Vigencia {
        final Handler handler = new Handler(Looper.getMainLooper());
        final AccesoCliente acceso;
        final ResultCallback<List<TaxiService>> callback;
        volatile boolean cerrada;
        Sondeo(AccesoCliente acceso, ResultCallback<List<TaxiService>> callback) {
            this.acceso = acceso;
            this.callback = callback;
        }
        @Override public boolean activa() { return !cerrada && vigente(acceso); }
        @Override public void run() {
            if (!activa()) { cerrar(); return; }
            consultarHistorial(acceso.uid, this, new ResultCallback<List<TaxiService>>() {
                @Override public void onExito(@NonNull List<TaxiService> datos) {
                    if (!activa()) return;
                    callback.onExito(datos);
                    programar();
                }
                @Override public void onError(@NonNull String mensaje) {
                    if (!activa()) return;
                    callback.onError(mensaje);
                    programar();
                }
            });
        }
        void programar() { if (activa()) handler.postDelayed(this, INTERVALO_MS); }
        @Override public void cerrar() { cerrada = true; handler.removeCallbacks(this); }
    }

    @Override
    public void servicioActivo(@NonNull String clienteId, @NonNull ResultCallback<TaxiService> callback) {
        historial(clienteId, new ResultCallback<List<TaxiService>>() {
            @Override public void onExito(@NonNull List<TaxiService> datos) {
                TaxiService activo = null;
                for (TaxiService taxi : datos) {
                    if (taxi.getEstado().isActive() && (activo == null
                            || taxi.getEstado().ordinal() > activo.getEstado().ordinal())) activo = taxi;
                }
                if (activo == null) callback.onError("No tienes ningún servicio de taxi en curso.");
                else callback.onExito(activo);
            }
            @Override public void onError(@NonNull String mensaje) { callback.onError(mensaje); }
        });
    }

    @Override
    public void obtener(@NonNull String taxiId, @NonNull ResultCallback<TaxiService> callback) {
        if (SessionManager.getRolActivo() == Role.CONDUCTOR) {
            AccesoConductor acceso = conductor(SessionManager.getConductorId(), callback);
            if (acceso == null || !idRestValido(taxiId, callback)) return;
            ejecutarConductor(acceso, () -> {
                JsonObject detalle = api().objeto("GET", "/api/servicios/" + taxiId, null, acceso.token);
                exigirAsignado(detalle, acceso);
                return enriquecer(TaxistasJson.servicio(detalle, null), () -> exigirConductor(acceso));
            }, callback);
            return;
        }
        AccesoCliente acceso = cliente(SessionManager.getUsuarioId(), callback);
        if (acceso == null) return;
        locales.obtener(taxiId, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService local) {
                if (clienteVigente(acceso, callback)) actualizarCliente(acceso, local, callback);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    private void actualizarCliente(AccesoCliente acceso, TaxiService local,
                                    ResultCallback<TaxiService> callback) {
        TaxiService confirmado = creacionesConfirmadas.get(clave(acceso, local));
        if (confirmado != null) { guardarCreacion(acceso, confirmado, callback); return; }
        if (local.getApiId() == null) {
            callback.onExito(local);
            return;
        }
        TaxistasApi.ejecutar(() -> {
            exigirCliente(acceso);
            return consultarRemoto(local, () -> exigirCliente(acceso));
        }, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService actualizado) {
                if (!clienteVigente(acceso, callback)) return;
                locales.sincronizarDesdeApi(actualizado, new ResultCallback<TaxiService>() {
                    @Override public void onExito(@NonNull TaxiService guardado) {
                        if (clienteVigente(acceso, callback)) callback.onExito(guardado);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (clienteVigente(acceso, callback)) callback.onError(mensaje);
                    }
                });
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    private TaxiService consultarRemoto(TaxiService local, Runnable comprobar) throws Exception {
        comprobar.run();
        JsonObject datos = api().objeto("GET", "/api/servicios/" + local.getApiId(), null, null);
        return enriquecer(TaxistasJson.servicio(datos, local), comprobar);
    }

    private TaxiService enriquecer(TaxiService taxi, Runnable comprobar) throws Exception {
        comprobar.run();
        if (taxi.getDriver() == null) return taxi;
        String id = taxi.getDriver().getId();
        JsonObject datos = api().objeto("GET", "/api/taxistas/" + id, null, null);
        Driver conductor = perfil(datos);
        if (!id.equals(conductor.getId())) throw new IllegalArgumentException("El perfil no corresponde al conductor asignado.");
        taxi.withEstadoPersistido(taxi.getEstado(), conductor, taxi.getRatingCliente());
        if (taxi.getEstado().isActive()) {
            comprobar.run();
            JsonObject ubicacion = api().objeto("GET", "/api/taxistas/" + id + "/ubicacion", null, null);
            if (!presente(ubicacion, "taxistaId") || !id.equals(ubicacion.get("taxistaId").getAsString())) {
                throw new IllegalArgumentException("La ubicación no corresponde al conductor asignado.");
            }
            if (presente(ubicacion, "latitud") && presente(ubicacion, "longitud")
                    && presente(ubicacion, "actualizadaEn")) {
                double lat = coordenada(ubicacion, "latitud", 90d);
                double lng = coordenada(ubicacion, "longitud", 180d);
                LocalDateTime momento = LocalDateTime.parse(ubicacion.get("actualizadaEn").getAsString());
                taxi.actualizarUbicacion(lat, lng, momento);
            }
        }
        return taxi;
    }

    @Override
    public void calificar(@NonNull String taxiId, float rating,
                          @NonNull ResultCallback<TaxiService> callback) {
        AccesoCliente acceso = cliente(SessionManager.getUsuarioId(), callback);
        if (acceso == null) return;
        final int puntuacion;
        try { puntuacion = TaxistasJson.puntuacion(rating); }
        catch (IllegalArgumentException error) { callback.onError(error.getMessage()); return; }
        obtener(taxiId, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService taxi) {
                if (!clienteVigente(acceso, callback)) return;
                if (taxi.getApiId() == null || taxi.getDriver() == null || !taxi.getEstado().isFinished()) {
                    callback.onError("Solo puedes valorar un traslado finalizado y vinculado al sistema web.");
                    return;
                }
                if (taxi.isValorado()) {
                    if (Math.abs(taxi.getRatingCliente() - rating) < 0.0001f) callback.onExito(taxi);
                    else callback.onError("Ya valoraste este traslado.");
                    return;
                }
                String clave = clave(acceso, taxi);
                Float confirmada = valoracionesConfirmadas.get(clave);
                if (confirmada != null) { guardarValoracion(acceso, taxi, confirmada, callback); return; }
                TaxistasApi.ejecutar(() -> {
                    exigirCliente(acceso);
                    JsonObject cuerpo = new JsonObject();
                    cuerpo.addProperty("servicioId", Long.parseLong(taxi.getApiId()));
                    cuerpo.addProperty("puntuacion", puntuacion);
                    api().objeto("POST", "/api/taxistas/" + taxi.getDriver().getId() + "/valoracion", cuerpo, null);
                    valoracionesConfirmadas.put(clave, rating);
                    return taxi;
                }, new ResultCallback<TaxiService>() {
                    @Override public void onExito(@NonNull TaxiService ignorado) {
                        if (clienteVigente(acceso, callback)) guardarValoracion(acceso, taxi, rating, callback);
                    }
                    @Override public void onError(@NonNull String mensaje) {
                        if (clienteVigente(acceso, callback)) callback.onError(mensaje);
                    }
                });
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(mensaje);
            }
        });
    }

    private void guardarValoracion(AccesoCliente acceso, TaxiService taxi, float rating,
                                   ResultCallback<TaxiService> callback) {
        locales.calificar(taxi.getId(), rating, new ResultCallback<TaxiService>() {
            @Override public void onExito(@NonNull TaxiService guardado) {
                valoracionesConfirmadas.remove(clave(acceso, taxi));
                if (clienteVigente(acceso, callback)) callback.onExito(guardado);
            }
            @Override public void onError(@NonNull String mensaje) {
                if (clienteVigente(acceso, callback)) callback.onError(
                        "La web recibió tu valoración, pero no pudimos guardarla en tu historial. "
                                + "Vuelve a guardar la valoración; no la enviaremos otra vez a la web.");
            }
        });
    }

    @Override
    public void sincronizarAvisoEstado(@NonNull String taxiId,
                                       @NonNull ResultCallback<Boolean> callback) {
        // El estado ya fue consultado y validado antes de publicar el historial.
        locales.sincronizarAvisoEstado(taxiId, callback);
    }

    @Override
    public void perfilDe(@NonNull String conductorId, @NonNull ResultCallback<Driver> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null) return;
        ejecutarConductor(acceso, () -> {
            Driver dato = perfil(api().objeto("GET", "/api/taxistas/" + acceso.id, null, acceso.token));
            if (!acceso.id.equals(dato.getId())) throw new IllegalArgumentException("El perfil web no corresponde a tu cuenta de taxista.");
            return dato;
        }, callback);
    }

    @Override
    public void actualizarUbicacion(@NonNull String conductorId, double latitud, double longitud,
                                     @NonNull ResultCallback<Driver> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null) return;
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)
                || Math.abs(latitud) > 90d || Math.abs(longitud) > 180d) {
            callback.onError("No pudimos obtener una ubicación válida del dispositivo.");
            return;
        }
        ejecutarConductor(acceso, () -> {
            JsonObject cuerpo = new JsonObject();
            cuerpo.addProperty("latitud", latitud);
            cuerpo.addProperty("longitud", longitud);
            api().objeto("PUT", "/api/taxistas/" + acceso.id + "/ubicacion", cuerpo, acceso.token);
            exigirConductor(acceso);
            return perfil(api().objeto("GET", "/api/taxistas/" + acceso.id, null, acceso.token));
        }, callback);
    }

    @Override
    public void disponibles(@NonNull String conductorId,
                            @NonNull ResultCallback<List<OfertaDeTaxi>> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null) return;
        ejecutarConductor(acceso, () -> {
            JsonObject ficha = api().objeto("GET", "/api/taxistas/" + acceso.id, null, acceso.token);
            Driver conductor = perfil(ficha);
            if (!acceso.id.equals(conductor.getId())) throw new IllegalArgumentException("El perfil web no corresponde a tu cuenta de taxista.");
            List<OfertaDeTaxi> ofertas = new ArrayList<>();
            if (!conductor.isHabilitado() || !"DISPONIBLE".equals(texto(ficha, "disponibilidad"))) return ofertas;
            if (!presente(ficha, "latitud") || !presente(ficha, "longitud")) {
                throw new IllegalArgumentException("Reporta tu ubicación en el sistema de taxistas para consultar solicitudes cercanas.");
            }
            double lat = coordenada(ficha, "latitud", 90d);
            double lng = coordenada(ficha, "longitud", 180d);
            JsonArray pendientes = api().lista("/api/servicios/pendientes", acceso.token);
            for (JsonElement dato : pendientes) {
                exigirConductor(acceso);
                TaxiService taxi = TaxistasJson.servicio(dato.getAsJsonObject(), null);
                if (taxi.getEstado() == TaxiStatus.SOLICITADO && taxi.hasRecojo()) {
                    double distancia = Distancia.metrosEntre(lat, lng, taxi.getLatRecojo(), taxi.getLngRecojo());
                    ofertas.add(new OfertaDeTaxi(taxi, distancia));
                }
            }
            ofertas.sort(Comparator.comparingDouble(OfertaDeTaxi::getDistanciaM));
            return ofertas;
        }, callback);
    }

    @Override
    public void serviciosEnCursoDe(@NonNull String conductorId,
                                   @NonNull ResultCallback<List<TaxiService>> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null) return;
        ejecutarConductor(acceso, () -> {
            List<TaxiService> servicios = new ArrayList<>();
            for (JsonElement dato : api().lista("/api/servicios/taxista/" + acceso.id, acceso.token)) {
                exigirConductor(acceso);
                JsonObject detalle = dato.getAsJsonObject();
                exigirAsignado(detalle, acceso);
                TaxiService taxi = TaxistasJson.servicio(detalle, null);
                if (taxi.getEstado().isActive()) servicios.add(enriquecer(taxi, () -> exigirConductor(acceso)));
            }
            return servicios;
        }, callback);
    }

    @Override
    public void aceptar(@NonNull String taxiId, @NonNull String conductorId,
                         @NonNull ResultCallback<TaxiService> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null || !idRestValido(taxiId, callback)) return;
        ejecutarConductor(acceso, () -> {
            JsonObject datos = api().objeto("PUT", "/api/servicios/" + taxiId + "/aceptar", null, acceso.token);
            exigirAsignado(datos, acceso);
            // La respuesta de la escritura ya confirma la operación; no se hace
            // un GET adicional que pueda convertirla en un falso fallo.
            return TaxistasJson.servicio(datos, null);
        }, callback);
    }

    @Override
    public void avanzarComoConductor(@NonNull String taxiId, @NonNull String conductorId,
                                      @NonNull ResultCallback<TaxiService> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null || !idRestValido(taxiId, callback)) return;
        ejecutarConductor(acceso, () -> {
            JsonObject detalle = api().objeto("GET", "/api/servicios/" + taxiId, null, acceso.token);
            exigirAsignado(detalle, acceso);
            TaxiService antes = TaxistasJson.servicio(detalle, null);
            if (antes.getEstado() != TaxiStatus.ASIGNADO && antes.getEstado() != TaxiStatus.EN_CAMINO) {
                throw new IllegalArgumentException("El servicio no admite ese avance. Para finalizar, valida el código del cliente.");
            }
            JsonObject cuerpo = new JsonObject();
            cuerpo.addProperty("estado", antes.getEstado().next().name());
            exigirConductor(acceso);
            JsonObject estado = api().objeto("PUT", "/api/servicios/" + taxiId + "/estado", cuerpo, acceso.token);
            if (!taxiId.equals(estado.get("servicioId").getAsString())) {
                throw new IllegalArgumentException("La respuesta no corresponde a este servicio.");
            }
            detalle.add("estado", estado.get("estado"));
            detalle.add("taxistaId", estado.get("taxistaId"));
            exigirAsignado(detalle, acceso);
            return TaxistasJson.servicio(detalle, antes);
        }, callback);
    }

    @Override
    public void validarCodigo(@NonNull String taxiId, @NonNull String conductorId,
                               @NonNull String codigo, @NonNull ResultCallback<TaxiService> callback) {
        AccesoConductor acceso = conductor(conductorId, callback);
        if (acceso == null || !idRestValido(taxiId, callback)) return;
        if (codigo.trim().isEmpty() || codigo.trim().length() > 40) {
            callback.onError("Escribe el código del cliente, de hasta 40 caracteres.");
            return;
        }
        ejecutarConductor(acceso, () -> {
            JsonObject cuerpo = new JsonObject();
            cuerpo.addProperty("codigoQr", codigo.trim());
            JsonObject datos = api().objeto("PUT", "/api/servicios/" + taxiId + "/finalizar", cuerpo, acceso.token);
            exigirAsignado(datos, acceso);
            return TaxistasJson.servicio(datos, null);
        }, callback);
    }

    private static Driver perfil(JsonObject datos) {
        Driver conductor = TaxistasJson.conductor(datos);
        conductor.withFoto(TaxistasApi.resolverUrl(conductor.getFotoUrl()));
        if (conductor.getVehiculo() != null) {
            conductor.getVehiculo().withFoto(TaxistasApi.resolverUrl(conductor.getVehiculo().getFotoUrl()));
        }
        return conductor;
    }

    private static TaxistasApi api() { return TaxistasApi.getInstancia(); }

    private static AccesoAdministrador administrador(String hotelId, String bookingId, ResultCallback<?> callback) {
        AccesoAdministrador acceso = new AccesoAdministrador(SessionManager.getIdentidadId(), hotelId, bookingId);
        if (administradorVigente(acceso, callback)) return acceso;
        return null;
    }

    private static boolean vigente(AccesoAdministrador acceso) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return acceso.uid != null && usuario != null && acceso.uid.equals(usuario.getUid())
                && acceso.uid.equals(SessionManager.getIdentidadId())
                && SessionManager.getRolActivo() == Role.ADMIN_HOTEL
                && acceso.hotelId != null && acceso.hotelId.equals(SessionManager.getHotelAdministrado())
                && acceso.bookingId != null && !acceso.bookingId.isEmpty();
    }

    private static boolean administradorVigente(AccesoAdministrador acceso, ResultCallback<?> callback) {
        if (vigente(acceso)) return true;
        callback.onError("La sesión o la asignación del administrador cambiaron. Actualiza la reserva.");
        return false;
    }

    private static void exigirConsultaAdministrador(AccesoAdministrador acceso,
                                                     FirebaseTaxiRepository.ContextoAdministrador contexto,
                                                     Vigencia consulta) {
        if (!consulta.activa() || !vigente(acceso) || !contexto.vigente()) {
            throw new IllegalArgumentException("La consulta del traslado de esta reserva ya no está autorizada.");
        }
    }

    private static AccesoCliente cliente(String uid, ResultCallback<?> callback) {
        Cuenta cuenta = SessionManager.getCuenta();
        AccesoCliente acceso = new AccesoCliente(uid, cuenta);
        return clienteVigente(acceso, callback) ? acceso : null;
    }

    private static boolean vigente(AccesoCliente acceso) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return acceso.uid != null && usuario != null && acceso.uid.equals(usuario.getUid())
                && SessionManager.getRolActivo() == Role.CLIENTE
                && acceso.uid.equals(SessionManager.getUsuarioId());
    }

    private static void exigirCliente(AccesoCliente acceso) {
        if (!vigente(acceso)) throw new IllegalArgumentException("Tu sesión cambió. Inicia sesión como cliente para usar Taxi.");
    }

    private static boolean clienteVigente(AccesoCliente acceso, ResultCallback<?> callback) {
        if (vigente(acceso)) return true;
        callback.onError("Tu sesión cambió. Inicia sesión como cliente para usar Taxi.");
        return false;
    }

    private static void exigirConsulta(AccesoCliente acceso, Vigencia consulta) {
        exigirCliente(acceso);
        if (!consulta.activa()) throw new IllegalArgumentException("La consulta de taxi ya no está activa.");
    }

    private static AccesoConductor conductor(String id, ResultCallback<?> callback) {
        AccesoConductor acceso = new AccesoConductor(id, SesionTaxistaRest.getToken());
        if (vigente(acceso)) return acceso;
        callback.onError("Inicia sesión con la cuenta aprobada del sistema web de taxistas para continuar.");
        return null;
    }

    private static boolean vigente(AccesoConductor acceso) {
        return acceso.id != null && acceso.token != null && !acceso.token.isEmpty()
                && SessionManager.getRolActivo() == Role.CONDUCTOR
                && ("taxista:" + acceso.id).equals(SessionManager.getIdentidadId())
                && acceso.id.equals(SessionManager.getConductorId())
                && acceso.id.equals(SesionTaxistaRest.getId())
                && acceso.token.equals(SesionTaxistaRest.getToken());
    }

    private static void exigirConductor(AccesoConductor acceso) {
        if (!vigente(acceso)) throw new IllegalArgumentException("La sesión del taxista cambió. Vuelve a iniciar sesión.");
    }

    private static <T> void ejecutarConductor(AccesoConductor acceso, TaxistasApi.Trabajo<T> trabajo,
                                               ResultCallback<T> callback) {
        TaxistasApi.ejecutar(() -> {
            exigirConductor(acceso);
            T resultado = trabajo.ejecutar();
            exigirConductor(acceso);
            return resultado;
        }, new ResultCallback<T>() {
            @Override public void onExito(@NonNull T dato) {
                if (vigente(acceso)) callback.onExito(dato);
                else callback.onError("La sesión del taxista cambió. Vuelve a iniciar sesión.");
            }
            @Override public void onError(@NonNull String mensaje) {
                if (vigente(acceso)) callback.onError(mensaje);
                else callback.onError("La sesión del taxista cambió. Vuelve a iniciar sesión.");
            }
        });
    }

    private static void exigirAsignado(JsonObject datos, AccesoConductor acceso) {
        if (!presente(datos, "taxistaId") || !acceso.id.equals(datos.get("taxistaId").getAsString())) {
            throw new IllegalArgumentException("Este servicio no está asignado a tu cuenta de taxista.");
        }
    }

    private static boolean idRestValido(String id, ResultCallback<?> callback) {
        try {
            if (id == null || !id.matches("[1-9][0-9]*") || Long.parseLong(id) <= 0) throw new NumberFormatException();
            return true;
        } catch (NumberFormatException error) {
            callback.onError("No encontramos un identificador válido para el servicio web.");
            return false;
        }
    }

    private static boolean presente(JsonObject datos, String campo) {
        return datos.has(campo) && !datos.get(campo).isJsonNull();
    }

    private static String texto(JsonObject datos, String campo) {
        return presente(datos, campo) ? datos.get(campo).getAsString() : null;
    }

    private static double coordenada(JsonObject datos, String campo, double limite) {
        double valor = datos.get(campo).getAsDouble();
        if (!Double.isFinite(valor) || Math.abs(valor) > limite) {
            throw new IllegalArgumentException("El sistema web devolvió una ubicación inválida.");
        }
        return valor;
    }

    private static String clave(AccesoCliente acceso, TaxiService taxi) {
        return acceso.uid + "/" + taxi.getId();
    }

    private interface Vigencia { boolean activa(); }

    private static final class AccesoAdministrador {
        final String uid;
        final String hotelId;
        final String bookingId;
        AccesoAdministrador(String uid, String hotelId, String bookingId) {
            this.uid = uid;
            this.hotelId = hotelId;
            this.bookingId = bookingId;
        }
    }

    private static final class AccesoCliente {
        final String uid;
        final String nombre;
        AccesoCliente(String uid, Cuenta cuenta) {
            this.uid = uid;
            this.nombre = cuenta == null ? "" : cuenta.getNombreCompleto();
        }
    }

    private static final class AccesoConductor {
        final String id;
        final String token;
        AccesoConductor(String id, String token) { this.id = id; this.token = token; }
    }

    private static final class ResultadoPost {
        final TaxiService taxi;
        final String error;
        final boolean rechazo;
        private ResultadoPost(TaxiService taxi, String error, boolean rechazo) {
            this.taxi = taxi;
            this.error = error;
            this.rechazo = rechazo;
        }
        static ResultadoPost exito(TaxiService taxi) { return new ResultadoPost(taxi, null, false); }
        static ResultadoPost error(String error, boolean rechazo) { return new ResultadoPost(null, error, rechazo); }
    }
}
