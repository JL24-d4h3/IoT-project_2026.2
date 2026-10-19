package org.iot.project.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.iot.project.models.Card;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.models.User;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Limites de confianza del perfil; no requiere SDK Firebase ni llamadas de red. */
public class PerfilFirebaseTest {

    private static final String UID = "uid-auth-real";
    private static final String EMAIL = "cliente@correo.com";

    @Test
    public void conservaLosTresRolesAdmitidosDesdeElPerfil() {
        for (Role rol : Arrays.asList(Role.CLIENTE, Role.ADMIN_HOTEL, Role.SUPERADMIN)) {
            Map<String, Object> perfil = perfil(rol.name());

            User usuario = PerfilFirebase.usuario(UID, EMAIL, perfil);
            Cuenta cuenta = PerfilFirebase.cuenta(UID, EMAIL, perfil);

            assertEquals(rol, usuario.getRol());
            assertEquals(rol, cuenta.getRol());
            assertEquals(UID, usuario.getId());
            assertEquals(UID, cuenta.getIdentidadId());
            assertTrue(usuario.isActivo());
        }
    }

    @Test
    public void correoConNombreDeAdministradorNoConcedePrivilegios() {
        String email = "superadmin@example.com";
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.put("email", email);

        assertEquals(Role.CLIENTE, PerfilFirebase.usuario(UID, email, perfil).getRol());
        assertEquals(Role.CLIENTE, PerfilFirebase.cuenta(UID, email, perfil).getRol());
    }

    @Test
    public void unCampoIdDentroDelDocumentoNoSustituyeElUidAutenticado() {
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.put("id", "U1");
        perfil.put("identidadId", "uid-de-otra-cuenta");

        assertEquals(UID, PerfilFirebase.usuario(UID, EMAIL, perfil).getId());
        assertEquals(UID, PerfilFirebase.cuenta(UID, EMAIL, perfil).getIdentidadId());
    }

    @Test
    public void correoDelDocumentoNoSustituyeElCorreoAutenticado() {
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.put("email", "cuenta-ajena@correo.com");

        assertEquals(EMAIL, PerfilFirebase.usuario(UID, EMAIL, perfil).getEmail());
        assertEquals(EMAIL, PerfilFirebase.cuenta(UID, EMAIL, perfil).getEmail());
    }

    @Test
    public void uidVacioNoSeConvierteEnClienteDeDemostracion() {
        for (String uid : Arrays.asList("", " ", "usuarios/otra-cuenta")) {
            assertThrows(IllegalArgumentException.class,
                    () -> PerfilFirebase.usuario(uid, EMAIL, perfil("CLIENTE")));
            assertThrows(IllegalArgumentException.class,
                    () -> PerfilFirebase.cuenta(uid, EMAIL, perfil("CLIENTE")));
        }
    }

    @Test
    public void rolAusenteNoSeAsumeCliente() {
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.remove("rol");
        exigirRechazado(perfil);
    }

    @Test
    public void rolDesconocidoONoTextualSeRechaza() {
        for (Object rol : Arrays.asList("SOCIO", "", 1, true)) {
            Map<String, Object> perfil = perfil("CLIENTE");
            perfil.put("rol", rol);
            exigirRechazado(perfil);
        }
    }

    @Test
    public void conductorNoEntraPorElPerfilFirestore() {
        exigirRechazado(perfil("CONDUCTOR"));
    }

    @Test
    public void activoAusenteNoHabilitaUnaCuenta() {
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.remove("activo");
        exigirRechazado(perfil);
    }

    @Test
    public void cuentaInactivaNoSePublicaComoSesionValida() {
        for (Role rol : Arrays.asList(Role.CLIENTE, Role.ADMIN_HOTEL, Role.SUPERADMIN)) {
            Map<String, Object> perfil = perfil(rol.name());
            perfil.put("activo", false);
            exigirRechazado(perfil);
        }
    }

    @Test
    public void activoDebeSerBooleanoTrueSinConversionesImplicitas() {
        for (Object activo : Arrays.asList("true", "false", 1, 0)) {
            Map<String, Object> perfil = perfil("CLIENTE");
            perfil.put("activo", activo);
            exigirRechazado(perfil);
        }
        Map<String, Object> perfil = perfil("CLIENTE");
        perfil.put("activo", null);
        exigirRechazado(perfil);
    }

    @Test
    public void administradorSinHotelSeMantieneSinAsignacion() {
        Map<String, Object> perfil = perfil("ADMIN_HOTEL");
        assertNull(PerfilFirebase.cuenta(UID, EMAIL, perfil).getHotelId());
        assertNull(PerfilFirebase.usuario(UID, EMAIL, perfil).getHotelId());

        perfil.put("hotelId", null);
        assertNull(PerfilFirebase.cuenta(UID, EMAIL, perfil).getHotelId());
        assertNull(PerfilFirebase.usuario(UID, EMAIL, perfil).getHotelId());
    }

    @Test
    public void conservaHotelAsignadoEnElPerfilAdministrativo() {
        Map<String, Object> perfil = perfil("ADMIN_HOTEL");
        perfil.put("hotelId", "hotel-autorizado");

        assertEquals("hotel-autorizado",
                PerfilFirebase.cuenta(UID, EMAIL, perfil).getHotelId());
        assertEquals("hotel-autorizado",
                PerfilFirebase.usuario(UID, EMAIL, perfil).getHotelId());
    }

