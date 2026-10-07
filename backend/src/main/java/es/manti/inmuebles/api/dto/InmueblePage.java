package es.manti.inmuebles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Envelope del listado público: paginación por CURSOR (nunca offset) con
 * {@code nextCursor} opaco base64.
 *
 * @param totalElements total del filtro sólo en la primera página; {@code null}
 *                      en las siguientes (no se paga un COUNT por cada salto)
 */
@Schema(description = "Página del listado de inmuebles paginada por cursor")
public record InmueblePage(
        @Schema(description = "Filas de la página")
        List<InmuebleSummary> content,

        @Schema(description = "Cursor opaco para la página siguiente; null si no hay más",
                example = "Q1JFQURPX0RFU0N8MjAyNi0wOS0wMVQxMDowMDowMFp8Mw")
        String nextCursor,

        @Schema(description = "Total de inmuebles que cumplen el filtro (sólo primera página)",
                example = "35")
        Long totalElements) {
}
