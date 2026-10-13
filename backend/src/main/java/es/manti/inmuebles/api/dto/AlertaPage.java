package es.manti.inmuebles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Envelope del listado de alertas (follow-up #98): MISMO formato que
 * {@code InmueblePage}/{@code SwipePage} ({@code content} + totalElements).
 *
 * <p>Las alertas NO se paginan en la API (el techo es de 50 por usuario, se
 * devuelve el listado completo en una sola respuesta), así que no hay
 * {@code nextCursor} ni parámetros de página.</p>
 */
@Schema(description = "Listado de alertas del usuario autenticado")
public record AlertaPage(
        @Schema(description = "Alertas del usuario, de más reciente a más antigua")
        List<AlertaResponse> content,

        @Schema(description = "Total de alertas del usuario", example = "2")
        long totalElements) {
}
