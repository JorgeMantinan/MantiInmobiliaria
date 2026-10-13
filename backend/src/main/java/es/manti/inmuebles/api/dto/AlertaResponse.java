package es.manti.inmuebles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Representación pública de una alerta de búsqueda. Sin la marca de agua
 * interna: al usuario sólo le importa CUÁNDO fue el último aviso.
 */
@Schema(description = "Alerta de búsqueda del usuario autenticado")
public record AlertaResponse(
        @Schema(description = "Identificador de la alerta", example = "1")
        Long id,

        @Schema(description = "Nombre propio de la alerta", example = "Pisos en Madrid")
        String nombre,

        @Schema(description = "Filtros guardados (los mismos del listado público)")
        AlertaFiltros filtros,

        @Schema(description = "Si está activa, el job la evalúa en cada pasada")
        boolean activa,

        @Schema(description = "Instante ISO-8601 del último aviso enviado; null si aún no avisó")
        Instant ultimoAvisoEn,

        @Schema(description = "Instante ISO-8601 de creación")
        Instant creadoEn,

        @Schema(description = "Instante ISO-8601 de la última modificación")
        Instant actualizadoEn) {
}
