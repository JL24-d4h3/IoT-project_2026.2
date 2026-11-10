package com.taxi.gestion.domain;

import com.taxi.gestion.domain.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un viaje concreto. Su estado es el que la app movil consulta y actualiza
 * durante todo el ciclo de vida del servicio.
 */
@Entity
@Table(name = "servicios_taxi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicioTaxi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo mientras el servicio esta en SOLICITADO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxista_id", foreignKey = @ForeignKey(name = "fk_servicio_taxista"))
    private Taxista taxista;

    // ---------- Cliente (viene de la app movil) ----------

    @Column(name = "cliente_id", length = 60)
    private String clienteId;

    @Column(name = "nombre_cliente", nullable = false, length = 120)
    private String nombreCliente;

    @Column(name = "telefono_cliente", length = 20)
    private String telefonoCliente;

    // ---------- Ruta ----------

    @Column(name = "direccion_origen", nullable = false, length = 200)
    private String direccionOrigen;

    @Column(name = "latitud_origen", nullable = false)
    private Double latitudOrigen;

    @Column(name = "longitud_origen", nullable = false)
    private Double longitudOrigen;

    @Column(name = "direccion_destino", nullable = false, length = 200)
    private String direccionDestino;

    @Column(name = "latitud_destino")
    private Double latitudDestino;

    @Column(name = "longitud_destino")
    private Double longitudDestino;

    @Column(precision = 10, scale = 2)
    private BigDecimal tarifa;

    // ---------- Estado ----------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoServicio estado = EstadoServicio.SOLICITADO;

    /**
     * Codigo que el cliente muestra como QR al terminar el viaje.
     * El taxista lo escanea y lo envia en /finalizar para cerrar el servicio.
     */
    @Column(name = "codigo_qr", nullable = false, unique = true, length = 40)
    private String codigoQr;

    // ---------- Trazabilidad ----------

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Column(name = "fecha_inicio_traslado")
    private LocalDateTime fechaInicioTraslado;

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    @PrePersist
    void alCrear() {
        if (fechaSolicitud == null) {
            fechaSolicitud = LocalDateTime.now();
        }
    }

    public boolean codigoQrCoincide(String codigo) {
        return codigoQr != null && codigoQr.equalsIgnoreCase(codigo == null ? null : codigo.trim());
    }
}
