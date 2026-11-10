package com.taxi.gestion.config;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.domain.enums.TipoDocumento;
import com.taxi.gestion.repository.TaxistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Datos ficticios solo con perfil demo y contrasena configurada; nunca en prod. */
@Configuration
@Profile("demo & !prod")
@RequiredArgsConstructor
public class DatosDemo {

    @Bean
    CommandLineRunner cargarTaxistas(TaxistaRepository repo, PasswordEncoder encoder,
            @Value("${DEMO_TAXISTA_PASSWORD:}") String demoTaxistaPassword) {
        return args -> {
            if (demoTaxistaPassword.isBlank() || repo.count() > 0) {
                return;
            }
            repo.save(Taxista.builder()
                    .nombres("Luis Alberto").apellidos("Ramirez Quispe")
                    .tipoDocumento(TipoDocumento.DNI).numeroDocumento("45781236")
                    .fechaNacimiento(LocalDate.of(1988, 3, 12))
                    .correo("luis.ramirez@correo.com").telefono("987654321")
                    .passwordHash(encoder.encode(demoTaxistaPassword))
                    .domicilio("Av. Arequipa 1234, Lince, Lima")
                    .placaVehiculo("ABC-123")
                    .estadoHabilitacion(EstadoHabilitacion.HABILITADO).cuentaActiva(true)
                    .disponibilidad(Disponibilidad.DISPONIBLE)
                    .valoracionPromedio(new BigDecimal("4.72")).totalValoraciones(38)
                    .latitud(-12.09654).longitud(-77.03611)
                    .ubicacionActualizadaEn(LocalDateTime.now())
                    .build());

            repo.save(Taxista.builder()
                    .nombres("Marisol").apellidos("Chavez Huaman")
                    .tipoDocumento(TipoDocumento.DNI).numeroDocumento("70123984")
                    .fechaNacimiento(LocalDate.of(1994, 11, 2))
                    .correo("marisol.chavez@correo.com").telefono("955112233")
                    .passwordHash(encoder.encode(demoTaxistaPassword))
                    .domicilio("Jr. Puno 455, Cercado de Lima")
                    .placaVehiculo("XKD-908")
                    .estadoHabilitacion(EstadoHabilitacion.PENDIENTE).cuentaActiva(true)
                    .disponibilidad(Disponibilidad.DISPONIBLE)
                    .build());

            repo.save(Taxista.builder()
                    .nombres("Jorge Enrique").apellidos("Palomino Vera")
                    .tipoDocumento(TipoDocumento.CARNET_EXTRANJERIA).numeroDocumento("CE8812345")
                    .fechaNacimiento(LocalDate.of(1979, 7, 25))
                    .correo("jorge.palomino@correo.com").telefono("921445566")
                    .passwordHash(encoder.encode(demoTaxistaPassword))
                    .domicilio("Calle Los Cedros 210, San Miguel")
                    .placaVehiculo("PQR-551")
                    .estadoHabilitacion(EstadoHabilitacion.HABILITADO).cuentaActiva(false)
                    .disponibilidad(Disponibilidad.OCUPADO)
                    .valoracionPromedio(new BigDecimal("3.90")).totalValoraciones(11)
                    .build());
        };
    }
}
