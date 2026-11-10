package com.taxi.gestion.repository;

import com.taxi.gestion.domain.ServicioTaxi;
import com.taxi.gestion.domain.enums.EstadoServicio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicioTaxiRepository extends JpaRepository<ServicioTaxi, Long> {

    List<ServicioTaxi> findByTaxistaIdOrderByFechaSolicitudDesc(Long taxistaId);

    List<ServicioTaxi> findByEstadoOrderByFechaSolicitudAsc(EstadoServicio estado);

    boolean existsByTaxistaIdAndEstadoIn(Long taxistaId, List<EstadoServicio> estados);

    Optional<ServicioTaxi> findByCodigoQr(String codigoQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServicioTaxi s where s.id = :id")
    Optional<ServicioTaxi> findByIdParaActualizar(Long id);
}
