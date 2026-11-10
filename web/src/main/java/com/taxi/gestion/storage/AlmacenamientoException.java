package com.taxi.gestion.storage;

/** Fallo tecnico al guardar una imagen (disco lleno, Cloudinary caido, etc.). */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
