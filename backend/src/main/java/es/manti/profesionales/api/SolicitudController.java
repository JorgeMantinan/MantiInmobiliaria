package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.SolicitudCreateRequest;
import es.manti.profesionales.api.dto.SolicitudMapper;
import es.manti.profesionales.api.dto.SolicitudResponse;
import es.manti.profesionales.application.SolicitudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solicitud de servicio desde el portal ({@code POST /api/v1/solicitudes},
 * permitAll). Protegida por rate limit por IP (10/hora) y su outbox de
 * notificaciones se escribe en la MISMA transacción del alta.
 */
@Tag(name = "Solicitudes", description = "Solicitud de servicio de un usuario del portal (sin autenticación)")
@RestController
@RequestMapping("/api/v1/solicitudes")
@Validated
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService service;
    private final SolicitudMapper mapper;

    @PostMapping
    @Operation(operationId = "crearSolicitud",
            summary = "Crea una solicitud de servicio",
            description = "Alta anónima de una petición de trabajo (municipio, descripción y 1..8 "
                    + "gremios del catálogo). En la MISMA transacción se encolan las notificaciones "
                    + "a los profesionales ACTIVOS que compartan algún gremio (outbox). "
                    + "Rate limit: 10 peticiones/hora por IP.")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada en estado NUEVA"),
            @ApiResponse(responseCode = "400", description = "Validación fallida o gremios inexistentes "
                    + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit por IP superado (10/hora)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody SolicitudCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mapper.toResponse(service.crear(mapper.toCommand(request))));
    }
}
