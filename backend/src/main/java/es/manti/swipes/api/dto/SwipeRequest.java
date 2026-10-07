package es.manti.swipes.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Decisión de un swipe. Request y response son tipos separados (regla 3 de la
 * skill dto): aquí sólo entra lo que decide el cliente y sólo se valida formato.
 *
 * <p>{@code decision} viaja como {@link String} con {@code @Pattern} en lugar
 * de como enum: así un valor no admitido produce 400 con
 * {@code errors:[{field: "decision", ...}]} en lugar de un 400 genérico de JSON
 * sin campo (Jackson falla antes de llegar a la validación Jakarta).</p>
 */
@Schema(description = "Decisión de un swipe sobre un inmueble")
public record SwipeRequest(
        @NotNull @Positive
        @Schema(description = "Identificador del inmueble", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long inmuebleId,

        @NotNull @Pattern(regexp = "LIKE|NOPE")
        @Schema(description = "Decisión: LIKE (favorito) o NOPE (descartar)",
                example = "LIKE", allowableValues = {"LIKE", "NOPE"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String decision) {
}
