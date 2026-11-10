package com.taxi.gestion.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Contrato unico para guardar fotos. El resto de la aplicacion solo conoce esta
 * interfaz; cual implementacion se usa (disco local o Cloudinary) se decide con
 * la propiedad {@code app.almacenamiento.tipo}, sin tocar codigo.
 */
public interface AlmacenamientoFotos {

    /**
     * Guarda una imagen ya validada y devuelve la URL con la que se mostrara.
     *
     * @param archivo imagen JPG o PNG
     * @param carpeta subcarpeta logica, definida por el sistema (p. ej. "taxistas")
     * @return URL absoluta que se guarda en la base de datos y se entrega a la app
     */
    String guardar(MultipartFile archivo, String carpeta);
}
