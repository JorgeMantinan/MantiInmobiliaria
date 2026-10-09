package es.manti.swipes.api.dto;

import es.manti.swipes.domain.DecisionSwipe;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirmación de una decisión registrada. El código de estado distingue el
 * caso: <b>201</b> primera decisión, <b>200</b> repetición idempotente o
 * cambio de decisión.
 */
@Schema(description = "Decisión registrada sobre un inmueble")
public record SwipeResponse(
        @Schema(description = "Inmueble sobre el que se decidió", example = "42")
        Long inmuebleId,

        @Schema(description = "Decisión final", example = "LIKE",
                allowableValues = {"LIKE", "NOPE"})
        DecisionSwipe decision) {
}
