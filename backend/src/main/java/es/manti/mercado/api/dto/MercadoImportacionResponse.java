package es.manti.mercado.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Resultado de una reimportación de los CSV del MIVAU desde el backoffice. */
@Schema(description = "Resultado de la reimportación de los CSV del MIVAU")
public record MercadoImportacionResponse(
        @Schema(description = "Filas de alquiler municipal escritas (nuevas + actualizadas)",
                example = "7665")
        int filasAlquiler,

        @Schema(description = "Filas de valor tasado provincial escritas (nuevas + actualizadas)",
                example = "8412")
        int filasVenta,

        @Schema(description = "Instante de descarga de los dos ficheros (ISO-8601)",
                example = "2026-10-11T15:20:07Z")
        Instant fechaDescarga) {
}
