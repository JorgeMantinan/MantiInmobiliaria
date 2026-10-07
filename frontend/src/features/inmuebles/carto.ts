/**
 * Configuración compartida de los mapas de CARTO: la mini-tarjeta estática
 * (`mapa-tarjeta`) y el mapa interactivo de la ficha (`mapa-detalle`).
 *
 * La clave es PÚBLICA (`NEXT_PUBLIC_*`): viaja en el bundle del navegador y
 * por eso hay que restringirla por dominio en carto.com. Nunca se comitea el
 * valor real (vive en `frontend/.env.local`).
 */
export const CLAVE_CARTO = process.env.NEXT_PUBLIC_CARTO_KEY ?? "";

/** Atribución obligatoria de OpenStreetMap/CARTO en cualquier mapa. */
export const ATRIBUCION = "© OpenStreetMap contributors, © CARTO";

/** Plantilla XYZ de tiles Voyager (formato Leaflet: `{z}/{x}/{y}`). */
export const PLANTILLA_TESELA_CARTO = `https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key=${CLAVE_CARTO}`;

/** URL de un tile concreto (para el `<img>` de la tarjeta). */
export function urlTeselaCarto(zoom: number, x: number, y: number): string {
  return `https://basemaps.cartocdn.com/rastertiles/voyager/${zoom}/${x}/${y}.png?key=${CLAVE_CARTO}`;
}

let avisoEmitido = false;

/**
 * Avisa UNA sola vez en development si falta la clave: sin ella CARTO responde
 * 403 y el usuario ve el mapa vacío sin ninguna pista de por qué.
 */
export function avisarClaveCartoAusente(): void {
  if (CLAVE_CARTO !== "" || avisoEmitido || process.env.NODE_ENV === "production") return;
  avisoEmitido = true;
  console.warn(
    "[mapas] Falta NEXT_PUBLIC_CARTO_KEY en frontend/.env.local: los tiles de CARTO no cargarán.",
  );
}
