/**
 * Endpoints del backoffice vía `lib/api.ts` (base URL, cookie de credenciales
 * y Bearer de la sesión ADMIN). Rutas EXACTAS del contrato de los módulos
 * `profesionales` (`AdminProfesionalController` y `AdminSolicitudController`)
 * e `inmuebles` (`AdminInmuebleController`).
 *
 * El LISTADO de inmuebles reutiliza el endpoint público `GET /api/v1/inmuebles`
 * (`features/inmuebles/api.ts`): no hay página admin de inmuebles y el público
 * filtra siempre los soft-deleted, que es exactamente lo que debe gestionar el
 * backoffice.
 *
 * TODO(issue #19): los GET de admin de profesionales/solicitudes devuelven hoy
 * un ARRAY plano, sin el envelope `{content, page, totalElements, …}` común al
 * resto de la API; cuando el backend unifique envelopes hay que envolver aquí
 * las firmas.
 */
import { apiFetch } from '@/lib/api';
import type { InmuebleDetalle } from '@/features/inmuebles/types';
import type {
  EstadoModeracion,
  EstadoProfesional,
  EstadoProfesionalRespuesta,
  InmuebleEnvio,
  ProfesionalAdmin,
  SolicitudAdmin,
} from './types';

const RUTAS = {
  profesionales: '/api/v1/admin/profesionales',
  solicitudes: '/api/v1/admin/solicitudes',
  inmuebles: '/api/v1/admin/inmuebles',
} as const;

/** `GET /api/v1/admin/profesionales[?estado=…]` — 200 `[…]`; 400/401/403. */
export function listarProfesionales(
  estado?: EstadoProfesional,
): Promise<readonly ProfesionalAdmin[]> {
  const ruta =
    estado === undefined ? RUTAS.profesionales : `${RUTAS.profesionales}?estado=${estado}`;
  return apiFetch<readonly ProfesionalAdmin[]>(ruta);
}

/** `PATCH /api/v1/admin/profesionales/{id}` body `{estado}` — 200 `{id, estado}`; 400/404/401/403. */
export function cambiarEstadoProfesional(
  id: number,
  estado: EstadoModeracion,
): Promise<EstadoProfesionalRespuesta> {
  return apiFetch<EstadoProfesionalRespuesta>(`${RUTAS.profesionales}/${id}`, {
    method: 'PATCH',
    body: { estado },
  });
}

/** `GET /api/v1/admin/solicitudes` — 200 `[…]` (últimas 50, sin paginar); 401/403. */
export function listarSolicitudes(): Promise<readonly SolicitudAdmin[]> {
  return apiFetch<readonly SolicitudAdmin[]>(RUTAS.solicitudes);
}

/** `POST /api/v1/admin/inmuebles` — 201 `InmuebleResponse`; 400/401/403. */
export function crearInmueble(datos: InmuebleEnvio): Promise<InmuebleDetalle> {
  return apiFetch<InmuebleDetalle>(RUTAS.inmuebles, { method: 'POST', body: datos });
}

/** `PUT /api/v1/admin/inmuebles/{id}` — 200; 400/401/403/404/409 (ya eliminado). */
export function actualizarInmueble(id: number, datos: InmuebleEnvio): Promise<InmuebleDetalle> {
  return apiFetch<InmuebleDetalle>(`${RUTAS.inmuebles}/${id}`, { method: 'PUT', body: datos });
}

/** `DELETE /api/v1/admin/inmuebles/{id}` — 204 soft-delete; 401/403/404/409. */
export function eliminarInmueble(id: number): Promise<void> {
  return apiFetch<void>(`${RUTAS.inmuebles}/${id}`, { method: 'DELETE' });
}
