package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.GremioMapper;
import es.manti.profesionales.api.dto.GremioResponse;
import es.manti.profesionales.application.GremioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Catálogo PÚBLICO de gremios ({@code GET /api/v1/gremios}, permitAll en
 * {@code SecurityConfig}). Es estático (12 oficios), así que sale con caché de
 * 60 s y {@code Vary: Origin}, igual que el listado de inmuebles.
 */
@Tag(name = "Gremios", description = "Catálogo público de oficios (sin autenticación)")
@RestController
@RequestMapping("/api/v1/gremios")
@RequiredArgsConstructor
public class GremioController {

    /** 60 s público: el catálogo cambia sólo con una migración. */
    private static final CacheControl CACHE_PUBLICO = CacheControl
            .maxAge(Duration.ofSeconds(60)).cachePublic();

    private final GremioService service;
    private final GremioMapper mapper;

    @GetMapping
    @Operation(operationId = "listarGremios",
            summary = "Lista el catálogo de gremios",
            description = "Devuelve todos los oficios disponibles con su `slug` (identificador que "
                    + "los formularios envían en `gremios[]`) y su `nombre`. Recurso estático: "
                    + "`Cache-Control: public, max-age=60`.")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo completo (array de {slug, nombre})")})
    public ResponseEntity<List<GremioResponse>> listar() {
        return ResponseEntity.ok()
                .cacheControl(CACHE_PUBLICO)
                .varyBy(HttpHeaders.ORIGIN)
                .body(mapper.toResponseList(service.listar()));
    }
}
