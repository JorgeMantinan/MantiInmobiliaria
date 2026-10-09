/**
 * Presentación del backoffice: fechas, etiquetas de estado y de gremio.
 * Sin acceso a datos (sólo formateo).
 */

const FORMATEADOR_FECHA = new Intl.DateTimeFormat('es-ES', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/** Instante ISO del backend (`2026-10-08T10:00:00Z`) → `8 oct 2026, 12:00`. */
export function formatoFecha(iso: string): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  return FORMATEADOR_FECHA.format(fecha);
}

/** `fontanero` → `Fontanero`; `pintor-interior` → `Pintor interior`. */
export function etiquetaGremio(slug: string): string {
  const texto = slug.trim();
  if (texto === '') return texto;
  return texto.charAt(0).toLocaleUpperCase('es') + texto.slice(1).replaceAll('-', ' ');
}

const ETIQUETAS_ESTADO: Readonly<Record<string, string>> = {
  PENDIENTE: 'Pendiente',
  ACTIVO: 'Activo',
  RECHAZADO: 'Rechazado',
  NUEVA: 'Nueva',
  CERRADA: 'Cerrada',
};

/** Etiqueta legible de cualquier estado; el valor crudo si llega uno nuevo. */
export function etiquetaEstado(estado: string): string {
  return ETIQUETAS_ESTADO[estado] ?? estado;
}
