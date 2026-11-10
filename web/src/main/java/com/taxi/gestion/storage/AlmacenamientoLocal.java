package com.taxi.gestion.storage;

import com.taxi.gestion.exception.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda las fotos en una carpeta del disco. Pensado solo para desarrollo:
 * en contenedores el disco es efimero y las fotos se perderian al redesplegar.
 * Para produccion se usa {@link AlmacenamientoCloudinary}.
 */
@Component
@ConditionalOnProperty(name = "app.almacenamiento.tipo", havingValue = "local", matchIfMissing = true)
public class AlmacenamientoLocal implements AlmacenamientoFotos {

    private final Path raiz;
    private final String urlBase;
    private final ValidadorImagen validador;

    public AlmacenamientoLocal(@Value("${app.almacenamiento.local.directorio}") String directorio,
                               @Value("${app.almacenamiento.local.url-base}") String urlBase,
                               ValidadorImagen validador) {
        this.raiz = Paths.get(directorio).toAbsolutePath().normalize();
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.validador = validador;
    }

    @PostConstruct
    void crearDirectorioRaiz() throws IOException {
        Files.createDirectories(raiz);
    }

    /** Carpeta fisica desde la que se sirven las fotos en /fotos/**. */
    public Path raiz() {
        return raiz;
    }

    @Override
    public String guardar(MultipartFile archivo, String carpeta) {
        String extension = validador.extensionDe(archivo);
        if (extension == null) {
            throw new ReglaNegocioException("El archivo no es una imagen JPG o PNG valida");
        }
        Path destinoDir = raiz.resolve(carpeta).normalize();
        if (!destinoDir.startsWith(raiz)) {
            throw new IllegalArgumentException("Carpeta de destino invalida");
        }
        // Nombre generado por el servidor: el nombre que envia el cliente nunca se usa.
        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(destinoDir);
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destinoDir.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new AlmacenamientoException("No se pudo guardar la imagen en disco", ex);
        }
        return urlBase + "/fotos/" + carpeta + "/" + nombre;
    }
}
