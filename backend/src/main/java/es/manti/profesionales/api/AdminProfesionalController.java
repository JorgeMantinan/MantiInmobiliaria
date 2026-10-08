package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.ProfesionalAdminResponse;
import es.manti.profesionales.api.dto.ProfesionalEstadoRequest;
import es.manti.profesionales.api.dto.ProfesionalEstadoResponse;
import es.manti.profesionales.api.dto.ProfesionalMapper;
import es.manti.profesionales.application.GestionProfesionalService;
import es.manti.profesionales.domain.EstadoProfesional;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backoffice de profesionales (rol ADMIN vía {@code @PreAuthorize} en clase).
 * Las rutas NO se abren en {@code SecurityConfig}: un anónimo recibe 401 en la
 * cadena de seguridad y un USER sin rol, 403 aquí.
 *
 * <p><b>TODO (MVP):</b> el listado no está paginado y devuelve todos los
 * profesionales del estado pedido; cuando el volumen lo exija se añadirá
 * {@code page}/{@code size} igual que en el resto de backoffice.</p>
 */
@Tag(name = "Profesionales (admin)",
        description = "Moderación de altas de profesionales (requiere rol ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/profesionales")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProfesionalController {

    private final GestionProfesionalService service;
    private final ProfesionalMapper mapper;

    @GetMapping
    @Operation(operationId = "listarProfesionales",
            summary = "Lista profesionales por estado",
            description = "Lista simple SIN paginación (MVP): devuelve todos los profesionales del "
                    + "estado indicado, más recientes primero, con sus gremios en `slug`. "
                    + "Si se omite `estado`, devuelve todos.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de profesionales (array)"),
            @ApiResponse(responseCode = "400", description = "`estado` no admitido "
                    + "(PENDIENTE, ACTIVO o RECHAZADO): ProblemDetail con errors[]",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<List<ProfesionalAdminResponse>> listar(
            @Parameter(description = "Estado a filtrar (PENDIENTE, ACTIVO o RECHAZADO); "
                    + "si se omite, todos")
            @RequestParam(required = false) EstadoProfesional estado) {
        return ResponseEntity.ok(mapper.toAdminResponseList(service.listar(estado)));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "actualizarEstadoProfesional",
            summary = "Cambia el estado de un profesional",
            description = "Sólo admite `ACTIVO` (puede recibir solicitudes) o `RECHAZADO`. "
                    + "Un profesional `PENDIENTE` aprobado pasa a `ACTIVO`; desde `ACTIVO` ya no "
                    + "vuelve a `PENDIENTE`.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado: {id, estado}"),
            @ApiResponse(responseCode = "400", description = "Validación fallida: el estado debe ser "
                    + "ACTIVO o RECHAZADO (ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profesional inexistente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<ProfesionalEstadoResponse> cambiarEstado(
            @Parameter(description = "Identificador del profesional", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ProfesionalEstadoRequest request) {
        return ResponseEntity.ok(mapper.toEstadoResponse(
                service.cambiarEstado(id, EstadoProfesional.valueOf(request.estado()))));
    }
}
