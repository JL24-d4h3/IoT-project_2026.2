package com.taxi.gestion.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.taxi.gestion.exception.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Sube las fotos a Cloudinary y devuelve su URL https. Se activa con
 * {@code app.almacenamiento.tipo=cloudinary}. Las credenciales llegan por
 * variables de entorno: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET.
 */
@Component
@ConditionalOnProperty(name = "app.almacenamiento.tipo", havingValue = "cloudinary")
public class AlmacenamientoCloudinary implements AlmacenamientoFotos {

    private final Cloudinary cloudinary;
    private final String carpetaRaiz;
    private final ValidadorImagen validador;

    public AlmacenamientoCloudinary(
            @Value("${app.almacenamiento.cloudinary.cloud-name}") String cloudName,
            @Value("${app.almacenamiento.cloudinary.api-key}") String apiKey,
            @Value("${app.almacenamiento.cloudinary.api-secret}") String apiSecret,
            @Value("${app.almacenamiento.cloudinary.carpeta}") String carpetaRaiz,
            ValidadorImagen validador) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("Almacenamiento 'cloudinary' activo pero faltan las "
                    + "variables CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY o CLOUDINARY_API_SECRET");
        }
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
        this.carpetaRaiz = carpetaRaiz;
        this.validador = validador;
    }

    @Override
    public String guardar(MultipartFile archivo, String carpeta) {
        if (validador.extensionDe(archivo) == null) {
            throw new ReglaNegocioException("El archivo no es una imagen JPG o PNG valida");
        }
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(archivo.getBytes(),
                    ObjectUtils.asMap(
                            // La ruta va dentro del public_id: asi funciona igual en cuentas con
                            // carpetas fijas o dinamicas.
                            "public_id", carpetaRaiz + "/" + carpeta + "/" + UUID.randomUUID(),
                            "resource_type", "image",
                            "overwrite", false));
            return (String) resultado.get("secure_url");
        } catch (IOException ex) {
            throw new AlmacenamientoException("No se pudo subir la imagen a Cloudinary", ex);
        }
    }
}
