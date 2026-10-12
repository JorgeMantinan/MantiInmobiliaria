import { esApiError } from '@/lib/api';

/** Mensaje amigable cuando el backend aplica rate limit (429). */
export const MENSAJE_RATE_LIMIT =
  'Demasiados intentos. Espera un minuto y vuelve a intentarlo.';

/**
 * Rate limit específico de forgot/reset (5-10 peticiones/hora por IP, no
 * "un minuto"): se informa con su propia cadencia para no prometer de más.
 */
export const MENSAJE_RATE_LIMIT_RECUPERACION =
  'Demasiadas peticiones. Espera unas horas antes de solicitar otro enlace.';

/** Confirmación genérica de `/recuperar` (el backend no revela si la cuenta existe). */
export const MENSAJE_RECUPERACION_ENVIADA =
  'Si existe una cuenta con ese email, recibirás un enlace para restablecer tu contraseña. Revisa tu bandeja de entrada y también la carpeta de spam.';

/** Enlace de `/restablecer` caducado, desconocido o ya canjeado. */
export const MENSAJE_ENLACE_INVALIDO = 'El enlace no es válido o ha caducado.';

/** Clase de los avisos de error: vive en las primitivas de UI, aquí sólo se reexporta. */
export { AVISO_ERROR } from '@/components/ui/avisos';

/** ¿El error es el 400 `token-invalido` del canje de restablecimiento? */
export function esTokenInvalido(error: unknown): boolean {
  return esApiError(error) && error.status === 400 && error.type.endsWith('/token-invalido');
}

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
