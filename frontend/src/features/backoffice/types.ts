/**
 * Tipos del backoffice, derivados EXACTOS de los DTO Java de los módulos
 * `profesionales` (`ProfesionalAdminResponse`, `ProfesionalEstadoResponse` y
 * `SolicitudAdminResponse`) e `inmuebles` (`InmuebleRequest`).
 */
import type { EstadoInmueble, TipoInmueble } from '@/features/inmuebles/types';

/**
 * `InmuebleRequest` del backend (POST/PUT de `/api/v1/admin/inmuebles`).
 * Los opcionales vacíos se envían `null` (el DTO los admite); `destacado`
 * siempre lleva valor porque el formulario expone el checkbox.
 * NOTA: `codigoIne` NO viaja en el request (el backend lo rellena por
 * backfill/importación del MIVAU, nunca lo acepta en la API de escritura).
 */
export type InmuebleEnvio = Readonly<{
  titulo: string;
  descripcion: string | null;
  precio: number;
  superficieM2: number | null;
  habitaciones: number | null;
  banios: number | null;
  tipo: TipoInmueble;
  estado: EstadoInmueble;
  direccion: string;
  codigoPostal: string | null;
  municipio: string;
  provincia: string;
  lat: number;
  lng: number;
  imagenUrl: string | null;
  destacado: boolean;
}>;

/** Estados del alta de un profesional (`EstadoProfesional` del backend). */
export type EstadoProfesional = 'PENDIENTE' | 'ACTIVO' | 'RECHAZADO';

/** Estados que admite el PATCH de moderación (el backend rechaza `PENDIENTE`). */
export type EstadoModeracion = Exclude<EstadoProfesional, 'PENDIENTE'>;

/** Pestañas del filtro por estado, en el orden en que se muestran. */
export const ESTADOS_PROFESIONAL: readonly EstadoProfesional[] = [
  'PENDIENTE',
  'ACTIVO',
  'RECHAZADO',
];

export type ProfesionalAdmin = Readonly<{
  id: number;
  nombre: string;
  email: string;
  telefono: string;
  municipio: string;
  /** Slugs de gremio ordenados alfabéticamente por el mapper. */
  gremios: readonly string[];
  estado: EstadoProfesional;
  creadoEn: string;
}>;

/** `{id, estado}` que devuelve `PATCH /api/v1/admin/profesionales/{id}`. */
export type EstadoProfesionalRespuesta = Readonly<{ id: number; estado: string }>;

export type SolicitudAdmin = Readonly<{
  id: number;
  nombreContacto: string;
  emailContacto: string;
  telefono: string;
  municipio: string;
  descripcion: string;
  /** `NUEVA`/`CERRADA` en el MVP; se deja `string` por compatibilidad. */
  estado: string;
  gremios: readonly string[];
  creadoEn: string;
}>;

/** Guard de lectura de la query `?estado=` (cualquier otro valor ⇒ pestaña por defecto). */
export function esEstadoProfesional(valor: string | null | undefined): valor is EstadoProfesional {
  return ESTADOS_PROFESIONAL.includes(valor as EstadoProfesional);
}
