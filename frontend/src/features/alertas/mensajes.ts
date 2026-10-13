/**
 * Textos de error del feature de alertas: un único sitio para retocarlos y
 * para traducir un `unknown` de carga a algo presentable.
 */
import { esApiError } from '@/lib/api';

/** Mensajes por defecto por flujo (cuando el error no trae nada legible). */
export const MENSAJES = {
  alertas: 'No se han podido cargar tus alertas.',
  guardar: 'No se ha podido guardar la alerta; vuelve a intentarlo.',
  actualizar: 'No se ha podido actualizar la alerta.',
  eliminar: 'No se ha podido eliminar la alerta.',
} as const;

/** true si el fallo es un 401 de la API (sesión ausente o caducada). */
export function esSinSesion(fallo: unknown): boolean {
  return esApiError(fallo) && fallo.status === 401;
}

/**
 * Mensaje legible de un error de carga.
 *
 * - `ApiError`: conserva `title` + `detail` (el backend garantiza mensajes
 *   genéricos RFC 9457, aptos para mostrar).
 * - Resto de errores: en producción NUNCA se filtra el mensaje interno (puede
 *   mezclar detalles del stack); sólo se muestra en desarrollo para depurar.
 */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  if (esApiError(error)) return error.message;
  if (process.env.NODE_ENV === 'production') return mensajePorDefecto;
  return error instanceof Error ? error.message : mensajePorDefecto;
}
