/**
 * Contratos de gremios, solicitudes de trabajo y alta de profesionales.
 *
 * Ajustados al contrato FIJO del backend (PR `feature/08-profesionales`):
 * `GET /api/v1/gremios`, `POST /api/v1/solicitudes` y
 * `POST /api/v1/profesionales/registro`.
 */

export type Gremio = Readonly<{ slug: string; nombre: string }>;

export type SolicitudNueva = Readonly<{
  nombreContacto: string;
  emailContacto: string;
  telefono?: string;
  municipio: string;
  descripcion: string;
  gremios: readonly string[];
}>;

export type SolicitudCreada = Readonly<{
  id: number;
  estado: string;
  creadoEn: string;
}>;

export type AltaProfesional = Readonly<{
  nombre: string;
  email: string;
  telefono?: string;
  municipio?: string;
  gremios: readonly string[];
}>;

export type AltaCreada = Readonly<{ id: number; estado: 'PENDIENTE' }>;

/**
 * Catálogo de RESERVA: los 12 oficios sembrados en
 * `V4__profesionales.sql` (PR `feature/08-profesionales`), en el MISMO orden.
 * Sólo se usa si `GET /api/v1/gremios` falla o devuelve vacío, para que el
 * formulario siga siendo utilizable; `types.test.ts` fija este array con un
 * literal para que cualquier divergencia con el backend rompa el build.
 */
export const GREMIOS_DE_RESERVA: readonly Gremio[] = [
  { slug: 'fontanero', nombre: 'Fontanero' },
  { slug: 'albañil', nombre: 'Albañil' },
  { slug: 'electricista', nombre: 'Electricista' },
  { slug: 'carpintero', nombre: 'Carpintero' },
  { slug: 'maderista', nombre: 'Maderista' },
  { slug: 'soldador', nombre: 'Soldador' },
  { slug: 'pintor', nombre: 'Pintor' },
  { slug: 'jardinero', nombre: 'Jardinero' },
  { slug: 'cerrajero', nombre: 'Cerrajero' },
  { slug: 'gasista', nombre: 'Gasista' },
  { slug: 'techador', nombre: 'Techador' },
  { slug: 'arquitecto', nombre: 'Arquitecto' },
];
