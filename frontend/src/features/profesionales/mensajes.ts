import { esApiError } from '@/lib/api';

/** Textos compartidos del feature de profesionales (un único sitio que retocar). */
export const MENSAJES = {
  /** Aviso de fallback: la API de gremios ha fallado y se usa la lista local. */
  gremiosReserva: 'No se han podido cargar los gremios; mostramos la lista de referencia.',
  solicitud: 'No se ha podido enviar la solicitud.',
  registro: 'No se ha podido completar el registro.',
  /** 429 del backend: mensaje amigable, sin tecnicismos. */
  rateLimit: 'Demasiadas solicitudes, inténtalo más tarde',
  /** 409 del registro de profesional (email duplicado). */
  emailRepetido: 'Ese email ya está registrado.',
  solicitudEnviada: 'Solicitud enviada, te contactarán los profesionales',
  registroRecibido: 'Registro recibido — validaremos tu cuenta y recibirás notificaciones',
} as const;

/** Clase de los avisos de error: vive en las primitivas de UI, aquí sólo se reexporta. */
export { AVISO_ERROR } from '@/components/ui/avisos';

/**
 * Traduce cualquier error del cliente API a un mensaje mostrable:
 * 429 ⇒ mensaje amigable; 400 con `errors[]` ⇒ mensajes de campo (RFC 9457);
 * el resto ⇒ `detail` del ProblemDetail (y `title` si viniera vacío).
 */
export function mensajeDeError(error: unknown, mensajePorDefecto: string): string {
  if (!esApiError(error)) {
    return mensajePorDefecto;
  }
  if (error.status === 429) {
    return MENSAJES.rateLimit;
  }
  if (error.status === 400 && error.errors.length > 0) {
    return error.errors.map((campo) => campo.message).join(' · ');
  }
  return error.detail.length > 0 ? error.detail : error.title;
}
