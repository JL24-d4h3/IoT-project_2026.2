package org.iot.project.models;

/**
 * Conductor de taxi (§28, §38). Los datos provienen del sistema web de
 * gestion de taxistas a traves de su API REST (RT-006, RT-023): la aplicacion
 * movil nunca accede directamente a esa base de datos (RT-005).
 */
public class Driver {

    private final String id;
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String fechaNacimiento;
    private String email;
    private String telefono;
    private String direccion;
    private String fotoUrl;
    private float rating;
    private int numServicios;

    /**
     * Si el Superadmin ya aprobo a este conductor (RF-077, RT-014).
     *
     * <p>Empieza en falso a proposito: un conductor recien dado de alta en el
     * sistema de gestion todavia no puede prestar servicios, y quien lo aprueba
     * es el Superadmin. Por eso {@code withDocumento} y compania no lo tocan:
     * habilitarlo es una decision, no un dato de alta.
     */
    private boolean habilitado;

    private Vehicle vehiculo;

    public Driver(String id, String nombres, String apellidos) {
        this.id = id;
        this.nombres = nombres;
        this.apellidos = apellidos;
    }

    public String getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    /** Iniciales para el avatar cuando la API no devuelve fotografia. */
    public String getIniciales() {
        String inicialNombre = nombres == null || nombres.isEmpty() ? "" : nombres.substring(0, 1);
        String inicialApellido = apellidos == null || apellidos.isEmpty() ? "" : apellidos.substring(0, 1);
        return (inicialNombre + inicialApellido).toUpperCase();
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public Driver withFoto(String url) {
        this.fotoUrl = url;
        return this;
    }

    /** Valoracion promedio de 1 a 10 (RF-081). */
    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = Math.max(1f, Math.min(10f, rating));
    }

    public int getNumServicios() {
        return numServicios;
    }

    public void setNumServicios(int numServicios) {
        this.numServicios = numServicios;
    }

    /** Un conductor no habilitado no puede prestar servicios (RF-077, RT-014). */
    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public Vehicle getVehiculo() {
        return vehiculo;
    }

    public Driver withVehiculo(Vehicle vehiculo) {
        this.vehiculo = vehiculo;
        return this;
    }

    public Driver withDocumento(String tipo, String numero) {
        this.tipoDocumento = tipo;
        this.numeroDocumento = numero;
        return this;
    }

    public Driver withContacto(String email, String telefono, String direccion) {
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        return this;
    }

    public Driver withRating(float rating, int numServicios) {
        setRating(rating);
        this.numServicios = numServicios;
        return this;
    }
}
