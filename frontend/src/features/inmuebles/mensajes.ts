/**
 * Textos de error compartidos del feature de inmuebles: un único sitio para
 * retocarlos y para definir cómo se traduce un `unknown` de carga a algo
 * presentable (el `ApiError` de `lib/api.ts` ya trae mensaje propio).
 */

/** Mensajes por defecto por flujo (cuando el error no trae nada legible). */
export const MENSAJES = {
  listado: 'No se han podido cargar los inmuebles.',
  destacados: 'No se han podido cargar los destacados.',
  ficha: 'No se ha podido cargar el inmueble.',
} as const;

/** Mensaje legible de un error de carga; `mensajePorDefecto` si no lo tiene. */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  return error instanceof Error ? error.message : mensajePorDefecto;
}
