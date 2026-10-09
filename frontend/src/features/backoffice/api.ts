/**
 * Endpoints del backoffice vía `lib/api.ts` (base URL, cookie de credenciales
 * y Bearer de la sesión ADMIN). Rutas EXACTAS del contrato del módulo
 * `profesionales` (`AdminProfesionalController` y `AdminSolicitudController`).
 *
 * TODO(issue #19): los GET de admin devuelven hoy un ARRAY plano, sin el
 * envelope `{content, page, totalElements, …}` común al resto de la API;
 * cuando el backend unifique envelopes hay que envolver aquí las firmas.
 */
import { apiFetch } from '@/lib/api';
import type {
  EstadoModeracion,
  EstadoProfesional,
  EstadoProfesionalRespuesta,
  ProfesionalAdmin,
  SolicitudAdmin,
} from './types';

const RUTAS = {
  profesionales: '/api/v1/admin/profesionales',
  solicitudes: '/api/v1/admin/solicitudes',
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
