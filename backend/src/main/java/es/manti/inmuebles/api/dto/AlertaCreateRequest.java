package es.manti.inmuebles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una alerta de búsqueda (POST): nombre propio + filtros del listado.
 * Request y response son tipos separados (regla 3 de la skill dto).
 */
@Schema(description = "Alta de una alerta de búsqueda con los filtros del listado")
public record AlertaCreateRequest(
        @NotBlank
        @Size(max = 80)
        @Schema(description = "Nombre propio de la alerta (1..80 caracteres)",
                example = "Pisos en Madrid", requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 80)
        String nombre,

        @NotNull
        @Valid
        @Schema(description = "Filtros a guardar (los mismos del listado público)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        AlertaFiltros filtros) {
}
