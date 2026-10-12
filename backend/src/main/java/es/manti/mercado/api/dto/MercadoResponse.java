package es.manti.mercado.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Precios oficiales de la zona de un municipio (GET /api/v1/mercado).
 *
 * <p>Toda respuesta —incluso la parcial— lleva la atribución exigida por el
 * aviso legal del MIVAU en {@code fuente}.</p>
 */
@Schema(description = "Precios oficiales del mercado de la zona de un municipio")
public record MercadoResponse(
        @Schema(description = "Código INE de 5 dígitos consultado", example = "28079")
        String codigoINE,

        @Schema(description = "Año del dato de alquiler municipal; si no hay alquiler, "
                + "el año del dato de venta", example = "2024")
        int anio,

        @Schema(description = "Alquiler municipal; null si el municipio no tiene dato publicado")
        AlquilerMercadoResponse alquiler,

        @Schema(description = "Valor tasado provincial; null si la fuente no publica esa provincia")
        VentaMercadoResponse venta,

        @Schema(description = "Fuente obligatoria (aviso legal MIVAU)",
                example = "Ministerio de Vivienda y Agenda Urbana")
        String fuente) {
}
