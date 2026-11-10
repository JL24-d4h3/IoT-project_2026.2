package org.iot.project.ui.driver.qr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.iot.project.core.SessionManager;
import org.iot.project.core.permisos.PreferenciasPermisos;
import org.iot.project.data.rest.SesionTaxistaRest;
import org.iot.project.models.Cuenta;
import org.iot.project.models.Role;

/** Captura un QR; los permisos y la confirmación del cierre pertenecen a la hoja. */
public class EscanerTaxiActivity extends ComponentActivity {
    public static final String EXTRA_IDENTIDAD = "taxistaIdentidadQr";
    public static final String EXTRA_SERVICIO = "taxiServicioQr";
    public static final String EXTRA_CAMARA_NO_DISPONIBLE = "camaraNoDisponibleQr";
    public static final String EXTRA_CONTEXTO_INVALIDO = "contextoInvalidoQr";

    private CaptureManager captura;
    private DecoratedBarcodeView visor;
    private ContextoCamara contexto;

    /** La vinculación al mismo login permanece solo en RAM, también al rotar. */
    public static class ContextoCamara extends ViewModel {
        private String tokenSesion;
    }

    @Override
    protected void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        contexto = new ViewModelProvider(this).get(ContextoCamara.class);
        if (estado != null && contexto.tokenSesion == null) {
            cancelar(EXTRA_CONTEXTO_INVALIDO);
            return;
        }
        if (!puedeCapturar()) return;
        try {
            getIntent().setAction(Intents.Scan.ACTION);
            getIntent().putExtra(Intents.Scan.FORMATS, "QR_CODE");
            getIntent().putExtra(Intents.Scan.BEEP_ENABLED, false);
            getIntent().putExtra(Intents.Scan.BARCODE_IMAGE_ENABLED, false);
            setContentView(com.google.zxing.client.android.R.layout.zxing_capture);
            visor = findViewById(com.google.zxing.client.android.R.id.zxing_barcode_scanner);
            captura = new CaptureManager(this, visor);
            captura.initializeFromIntent(getIntent(), estado);
            captura.setShowMissingCameraPermissionDialog(false);
            captura.decode();
        } catch (RuntimeException error) {
            cancelar(EXTRA_CAMARA_NO_DISPONIBLE);
        }
    }

    private boolean puedeCapturar() {
        boolean permisoConcedido = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        PreferenciasPermisos.desde(this).sincronizar(PreferenciasPermisos.Permiso.CAMARA, permisoConcedido);
        if (!permisoConcedido) {
            cancelar(Intents.Scan.MISSING_CAMERA_PERMISSION);
            return false;
        }
        Cuenta cuenta = SessionManager.getCuenta();
        Cuenta autorizada = SesionTaxistaRest.getCuenta();
        String tokenActual = SesionTaxistaRest.getToken();
        Intent inicio = getIntent();
        String identidad = inicio == null ? null : inicio.getStringExtra(EXTRA_IDENTIDAD);
        String servicio = inicio == null ? null : inicio.getStringExtra(EXTRA_SERVICIO);
        if (cuenta == null || cuenta.getRol() != Role.CONDUCTOR || autorizada == null
                || identidad == null || !identidad.equals(cuenta.getIdentidadId())
                || !identidad.equals(autorizada.getIdentidadId())
                || tokenActual == null
                || (contexto.tokenSesion != null && !contexto.tokenSesion.equals(tokenActual))
                || SessionManager.getConductorId() == null
                || servicio == null || !servicio.matches("[1-9][0-9]*")) {
            cancelar(EXTRA_CONTEXTO_INVALIDO);
            return false;
        }
        if (contexto.tokenSesion == null) contexto.tokenSesion = tokenActual;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            cancelar(EXTRA_CAMARA_NO_DISPONIBLE);
            return false;
        }
        return true;
    }

    private void cancelar(@NonNull String motivo) {
        setResult(RESULT_CANCELED, new Intent(Intents.Scan.ACTION).putExtra(motivo, true));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // La hoja es la única que solicita CAMERA. Se reanuda el visor
        // directamente porque CaptureManager.onResume pediría permisos.
        if (isFinishing() || captura == null || !puedeCapturar()) return;
        try {
            visor.resume();
        } catch (RuntimeException error) {
            cancelar(EXTRA_CAMARA_NO_DISPONIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (captura != null) captura.onPause();
    }

    @Override
    protected void onDestroy() {
        if (captura != null) captura.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        if (captura != null) captura.onSaveInstanceState(estado);
    }

    @Override
    public boolean onKeyDown(int tecla, KeyEvent evento) {
        return (visor != null && visor.onKeyDown(tecla, evento)) || super.onKeyDown(tecla, evento);
    }
}
