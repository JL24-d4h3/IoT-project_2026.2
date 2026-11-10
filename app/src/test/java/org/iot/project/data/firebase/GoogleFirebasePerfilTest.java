package org.iot.project.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.iot.project.core.ResultCallback;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Datos de alta y límites del intercambio Google, sin Firebase real ni Android. */
public class GoogleFirebasePerfilTest {
    @Test public void cuentaGoogleNuevaSiempreSeCreaComoClienteActivo() {
        Map<String, Object> perfil = FirebaseAccesoRepository.datosPerfilInicial(
                "  Ana Pérez  ", "  ana@example.test  ", " https://example.test/foto.png ");
        Cuenta cuenta = PerfilFirebase.cuenta("uid-google", "ana@example.test", perfil);
        assertEquals(Role.CLIENTE, cuenta.getRol());
        assertEquals("uid-google", cuenta.getIdentidadId());
        assertEquals("Ana Pérez", perfil.get("nombres"));
        assertEquals("ana@example.test", perfil.get("email"));
        assertEquals("https://example.test/foto.png", perfil.get("fotoUrl"));
        assertEquals(true, perfil.get("activo"));
    }

    @Test public void nombreOCorreoQueDicenAdministradorNoCreanPrivilegios() {
        for (String nombre : Arrays.asList("superadmin", "administrador", "taxista")) {
            Map<String, Object> perfil = FirebaseAccesoRepository.datosPerfilInicial(
                    nombre, nombre + "@example.test", null);
            assertEquals("CLIENTE", perfil.get("rol"));
            assertFalse(perfil.containsKey("hotelId"));
            assertFalse(perfil.containsKey("habilitado"));
            assertFalse(perfil.containsKey("password"));
            assertFalse(perfil.containsKey("idToken"));
        }
    }

    @Test public void faltaDeNombreGoogleTieneAlternativaSinExponerCredenciales() {
        assertEquals("ana", FirebaseAccesoRepository.datosPerfilInicial(null,
                "ana@example.test", null).get("nombres"));
        assertEquals("Usuario", FirebaseAccesoRepository.datosPerfilInicial(" ", null, null).get("nombres"));
        assertEquals("", FirebaseAccesoRepository.datosPerfilInicial(null, null, null).get("fotoUrl"));
    }

    @Test public void perfilAdministrativoExistenteSigueSiendoLaAutoridadPorUid() {
        for (Role rol : Arrays.asList(Role.ADMIN_HOTEL, Role.SUPERADMIN)) {
            Map<String, Object> perfil = FirebaseAccesoRepository.datosPerfilInicial("Nombre Google",
                    "usuario@example.test", null);
            perfil.put("rol", rol.name());
            perfil.put("hotelId", "hotel-asignado");
            Cuenta cuenta = PerfilFirebase.cuenta("uid-autenticado", "usuario@example.test", perfil);
            assertEquals(rol, cuenta.getRol());
            assertEquals("uid-autenticado", cuenta.getIdentidadId());
            assertEquals("hotel-asignado", cuenta.getHotelId());
        }
    }

    @Test public void credencialesVaciasConControlesYFueraDeLimiteNoLleganAlSdk() {
        FirebaseAccesoRepository repositorio = new FirebaseAccesoRepository();
        for (String token : Arrays.asList(null, "", " ", "header.payload.signature\n",
                "header.pay load.signature", "token-opaco", "h.p." + "s".repeat(16384),
                "header.payload.signature\u0000", "header.payload.signature\r\nAuthorization:otra")) {
            AtomicInteger errores = new AtomicInteger();
            repositorio.iniciarSesionGoogle(token, new ResultCallback<Cuenta>() {
                @Override public void onExito(@NonNull Cuenta datos) {
                    throw new AssertionError("Una credencial inválida no puede iniciar sesión");
                }
                @Override public void onError(@NonNull String mensaje) {
                    assertTrue(mensaje.contains("Google"));
                    errores.incrementAndGet();
                }
            });
            assertEquals(1, errores.get());
        }
    }

    @Test public void validacionLocalDeTransporteNoPretendeValidarFirmaDelJwt() {
        FirebaseAccesoRepository.exigirTokenGoogle("cabecera.cuerpo.firma");
    }

}
