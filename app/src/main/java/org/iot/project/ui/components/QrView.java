package org.iot.project.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Codigo QR del servicio de taxi (§39, §69).
 *
 * <p>Es lo unico que cierra un servicio (RF-110, RT-016): no hay un boton de
 * "terminar", el conductor escanea este codigo. De ahi que §39 pida que sea
 * extremadamente facil de escanear, y que aqui el dibujo se haga con dos
 * cuidados que un ImageView normal no tiene:
 *
 * <ul>
 *   <li>Se codifica en blanco y negro puros, sin transparencia. Un QR con
 *       fondo translucido se lee mal o no se lee.
 *   <li>Se deja el margen de silencio alrededor del simbolo. Es parte de la
 *       especificacion del codigo, no un adorno: sin el, un lector que recorte
 *       por el borde pierde los modulos de las esquinas.
 * </ul>
 *
 * <p>La correccion de errores va en el nivel alto (H): el cliente enseña la
 * pantalla desde el asiento de atras, y el reflejo o un dedo por encima son
 * parte del uso normal.
 */
public class QrView extends AppCompatImageView {

    /** Lado del simbolo en pixeles. Es una imagen, no texto: no se escala con la fuente. */
    private static final int LADO_PX = 720;

    /** Modulos de margen alrededor del simbolo, como pide la especificacion. */
    private static final int MARGEN_MODULOS = 2;

    public QrView(@NonNull Context context) {
        this(context, null);
    }

    public QrView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QrView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // El simbolo se dibuja nítido o no sirve: escalarlo con suavizado
        // emborrona los modulos y el lector deja de distinguirlos.
        setScaleType(ScaleType.FIT_CENTER);
    }

    /**
     * Dibuja el QR del contenido indicado.
     *
     * <p>Si la codificacion falla se deja la vista sin imagen y se informa por
     * el valor de retorno: quien la usa decide que decir, porque el componente
     * no sabe si esta dentro de una hoja, de una pantalla o de un dialogo.
     *
     * @return true si el codigo quedo dibujado.
     */
    public boolean setContenido(@Nullable String contenido) {
        if (contenido == null || contenido.isEmpty()) {
            setImageDrawable(null);
            return false;
        }

        Map<EncodeHintType, Object> ajustes = new EnumMap<>(EncodeHintType.class);
        ajustes.put(EncodeHintType.MARGIN, MARGEN_MODULOS);
        ajustes.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        // El contenido es un identificador de servicio, siempre ASCII: fijar el
        // juego de caracteres evita que el resultado dependa del equipo.
        ajustes.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matriz = new MultiFormatWriter()
                    .encode(contenido, BarcodeFormat.QR_CODE, LADO_PX, LADO_PX, ajustes);
            setImageBitmap(aBitmap(matriz));
            return true;
        } catch (WriterException e) {
            setImageDrawable(null);
            return false;
        }
    }

    /**
     * Convierte la matriz de modulos en una imagen de dos colores.
     *
     * <p>Se arma pixel a pixel en vez de escalar un mapa de bits pequeño: asi
     * cada modulo cae en un bloque exacto de pixeles y no quedan bordes a medio
     * camino entre negro y blanco, que es lo que hace fallar a un lector.
     */
    private static Bitmap aBitmap(@NonNull BitMatrix matriz) {
        int ancho = matriz.getWidth();
        int alto = matriz.getHeight();
        int[] pixeles = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            int fila = y * ancho;
            for (int x = 0; x < ancho; x++) {
                pixeles[fila + x] = matriz.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap mapa = Bitmap.createBitmap(ancho, alto, Bitmap.Config.RGB_565);
        mapa.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto);
        return mapa;
    }
}
