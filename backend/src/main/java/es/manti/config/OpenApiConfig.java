package es.manti.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuración global de springdoc (spec + esquema de seguridad bearer). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI mantiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MantiInmobiliaria API")
                        .version("v1")
                        .description("API del portal inmobiliario MantiInmobiliaria"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token JWT (HS256, 15 min) devuelto por /api/v1/auth/login")));
    }
}
