package com.taxi.gestion.storage;

import com.taxi.gestion.exception.ReglaNegocioException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Valida que lo subido sea realmente una imagen JPG o PNG de hasta 5 MB.
 * El tipo se detecta por los primeros bytes del archivo (firma), no por la
 * extension ni por el Content-Type que declara el navegador, que son falsificables.
 */
@Component
public class ValidadorImagen {

    public static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final byte[] FIRMA_PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * @param etiqueta nombre legible para el mensaje, p. ej. "La foto del taxista"
     * @throws ReglaNegocioException si falta, pesa de mas o no es JPG/PNG
     */
    public void validar(MultipartFile archivo, String etiqueta) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException(etiqueta + " es obligatoria");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new ReglaNegocioException(etiqueta + " pesa mas de 5 MB");
        }
        if (extensionDe(archivo) == null) {
            throw new ReglaNegocioException(etiqueta + " debe ser una imagen JPG o PNG");
        }
    }

    /** "jpg" o "png" segun el contenido real del archivo; null si no es ninguno. */
    public String extensionDe(MultipartFile archivo) {
        byte[] cabecera;
        try (InputStream in = archivo.getInputStream()) {
            cabecera = in.readNBytes(FIRMA_PNG.length);
        } catch (IOException ex) {
            return null;
        }
        if (cabecera.length >= 3
                && cabecera[0] == (byte) 0xFF
                && cabecera[1] == (byte) 0xD8
                && cabecera[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (cabecera.length >= FIRMA_PNG.length && empiezaCon(cabecera, FIRMA_PNG)) {
            return "png";
        }
        return null;
    }

    private boolean empiezaCon(byte[] datos, byte[] firma) {
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
