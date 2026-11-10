package com.taxi.gestion.storage;

import com.taxi.gestion.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorImagenTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    private final ValidadorImagen validador = new ValidadorImagen();

    private MockMultipartFile archivo(String nombre, String tipo, byte[] datos) {
        return new MockMultipartFile("foto", nombre, tipo, datos);
    }

    @Test
    @DisplayName("Acepta JPG y PNG reales")
    void aceptaJpgYPng() {
        assertDoesNotThrow(() -> validador.validar(archivo("a.jpg", "image/jpeg", JPEG), "La foto"));
        assertDoesNotThrow(() -> validador.validar(archivo("a.png", "image/png", PNG), "La foto"));
        assertEquals("jpg", validador.extensionDe(archivo("x", "x", JPEG)));
        assertEquals("png", validador.extensionDe(archivo("x", "x", PNG)));
    }

    @Test
    @DisplayName("Rechaza un archivo que solo dice ser imagen (texto renombrado a .jpg)")
    void rechazaFalsificado() {
        var falso = archivo("virus.jpg", "image/jpeg", "no soy una imagen".getBytes());
        assertThrows(ReglaNegocioException.class, () -> validador.validar(falso, "La foto"));
    }

    @Test
    @DisplayName("Rechaza vacio, ausente y mayor de 5 MB")
    void rechazaVacioAusenteYGrande() {
        assertThrows(ReglaNegocioException.class, () -> validador.validar(null, "La foto"));
        assertThrows(ReglaNegocioException.class,
                () -> validador.validar(archivo("a.jpg", "image/jpeg", new byte[0]), "La foto"));

        byte[] grande = new byte[(int) ValidadorImagen.TAMANO_MAXIMO_BYTES + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);
        assertThrows(ReglaNegocioException.class,
                () -> validador.validar(archivo("a.jpg", "image/jpeg", grande), "La foto"));
    }
}
