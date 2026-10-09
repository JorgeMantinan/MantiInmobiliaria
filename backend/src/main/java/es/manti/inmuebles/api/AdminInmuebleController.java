package es.manti.inmuebles.api;

import es.manti.inmuebles.api.dto.InmuebleMapper;
import es.manti.inmuebles.api.dto.InmuebleRequest;
import es.manti.inmuebles.api.dto.InmuebleResponse;
import es.manti.inmuebles.application.GestionInmuebleService;
import es.manti.inmuebles.domain.Inmueble;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Backoffice de inmuebles. Autorización de ROL a nivel de CLASE:
 * {@code @PreAuthorize("hasRole('ADMIN')")} — primer uso real de autorización
 * metodológica del proyecto ({@code @EnableMethodSecurity} ya activo).
 * Las rutas de escritura NO se abren en SecurityConfig: un anónimo recibe 401
 * en la cadena de seguridad y un USER con token correcto 403 aquí.
 */
@Tag(name = "Inmuebles (admin)", description = "Alta, actualización y borrado de inmuebles (requiere rol ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/inmuebles")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInmuebleController {

    private final GestionInmuebleService service;
    private final InmuebleMapper mapper;

    @PostMapping
    @Operation(summary = "Crea un inmueble", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creado; cabecera Location con el recurso público"),
            @ApiResponse(responseCode = "400", description = "Validación fallida (ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<InmuebleResponse> crear(@Valid @RequestBody InmuebleRequest request) {
        Inmueble creado = service.crear(mapper.toDatos(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/inmuebles/" + creado.getId()))
                .body(mapper.toResponse(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un inmueble (PUT completo)",
            description = "Reemplaza la representación completa del recurso; los campos "
                    + "no enviados (opcional: `destacado`) conservan su valor.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inmueble actualizado"),
            @ApiResponse(responseCode = "400", description = "Validación fallida (ProblemDetail con errors[])",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Inexistente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Inmueble ya eliminado (soft-delete); no se reescribe un borrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<InmuebleResponse> actualizar(
            @Parameter(description = "Identificador del inmueble", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody InmuebleRequest request) {
        Inmueble actualizado = service.actualizar(id, mapper.toDatos(request));
        return ResponseEntity.ok(mapper.toResponse(actualizado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina un inmueble (soft-delete)",
            description = "Marca `eliminado_en`: desaparece del listado y del detalle público, "
                    + "pero la fila se conserva (auditoría).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado (sin cuerpo)"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Inexistente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Inmueble ya eliminado (soft-delete); no se repite el borrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "Identificador del inmueble", example = "1")
            @PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
