package org.iot.project.core.ubicacion;

/** Estado local del GPS. El estado del viaje siempre procede del servidor. */
public final class EstadoSeguimiento {
    private final boolean activo;
    private final boolean bloqueado;
    private final String mensaje;
    private final long ultimoEnvioElapsedMs;
    private final String identidadId;
    private final String servicioId;

    EstadoSeguimiento(boolean activo, boolean bloqueado, String mensaje, long ultimoEnvioElapsedMs,
                      String identidadId, String servicioId) {
        this.activo = activo;
        this.bloqueado = bloqueado;
        this.mensaje = mensaje;
        this.ultimoEnvioElapsedMs = ultimoEnvioElapsedMs;
        this.identidadId = identidadId;
        this.servicioId = servicioId;
    }

    public boolean isActivo() { return activo; }
    public boolean isBloqueado() { return bloqueado; }
    public String getMensaje() { return mensaje; }
    /** Captura monotónica del último punto confirmado; no la hora de llegada del ACK. */
    public long getUltimoEnvioElapsedMs() { return ultimoEnvioElapsedMs; }
    public String getIdentidadId() { return identidadId; }
    public String getServicioId() { return servicioId; }
}
