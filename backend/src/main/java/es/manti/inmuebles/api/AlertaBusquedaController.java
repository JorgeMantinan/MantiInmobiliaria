package es.manti.inmuebles.api;

import es.manti.inmuebles.api.dto.AlertaCreateRequest;
import es.manti.inmuebles.api.dto.AlertaActualizarRequest;
import es.manti.inmuebles.api.dto.AlertaMapper;
import es.manti.inmuebles.api.dto.AlertaPage;
import es.manti.inmuebles.api.dto.AlertaResponse;
import es.manti.inmuebles.application.AlertaBusquedaService;
import es.manti.inmuebles.domain.AlertaBusqueda;
import es.manti.inmuebles.domain.SesionInvalidaException;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * CRUD de alertas de búsqueda del usuario autenticado (issue #96).
 *
 * <p><b>Owner-scoped desde el Bearer</b>: el usuario sale SIEMPRE del
 * {@code sub} del JWT (nunca de la ruta ni del body), de modo que cada
 * operación sólo toca filas de su propio {@code usuario_id}. No hay ningún
 * {@code permitAll} nuevo: las cuatro rutas caen en
 * {@code .anyRequest().authenticated()} de {@code SecurityConfig}, así que un
 * anónimo recibe 401 en la cadena de seguridad antes de llegar aquí, y una
 * alerta ajena responde 404 (nunca revela su existencia con 403).</p>
 */
@Tag(name = "Alertas",
        description = "Alertas de búsqueda del usuario: guardar los filtros del listado y "
                + "recibir aviso de los inmuebles nuevos que encajan")
@RestController
@RequestMapping("/api/v1/alertas")
@RequiredArgsConstructor
public class AlertaBusquedaController {

    private final AlertaBusquedaService service;
    private final AlertaMapper mapper;

    @GetMapping
    @Operation(operationId = "listarAlertas",
            summary = "Lista tus alertas de búsqueda",
            description = "Alertas del usuario autenticado, de más reciente a más antigua, "
                    + "en envelope `content` + `totalElements` (igual que el resto de "
                    + "listados; `content` vacío si no tiene ninguna). Sin paginación: el "
                    + "techo por usuario es de 50 alertas.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope con las alertas del usuario"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AlertaPage> listar(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        List<AlertaResponse> contenido = mapper.toResponseList(service.listar(usuarioId(jwt)));
        return ResponseEntity.ok(new AlertaPage(contenido, contenido.size()));
    }

    @PostMapping
    @Operation(operationId = "crearAlerta",
            summary = "Crea una alerta con los filtros del listado",
            description = "Alta con los MISMOS filtros que `GET /api/v1/inmuebles`: un rango "
                    + "inválido (precioMin > precioMax, precios negativos, habitaciones < 0) "
                    + "responde 400 igual que el listado. La alerta nace ACTIVA y con su marca "
                    + "de agua en el mayor id de inmueble existente, así que sólo se avisará de "
                    + "los inmuebles que entren DESPUÉS de guardarla (máximo 50 alertas por "
                    + "usuario).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alerta creada (cabecera Location)",
                    content = @Content(schema = @Schema(implementation = AlertaResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Validación fallida o rango de filtros inválido "
                            + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Máximo de alertas por usuario alcanzado (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiadas peticiones (rate limit por IP)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AlertaResponse> crear(
            @Valid @RequestBody AlertaCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        AlertaBusqueda creada = service.crear(usuarioId(jwt), mapper.toDatos(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/alertas/" + creada.getId()))
                .body(mapper.toResponse(creada));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "actualizarAlerta",
            summary = "Actualiza el nombre y/o la actividad de una alerta",
            description = "Edición PARCIAL: los campos ausentes no cambian. Los filtros no se "
                    + "editan (para cambiarlos, crea otra alerta). 404 si la alerta no existe o "
                    + "NO es del usuario actual.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta actualizada",
                    content = @Content(schema = @Schema(implementation = AlertaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validación fallida (ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Alerta inexistente o de otro usuario (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiadas peticiones (rate limit por IP)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<AlertaResponse> actualizar(
            @Parameter(description = "Identificador de la alerta", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AlertaActualizarRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toResponse(
                service.actualizar(usuarioId(jwt), id, request.nombre(), request.activa())));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "eliminarAlerta",
            summary = "Elimina una alerta",
            description = "Borra la alerta y sus avisos pendientes en cascada. 404 si la alerta "
                    + "no existe o NO es del usuario actual.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alerta eliminada (sin cuerpo)"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Alerta inexistente o de otro usuario (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiadas peticiones (rate limit por IP)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "Identificador de la alerta", example = "1")
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        service.eliminar(usuarioId(jwt), id);
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
