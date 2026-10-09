/**
 * Endpoints de profesionales vía `lib/api.ts`: base URL, cookie de
 * credenciales y Bearer si hay sesión. Los tres endpoints son anónimos
 * (el alta queda `PENDIENTE` hasta que el backend la valide).
 *
 * Rutas EXACTAS del contrato fijado con el backend (PR `feature/08-profesionales`).
 */
import { apiFetch } from '@/lib/api';
import type {
  AltaCreada,
  AltaProfesional,
  Gremio,
  SolicitudCreada,
  SolicitudNueva,
} from './types';

const RUTAS = {
  gremios: '/api/v1/gremios',
  solicitudes: '/api/v1/solicitudes',
  registro: '/api/v1/profesionales/registro',
} as const;

/** `GET /api/v1/gremios` — catálogo `[{slug, nombre}]`. */
export function listarGremios(): Promise<readonly Gremio[]> {
  return apiFetch<readonly Gremio[]>(RUTAS.gremios);
}

/** `POST /api/v1/solicitudes` — 201 `{id, estado, creadoEn}`; 400/429 en error. */
export function crearSolicitud(datos: SolicitudNueva): Promise<SolicitudCreada> {
  return apiFetch<SolicitudCreada>(RUTAS.solicitudes, { method: 'POST', body: datos });
}

/** `POST /api/v1/profesionales/registro` — 201 `{id, estado:'PENDIENTE'}`; 409 si el email existe. */
export function registrarProfesional(datos: AltaProfesional): Promise<AltaCreada> {
  return apiFetch<AltaCreada>(RUTAS.registro, { method: 'POST', body: datos });
}
