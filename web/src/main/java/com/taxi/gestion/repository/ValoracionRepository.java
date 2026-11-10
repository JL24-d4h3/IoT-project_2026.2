package com.taxi.gestion.repository;

import com.taxi.gestion.domain.Valoracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    List<Valoracion> findByTaxistaIdOrderByFechaRegistroDesc(Long taxistaId);

    boolean existsByServicioId(Long servicioId);
}
