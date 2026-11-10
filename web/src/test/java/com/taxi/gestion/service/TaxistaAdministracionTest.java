package com.taxi.gestion.service;

import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.exception.ConflictoEstadoException;
import com.taxi.gestion.repository.ServicioTaxiRepository;
import com.taxi.gestion.repository.TaxistaRepository;
import com.taxi.gestion.repository.ValoracionRepository;
import com.taxi.gestion.storage.AlmacenamientoFotos;
import com.taxi.gestion.storage.ValidadorImagen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxistaAdministracionTest {

    @Mock TaxistaRepository taxistas;
    @Mock ServicioTaxiRepository servicios;
    @Mock ValoracionRepository valoraciones;
    @Mock PasswordEncoder password;
    @Mock AlmacenamientoFotos fotos;
    @Mock ValidadorImagen imagenes;
    @InjectMocks TaxistaService servicio;
    private Taxista taxista;

    @BeforeEach
    void preparar() {
        taxista = Taxista.builder().id(7L).estadoHabilitacion(EstadoHabilitacion.HABILITADO)
                .cuentaActiva(true).disponibilidad(Disponibilidad.DISPONIBLE).build();
        when(taxistas.findByIdParaActualizar(7L)).thenReturn(Optional.of(taxista));
    }

    @Test
    void reactivarSinViajeRecuperaDisponibilidadSinCambiarAprobacion() {
        taxista.setCuentaActiva(false);
        taxista.setDisponibilidad(Disponibilidad.OCUPADO);
        assertSame(taxista, servicio.cambiarEstadoCuenta(7L, true));
        assertTrue(taxista.getCuentaActiva());
        assertEquals(Disponibilidad.DISPONIBLE, taxista.getDisponibilidad());
        assertEquals(EstadoHabilitacion.HABILITADO, taxista.getEstadoHabilitacion());
        verify(taxistas).findByIdParaActualizar(7L);
        verify(taxistas, never()).findById(anyLong());
    }

    @Test
    void desactivarSinViajeLaQuitaDeOfertasConservandoAprobacion() {
        servicio.cambiarEstadoCuenta(7L, false);
        assertFalse(taxista.getCuentaActiva());
        assertEquals(Disponibilidad.OCUPADO, taxista.getDisponibilidad());
        assertEquals(EstadoHabilitacion.HABILITADO, taxista.getEstadoHabilitacion());
    }

    @Test
    void noDesactivaMientrasExisteViajeNiMutaCuenta() {
        when(servicios.existsByTaxistaIdAndEstadoIn(eq(7L), anyList())).thenReturn(true);
        assertThrows(ConflictoEstadoException.class, () -> servicio.cambiarEstadoCuenta(7L, false));
        assertTrue(taxista.getCuentaActiva());
        assertEquals(Disponibilidad.DISPONIBLE, taxista.getDisponibilidad());
    }

    @Test
    void activarCuentaConViajeNoLiberaDisponibilidad() {
        taxista.setDisponibilidad(Disponibilidad.OCUPADO);
        when(servicios.existsByTaxistaIdAndEstadoIn(eq(7L), anyList())).thenReturn(true);
        servicio.cambiarEstadoCuenta(7L, true);
        assertEquals(Disponibilidad.OCUPADO, taxista.getDisponibilidad());
    }

    @Test
    void noRetiraAprobacionDuranteViaje() {
        when(servicios.existsByTaxistaIdAndEstadoIn(eq(7L), anyList())).thenReturn(true);
        assertThrows(ConflictoEstadoException.class,
                () -> servicio.cambiarHabilitacion(7L, EstadoHabilitacion.PENDIENTE));
        assertEquals(EstadoHabilitacion.HABILITADO, taxista.getEstadoHabilitacion());
    }

    @Test
    void aprobarNoReactivaCuentaSuspendida() {
        taxista.setEstadoHabilitacion(EstadoHabilitacion.PENDIENTE);
        taxista.setCuentaActiva(false);
        servicio.cambiarHabilitacion(7L, EstadoHabilitacion.HABILITADO);
        assertEquals(EstadoHabilitacion.HABILITADO, taxista.getEstadoHabilitacion());
        assertFalse(taxista.getCuentaActiva());
        verify(taxistas).findByIdParaActualizar(7L);
    }
}
