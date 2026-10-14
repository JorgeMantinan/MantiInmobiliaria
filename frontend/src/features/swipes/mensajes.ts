/**
 * Textos por defecto del feature de swipes (cuando el error no trae nada
 * legible). Las helpers para traducir un `unknown` viven en `@/lib/errores`
 * (compartidas, follow-up #98).
 */
export const MENSAJES = {
  deck: 'No se han podido cargar las viviendas para revisar.',
  favoritos: 'No se han podido cargar tus favoritos.',
  decision: 'No se ha podido guardar tu decisión; vuelve a intentarlo.',
  quitar: 'No se ha podido quitar de favoritos.',
} as const;
