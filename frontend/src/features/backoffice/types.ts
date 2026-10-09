/**
 * Tipos del backoffice, derivados EXACTOS de los DTO Java del módulo
 * `profesionales`: `ProfesionalAdminResponse`, `ProfesionalEstadoResponse`
 * y `SolicitudAdminResponse`.
 */

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
