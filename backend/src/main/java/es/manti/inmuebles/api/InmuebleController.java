package es.manti.inmuebles.api;

import es.manti.inmuebles.api.dto.InmuebleMapper;
import es.manti.inmuebles.api.dto.InmueblePage;
import es.manti.inmuebles.api.dto.InmuebleResponse;
import es.manti.inmuebles.application.BusquedaInmuebleService;
import es.manti.inmuebles.application.BusquedaInmueblesCommand;
import es.manti.inmuebles.application.ListadoInmuebles;
import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Lectura PÚBLICA de inmuebles (permitAll en SecurityConfig, sólo GET).
 * Las escrituras viven en {@link AdminInmuebleController} (rol ADMIN).
 *
 * <p>Caché: {@code Cache-Control: public, max-age=60} en listado y detalle —
 * son recursos anónimos y casi estáticos; CDN/proxy pueden reutilizarlos un
 * minuto. Los escritos del backoffice no llevan caché.</p>
 */
@Tag(name = "Inmuebles", description = "Listado y detalle público de inmuebles (sin autenticación)")
@RestController
@RequestMapping("/api/v1/inmuebles")
@Validated
@RequiredArgsConstructor
public class InmuebleController {

    /** 60 s público para listado y detalle (ver javadoc de la clase). */
    private static final CacheControl CACHE_PUBLICO = CacheControl
            .maxAge(Duration.ofSeconds(60)).cachePublic();

    private final BusquedaInmuebleService service;
    private final InmuebleMapper mapper;

    @GetMapping
    @Operation(summary = "Lista inmuebles con filtros y paginación por cursor",
            description = "Orden por defecto `created_at DESC, id DESC` (o `sort=precio_asc|precio_desc`). "
                    + "La paginación es keyset: pase `nextCursor` como `cursor` para la página siguiente. "
                    + "`limit` se acota a 1..50 (20 por defecto).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de inmuebles"),
            @ApiResponse(responseCode = "400", description = "Cursor, sort o parámetro inválido (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @SecurityRequirements   // endpoint público: security: [] en la spec
    public ResponseEntity<InmueblePage> listar(
            @Parameter(description = "Estado comercial (VENTA o ALQUILER)")
            @RequestParam(required = false) EstadoInmueble estado,
            @Parameter(description = "Tipo de inmueble (PISO, CASA, ATICO, DUPLEX, ESTUDIO, LOCAL, TERRENO)")
            @RequestParam(required = false) TipoInmueble tipo,
            @Parameter(description = "Municipio, coincidencia parcial e insensible a mayúsculas")
            @RequestParam(required = false) String municipio,
            @Parameter(description = "Precio mínimo en euros")
            @RequestParam(required = false) BigDecimal precioMin,
            @Parameter(description = "Precio máximo en euros")
            @RequestParam(required = false) BigDecimal precioMax,
            @Parameter(description = "Número mínimo de habitaciones")
            @RequestParam(required = false) Short habitacionesMin,
            @Parameter(description = "Sólo destacados (true) o sólo no destacados (false)")
            @RequestParam(required = false) Boolean destacado,
            @Parameter(description = "Cursor opaco de la página anterior (base64); ausente en la primera página")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Orden: precio_asc o precio_desc (por defecto, más recientes)")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Tamaño de página, 1..50 (20 por defecto)")
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        ListadoInmuebles listado = service.listar(new BusquedaInmueblesCommand(
                estado, tipo, municipio, precioMin, precioMax, habitacionesMin, destacado,
                sort, cursor, limit));
        return ResponseEntity.ok().cacheControl(CACHE_PUBLICO).body(mapper.toPage(listado));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle completo de un inmueble",
            description = "404 si no existe o si fue eliminado (soft-delete).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inmueble encontrado"),
            @ApiResponse(responseCode = "404", description = "Inexistente o eliminado (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @SecurityRequirements   // endpoint público: security: [] en la spec
    public ResponseEntity<InmuebleResponse> detalle(
            @Parameter(description = "Identificador del inmueble", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CACHE_PUBLICO)
                .body(mapper.toResponse(service.detalle(id)));
    }
}
