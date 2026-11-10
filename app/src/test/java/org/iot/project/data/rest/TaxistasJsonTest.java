package org.iot.project.data.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.iot.project.models.Driver;
import org.iot.project.models.TaxiService;
import org.iot.project.models.TaxiStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TaxistasJsonTest {
    @Test
    public void detalleMapeaIdentidadYConvierteEscalaSinInventarDatosPrivados() {
        Driver conductor = TaxistasJson.conductor(perfil());
        assertEquals("7", conductor.getId());
        assertEquals("Ana Pérez", conductor.getNombreCompleto());
        assertTrue(conductor.isHabilitado());
        assertEquals(9.4f, conductor.getRating(), 0.001f);
        assertEquals(12, conductor.getNumServicios());
        assertEquals("****1234", conductor.getNumeroDocumento());
        assertNull(conductor.getEmail());
        assertNull(conductor.getDireccion());
        assertEquals("ABC-123", conductor.getVehiculo().getPlaca());
        assertNull(conductor.getVehiculo().getMarca());
        assertNull(conductor.getVehiculo().getModelo());
        assertNull(conductor.getVehiculo().getColor());
    }

    @Test
    public void habilitacionRequiereAprobacionYCuentaActiva() {
        JsonObject perfil = perfil();
        perfil.addProperty("cuentaActiva", false);
        assertFalse(TaxistasJson.conductor(perfil).isHabilitado());
        perfil.addProperty("cuentaActiva", true);
        perfil.addProperty("estadoHabilitacion", "PENDIENTE");
        assertFalse(TaxistasJson.conductor(perfil).isHabilitado());
        perfil.remove("estadoHabilitacion");
        assertFalse(TaxistasJson.conductor(perfil).isHabilitado());
    }

    @Test
    public void resumenNoInventaApellidosAprobacionNiValoracion() {
        Driver conductor = TaxistasJson.conductor(json("{\"id\":7,\"nombreCompleto\":\"Ana María Pérez\","
                + "\"valoracionPromedio\":0,\"totalValoraciones\":0}"));
        assertEquals("Ana María Pérez", conductor.getNombres());
        assertEquals("", conductor.getApellidos());
        assertFalse(conductor.isHabilitado());
        assertEquals(0f, conductor.getRating(), 0f);
        assertNull(conductor.getVehiculo());
    }

    @Test
    public void fotosRelativasSeResuelvenConElServidorLocal() {
        Driver conductor = TaxistasJson.conductor(perfil(), "http://10.0.2.2:8080/api/");
        assertEquals("http://10.0.2.2:8080/uploads/ana.jpg", conductor.getFotoUrl());
        assertEquals("https://images.example.test/auto.jpg", conductor.getVehiculo().getFotoUrl());
    }

    @Test
    public void crearServicioObtieneCodigoRealYConservaDatosDeLaReserva() {
        TaxiService contexto = contexto();
        JsonObject respuesta = servicio("SOLICITADO");
        respuesta.addProperty("codigoQr", "QR-REAL-123");
        TaxiService creado = TaxistasJson.servicio(respuesta, contexto);
        assertEquals("reserva-A", creado.getId());
        assertEquals("31", creado.getApiId());
        assertEquals("reserva-A", creado.getBookingId());
        assertEquals("cliente-A", creado.getClienteId());
        assertEquals("QR-REAL-123", creado.getCodigo());
        assertEquals(contexto.getFecha(), creado.getFecha());
        assertEquals(contexto.getHora(), creado.getHora());
        assertEquals(3, creado.getNumPasajeros());
        assertTrue(creado.isIdaYVuelta());
        // La tarifa cobrada al cliente depende del beneficio de su reserva, no de la tarifa web.
        assertEquals(0d, creado.getPrecio(), 0d);
        assertEquals("Hotel real", creado.getOrigen());
    }

    @Test
    public void consultarServicioConservaQrNotaYUltimaUbicacionDelMismoConductor() {
        Driver conductor = TaxistasJson.conductor(perfil());
        TaxiService contexto = contexto().withApiId("31")
                .withEstadoPersistido(TaxiStatus.FINALIZADO, conductor, 9f);
        LocalDateTime momento = LocalDateTime.of(2026, 10, 25, 10, 30);
        contexto.actualizarUbicacion(-12.03, -77.04, momento);
        TaxiService actualizado = TaxistasJson.servicio(asignado("FINALIZADO", 7), contexto);
        assertEquals(contexto.getCodigo(), actualizado.getCodigo());
        assertEquals(9f, actualizado.getRatingCliente(), 0f);
        assertSame(conductor, actualizado.getDriver());
        assertEquals(momento, actualizado.getUltimaActualizacionUbicacion());
        assertEquals(-12.03, actualizado.getLatConductor(), 0d);
        assertEquals(-77.04, actualizado.getLngConductor(), 0d);
    }

    @Test
    public void reasignacionNoAtribuyeUbicacionDelConductorAnteriorAlNuevo() {
        TaxiService contexto = contexto().withEstadoPersistido(TaxiStatus.ASIGNADO,
                TaxistasJson.conductor(perfil()), 0f);
        contexto.actualizarUbicacion(-12.03, -77.04, LocalDateTime.now());
        TaxiService actualizado = TaxistasJson.servicio(asignado("ASIGNADO", 8), contexto);
        assertEquals("8", actualizado.getDriver().getId());
        assertNull(actualizado.getUltimaActualizacionUbicacion());
    }

    @Test
    public void servicioSinContextoNoInventaReservaProgramacionQrNiUbicacion() {
        JsonObject respuesta = servicio("SOLICITADO");
        respuesta.remove("latitudOrigen");
        respuesta.remove("longitudOrigen");
        TaxiService recibido = TaxistasJson.servicio(respuesta, null);
        assertEquals("31", recibido.getId());
        assertEquals("31", recibido.getApiId());
        assertEquals("", recibido.getBookingId());
        assertEquals("", recibido.getClienteId());
        assertEquals("", recibido.getCodigo());
        assertNull(recibido.getFecha());
        assertNull(recibido.getHora());
        assertFalse(recibido.hasRecojo());
        assertNull(recibido.getUltimaActualizacionUbicacion());
        assertEquals(55d, recibido.getPrecio(), 0d);
    }

    @Test
    public void respuestaDeOtroServicioSeRechaza() {
        rechaza(() -> TaxistasJson.servicio(servicio("SOLICITADO"), contexto().withApiId("99")));
    }

    @Test
    public void estadosYAsignacionesIncompatiblesSeRechazan() {
        rechaza(() -> TaxistasJson.servicio(servicio("CANCELADO"), null));
        rechaza(() -> TaxistasJson.servicio(servicio("ASIGNADO"), null));
        rechaza(() -> TaxistasJson.servicio(asignado("SOLICITADO", 7), null));
    }

    @Test
    public void identificadoresNoNumericosFraccionariosYFueraDeRangoSeRechazan() {
        for (String valor : new String[]{"\"uid-firebase\"", "0", "-1", "1.5", "9223372036854775808"}) {
            JsonObject respuesta = servicio("SOLICITADO");
            respuesta.add("id", JsonParser.parseString(valor));
            rechaza(() -> TaxistasJson.servicio(respuesta, null));
        }
    }

    @Test
    public void coordenadasIncompletasOImposiblesSeRechazan() {
        JsonObject incompleto = servicio("SOLICITADO");
        incompleto.remove("longitudOrigen");
        rechaza(() -> TaxistasJson.servicio(incompleto, null));
        JsonObject imposible = servicio("SOLICITADO");
        imposible.addProperty("latitudOrigen", 91);
        rechaza(() -> TaxistasJson.servicio(imposible, null));
    }

    @Test
    public void solicitudCumpleContratoYOmitelaTarifaDelBeneficioGratuito() {
        JsonObject solicitud = TaxistasJson.solicitud(contexto(), " Ana Pérez ", "+51 999111222");
        assertEquals("cliente-A", solicitud.get("clienteId").getAsString());
        assertEquals("Ana Pérez", solicitud.get("nombreCliente").getAsString());
        assertEquals("+51 999111222", solicitud.get("telefonoCliente").getAsString());
        assertEquals(-12.1, solicitud.get("latitudOrigen").getAsDouble(), 0d);
        assertEquals(-77.1, solicitud.get("longitudOrigen").getAsDouble(), 0d);
        assertFalse(solicitud.has("tarifa"));
        assertFalse(solicitud.has("latitudDestino"));
        assertFalse(solicitud.has("fechaSolicitud"));
        JsonObject pagado = TaxistasJson.solicitud(contexto().withPrecio(120d), "Ana Pérez", "");
        assertEquals(120d, pagado.get("tarifa").getAsDouble(), 0d);
        assertFalse(pagado.has("telefonoCliente"));
    }

    @Test
    public void solicitudNoEnvíaCerosComoUbicacionDesconocidaNiContactoInvalido() {
        TaxiService sinRecojo = new TaxiService("local", "", "reserva-A", "cliente-A")
                .withRuta("Hotel", "Aeropuerto");
        rechaza(() -> TaxistasJson.solicitud(sinRecojo, "Ana", null));
        rechaza(() -> TaxistasJson.solicitud(contexto(), "", null));
        rechaza(() -> TaxistasJson.solicitud(contexto(), "Ana", "teléfono"));
        rechaza(() -> TaxistasJson.solicitud(contexto().withRecojo(Double.NaN, -77), "Ana", null));
    }

    @Test
    public void puntuacionConvierteDeDiezACincoConRedondeoExplicito() {
        assertEquals(1, TaxistasJson.puntuacion(1f));
        assertEquals(1, TaxistasJson.puntuacion(2f));
        assertEquals(3, TaxistasJson.puntuacion(5f));
        assertEquals(4, TaxistasJson.puntuacion(8f));
        assertEquals(5, TaxistasJson.puntuacion(9f));
        assertEquals(5, TaxistasJson.puntuacion(10f));
        rechaza(() -> TaxistasJson.puntuacion(0f));
        rechaza(() -> TaxistasJson.puntuacion(11f));
        rechaza(() -> TaxistasJson.puntuacion(Float.NaN));
    }

    private static JsonObject perfil() {
        return json("{\"id\":7,\"nombres\":\"Ana\",\"apellidos\":\"Pérez\","
                + "\"telefono\":\"999111222\",\"tipoDocumento\":\"DNI\","
                + "\"documentoEnmascarado\":\"****1234\",\"estadoHabilitacion\":\"HABILITADO\","
                + "\"cuentaActiva\":true,\"valoracionPromedio\":4.7,\"totalValoraciones\":12,"
                + "\"placaVehiculo\":\"ABC-123\",\"fotoUrl\":\"/uploads/ana.jpg\","
                + "\"fotoVehiculoUrl\":\"https://images.example.test/auto.jpg\"}");
    }

    private static JsonObject servicio(String estado) {
        return json("{\"id\":31,\"estado\":\"" + estado + "\",\"codigoQr\":null,"
                + "\"direccionOrigen\":\"Hotel real\",\"direccionDestino\":\"Aeropuerto\","
                + "\"latitudOrigen\":-12.1,\"longitudOrigen\":-77.1,\"tarifa\":55}");
    }

    private static JsonObject asignado(String estado, int conductorId) {
        JsonObject datos = servicio(estado);
        datos.addProperty("taxistaId", conductorId);
        datos.addProperty("taxistaNombre", "Ana Pérez");
        datos.addProperty("taxistaPlaca", "ABC-123");
        return datos;
    }

    private static TaxiService contexto() {
        return new TaxiService("reserva-A", "QR-GUARDADO", "reserva-A", "cliente-A")
                .withRuta("Hotel local", "Aeropuerto").withRecojo(-12.1, -77.1)
                .withProgramacion(LocalDate.of(2026, 10, 30), LocalTime.of(10, 30), true)
                .withPasajeros(3).withPrecio(0d);
    }

    private static JsonObject json(String valor) {
        return JsonParser.parseString(valor).getAsJsonObject();
    }

    private static void rechaza(Runnable accion) {
        try {
            accion.run();
            fail("Debía rechazar datos incompatibles con el contrato REST.");
        } catch (IllegalArgumentException esperado) {
            // La respuesta inválida no debe llegar al modelo ni enviarse al servidor.
        }
    }
}
