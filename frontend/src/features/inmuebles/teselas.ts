/**
 * Configuración compartida de los tiles de mapa: la mini-tarjeta estática
 * (`mapa-tarjeta`) y el mapa interactivo de la ficha (`mapa-detalle`).
 *
 * Proveedor por DEFECTO: OpenStreetMap estándar (sin cuenta, sin clave, sin
 * datos personales — solo exige atribución "© OpenStreetMap contributors" y
 * uso visual normal, sin descargas masivas: ver
 * https://operations.osmfoundation.org/policies/tiles/).
 *
 * Si se define `NEXT_PUBLIC_CARTO_KEY` (opcional, pública por diseño; restringir
 * por dominio en carto.com), se usan los tiles Voyager de CARTO en su lugar.
 * El valor real vive SOLO en `frontend/.env.local` y nunca se comitea.
 */
const CLAVE_CARTO = process.env.NEXT_PUBLIC_CARTO_KEY ?? "";
const USAR_CARTO = CLAVE_CARTO !== "";

const PLANTILLA_OSM = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const PLANTILLA_CARTO = `https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key=${CLAVE_CARTO}`;

/** Plantilla XYZ de tiles (formato Leaflet: `{z}/{x}/{y}`). */
export const PLANTILLA_TESELA = USAR_CARTO ? PLANTILLA_CARTO : PLANTILLA_OSM;

/** Atribución obligatoria en cualquier mapa, según el proveedor activo. */
export const ATRIBUCION = USAR_CARTO
  ? "© OpenStreetMap contributors, © CARTO"
  : "© OpenStreetMap contributors";

/** URL de un tile concreto (para el `<img>` de la tarjeta). */
export function urlTesela(zoom: number, x: number, y: number): string {
  return USAR_CARTO
    ? `https://basemaps.cartocdn.com/rastertiles/voyager/${zoom}/${x}/${y}.png?key=${CLAVE_CARTO}`
    : `https://tile.openstreetmap.org/${zoom}/${x}/${y}.png`;
}
