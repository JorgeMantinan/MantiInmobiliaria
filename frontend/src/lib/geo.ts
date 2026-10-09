/**
 * Conversión de coordenadas WGS84 → teselas (tiles) XYZ / Web Mercator.
 *
 * Matemática pura, sin dependencias: la usa la tarjeta de inmueble para
 * pintar un mini-mapa ESTÁTICO (un único tile de CARTO en z16) con el pin
 * desplazado a los píxeles exactos, sin cargar el JS de Leaflet.
 *
 * Referencia: https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
 */

/** Píxeles por tesela (estándar Slippy map). */
export const TAMANO_TESELA = 256;

/** Latitud máxima de la proyección Web Mercator (grados). */
const LATITUD_MAXIMA = 85.05112878;

/** Tesela que contiene la coordenada, con el offset en píxeles dentro de ella. */
export type Tesela = Readonly<{
  x: number;
  y: number;
  /** Distancia en píxeles (0..256) del punto al borde izquierdo de la tesela. */
  offsetX: number;
  /** Distancia en píxeles (0..256) del punto al borde superior de la tesela. */
  offsetY: number;
}>;

function limitar(valor: number, min: number, max: number): number {
  return Math.min(Math.max(valor, min), max);
}

/**
 * Índice de tesela (entero, dentro de `0..maximo-1`) y desplazamiento en
 * píxeles dentro de esa tesela (`[0, 256)`), a partir del valor global
 * (fraccionado) en la retícula de `maximo` teselas.
 */
function indiceYOffset(global: number, maximo: number): readonly [number, number] {
  const indice = limitar(Math.floor(global), 0, maximo - 1);
  const relativo = limitar(global - indice, 0, 1 - Number.EPSILON * 8);
  return [indice, relativo * TAMANO_TESELA] as const;
}

/**
 * Devuelve la tesela de `zoom` que contiene `(lat, lng)` y el offset en
 * píxeles del punto dentro de esa tesela.
 *
 * - `y` decrece al aumentar la latitud (el origen de las teselas está arriba).
 * - Las coordenadas fuera de rango se recortan en vez de lanzar error.
 */
export function latLngATile(lat: number, lng: number, zoom: number): Tesela {
  const n = 2 ** zoom;
  const latitud = limitar(lat, -LATITUD_MAXIMA, LATITUD_MAXIMA);
  const longitud = limitar(lng, -180, 180);

  const xGlobal = ((longitud + 180) / 360) * n;
  const latRad = (latitud * Math.PI) / 180;
  const yGlobal = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n;

  const [x, offsetX] = indiceYOffset(xGlobal, n);
  const [y, offsetY] = indiceYOffset(yGlobal, n);
  return { x, y, offsetX, offsetY };
}
