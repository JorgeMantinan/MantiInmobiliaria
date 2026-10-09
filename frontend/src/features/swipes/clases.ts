/**
 * Clases Tailwind compartidas del feature de swipes (DRY): enlaces de acción
 * con tokens del sistema madera, sin hexadecimales fuera de la paleta.
 */

/** Enlace/acción primaria: nogal sobre crema, focus ring visible. */
export const ENLACE_PRIMARIO =
  'rounded-md bg-acento-nogal px-4 py-2 text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover';

/** Enlace/acción secundaria: borde fino sobre superficie transparente. */
export const ENLACE_SECUNDARIO =
  'rounded-md border border-borde px-4 py-2 text-sm font-medium text-texto transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-superficie-alta';
