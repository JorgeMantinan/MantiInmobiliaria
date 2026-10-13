/**
 * Traducción de fallos de la API a algo presentable, compartida por los
 * features (follow-up #98: nació duplicada en swipes y alertas).
 */
import { esApiError } from '@/lib/api';

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
