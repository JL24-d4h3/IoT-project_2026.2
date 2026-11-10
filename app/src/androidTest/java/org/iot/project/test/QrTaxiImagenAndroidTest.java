package org.iot.project.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.iot.project.ui.components.QrView;
import org.iot.project.utils.CodigoQrTaxi;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Decodifica la imagen que genera el componente real del cliente, sin cámara ni backend. */
@RunWith(AndroidJUnit4.class)
public final class QrTaxiImagenAndroidTest {
    @Test public void elQrQueMuestraElClienteConservaElTokenDeCierre() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String token = "QR-0123456789AB";
        Bitmap[] imagen = new Bitmap[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            QrView vista = new QrView(contexto);
            assertTrue(vista.setContenido(token));
            assertTrue(vista.getDrawable() instanceof BitmapDrawable);
            imagen[0] = ((BitmapDrawable) vista.getDrawable()).getBitmap();
        });
        Bitmap bitmap = imagen[0];
        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();
        int[] pixeles = new int[ancho * alto];
        bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto);
        BinaryBitmap lectura = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(ancho, alto, pixeles)));
        Result resultado = new QRCodeReader().decode(lectura);
        assertEquals(BarcodeFormat.QR_CODE, resultado.getBarcodeFormat());
        assertEquals(token, resultado.getText());
        assertEquals(token, CodigoQrTaxi.normalizarEscaneado(resultado.getText()));
    }
}
