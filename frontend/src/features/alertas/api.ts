/**
 * Acceso a la API de alertas (`/api/v1/alertas`) vía `lib/api.ts`: base URL,
 * cookie de credenciales, Bearer si hay sesión y un único refresh en 401.
 *
 * Los cuatro endpoints exigen sesión; el llamador decide no llamar cuando el
 * usuario es anónimo y traduce un 401 a la CTA de login.
 */
import { apiFetch } from '@/lib/api';
import type { AlertaBusqueda, AlertaCambios, AlertaPage, FiltrosAlerta } from './types';

/**
 * Valida el id antes de interpolarlo en una ruta (defensa en profundidad:
 * nunca se concatena un valor no saneado a la URL).
 */
function validarId(id: number): void {
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('id de alerta inválido');
  }
}

/**
 * `GET /api/v1/alertas` — envelope del listado (content + totalElements);
 * vacío si el usuario no tiene ninguna.
 */
export async function listarAlertas(): Promise<AlertaPage> {
  return apiFetch<AlertaPage>('/api/v1/alertas');
}

/** `POST /api/v1/alertas` — crea una alerta con los filtros del listado. */
export async function crearAlerta(
  nombre: string,
  filtros: FiltrosAlerta,
): Promise<AlertaBusqueda> {
  return apiFetch<AlertaBusqueda>('/api/v1/alertas', {
    method: 'POST',
    body: { nombre, filtros },
  });
}

/** `PATCH /api/v1/alertas/{id}` — edición parcial (nombre y/o activa). */
export async function actualizarAlerta(
  id: number,
  cambios: AlertaCambios,
): Promise<AlertaBusqueda> {
  validarId(id);
  return apiFetch<AlertaBusqueda>(`/api/v1/alertas/${id}`, {
    method: 'PATCH',
    body: cambios,
  });
}

/** `DELETE /api/v1/alertas/{id}` — 204 sin cuerpo. */
export async function eliminarAlerta(id: number): Promise<void> {
  validarId(id);
  await apiFetch<unknown>(`/api/v1/alertas/${id}`, { method: 'DELETE' });
}
