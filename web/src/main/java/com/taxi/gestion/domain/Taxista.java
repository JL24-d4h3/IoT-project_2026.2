package com.taxi.gestion.domain;

import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.domain.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Taxista registrado en el sistema.
 *
 * Esta entidad NUNCA se serializa directamente en la API: el campo
 * {@code passwordHash} y el documento de identidad solo salen a traves de DTOs.
 */
@Entity
@Table(name = "taxistas",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_taxista_correo", columnNames = "correo"),
                @UniqueConstraint(name = "uk_taxista_documento",
                        columnNames = {"tipo_documento", "numero_documento"}),
                @UniqueConstraint(name = "uk_taxista_placa", columnNames = "placa_vehiculo")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Taxista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------- Datos personales ----------

    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 80)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 25)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 120)
    private String correo;

    @Column(nullable = false, length = 20)
    private String telefono;

    /** Hash BCrypt. Jamas se expone ni se guarda en texto plano. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 200)
    private String domicilio;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    // ---------- Datos del vehiculo ----------

    @Column(name = "placa_vehiculo", nullable = false, length = 10)
    private String placaVehiculo;

    @Column(name = "foto_vehiculo_url", length = 500)
    private String fotoVehiculoUrl;

    // ---------- Estado administrativo ----------

    /** Habilitacion inicial otorgada por el Superadmin. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_habilitacion", nullable = false, length = 20)
    @Builder.Default
    private EstadoHabilitacion estadoHabilitacion = EstadoHabilitacion.PENDIENTE;

    /** Interruptor independiente del Superadmin sobre una cuenta ya habilitada. */
    @Column(name = "cuenta_activa", nullable = false)
    @Builder.Default
    private Boolean cuentaActiva = Boolean.TRUE;

    // ---------- Reputacion ----------

    @Column(name = "valoracion_promedio", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal valoracionPromedio = BigDecimal.ZERO;

    @Column(name = "total_valoraciones", nullable = false)
    @Builder.Default
    private Integer totalValoraciones = 0;

    // ---------- Operacion ----------

    private Double latitud;

    private Double longitud;

    @Column(name = "ubicacion_actualizada_en")
    private LocalDateTime ubicacionActualizadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private Disponibilidad disponibilidad = Disponibilidad.DISPONIBLE;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (valoracionPromedio == null) {
            valoracionPromedio = BigDecimal.ZERO;
        }
    }

    // ---------- Reglas de negocio de la propia entidad ----------

    /** Un taxista puede tomar servicios solo si esta habilitado, activo y libre. */
    public boolean puedeTomarServicios() {
        return estadoHabilitacion == EstadoHabilitacion.HABILITADO
                && Boolean.TRUE.equals(cuentaActiva)
                && disponibilidad == Disponibilidad.DISPONIBLE;
    }

    public boolean estaOperativo() {
        return estadoHabilitacion == EstadoHabilitacion.HABILITADO
                && Boolean.TRUE.equals(cuentaActiva);
    }

    /** Recalcula el promedio de forma incremental, sin releer todas las valoraciones. */
    public void agregarValoracion(int puntuacion) {
        BigDecimal acumulado = valoracionPromedio.multiply(BigDecimal.valueOf(totalValoraciones));
        int nuevoTotal = totalValoraciones + 1;
        this.valoracionPromedio = acumulado
                .add(BigDecimal.valueOf(puntuacion))
                .divide(BigDecimal.valueOf(nuevoTotal), 2, RoundingMode.HALF_UP);
        this.totalValoraciones = nuevoTotal;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }
}
