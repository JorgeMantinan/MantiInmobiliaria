import { esApiError } from '@/lib/api';

/** Mensaje amigable cuando el backend aplica rate limit (429). */
export const MENSAJE_RATE_LIMIT =
  'Demasiados intentos. Espera un minuto y vuelve a intentarlo.';

/** Traduce cualquier error del cliente API a un mensaje mostrable al usuario. */
export function mensajeDeError(error: unknown): string {
  if (!esApiError(error)) {
    return 'Se ha producido un error inesperado. Inténtalo de nuevo.';
  }
  if (error.status === 429) {
    return MENSAJE_RATE_LIMIT;
  }
  if (error.status === 400 && error.errors.length > 0) {
    return error.errors.map((campo) => campo.message).join(' · ');
  }
  if (error.status === 0) {
    return error.detail;
  }
  return error.detail.length > 0 ? error.detail : error.title;
}
