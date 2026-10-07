/**
 * Textos de error del feature de swipes: un único sitio para retocarlos y
 * para traducir un `unknown` de carga a algo presentable (el `ApiError` de
 * `lib/api.ts` ya trae mensaje propio).
 */

/** Mensajes por defecto por flujo (cuando el error no trae nada legible). */
export const MENSAJES = {
  deck: 'No se han podido cargar las viviendas para revisar.',
  favoritos: 'No se han podido cargar tus favoritos.',
  decision: 'No se ha podido guardar tu decisión; vuelve a intentarlo.',
  quitar: 'No se ha podido quitar de favoritos.',
} as const;

/** Mensaje legible de un error de carga; `mensajePorDefecto` si no lo tiene. */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  return error instanceof Error ? error.message : mensajePorDefecto;
}
