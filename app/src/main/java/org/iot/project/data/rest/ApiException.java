package org.iot.project.data.rest;

/** Error de la API sin exponer credenciales, cuerpos HTTP ni detalles del servidor. */
public final class ApiException extends Exception {
    private final int status;
    private final boolean incierto;

    public ApiException(int status, String mensaje) {
        this(status, mensaje, status == 0 || status >= 500);
    }

    public ApiException(int status, String mensaje, boolean incierto) {
        super(mensaje);
        this.status = status;
        this.incierto = incierto;
    }

    public int getStatus() { return status; }
    public int getCodigoHttp() { return status; }
    public String getMensaje() { return getMessage(); }

    /** Un POST no debe repetirse automáticamente si su resultado pudo perderse. */
    public boolean esIncierto() { return incierto; }
}