    @Test
    public void hotelIdDeTipoInvalidoSeRechaza() {
        for (Object hotelId : Arrays.asList(123, true, Arrays.asList("H1"))) {
            Map<String, Object> perfil = perfil("ADMIN_HOTEL");
            perfil.put("hotelId", hotelId);
            exigirRechazado(perfil);
        }
    }

    @Test
    public void hotelIdNoAdmiteUnaRutaComoAsignacion() {
        Map<String, Object> perfil = perfil("ADMIN_HOTEL");
        perfil.put("hotelId", "hoteles/H1");
        exigirRechazado(perfil);
    }

    @Test
    public void mapeaDatosPersonalesSinExigirOpcionales() {
        Map<String, Object> perfil = perfil("CLIENTE");
        User minimo = PerfilFirebase.usuario(UID, EMAIL, perfil);
        assertEquals("Lucia", minimo.getNombres());
        assertEquals("Quispe", minimo.getApellidos());
        assertEquals(EMAIL, minimo.getEmail());
        assertEquals("", minimo.getTelefono());
        assertEquals("", minimo.getDireccion());
        assertEquals("", minimo.getFotoUrl());

        perfil.put("telefono", "999123456");
        perfil.put("direccion", "Lima");
        perfil.put("fotoUrl", "https://example.com/avatar.png");
        User completo = PerfilFirebase.usuario(UID, EMAIL, perfil);
        assertEquals("999123456", completo.getTelefono());
        assertEquals("Lima", completo.getDireccion());
        assertEquals("https://example.com/avatar.png", completo.getFotoUrl());
    }

    @Test
    public void registroNoConfiaEnRolActivoOEmailDelBorrador() {
        User borrador = new User("U3", "Lucia", "Quispe", Role.SUPERADMIN);
        borrador.setActivo(false);
        borrador.setEmail("otra-cuenta@correo.com");
        borrador.setHotelId("hotel-ajeno");

        Map<String, Object> nuevo = PerfilFirebase.nuevoCliente(borrador, EMAIL);

        assertEquals("CLIENTE", nuevo.get("rol"));
        assertEquals(Boolean.TRUE, nuevo.get("activo"));
        assertEquals(EMAIL, nuevo.get("email"));
        assertFalse(nuevo.containsKey("id"));
        assertFalse(nuevo.containsKey("identidadId"));
        assertTrue(!nuevo.containsKey("hotelId") || nuevo.get("hotelId") == null);
    }

    @Test
    public void registroNoSerializaTarjetasDocumentosNiGettersCalculados() {
        User borrador = new User("uid", "Lucia", "Quispe", Role.CLIENTE)
                .withDocumento("DNI", "12345678")
                .withNacimiento("2000-01-01")
                .addTarjeta(new Card("C1", "Visa", "4242", "Lucia", "12/30"));

        Map<String, Object> nuevo = PerfilFirebase.nuevoCliente(borrador, EMAIL);
        Set<String> permitidos = new HashSet<>(Arrays.asList(
                "nombres", "apellidos", "email", "telefono", "direccion", "fotoUrl",
                "rol", "activo", "hotelId"));

        assertTrue("Campos inesperados: " + nuevo.keySet(),
                permitidos.containsAll(nuevo.keySet()));
    }

    @Test
    public void registroExigeNombreYApellido() {
        User sinNombre = new User("uid", " ", "Quispe", Role.CLIENTE);
        User sinApellido = new User("uid", "Lucia", " ", Role.CLIENTE);

        assertThrows(IllegalArgumentException.class,
                () -> PerfilFirebase.nuevoCliente(sinNombre, EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> PerfilFirebase.nuevoCliente(sinApellido, EMAIL));
    }

    @Test
    public void actualizacionSoloIncluyeCamposEditablesDelPerfil() {
        User datos = new User("uid-ajeno", "Lucia", "Quispe", Role.SUPERADMIN);
        datos.setActivo(false);
        datos.setEmail("otro@correo.com");
        datos.setHotelId("hotel-ajeno");
        datos.setTelefono("999123456");
        datos.setDireccion("Lima");
        datos.withFoto("https://example.com/foto.png");
        Map<String, Object> actualizacion = PerfilFirebase.actualizacion(datos);
        Set<String> permitidos = new HashSet<>(Arrays.asList(
                "nombres", "apellidos", "telefono", "direccion", "fotoUrl"));

        assertTrue("Campos inesperados: " + actualizacion.keySet(),
                permitidos.containsAll(actualizacion.keySet()));
        assertEquals("Lucia", actualizacion.get("nombres"));
        assertEquals("Quispe", actualizacion.get("apellidos"));
        assertEquals("999123456", actualizacion.get("telefono"));
        assertEquals("Lima", actualizacion.get("direccion"));
        assertEquals("https://example.com/foto.png", actualizacion.get("fotoUrl"));
        assertFalse(actualizacion.containsKey("id"));
        assertFalse(actualizacion.containsKey("email"));
        assertFalse(actualizacion.containsKey("rol"));
        assertFalse(actualizacion.containsKey("activo"));
        assertFalse(actualizacion.containsKey("hotelId"));
    }

    private static Map<String, Object> perfil(String rol) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombres", "Lucia");
        datos.put("apellidos", "Quispe");
        datos.put("email", EMAIL);
        datos.put("rol", rol);
        datos.put("activo", true);
        return datos;
    }

    private static void exigirRechazado(Map<String, Object> perfil) {
        assertThrows(IllegalArgumentException.class,
                () -> PerfilFirebase.usuario(UID, EMAIL, perfil));
        assertThrows(IllegalArgumentException.class,
                () -> PerfilFirebase.cuenta(UID, EMAIL, perfil));
    }
}
