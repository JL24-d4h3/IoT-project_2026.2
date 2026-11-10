package org.iot.project.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.iot.project.R;
import org.iot.project.core.ubicacion.ServicioUbicacionTaxista;
import org.iot.project.ui.components.TrackingMapView;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Configuración Android y fallback del mapa, sin iniciar GPS ni llamar al backend. */
@RunWith(AndroidJUnit4.class)
public final class MapaSeguimientoAndroidTest {
    @Test public void servicioPrivadoDeclaraUbicacionYPermisosMinimos() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ServiceInfo servicio = contexto.getPackageManager().getServiceInfo(
                new ComponentName(contexto, ServicioUbicacionTaxista.class), 0);
        assertFalse(servicio.exported);
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, servicio.getForegroundServiceType());
        PackageInfo paquete = contexto.getPackageManager().getPackageInfo(contexto.getPackageName(),
                PackageManager.GET_PERMISSIONS);
        assertTrue(Arrays.asList(paquete.requestedPermissions).contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertTrue(Arrays.asList(paquete.requestedPermissions).contains(Manifest.permission.FOREGROUND_SERVICE_LOCATION));
        assertFalse(Arrays.asList(paquete.requestedPermissions).contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION));
    }

    @Test public void mapaSinClaveExplicaConfiguracionYAdmiteCicloDeVida() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ApplicationInfo info = contexto.getPackageManager().getApplicationInfo(contexto.getPackageName(),
                PackageManager.GET_META_DATA);
        String clave = info.metaData == null ? null : info.metaData.getString("com.google.android.geo.API_KEY");
        assumeTrue("Este caso comprueba el build sin clave local", clave == null || clave.isBlank());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TrackingMapView mapa = new TrackingMapView(contexto);
            mapa.onCreate(null);
            assertEquals(1, mapa.getChildCount());
            assertTrue(mapa.getChildAt(0) instanceof TextView);
            TextView mensaje = (TextView) mapa.getChildAt(0);
            assertEquals(contexto.getString(R.string.mapa_google_sin_configurar), mensaje.getText().toString());
            assertEquals(View.VISIBLE, mensaje.getVisibility());
            mapa.onStart();
            mapa.onResume();
            mapa.setRecojo(-12.09654, -77.03611);
            mapa.setConductor(-12.097, -77.037);
            assertTrue(mapa.getDistanciaM() > 0);
            mapa.onSaveInstanceState(new Bundle());
            mapa.onPause();
            mapa.onStop();
            mapa.onLowMemory();
            mapa.onDestroy();
            mapa.onDestroy();
        });
    }
}
