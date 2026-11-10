package org.iot.project.core.permisos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.core.permisos.PreferenciasPermisos.Estado;
import org.iot.project.core.permisos.PreferenciasPermisos.Permiso;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Archivos privados aislados: no solicita ni cambia permisos reales del dispositivo. */
@RunWith(AndroidJUnit4.class)
public final class PreferenciasPermisosAndroidTest {
    private Context contexto;
    private String archivo;
    private String archivoLegacy;
    private SharedPreferences preferencias;
    private SharedPreferences avisosLegacy;
    private PreferenciasPermisos permisos;

    @Before public void preparar() {
        contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String sufijo = UUID.randomUUID().toString();
        archivo = "prueba_permisos_" + sufijo;
        archivoLegacy = "prueba_permisos_legacy_" + sufijo;
        preferencias = contexto.getSharedPreferences(archivo, Context.MODE_PRIVATE);
        avisosLegacy = contexto.getSharedPreferences(archivoLegacy, Context.MODE_PRIVATE);
        permisos = nuevaInstancia();
    }

    @After public void limpiarArchivosDePrueba() {
        if (preferencias != null) preferencias.edit().clear().commit();
        if (avisosLegacy != null) avisosLegacy.edit().clear().commit();
        if (contexto != null && archivo != null) contexto.deleteSharedPreferences(archivo);
        if (contexto != null && archivoLegacy != null) contexto.deleteSharedPreferences(archivoLegacy);
    }

    @Test public void instalacionSinHistorialNoPresuponeConcesionNiSolicitud() {
        for (Permiso permiso : Permiso.values()) {
            assertFalse(permisos.fueSolicitado(permiso));
            assertEquals(Estado.NO_SOLICITADO, permisos.getEstado(permiso));
        }
    }

    @Test public void solicitudQuedaRecordadaAunqueNoHayaLlegadoElResultado() {
        permisos.registrarSolicitud(Permiso.CAMARA);

        PreferenciasPermisos reabiertos = nuevaInstancia();

        assertTrue(reabiertos.fueSolicitado(Permiso.CAMARA));
        assertFalse(reabiertos.fueSolicitado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.DENEGADO, reabiertos.sincronizar(Permiso.CAMARA, false));
    }

    @Test public void concesionesDeAmbosPermisosPersistenAlReabrirElAlmacen() {
        permisos.registrarResultado(Permiso.NOTIFICACIONES, true);
        permisos.registrarResultado(Permiso.CAMARA, true);

        PreferenciasPermisos reabiertos = nuevaInstancia();

        for (Permiso permiso : Permiso.values()) {
            assertTrue(reabiertos.fueSolicitado(permiso));
            assertEquals(Estado.CONCEDIDO, reabiertos.getEstado(permiso));
        }
    }

    @Test public void rechazosDeAmbosPermisosPersistenAlReabrirElAlmacen() {
        permisos.registrarResultado(Permiso.NOTIFICACIONES, false);
        permisos.registrarResultado(Permiso.CAMARA, false);

        PreferenciasPermisos reabiertos = nuevaInstancia();

        for (Permiso permiso : Permiso.values()) {
            assertTrue(reabiertos.fueSolicitado(permiso));
            assertEquals(Estado.DENEGADO, reabiertos.getEstado(permiso));
        }
    }

    @Test public void decisionesDeCamaraYNotificacionesSonIndependientes() {
        permisos.registrarResultado(Permiso.NOTIFICACIONES, true);
        permisos.registrarResultado(Permiso.CAMARA, false);
        assertEquals(Estado.CONCEDIDO, permisos.getEstado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.DENEGADO, permisos.getEstado(Permiso.CAMARA));

        permisos.sincronizar(Permiso.CAMARA, true);
        permisos.registrarResultado(Permiso.NOTIFICACIONES, false);

        PreferenciasPermisos reabiertos = nuevaInstancia();
        assertEquals(Estado.DENEGADO, reabiertos.getEstado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.CONCEDIDO, reabiertos.getEstado(Permiso.CAMARA));
    }

    @Test public void revocacionEnAndroidSustituyeUnaConcesionGuardada() {
        for (Permiso permiso : Permiso.values()) {
            permisos.registrarResultado(permiso, true);
            assertEquals(Estado.DENEGADO, permisos.sincronizar(permiso, false));
        }

        PreferenciasPermisos reabiertos = nuevaInstancia();
        for (Permiso permiso : Permiso.values()) {
            assertTrue(reabiertos.fueSolicitado(permiso));
            assertEquals(Estado.DENEGADO, reabiertos.getEstado(permiso));
        }
    }

    @Test public void concesionDesdeAjustesActualizaUnRechazoGuardado() {
        for (Permiso permiso : Permiso.values()) {
            permisos.registrarResultado(permiso, false);
            assertEquals(Estado.CONCEDIDO, permisos.sincronizar(permiso, true));
        }

        PreferenciasPermisos reabiertos = nuevaInstancia();
        for (Permiso permiso : Permiso.values()) {
            assertEquals(Estado.CONCEDIDO, reabiertos.getEstado(permiso));
        }
    }

    @Test public void permisoNoConcedidoSinSolicitudPreviaNoSeMarcaComoRechazado() {
        for (Permiso permiso : Permiso.values()) {
            assertEquals(Estado.NO_SOLICITADO, permisos.sincronizar(permiso, false));
            assertFalse(permisos.fueSolicitado(permiso));
        }
    }

    @Test public void migraSolicitudAntiguaDeChatSinInventarPermisoNiAfectarCamara() {
        avisosLegacy.edit().putBoolean("chat_solicitado", true).commit();

        PreferenciasPermisos migrados = nuevaInstancia();

        assertTrue(migrados.fueSolicitado(Permiso.NOTIFICACIONES));
        assertNotEquals(Estado.CONCEDIDO, migrados.getEstado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.DENEGADO, migrados.sincronizar(Permiso.NOTIFICACIONES, false));
        assertFalse(migrados.fueSolicitado(Permiso.CAMARA));
        assertEquals(Estado.NO_SOLICITADO, migrados.getEstado(Permiso.CAMARA));
    }

    @Test public void migrarDeNuevoNoSobrescribeResultadosConfirmadosDespues() {
        avisosLegacy.edit().putBoolean("chat_solicitado", true).commit();
        PreferenciasPermisos migrados = nuevaInstancia();
        migrados.registrarResultado(Permiso.NOTIFICACIONES, true);
        migrados.registrarResultado(Permiso.CAMARA, false);

        PreferenciasPermisos reabiertos = nuevaInstancia();

        assertTrue(reabiertos.fueSolicitado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.CONCEDIDO, reabiertos.getEstado(Permiso.NOTIFICACIONES));
        assertEquals(Estado.DENEGADO, reabiertos.getEstado(Permiso.CAMARA));
    }

    private PreferenciasPermisos nuevaInstancia() {
        // La barrera de escritura también espera las operaciones apply anteriores.
        preferencias.edit().commit();
        avisosLegacy.edit().commit();
        return new PreferenciasPermisos(preferencias, avisosLegacy);
    }
}
