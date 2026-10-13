package es.manti.inmuebles.api.dto;

import es.manti.inmuebles.domain.EstadoInmueble;
import es.manti.inmuebles.domain.TipoInmueble;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Filtros de una alerta: EXACTAMENTE los parámetros de filtro de
 * {@code GET /api/v1/inmuebles} (el contrato a guardar, issue #96). No llevan
 * {@code sort}/{@code cursor}: el orden y la paginación no son filtros.
 *
 * <p>Los rangos inválidos ({@code precioMin > precioMax}, precios negativos,
 * habitaciones &lt; 0) NO se expresan con anotaciones (comparan DOS campos):
 * se rechazan en el caso de uso con la MISMA rutina que valida el listado
 * público, y responden 400 con {@code errors:[{field, message}]}.</p>
 */
@Schema(description = "Filtros de la alerta (los mismos que acepta el listado público de inmuebles)")
public record AlertaFiltros(
        @Schema(description = "Estado comercial (VENTA o ALQUILER); ausente = sin filtrar",
                allowableValues = {"VENTA", "ALQUILER"})
        EstadoInmueble estado,

        @Schema(description = "Tipo de inmueble; ausente = sin filtrar",
                allowableValues = {"PISO", "CASA", "ATICO", "DUPLEX", "ESTUDIO", "LOCAL", "TERRENO"})
        TipoInmueble tipo,

        @Size(max = 120)
        @Schema(description = "Municipio, coincidencia parcial e insensible a mayúsculas (máx. 120)",
                example = "Madrid", maxLength = 120)
        String municipio,

        @DecimalMax(value = "9999999999.99", message = "no puede superar 9999999999.99")
        @Schema(description = "Precio mínimo en euros (>= 0; no puede superar precioMax)",
                example = "100000")
        BigDecimal precioMin,

        @DecimalMax(value = "9999999999.99", message = "no puede superar 9999999999.99")
        @Schema(description = "Precio máximo en euros (>= 0; no puede ser menor que precioMin)",
                example = "250000")
        BigDecimal precioMax,

        @Schema(description = "Número mínimo de habitaciones (>= 0)", example = "3")
        Short habitacionesMin,

        @Schema(description = "Sólo destacados (true) o sólo no destacados (false); ausente = sin filtrar")
        Boolean destacado) {
}
