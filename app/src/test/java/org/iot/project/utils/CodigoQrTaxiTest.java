package org.iot.project.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;

import java.util.Locale;

public class CodigoQrTaxiTest {
    @Test
    public void aceptaTokenBackendYNormalizaEspaciosYMinusculas() {
        assertEquals("QR-012345ABCDEF", CodigoQrTaxi.normalizarEscaneado("QR-012345ABCDEF"));
        assertEquals("QR-012345ABCDEF", CodigoQrTaxi.normalizarEscaneado("  qr-012345abcdef  "));
        assertEquals("QR-A0B1C2D3E4F5", CodigoQrTaxi.normalizarEscaneado(" qR-a0B1c2D3e4F5 "));
    }

    @Test
    public void normalizacionNoDependeDelIdiomaDelTelefono() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("QR-ABCDEF012345", CodigoQrTaxi.normalizarEscaneado("qr-abcdef012345"));
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    public void rechazaAusenciaDeContenido() {
        rechaza(null);
        rechaza("");
        rechaza("   ");
    }

    @Test
    public void exigePrefijoLongitudExactaYHexadecimal() {
        for (String valor : new String[]{"QR-012345ABCDE", "QR-012345ABCDEF0", "QR-012345ABCDEG",
                "012345ABCDEF", "TAX-012345ABCDEF", "QR012345ABCDEF", "QR-012345 ABCDE",
                "QR-012345ABCDEF QR-012345ABCDEF"}) {
            rechaza(valor);
        }
    }

    @Test
    public void noExtraeTokensDeEnlacesJsonOTextoAjeno() {
        for (String valor : new String[]{"https://example.test/QR-012345ABCDEF",
                "https://example.test?codigo=QR-012345ABCDEF", "taxi://QR-012345ABCDEF",
                "{\"codigoQr\":\"QR-012345ABCDEF\"}", "[\"QR-012345ABCDEF\"]",
                "Código del servicio: QR-012345ABCDEF", "<qr>QR-012345ABCDEF</qr>",
                "javascript:alert('QR-012345ABCDEF')"}) {
            rechaza(valor);
        }
    }

    @Test
    public void rechazaControlesInclusoOcultosEnLosExtremos() {
        for (String valor : new String[]{"\nQR-012345ABCDEF", "QR-012345ABCDEF\r", "\tQR-012345ABCDEF",
                "QR-012345\nABCDEF", "QR-012345ABCDEF\u0000", "QR-012345ABCDEF\u007f"}) {
            rechaza(valor);
        }
    }

    @Test
    public void rechazaSimbolosParecidosYExpansionesUnicode() {
        for (String valor : new String[]{"QR–012345ABCDEF", "ＱＲ-012345ABCDEF",
                "QR-０12345ABCDEF", "QR-012345ABCDEＦ", "QR-012345ABCDﬀ", "QR-012345ABCDEF\u200b"}) {
            rechaza(valor);
        }
    }

    @Test
    public void imagenZxingSeDecodificaAlMismoTokenGeneradoPorBackend() throws Exception {
        String token = "QR-29F0C6E8A1B3";
        String leido = decodificarImagen(token);
        assertEquals(token, leido);
        assertEquals(token, CodigoQrTaxi.normalizarEscaneado(leido));
    }

    @Test
    public void decodificarUnaImagenNoAutorizaUnQrDeOtraAplicacion() throws Exception {
        String payload = "https://example.test/QR-29F0C6E8A1B3";
        assertEquals(payload, decodificarImagen(payload));
        rechaza(decodificarImagen(payload));
    }

    private static String decodificarImagen(String contenido) throws Exception {
        BitMatrix imagen = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 320, 320);
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int[] pixeles = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                pixeles[y * ancho + x] = imagen.get(x, y) ? 0xff000000 : 0xffffffff;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(ancho, alto, pixeles)));
        Result resultado = new QRCodeReader().decode(bitmap);
        assertEquals(BarcodeFormat.QR_CODE, resultado.getBarcodeFormat());
        return resultado.getText();
    }

    private static void rechaza(String contenido) {
        try {
            CodigoQrTaxi.normalizarEscaneado(contenido);
            fail("El lector no debe aceptar un contenido ajeno al contrato del taxi.");
        } catch (IllegalArgumentException esperado) {
            // El mensaje explica cómo continuar sin reproducir contenido arbitrario del QR.
            assertFalse(esperado.getMessage().isEmpty());
            if (contenido != null && contenido.length() > 5) {
                assertFalse(esperado.getMessage().contains(contenido));
            }
        }
    }
}
