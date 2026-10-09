package es.manti.swipes.api.dto;

import es.manti.inmuebles.api.dto.InmuebleSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Envelope del deck y de favoritos: MISMO formato que el listado público de
 * inmuebles ({@code content + nextCursor + totalElements}), paginación por
 * CURSOR (nunca offset) con {@code nextCursor} opaco base64.
 *
 * <p>Reutiliza {@link InmuebleSummary} como tipo de fila: el contrato con el
 * frontend es que una carta del deck y una tarjeta de favoritos sean
 * exactamente un {@code InmuebleSummary} del portal.</p>
 *
 * @param totalElements total del listado SÓLO en la primera página; {@code null}
 *                      en las siguientes (no se paga un COUNT por cada salto)
 */
@Schema(description = "Página del deck o de favoritos, paginada por cursor")
public record SwipePage(
        @Schema(description = "Filas de la página")
        List<InmuebleSummary> content,

        @Schema(description = "Cursor opaco para la página siguiente; null si no hay más",
                example = "REVRLfDIwMjYtMTAtMDdUMTE6MjA6MDAuMDAwMDAwMHw0Mg")
        String nextCursor,

        @Schema(description = "Total de filas del listado (sólo primera página)",
                example = "35")
        Long totalElements) {
}
