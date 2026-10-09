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

/** Clase de los avisos de error: vive en las primitivas de UI, aquí se reexporta. */
export { AVISO_ERROR } from '@/components/ui/avisos';

/**
 * Traduce cualquier error a un mensaje mostrable: `ApiError` ⇒ `detail`
 * (o `title`); cualquier `Error` ⇒ su mensaje; el resto ⇒ `mensajePorDefecto`.
 */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  if (esApiError(error)) {
    return error.detail.length > 0 ? error.detail : error.title;
  }
  if (error instanceof Error && error.message.length > 0) {
    return error.message;
  }
  return mensajePorDefecto;
}
