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
 * Lista estática de reserva (12 gremios): sólo se usa si `GET /api/v1/gremios`
 * falla o devuelve vacío, para que el formulario siga siendo utilizable.
 */
export const GREMIOS_DE_RESERVA: readonly Gremio[] = [
  { slug: 'fontaneria', nombre: 'Fontanería' },
  { slug: 'electricidad', nombre: 'Electricidad' },
  { slug: 'albanileria', nombre: 'Albañilería' },
  { slug: 'carpinteria', nombre: 'Carpintería' },
  { slug: 'pintura', nombre: 'Pintura' },
  { slug: 'cerrajeria', nombre: 'Cerrajería' },
  { slug: 'climatizacion', nombre: 'Climatización' },
  { slug: 'suelos', nombre: 'Suelos y revestimientos' },
  { slug: 'tejados', nombre: 'Tejados y cubiertas' },
  { slug: 'jardineria', nombre: 'Jardinería' },
  { slug: 'limpieza', nombre: 'Limpieza' },
  { slug: 'domotica', nombre: 'Domótica y seguridad' },
];
