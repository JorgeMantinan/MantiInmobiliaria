/**
 * Textos por defecto del feature de alertas (cuando el error no trae nada
 * legible). Las helpers para traducir un `unknown` viven en `@/lib/errores`
 * (compartidas, follow-up #98).
 */
export const MENSAJES = {
  alertas: 'No se han podido cargar tus alertas.',
  guardar: 'No se ha podido guardar la alerta; vuelve a intentarlo.',
  actualizar: 'No se ha podido actualizar la alerta.',
  eliminar: 'No se ha podido eliminar la alerta.',
} as const;
