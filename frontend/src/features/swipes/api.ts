/**
 * Acceso a la API de swipes (`/api/v1/swipes`) vía `lib/api.ts`: base URL,
 * cookie de credenciales, Bearer si hay sesión y un único refresh en 401.
 *
 * Los endpoints exigen sesión; el llamador (hooks) decide no llamar cuando el
 * usuario es anónimo y traduce un 401 a la pantalla de CTA de login.
 *
 * NOTA (decisión de contrato con el backend, PR `feature/05-swipes`): NO se
 * envía cabecera `Idempotency-Key`: la idempotencia del POST es NATURAL por
 * la restricción única `uq_swipes_usuario_inmueble` (un usuario sólo puede
 * tener un swipe por inmueble), así que un doble envío no duplica el efecto.
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

/**
 * Valida el id antes de interpolarlo en una ruta (defensa en profundidad:
 * nunca se concatena un valor no saneado a la URL).
 */
function validarInmuebleId(inmuebleId: number): void {
  if (!Number.isInteger(inmuebleId) || inmuebleId <= 0) {
    throw new Error('inmuebleId debe ser un entero positivo');
  }
}

/** `POST /api/v1/swipes` — registra la decisión (idempotente en el backend). */
export async function registrarSwipe(inmuebleId: number, decision: DecisionSwipe): Promise<void> {
  validarInmuebleId(inmuebleId);
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
  validarInmuebleId(inmuebleId);
  await apiFetch<unknown>(`/api/v1/swipes/favoritos/${inmuebleId}`, { method: 'DELETE' });
}
