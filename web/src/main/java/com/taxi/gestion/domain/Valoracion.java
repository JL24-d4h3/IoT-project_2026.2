package com.taxi.gestion.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Calificacion que el cliente deja a un taxista al terminar un servicio. */
@Entity
@Table(name = "valoraciones",
        uniqueConstraints = @UniqueConstraint(name = "uk_valoracion_servicio",
                columnNames = "servicio_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxista_id", foreignKey = @ForeignKey(name = "fk_valoracion_taxista"))
    private Taxista taxista;

    /** Opcional: ata la valoracion a un viaje concreto y evita duplicados. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", foreignKey = @ForeignKey(name = "fk_valoracion_servicio"))
    private ServicioTaxi servicio;

    @Column(nullable = false)
    private Integer puntuacion;

    @Column(length = 300)
    private String comentario;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
