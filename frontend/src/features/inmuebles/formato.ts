/**
 * Formateo y etiquetas de inmuebles (presentación, sin acceso a datos).
 */
import type {
  EstadoInmueble,
  InmuebleDetalle,
  InmuebleResumen,
  TipoInmueble,
} from "./types";

const FORMATEADOR_PRECIO = new Intl.NumberFormat("es-ES", {
  style: "currency",
  currency: "EUR",
  maximumFractionDigits: 0,
});

/** `245000` → `245.000 €`. */
export function formatoPrecio(precio: number): string {
  return FORMATEADOR_PRECIO.format(precio);
}

export const ETIQUETAS_ESTADO: Readonly<Record<EstadoInmueble, string>> = {
  VENTA: "En venta",
  ALQUILER: "En alquiler",
};

export const ETIQUETAS_TIPO: Readonly<Record<TipoInmueble, string>> = {
  PISO: "Piso",
  CASA: "Casa",
  ATICO: "Ático",
  DUPLEX: "Dúplex",
  ESTUDIO: "Estudio",
  LOCAL: "Local",
  TERRENO: "Terreno",
};

/**
 * Dirección en texto de la tarjeta. El resumen del listado no incluye el
 * campo `direccion` (sólo va en el detalle), así que se compone con
 * municipio y provincia.
 */
export function direccionDe(inmueble: Pick<InmuebleResumen, "municipio" | "provincia">): string {
  const { municipio, provincia } = inmueble;
  return provincia !== "" && provincia !== municipio ? `${municipio}, ${provincia}` : municipio;
}

/** Dirección completa de la ficha: vía, código postal, municipio y provincia. */
export function direccionCompleta(inmueble: InmuebleDetalle): string {
  const nucleo = [inmueble.codigoPostal, inmueble.municipio]
    .map((parte) => parte ?? "")
    .filter((parte) => parte !== "")
    .join(" ");
  const provincia =
    inmueble.provincia !== "" && inmueble.provincia !== inmueble.municipio
      ? inmueble.provincia
      : "";
  return [inmueble.direccion, nucleo, provincia].filter((parte) => parte !== "").join(", ");
}

/** Datos rápidos de la tarjeta: `96 m² · 3 hab. · 1 baño`. */
export function datosDe(inmueble: InmuebleResumen): string {
  const partes: string[] = [];
  if (inmueble.superficieM2 !== null) partes.push(`${inmueble.superficieM2} m²`);
  if (inmueble.habitaciones !== null) partes.push(`${inmueble.habitaciones} hab.`);
  if (inmueble.banios !== null) partes.push(`${inmueble.banios} ${inmueble.banios === 1 ? "baño" : "baños"}`);
  return partes.join(" · ");
}
