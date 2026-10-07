import type { DecisionSwipe } from './types';

/** Desplazamiento mínimo (px) para que el arrastre cuente como decisión. */
export const UMBRAL_ARRASTRE = 110;

/** Tirón rápido (px/s) que decide aunque el desplazamiento sea corto. */
export const VELOCIDAD_ARRASTRE = 700;

/**
 * Decisión que codifica un arrastre soltado (lógica pura del drag):
 *
 * - desplazamiento con signo > umbral (110 px), O
 * - velocidad con signo > 700 px/s (flick rápido corto), O
 * - `null` si el gesto no llega a ninguno de los dos (vuelve a origen).
 *
 * El signo marca el sentido: positivo = derecha = LIKE, negativo = izquierda.
 */
export function debeDecidir(offsetX: number, velocityX: number): DecisionSwipe | null {
  if (offsetX > UMBRAL_ARRASTRE || velocityX > VELOCIDAD_ARRASTRE) return 'LIKE';
  if (offsetX < -UMBRAL_ARRASTRE || velocityX < -VELOCIDAD_ARRASTRE) return 'NOPE';
  return null;
}
