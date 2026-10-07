/**
 * Acceso a la API de swipes (`/api/v1/swipes`) vía `lib/api.ts`: base URL,
 * cookie de credenciales, Bearer si hay sesión y un único refresh en 401.
 *
 * Los endpoints exigen sesión; el llamador (hooks) decide no llamar cuando el
 * usuario es anónimo y traduce un 401 a la pantalla de CTA de login.
 */
import { apiFetch } from '@/lib/api';
import type { DecisionSwipe, PaginaSwipes } from './types';

/** Tamaño de página fijado en el contrato (`?limit=20`). */
const LIMITE = 20;

/** `limit` + `cursor` opaco (sólo si la página anterior devolvió uno). */
function queryPagina(cursor: string | null): string {
  const base = `limit=${LIMITE}`;
  return cursor === null ? base : `${base}&cursor=${encodeURIComponent(cursor)}`;
}

/** `POST /api/v1/swipes` — registra la decisión (idempotente en el backend). */
export async function registrarSwipe(inmuebleId: number, decision: DecisionSwipe): Promise<void> {
  await apiFetch<unknown>('/api/v1/swipes', {
    method: 'POST',
    body: { inmuebleId, decision },
  });
}

/** `GET /api/v1/swipes/deck` — activos aún no swipeados, orden recientes. */
export async function obtenerDeck(cursor: string | null = null): Promise<PaginaSwipes> {
  return apiFetch<PaginaSwipes>(`/api/v1/swipes/deck?${queryPagina(cursor)}`);
}

/** `GET /api/v1/swipes/favoritos` — favoritos del usuario con su cursor. */
export async function listarFavoritos(cursor: string | null = null): Promise<PaginaSwipes> {
  return apiFetch<PaginaSwipes>(`/api/v1/swipes/favoritos?${queryPagina(cursor)}`);
}

/** `DELETE /api/v1/swipes/favoritos/{inmuebleId}` — 204 sin cuerpo. */
export async function eliminarFavorito(inmuebleId: number): Promise<void> {
  await apiFetch<unknown>(`/api/v1/swipes/favoritos/${inmuebleId}`, { method: 'DELETE' });
}
