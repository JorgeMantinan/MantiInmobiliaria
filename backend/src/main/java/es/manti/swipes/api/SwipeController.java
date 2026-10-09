package es.manti.swipes.api;

import es.manti.swipes.api.dto.SwipeMapper;
import es.manti.swipes.api.dto.SwipePage;
import es.manti.swipes.api.dto.SwipeRequest;
import es.manti.swipes.api.dto.SwipeResponse;
import es.manti.swipes.application.ListadoSwipe;
import es.manti.swipes.application.SwipeResultado;
import es.manti.swipes.application.SwipeService;
import es.manti.swipes.domain.SesionInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Swipes y favoritos del usuario autenticado.
 *
 * <p><b>Owner-scoped desde el Bearer:</b> el usuario sale SIEMPRE del
 * {@code sub} del JWT (nunca de la ruta ni del body), de modo que cada
 * operación sólo puede tocar filas de su propio {@code usuario_id}. No hay
 * ningún {@code permitAll} nuevo: las cuatro rutas caen en
 * {@code .anyRequest().authenticated()} de {@code SecurityConfig}, así que un
 * anónimo recibe 401 en la cadena de seguridad antes de llegar aquí.</p>
 */
@Tag(name = "Swipes", description = "Deck de swipes (like/nope) y favoritos del usuario autenticado")
@RestController
@RequestMapping("/api/v1/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService service;
    private final SwipeMapper mapper;

    @PostMapping
    @Operation(operationId = "registrarSwipe",
            summary = "Registra un like o nope sobre un inmueble",
            description = "Idempotente por DISEÑO (la constraint `uq_swipes_usuario_inmueble` "
                    + "garantiza una fila por pareja): 201 en la primera decisión; repetir la "
                    + "MISMA decisión → 200 sin crear fila; cambiar de opinión → 200 actualizando. "
                    + "No requiere cabecera `Idempotency-Key`: la unicidad natural es la "
                    + "idempotencia. Ante una carrera de inserción (doble tap del frontend) el "
                    + "servicio reintenta una vez en una transacción nueva y responde 200; sólo "
                    + "si la relectura sigue vacía devuelve 409. 404 si el inmueble no existe o "
                    + "está eliminado.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Primera decisión registrada"),
            @ApiResponse(responseCode = "200", description = "Repetición idempotente o cambio de decisión"),
            @ApiResponse(responseCode = "400", description = "Validación fallida (ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Inmueble inexistente o eliminado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto de unicidad no resuelto por el "
                    + "reintento (dos escrituras simultáneas sobre la misma pareja usuario+inmueble)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<SwipeResponse> registrar(
            @Valid @RequestBody SwipeRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        SwipeResultado resultado = service.registrar(
                usuarioId(jwt), request.inmuebleId(), mapper.toDecision(request.decision()));
        return resultado.creada()
                ? ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(resultado))
                : ResponseEntity.ok(mapper.toResponse(resultado));
    }

    @GetMapping("/deck")
    @Operation(summary = "Deck de inmuebles pendientes del usuario",
            description = "Inmuebles ACTIVOS que el usuario todavía no ha swipeado (LIKE ni NOPE), "
                    + "orden `created_at DESC, id DESC`. Paginación keyset: pase `nextCursor` como "
                    + "`cursor`; `limit` 1..50 (20 por defecto).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página del deck"),
            @ApiResponse(responseCode = "400", description = "Cursor inválido o `limit` fuera de rango "
                    + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<SwipePage> deck(
            @Parameter(description = "Cursor opaco de la página anterior (base64); ausente en la primera página")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Tamaño de página, 1..50 (20 por defecto)")
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toPage(service.deck(usuarioId(jwt), cursor, limit)));
    }

    @GetMapping("/favoritos")
    @Operation(summary = "Lista los favoritos (likes) del usuario",
            description = "Swipes con decisión LIKE, orden `swipe creado_en DESC, id DESC`. "
                    + "Paginación keyset: pase `nextCursor` como `cursor`; `limit` 1..50 "
                    + "(20 por defecto).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de favoritos"),
            @ApiResponse(responseCode = "400", description = "Cursor inválido o `limit` fuera de rango "
                    + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<SwipePage> favoritos(
            @Parameter(description = "Cursor opaco de la página anterior (base64); ausente en la primera página")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Tamaño de página, 1..50 (20 por defecto)")
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toPage(service.favoritos(usuarioId(jwt), cursor, limit)));
    }

    @DeleteMapping("/favoritos/{inmuebleId}")
    @Operation(summary = "Elimina un favorito",
            description = "Borra el swipe LIKE y el inmueble vuelve al deck. 404 si el USUARIO "
                    + "ACTUAL no tiene ese favorito (también si es favorito de otro usuario o si "
                    + "el swipe era un NOPE).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Favorito eliminado (sin cuerpo)"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No es un favorito del usuario actual",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> eliminarFavorito(
            @Parameter(description = "Identificador del inmueble", example = "42")
            @PathVariable Long inmuebleId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        service.eliminarFavorito(usuarioId(jwt), inmuebleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Usuario dueño de la petición, leído del {@code sub} del JWT: toda
     * operación queda acotada a su {@code usuario_id} (owner-scoped). El sub
     * lo emite este backend con el id numérico de usuarios; un valor no
     * parseable sólo puede venir de un token forjado con otra clave, así que se
     * responde 401 (nunca 500 y sin filtrar el detalle interno).
     */
    private static Long usuarioId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new SesionInvalidaException();
        }
    }
}
