import type { DecisionSwipe } from '../types';

type BotonesDecisionProps = Readonly<{
  onDecidir: (decision: DecisionSwipe) => void;
  /** Sin carta visible (deck cargando/agotado): los botones quedan inactivos. */
  deshabilitados?: boolean;
}>;

const CLASE_BOTON =
  'flex h-16 w-16 items-center justify-center rounded-lg border bg-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal focus:ring-offset-2 focus:ring-offset-fondo disabled:cursor-not-allowed disabled:opacity-60';

/**
 * Alternativa accesible al arrastre: dos botones reales con `aria-label`
 * ("Guardar como favorito" / "Descartar") que ejecutan la misma decisión.
 */
export function BotonesDecision({ onDecidir, deshabilitados = false }: BotonesDecisionProps) {
  return (
    <div className="mt-6 flex items-center justify-center gap-6">
      <button
        type="button"
        aria-label="Descartar"
        disabled={deshabilitados}
        onClick={() => onDecidir('NOPE')}
        className={`${CLASE_BOTON} border-borde text-texto hover:bg-superficie-alta`}
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          strokeLinecap="round"
          className="h-8 w-8"
        >
          <path d="M18 6 6 18" />
          <path d="m6 6 12 12" />
        </svg>
      </button>

      <button
        type="button"
        aria-label="Guardar como favorito"
        disabled={deshabilitados}
        onClick={() => onDecidir('LIKE')}
        className={`${CLASE_BOTON} border-acento-nogal text-acento-nogal hover:bg-superficie-alta`}
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-8 w-8"
        >
          <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
        </svg>
      </button>
    </div>
  );
}
