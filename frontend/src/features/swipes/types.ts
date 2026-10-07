/**
 * Tipos del feature de swipes, ajustados EXACTOS al contrato fijado con el
 * backend (`POST /api/v1/swipes`, `GET /swipes/deck`, `GET|DELETE
 * /swipes/favoritos`). El envelope repite el de los listados públicos:
 * `{ content, nextCursor }`.
 */
import type { InmuebleResumen } from '@/features/inmuebles/types';

/** Decisión de un swipe: me gusta (favorito) o descartar. */
export type DecisionSwipe = 'LIKE' | 'NOPE';

/** Página por cursor del deck y de favoritos. */
export type PaginaSwipes = Readonly<{
  content: readonly InmuebleResumen[];
  nextCursor: string | null;
}>;
