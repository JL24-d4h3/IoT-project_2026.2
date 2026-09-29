package org.iot.project.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Usuario del sistema en cualquiera de los cuatro roles (§38).
 *
 * <p>Un usuario deshabilitado no puede acceder a las funcionalidades
 * protegidas (RF-009). Los conductores no son usuarios: se habilitan aparte
 * (RF-077, RT-014) y su estado vive en {@link Driver}.
 */
public class User {

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
    private final Role rol;
    private boolean activo = true;

    private final List<Card> tarjetas = new ArrayList<>();

    public User(String id, String nombres, String apellidos, Role rol) {
        this.id = id;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.rol = rol;
    }

    public String getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    /** Editable desde la pantalla de perfil (§41). */
    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    /** Editable desde la pantalla de perfil (§41). */
    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    /** Iniciales para el avatar cuando no hay fotografia. */
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

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public User withFoto(String url) {
        this.fotoUrl = url;
        return this;
    }

    public Role getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Si esta cuenta se puede desactivar desde el panel (RF-006).
     *
     * <p>Un superadministrador no: desactivarlo dejaria la plataforma sin nadie
     * que pueda volver a activarlo. La regla vive en el modelo y no en el
     * repositorio porque describe al usuario, y asi la puede comprobar una
     * prueba sin levantar el repositorio entero.
     */
    public boolean esDesactivable() {
        return rol != Role.SUPERADMIN;
    }

    public List<Card> getTarjetas() {
        return Collections.unmodifiableList(tarjetas);
    }

    public User addTarjeta(Card tarjeta) {
        tarjetas.add(tarjeta);
        return this;
    }

    public User withDocumento(String tipo, String numero) {
        this.tipoDocumento = tipo;
        this.numeroDocumento = numero;
        return this;
    }

    public User withNacimiento(String fecha) {
        this.fechaNacimiento = fecha;
        return this;
    }
}
