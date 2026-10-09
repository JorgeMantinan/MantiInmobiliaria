package es.manti.swipes.application;

import es.manti.swipes.domain.DecisionSwipe;

/**
 * Resultado de registrar una decisión. {@code creada} distingue lo que el
 * contrato HTTP ya comunica con el código: {@code true} → 201 (fila nueva),
 * {@code false} → 200 (repetición idempotente sin escritura o cambio de
 * decisión que actualiza la fila existente).
 *
 * @param inmuebleId inmueble afectado
 * @param decision   decisión final registrada
 * @param creada     ¿se creó una fila nueva en esta llamada?
 */
public record SwipeResultado(Long inmuebleId, DecisionSwipe decision, boolean creada) {
}
