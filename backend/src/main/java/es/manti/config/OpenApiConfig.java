package es.manti.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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

    /**
     * Declara en el schema {@code ProblemDetail} el array {@code errors} que la
     * API emite en los 400 (validación de bodies, parámetros de búsqueda,
     * type-mismatch de query params). Spring no lo infiere: sin este customizer
     * la spec no menciona {@code errors} y el gate de documentación miente.
     *
     * <p>{@code message} es obligatorio en todo error; {@code field} es
     * opcional porque la validación de parámetros de método
     * ({@code HandlerMethodValidationException}) sólo puede reportar el mensaje.
     * El esquema se enriquece post-generación, sin tocar las anotaciones de los
     * controllers.</p>
     */
    @Bean
    OpenApiCustomizer problemDetailConErrores() {
        return openApi -> {
            Map<String, Schema> esquemas = openApi.getComponents() == null
                    ? null
                    : openApi.getComponents().getSchemas();
            if (esquemas == null) {
                return;
            }
            Schema problemDetail = esquemas.get("ProblemDetail");
            if (problemDetail == null) {
                return;
            }

            ObjectSchema error = new ObjectSchema();
            error.description("Error individual");
            error.addProperty("field",
                    new StringSchema().description("Campo o parámetro que falló (p. ej. precioMin, estado); "
                            + "ausente cuando sólo procede el mensaje"));
            error.addProperty("message", new StringSchema()
                    .description("Motivo fijo del error, sin datos internos de la petición"));
            error.setRequired(List.of("message"));

            ArraySchema errores = new ArraySchema();
            errores.description("Errores campo/mensaje; presente en las respuestas 400");
            errores.items(error);

            problemDetail.addProperty("errors", errores);
        };
    }

    /**
     * Documenta los límites de petición que emiten los filtros GLOBALES (no
     * un controller, luego no aparecen en las anotaciones):
     * <ul>
     *   <li><b>413</b> en toda operación con {@code request body}: la emite
     *       {@code BodySizeLimitFilter} cuando {@code Content-Length} o el
     *       stream contado (chunked) superan los 256 KiB;</li>
     *   <li><b>Retry-After</b> en las respuestas <b>429</b> existentes: los
     *       segundos que el {@code AuthRateLimitFilter} recomienda esperar.</li>
     * </ul>
     */
    @Bean
    OpenApiCustomizer limitesDePeticion() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((ruta, item) ->
                    item.readOperationsMap().forEach((metodo, operacion) -> {
                        Map<String, ApiResponse> respuestas = operacion.getResponses();
                        if (respuestas == null) {
                            return;
                        }
                        if (operacion.getRequestBody() != null && !respuestas.containsKey("413")) {
                            respuestas.put("413", respuestaProblemDetail(
                                    "Cuerpo de la petición superior a 256 KiB (ProblemDetail)"));
                        }
                        ApiResponse respuesta429 = respuestas.get("429");
                        if (respuesta429 != null && (respuesta429.getHeaders() == null
                                || !respuesta429.getHeaders().containsKey("Retry-After"))) {
                            respuesta429.addHeaderObject("Retry-After", new Header()
                                    .description("Segundos hasta liberar una petición en la ventana de rate limit")
                                    .schema(new StringSchema()));
                        }
                    }));
        };
    }

    private static ApiResponse respuestaProblemDetail(String descripcion) {
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("*/*",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
