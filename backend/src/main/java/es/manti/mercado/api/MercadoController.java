package es.manti.mercado.api;

import es.manti.mercado.api.dto.MercadoMapper;
import es.manti.mercado.api.dto.MercadoResponse;
import es.manti.mercado.application.MercadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Lectura PÚBLICA de precios oficiales de la zona (issue #12).
 *
 * <p>{@code permitAll} en SecurityConfig para GET {@code /api/v1/mercado}: son
 * datos abiertos del Ministerio. La reimportación vive en
 * {@link AdminMercadoController} y exige rol ADMIN.</p>
 *
 * <p>Caché: 1 hora pública + {@code Vary: Origin} — el dato cambia una vez al
 * año, igual razonamiento que en el portal de inmuebles.</p>
 */
@Tag(name = "Mercado", description = "Precios oficiales del mercado por zona (fuente: Ministerio de Vivienda y Agenda Urbana)")
@RestController
@RequestMapping("/api/v1/mercado")
@Validated
@RequiredArgsConstructor
public class MercadoController {

    private static final CacheControl CACHE_PUBLICO = CacheControl
            .maxAge(Duration.ofHours(1)).cachePublic();

    private final MercadoService service;
    private final MercadoMapper mapper;

    @GetMapping
    @Operation(summary = "Obtiene el alquiler medio y el valor tasado de la zona de un municipio",
            description = "Alquiler municipal (SERPAVI) del último año disponible + valor tasado de la "
                    + "provincia. Si el municipio no tiene NINGÚN dato publicado responde 404. "
                    + "La respuesta incluye siempre la atribución legal de la fuente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Precios de la zona (los bloques alquiler "
                    + "y venta pueden ser null de forma independiente)"),
            @ApiResponse(responseCode = "400", description = "codigoINE no es un código de 5 dígitos "
                    + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Sin datos publicados para ese municipio "
                    + "(ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @SecurityRequirements   // endpoint público: security: [] en la spec
    public ResponseEntity<MercadoResponse> consultar(
            @Parameter(description = "Código INE de 5 dígitos del municipio")
            @RequestParam(name = "codigoINE")
            // Sin anclas: Matcher.matches() ya exige coincidencia completa.
            @Pattern(regexp = "[0-9]{5}", message = "debe ser un código INE de 5 dígitos")
            String codigoIne) {
        return ResponseEntity.ok()
                .cacheControl(CACHE_PUBLICO)
                .varyBy(HttpHeaders.ORIGIN)
                .body(mapper.toResponse(service.consultar(codigoIne)));
    }
}
