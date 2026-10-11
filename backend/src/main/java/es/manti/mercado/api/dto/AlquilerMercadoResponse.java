package es.manti.mercado.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Bloque de alquiler municipal (SERPAVI) de la respuesta de mercado.
 * Los campos son obligatorios DENTRO del bloque, pero el bloque entero es
 * opcional en {@link MercadoResponse}.
 */
@Schema(description = "Alquiler municipal de referencia (SERPAVI), tipología colectiva")
public record AlquilerMercadoResponse(
        @Schema(description = "Renta mediana en € por m² y mes "
                + "(cuantía mediana dividida entre la superficie mediana, según la metodología SERPAVI)",
                example = "13.16")
        BigDecimal rentaMedianaEurM2Mes,

        @Schema(description = "Cuantía mediana del contrato en € al mes", example = "868.80")
        BigDecimal cuantiaMedianaEurMes) {
}
