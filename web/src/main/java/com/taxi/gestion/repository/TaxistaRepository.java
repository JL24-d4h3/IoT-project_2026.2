package com.taxi.gestion.repository;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.domain.enums.TipoDocumento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxistaRepository extends JpaRepository<Taxista, Long> {

    Optional<Taxista> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

    boolean existsByPlacaVehiculoIgnoreCase(String placa);

    /** Taxistas habilitados, con cuenta activa y libres para tomar un servicio. */
    @Query("""
            select t from Taxista t
            where t.estadoHabilitacion = :habilitacion
              and t.cuentaActiva = true
              and t.disponibilidad = :disponibilidad
            order by t.valoracionPromedio desc, t.totalValoraciones desc
            """)
    List<Taxista> buscarOperativosPorDisponibilidad(EstadoHabilitacion habilitacion,
                                                    Disponibilidad disponibilidad);

    List<Taxista> findAllByOrderByFechaRegistroDesc();

    List<Taxista> findByEstadoHabilitacionOrderByFechaRegistroDesc(EstadoHabilitacion estado);

    /**
     * Bloqueo pesimista: evita que dos taxistas acepten el mismo servicio o que
     * un taxista quede asignado a dos viajes por una carrera de peticiones.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Taxista t where t.id = :id")
    Optional<Taxista> findByIdParaActualizar(Long id);
}
