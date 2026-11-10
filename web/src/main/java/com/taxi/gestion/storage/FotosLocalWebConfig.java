package com.taxi.gestion.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve la carpeta local en /fotos/** (solo cuando el almacenamiento es "local"). */
@Configuration
@ConditionalOnProperty(name = "app.almacenamiento.tipo", havingValue = "local", matchIfMissing = true)
public class FotosLocalWebConfig implements WebMvcConfigurer {

    private final AlmacenamientoLocal almacenamiento;

    public FotosLocalWebConfig(AlmacenamientoLocal almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = almacenamiento.raiz().toUri().toString();
        if (!ubicacion.endsWith("/")) {
            ubicacion += "/";
        }
        registry.addResourceHandler("/fotos/**").addResourceLocations(ubicacion);
    }
}
