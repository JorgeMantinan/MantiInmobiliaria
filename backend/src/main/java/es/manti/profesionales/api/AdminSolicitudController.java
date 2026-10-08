package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.SolicitudAdminResponse;
import es.manti.profesionales.api.dto.SolicitudMapper;
import es.manti.profesionales.application.SolicitudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backoffice de solicitudes (rol ADMIN vía {@code @PreAuthorize} en clase).
 *
 * <p><b>TODO (MVP):</b> devuelve las últimas 50 sin paginación ni filtro de
 * estado; la paginación y el cierre de solicitudes llegan en una fase
 * posterior.</p>
 */
@Tag(name = "Solicitudes (admin)", description = "Listado de solicitudes recibidas (requiere rol ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/solicitudes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSolicitudController {

    /** Tamaño fijo del listado (MVP sin paginación). */
    private static final int ULTIMAS = 50;

    private final SolicitudService service;
    private final SolicitudMapper mapper;

    @GetMapping
    @Operation(operationId = "listarSolicitudes",
            summary = "Lista las últimas solicitudes",
            description = "Devuelve las 50 solicitudes más recientes (array plano, sin paginación — "
                    + "MVP) con sus gremios pedidos en `slug`.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitudes más recientes (array, máx. 50)"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<List<SolicitudAdminResponse>> listar() {
        return ResponseEntity.ok(mapper.toAdminResponseList(service.listarUltimas(ULTIMAS)));
    }
}
