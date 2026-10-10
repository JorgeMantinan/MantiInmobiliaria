/**
 * Textos del backoffice: un único sitio para los mensajes de error y éxito
 * de carga y de moderación.
 */
import { esApiError } from '@/lib/api';

export const MENSAJES = {
  profesionales: 'No se han podido cargar los profesionales.',
  solicitudes: 'No se han podido cargar las solicitudes.',
  cambioEstado: 'No se ha podido cambiar el estado del profesional.',
} as const;

/**
 * Traduce cualquier error a un mensaje mostrable:
 * - 5xx ⇒ `mensajePorDefecto`: el `detail` de un fallo interno es un volcado
 *   técnico que no se pinta en pantalla.
 * - resto de `ApiError` ⇒ `detail` (o `title` si viniera vacío).
 * - cualquier `Error` ⇒ su mensaje; el resto ⇒ `mensajePorDefecto`.
 */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  if (esApiError(error)) {
    if (error.status >= 500) return mensajePorDefecto;
    return error.detail.length > 0 ? error.detail : error.title;
  }
  if (error instanceof Error && error.message.length > 0) {
    return error.message;
  }
  return mensajePorDefecto;
}
