package org.iot.project.test;

import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.ScanOptions;

import org.iot.project.core.SessionManager;
import org.iot.project.data.rest.SesionTaxistaRest;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;
import org.iot.project.ui.driver.qr.EscanerTaxiActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Solo captura local: no abre MainActivity ni invoca repositorios REST/Firestore.
 * Ejecutar inicialmente con CAMERA denegado desde adb. El caso con permiso lo
 * concede y lo deja concedido: revocarlo dentro de la instrumentación podría
 * terminar el proceso de pruebas. El ejecutor externo restaura ese permiso.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class EscanerTaxiAndroidTest {
    private static final String IDENTIDAD = "taxista:73";
    private static final String TOKEN_FICTICIO = "token-local-escaner-sin-acceso-a-backend";
    private Context contexto;
    private Instrumentation instrumentacion;
    private ActivityScenario<EscanerTaxiActivity> escenario;

    @Before
    public void prepararSesionFicticiaSinAbrirElPanel() {
        FirebaseEmulatorApplication.exigirProyectoDemo();
        instrumentacion = InstrumentationRegistry.getInstrumentation();
        contexto = instrumentacion.getTargetContext();
        instrumentacion.runOnMainSync(() -> {
            SessionManager.cerrarSesion();
            SessionManager.iniciarSesionTaxistaRest(cuentaFicticia());
            assertEquals(Role.CONDUCTOR, SessionManager.getRolActivo());
            assertEquals(IDENTIDAD, SessionManager.getIdentidadId());
            assertEquals(TOKEN_FICTICIO, SesionTaxistaRest.getToken());
        });
    }

    @After
    public void cerrarEscanerYSesionLocal() {
        if (escenario != null) escenario.close();
        if (instrumentacion != null) instrumentacion.runOnMainSync(SessionManager::cerrarSesion);
    }

    @Test
    public void a_declaraCamaraOpcionalYCapturaNoExportada() throws Exception {
        PackageManager paquetes = contexto.getPackageManager();
        PackageInfo aplicacion = paquetes.getPackageInfo(contexto.getPackageName(),
                PackageManager.GET_PERMISSIONS | PackageManager.GET_CONFIGURATIONS);
        assertNotNull(aplicacion.requestedPermissions);
        assertTrue(Arrays.asList(aplicacion.requestedPermissions).contains(Manifest.permission.CAMERA));
        assertNotNull(aplicacion.reqFeatures);
        boolean camaraDeclarada = false;
        for (FeatureInfo requisito : aplicacion.reqFeatures) {
            if (requisito.name != null && requisito.name.startsWith("android.hardware.camera")) {
                camaraDeclarada = true;
                assertEquals("La cámara no debe impedir instalar el resto de la app", 0,
                        requisito.flags & FeatureInfo.FLAG_REQUIRED);
            }
        }
        assertTrue("El manifiesto debe declarar la cámara opcional", camaraDeclarada);
        ActivityInfo captura = paquetes.getActivityInfo(new ComponentName(contexto, EscanerTaxiActivity.class), 0);
        assertFalse("Otra aplicación no debe poder abrir la captura del viaje", captura.exported);
    }

    @Test
    public void b_sinPermisoRetornaCanceladoSinAbrirUnDialogoDePermisos() {
        assumeFalse("Ejecutar este caso con CAMERA denegado desde adb", permisoCamara());

        escenario = ActivityScenario.launchActivityForResult(intentEscaner());
        Instrumentation.ActivityResult resultado = escenario.getResult();

        assertEquals(Activity.RESULT_CANCELED, resultado.getResultCode());
        assertNotNull(resultado.getResultData());
        assertTrue(resultado.getResultData().getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false));
        assertFalse(permisoCamara());
        comprobarSesionConservada();
    }

    @Test
    public void c_conPermisoAbreVisorRecreaYCancelarConservaLaSesion() {
        exigirCamaraYConcederPermiso();
        escenario = ActivityScenario.launchActivityForResult(intentEscaner());
        comprobarVisorVisible();

        escenario.recreate();
        comprobarVisorVisible();
        pressBackUnconditionally();

        assertEquals(Activity.RESULT_CANCELED, escenario.getResult().getResultCode());
        comprobarSesionConservada();
    }

    private Intent intentEscaner() {
        return new ScanOptions().setCaptureActivity(EscanerTaxiActivity.class)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false)
                .createScanIntent(contexto)
                .putExtra(EscanerTaxiActivity.EXTRA_IDENTIDAD, IDENTIDAD)
                .putExtra(EscanerTaxiActivity.EXTRA_SERVICIO, "91");
    }

    private boolean permisoCamara() {
        return ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void exigirCamaraYConcederPermiso() {
        assumeTrue("Este caso necesita una cámara real o emulada",
                contexto.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        if (!permisoCamara()) {
            instrumentacion.getUiAutomation().grantRuntimePermission(contexto.getPackageName(), Manifest.permission.CAMERA);
            instrumentacion.waitForIdleSync();
        }
        assertTrue(permisoCamara());
    }

    private void comprobarVisorVisible() {
        escenario.onActivity(actividad -> {
            assertFalse(actividad.isFinishing());
            DecoratedBarcodeView visor = actividad.findViewById(
                    com.google.zxing.client.android.R.id.zxing_barcode_scanner);
            assertNotNull("Debe inflar el visor de JourneyApps", visor);
            assertEquals(View.VISIBLE, visor.getVisibility());
            assertNotNull(visor.getBarcodeView());
        });
    }

    private void comprobarSesionConservada() {
        instrumentacion.runOnMainSync(() -> {
            assertEquals(IDENTIDAD, SessionManager.getIdentidadId());
            assertEquals(TOKEN_FICTICIO, SesionTaxistaRest.getToken());
        });
    }

    private static Cuenta cuentaFicticia() {
        JsonObject taxista = new JsonObject();
        taxista.addProperty("id", 73);
        taxista.addProperty("nombres", "Conductor");
        taxista.addProperty("apellidos", "Prueba local");
        taxista.addProperty("estadoHabilitacion", "HABILITADO");
        taxista.addProperty("cuentaActiva", true);
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("token", TOKEN_FICTICIO);
        respuesta.addProperty("tipoToken", "Bearer");
        respuesta.addProperty("expiraEnMinutos", 30);
        respuesta.add("taxista", taxista);
        try {
            Method abrir = SesionTaxistaRest.class.getDeclaredMethod("abrir", JsonObject.class, String.class);
            abrir.setAccessible(true);
            return (Cuenta) abrir.invoke(null, respuesta, "escaner@prueba.invalid");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("No se pudo preparar la sesión REST ficticia local", error);
        }
    }
}
