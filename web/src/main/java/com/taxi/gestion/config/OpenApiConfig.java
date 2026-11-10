package com.taxi.gestion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Metadatos de la documentacion publicada en /swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiTaxistas() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de gestion de taxistas")
                        .version("1.0.0")
                        .description("""
                                API REST consumida por la aplicacion movil nativa (Android).

                                Flujo del servicio de taxi:
                                SOLICITADO -> ASIGNADO -> EN_CAMINO -> EN_TRASLADO -> FINALIZADO

                                El paso a FINALIZADO exige el codigo QR entregado al crear el servicio.
                                Ninguna respuesta incluye la contrasena ni el documento completo del taxista.

                                Autenticacion: POST /api/auth/login devuelve un token que se envia
                                como 'Authorization: Bearer <token>' en los endpoints marcados con
                                candado (reportar ubicacion, editar datos propios, aceptar o avanzar
                                un servicio, ver el propio historial).
                                """)
                        .contact(new Contact().name("Equipo de plataforma").email("dev@taxiapp.pe"))
                        .license(new License().name("Uso interno")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desarrollo local")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("firebaseAdminAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Firebase ID token")
                                .description("Token Firebase del superadministrador cuyo UID este autorizado en el servidor")));
    }
}
