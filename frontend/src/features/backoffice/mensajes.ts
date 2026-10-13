/**
 * Textos del backoffice: un único sitio para los mensajes de error y éxito
 * de carga, moderación y gestión de inmuebles.
 */
import { esApiError } from '@/lib/api';
import { erroresDeCampoInmueble } from './validacion-inmueble';

export const MENSAJES = {
  profesionales: 'No se han podido cargar los profesionales.',
  solicitudes: 'No se han podido cargar las solicitudes.',
  cambioEstado: 'No se ha podido cambiar el estado del profesional.',
  inmuebles: 'No se han podido cargar los inmuebles.',
  inmueblesMas: 'No se han podido cargar más inmuebles.',
  borrarInmueble: 'No se ha podido eliminar el inmueble.',
  guardarInmueble: 'No se ha podido guardar el inmueble.',
  detalleInmueble: 'No se ha podido cargar el inmueble.',
  /** 409 del PUT/DELETE: el inmueble ya tenía `eliminado_en` (soft-delete). */
  inmuebleYaEliminado: 'Ese inmueble ya estaba eliminado.',
  /** Éxito del alta: el backend responde 201 con Location. */
  inmuebleCreado: 'Inmueble creado correctamente.',
  /** Éxito del PUT. */
  inmuebleActualizado: 'Inmueble actualizado correctamente.',
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

/** Resultado de clasificar un error de envío del formulario de inmuebles. */
export type FalloEnvioInmueble = Readonly<{
  /** `errors[]` del 400 ya mapeados a campos conocidos (vacío si no hay). */
  campos: Readonly<Record<string, string>>;
  /** Mensaje global para el `role="alert"`; `null` si no queda nada suelto. */
  global: string | null;
}>;

/**
 * Un 400 con `errors[]` se muestra EN el campo correspondiente; lo que NO
 * encaja con `campos` (fields desconocidos o sin `field`) NO se descarta: se
 * conserva como aviso global junto a los errores de campo. El aviso global es
 * además el fallback cuando no hay ningún error de campo conocido (400 sin
 * `errors[]`, 409, 401/403, red, etc.).
 */
export function clasificarErrorEnvioInmueble(
  causa: unknown,
  campos: readonly string[],
  mensajePorDefecto: string,
): FalloEnvioInmueble {
  if (esApiError(causa)) {
    if (causa.status === 409) {
      return { campos: {}, global: MENSAJES.inmuebleYaEliminado };
    }
    if (causa.status === 400 && causa.errors.length > 0) {
      const deCampo = erroresDeCampoInmueble(causa.errors, campos);
      if (Object.keys(deCampo).length > 0) {
        const sueltos = causa.errors
          .filter((error) => error.field === undefined || !campos.includes(error.field))
          .map((error) => error.message)
          .join(' · ');
        return { campos: deCampo, global: sueltos.length > 0 ? sueltos : null };
      }
    }
  }
  return { campos: {}, global: mensajeDeError(causa, mensajePorDefecto) };
}
