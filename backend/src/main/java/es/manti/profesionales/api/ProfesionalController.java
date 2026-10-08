package es.manti.profesionales.api;

import es.manti.profesionales.api.dto.ProfesionalEstadoResponse;
import es.manti.profesionales.api.dto.ProfesionalMapper;
import es.manti.profesionales.api.dto.ProfesionalRegistroRequest;
import es.manti.profesionales.application.RegistroProfesionalService;
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
 * Auto-registro de profesionales ({@code POST /api/v1/profesionales/registro},
 * permitAll). La fila nace PENDIENTE y un ADMIN debe activarla; rate limit de
 * 5/hora por IP.
 */
@Tag(name = "Profesionales", description = "Registro público de profesionales (sin autenticación)")
@RestController
@RequestMapping("/api/v1/profesionales")
@Validated
@RequiredArgsConstructor
public class ProfesionalController {

    private final RegistroProfesionalService service;
    private final ProfesionalMapper mapper;

    @PostMapping("/registro")
    @Operation(operationId = "registrarProfesional",
            summary = "Registra un profesional (queda PENDIENTE)",
            description = "Alta anónima de una empresa o autónomo que quiere recibir solicitudes. "
                    + "Siempre nace con estado `PENDIENTE`: sólo un administrador puede moverla a "
                    + "`ACTIVO` o `RECHAZADO`. Rate limit: 5 peticiones/hora por IP.")
    @SecurityRequirements   // endpoint público: security: [] en la spec
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profesional registrado en estado PENDIENTE"),
            @ApiResponse(responseCode = "400", description = "Validación fallida o gremios inexistentes "
                    + "(ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado como profesional",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit por IP superado (5/hora)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<ProfesionalEstadoResponse> registrar(
            @Valid @RequestBody ProfesionalRegistroRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mapper.toRegistroResponse(service.registrar(mapper.toCommand(request))));
    }
}
