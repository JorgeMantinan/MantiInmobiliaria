package es.manti.mercado.api;

import es.manti.mercado.api.dto.MercadoImportacionResponse;
import es.manti.mercado.api.dto.MercadoMapper;
import es.manti.mercado.application.PreciosMercadoImportador;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backoffice de precios del mercado. Autorización de ROL a nivel de CLASE
 * ({@code @PreAuthorize}) y SIN entrada en SecurityConfig: un anónimo recibe
 * 401 en la cadena de seguridad y un USER con token 403 aquí.
 *
 * <p>Es el ÚNICO punto del sistema que descarga datos de red, y sólo bajo una
 * petición explícita de un ADMIN: no hay tareas programadas ni descargas
 * implícitas en el arranque (el sembrador de dev sólo actúa con la tabla vacía
 * y fuera del perfil test).</p>
 */
@Tag(name = "Mercado (admin)", description = "Reimportación de los CSV oficiales del MIVAU (requiere rol ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/mercado")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMercadoController {

    private final PreciosMercadoImportador importador;
    private final MercadoMapper mapper;

    @PostMapping("/actualizar")
    @Operation(summary = "Reimporta los CSV del MIVAU y actualiza los precios",
            description = "Descarga VDP001 (alquiler municipal, 37,8 MB) y VDP006 (valor tasado "
                    + "provincial) desde el CDN del Ministerio, los interpreta y hace upsert por clave "
                    + "natural. Puede tardar varios segundos; timeout de 30 s/60 s en la descarga.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Importación completada; nº de filas escritas"),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Token sin rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "El CDN del MIVAU no responde o publica un "
                    + "CSV con estructura distinta (ProblemDetail)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<MercadoImportacionResponse> actualizar() {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toImportacion(importador.importar()));
    }
}
