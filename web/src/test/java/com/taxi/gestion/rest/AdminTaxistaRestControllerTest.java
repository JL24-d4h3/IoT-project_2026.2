package com.taxi.gestion.rest;

import com.taxi.gestion.config.SecurityConfig;
import com.taxi.gestion.domain.Taxista;
import com.taxi.gestion.domain.enums.Disponibilidad;
import com.taxi.gestion.domain.enums.EstadoHabilitacion;
import com.taxi.gestion.domain.enums.TipoDocumento;
import com.taxi.gestion.exception.ConflictoEstadoException;
import com.taxi.gestion.exception.GlobalExceptionHandler;
import com.taxi.gestion.exception.RecursoNoEncontradoException;
import com.taxi.gestion.mapper.TaxistaMapper;
import com.taxi.gestion.security.FalloAutenticacionAdmin;
import com.taxi.gestion.security.JwtAuthenticationFilter;
import com.taxi.gestion.security.JwtService;
import com.taxi.gestion.security.VerificadorSuperadminFirebase;
import com.taxi.gestion.service.TaxistaService;
import com.taxi.gestion.storage.FotosLocalWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No arranca JPA ni Firebase: las tres cadenas reales se prueban con verificacion simulada. */
@WebMvcTest(controllers = AdminTaxistaRestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = FotosLocalWebConfig.class), properties = {
        "app.admin.usuario=web-test", "app.admin.password=solo-prueba-local"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TaxistaMapper.class,
        GlobalExceptionHandler.class})
class AdminTaxistaRestControllerTest {

    private static final String RUTA = "/api/admin/taxistas";

    @Autowired MockMvc mvc;
    @MockBean TaxistaService servicio;
    @MockBean JwtService jwt;
    @MockBean VerificadorSuperadminFirebase verificador;

    @Test
    void faltaTokenDevuelveJson401SinConsultarTaxistas() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        verifyNoInteractions(servicio, verificador, jwt);
    }

    @Test
    void sesionWebSuperadminNoAutorizaApiFirebase() throws Exception {
        mvc.perform(get(RUTA).with(user("web-test").roles("SUPERADMIN")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio, verificador, jwt);
    }

    @Test
    void jwtTaxistaNoSeInterpretaComoPermisoAdministrativo() throws Exception {
        when(verificador.verificar("jwt-taxista")).thenThrow(FalloAutenticacionAdmin.noAutenticado());
        mvc.perform(get(RUTA).header("Authorization", "Bearer jwt-taxista"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio, jwt);
    }

    @Test
    void tokenInvalidoORevocadoDevuelveJson401() throws Exception {
        when(verificador.verificar("revocado")).thenThrow(FalloAutenticacionAdmin.noAutenticado());
        mvc.perform(get(RUTA).header("Authorization", "Bearer revocado"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(servicio);
    }

    @Test
    void usuarioFirebaseFueraDeAllowlistRecibe403() throws Exception {
        when(verificador.verificar("cliente")).thenThrow(FalloAutenticacionAdmin.noAutorizado());
        mvc.perform(get(RUTA).header("Authorization", "Bearer cliente"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(servicio);
    }

    @Test
    void servidorSinVerificacionDisponibleDevuelve503SinFiltrarDetalles() throws Exception {
        when(verificador.verificar("valido")).thenThrow(FalloAutenticacionAdmin.noDisponible());
        mvc.perform(get(RUTA).header("Authorization", "Bearer valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value(
                        "La verificacion del superadministrador no esta disponible"));
        verifyNoInteractions(servicio);
    }

    @Test
    void listaIncluyeCorreoYEstadosIndependientesSinDatosPrivados() throws Exception {
        autorizar();
        when(servicio.listarTodos()).thenReturn(List.of(taxista()));
        mvc.perform(get(RUTA).header("Authorization", "Bearer valido"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].correo").value("conductor@ejemplo.test"))
                .andExpect(jsonPath("$[0].estadoHabilitacion").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].cuentaActiva").value(true))
                .andExpect(jsonPath("$[0].documentoEnmascarado").value("****5678"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].numeroDocumento").doesNotExist())
                .andExpect(jsonPath("$[0].domicilio").doesNotExist());
        verifyNoInteractions(jwt);
    }

    @Test
    void habilitarUsaEstadoExplicitoSinCsrfNiCuentaFirebase() throws Exception {
        autorizar();
        Taxista t = taxista();
        t.setEstadoHabilitacion(EstadoHabilitacion.HABILITADO);
        when(servicio.cambiarHabilitacion(7L, EstadoHabilitacion.HABILITADO)).thenReturn(t);
        mvc.perform(put(RUTA + "/7/habilitacion").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoHabilitacion").value("HABILITADO"));
        verify(servicio).cambiarHabilitacion(7L, EstadoHabilitacion.HABILITADO);
        verifyNoMoreInteractions(servicio);
    }

    @Test
    void retirarHabilitacionVuelvePendiente() throws Exception {
        autorizar();
        when(servicio.cambiarHabilitacion(7L, EstadoHabilitacion.PENDIENTE)).thenReturn(taxista());
        mvc.perform(put(RUTA + "/7/habilitacion").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoHabilitacion").value("PENDIENTE"));
    }

    @Test
    void desactivarNoCambiaHabilitacion() throws Exception {
        autorizar();
        Taxista t = taxista();
        t.setEstadoHabilitacion(EstadoHabilitacion.HABILITADO);
        t.setCuentaActiva(false);
        when(servicio.cambiarEstadoCuenta(7L, false)).thenReturn(t);
        mvc.perform(put(RUTA + "/7/cuenta").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuentaActiva").value(false))
                .andExpect(jsonPath("$.estadoHabilitacion").value("HABILITADO"));
    }

    @Test
    void booleanAusenteONullEs400YNoDesactivaAccidentalmente() throws Exception {
        autorizar();
        mvc.perform(put(RUTA + "/7/cuenta").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erroresCampo.activa").exists());
        mvc.perform(put(RUTA + "/7/habilitacion").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erroresCampo.habilitado").exists());
        verifyNoInteractions(servicio);
    }

    @Test
    void taxistaInexistenteEs404() throws Exception {
        autorizar();
        when(servicio.cambiarEstadoCuenta(99L, false))
                .thenThrow(RecursoNoEncontradoException.taxista(99L));
        mvc.perform(put(RUTA + "/99/cuenta").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void viajeEnCursoMantiene409DelServicio() throws Exception {
        autorizar();
        when(servicio.cambiarEstadoCuenta(7L, false))
                .thenThrow(new ConflictoEstadoException("El taxista tiene un servicio en curso"));
        mvc.perform(put(RUTA + "/7/cuenta").header("Authorization", "Bearer valido")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    private void autorizar() {
        when(verificador.verificar("valido")).thenReturn("uid-superadmin-prueba");
    }

    private Taxista taxista() {
        return Taxista.builder().id(7L).nombres("Ana").apellidos("Prueba")
                .correo("conductor@ejemplo.test").telefono("900000001")
                .tipoDocumento(TipoDocumento.DNI).numeroDocumento("12345678")
                .passwordHash("hash-que-nunca-sale").domicilio("Privado")
                .placaVehiculo("TEST-07").estadoHabilitacion(EstadoHabilitacion.PENDIENTE)
                .cuentaActiva(true).disponibilidad(Disponibilidad.DISPONIBLE)
                .valoracionPromedio(BigDecimal.ZERO).totalValoraciones(0)
                .fechaRegistro(LocalDateTime.of(2026, 10, 27, 1, 0)).build();
    }
}
