package es.manti.mercado.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Bloque de valor tasado de venta. Es siempre de ámbito PROVINCIA: no existe
 * un fichero público de venta municipal a escala estatal.
 */
@Schema(description = "Valor tasado de venta de la provincia")
public record VentaMercadoResponse(
        @Schema(description = "Valor tasado en € por m²", example = "1588.70")
        BigDecimal valorEurM2,

        @Schema(description = "Ámbito del dato", example = "PROVINCIA")
        String ambito,

        @Schema(description = "Nombre de la provincia", example = "Zaragoza")
        String provincia,

        @Schema(description = "Año del dato", example = "2026")
        int anio,

        @Schema(description = "Trimestre del dato (1..4)", example = "3")
        int trimestre) {
}
