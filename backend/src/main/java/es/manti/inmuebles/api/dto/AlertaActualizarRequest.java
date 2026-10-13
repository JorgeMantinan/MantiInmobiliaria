package es.manti.inmuebles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Edición PARCIAL de una alerta (PATCH): ambos campos opcionales y los
 * ausentes se interpretan como "sin cambios". Los filtros NO se editan —
 * para cambiarlos se crea una alerta nueva (decisión MVP, issue #96).
 */
@Schema(description = "Actualización parcial de una alerta (nombre y/o actividad)")
public record AlertaActualizarRequest(
        @Size(min = 1, max = 80)
        @Schema(description = "Nuevo nombre (1..80, sin blanco si se envía); ausente = sin cambios",
                example = "Pisos en Madrid", maxLength = 80)
        String nombre,

        @Schema(description = "Activa o pausa la alerta; ausente = sin cambios. "
                + "Pausada, el job deja de evaluarla (los avisos no se pierden: "
                + "al reactivar se continúa desde la última marca de agua)")
        Boolean activa) {
}
