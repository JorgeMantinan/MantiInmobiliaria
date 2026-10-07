/**
 * Construcción y lectura de la query del listado de inmuebles.
 *
 * Un único punto de verdad para la URL (filtros sincronizados con `useRouter`)
 * y para la query que se manda a `GET /api/v1/inmuebles`, así el navegador y
 * la API ven exactamente lo mismo.
 */
import type { EstadoInmueble, FiltrosInmuebles, OrdenPrecio, TipoInmueble } from "./types";

/** Tamaño de página por defecto que usa el cliente (el backend admite 1..50). */
export const LIMITE_PAGINA = 20;

const ESTADOS: readonly EstadoInmueble[] = ["VENTA", "ALQUILER"];
const TIPOS: readonly TipoInmueble[] = [
  "PISO",
  "CASA",
  "ATICO",
  "DUPLEX",
  "ESTUDIO",
  "LOCAL",
  "TERRENO",
];
const ORDENES: readonly OrdenPrecio[] = ["precio_asc", "precio_desc"];

type OpcionesQuery = Readonly<{
  /** Cursor opaco de la página siguiente (`nextCursor` de la anterior). */
  cursor?: string | null;
  /** Tamaño de página; si se omite no se envía y manda el default del backend. */
  limit?: number;
}>;

/** Cualquier contenedor de parámetros con `.get()` (`URLSearchParams`). */
type Parametros = Readonly<{ get(nombre: string): string | null }>;

/** Versión mutable de `FiltrosInmuebles`, para ir rellenándola campo a campo. */
type FiltrosParciales = { -readonly [K in keyof FiltrosInmuebles]?: FiltrosInmuebles[K] };

function elegido<T extends string>(valor: string | null, permitidos: readonly T[]): T | undefined {
  if (valor === null || valor === "") return undefined;
  return permitidos.includes(valor as T) ? (valor as T) : undefined;
}

/** Sólo dígitos (con opcionalmente espacios recortados); si no, se ignora. */
function numeroEntero(valor: string | null): string | undefined {
  if (valor === null) return undefined;
  const limpio = valor.trim();
  return /^\d+$/.test(limpio) ? limpio : undefined;
}

function texto(valor: string | null, maximo: number): string | undefined {
  if (valor === null) return undefined;
  const limpio = valor.trim();
  if (limpio.length === 0 || limpio.length > maximo) return undefined;
  return limpio;
}

/**
 * Querystring de `GET /api/v1/inmuebles` con los parámetros definidos, en
 * orden estable. Devuelve `''` sin filtros o `?a=b&c=d`.
 */
export function construirQueryInmuebles(
  filtros: FiltrosInmuebles,
  opciones: OpcionesQuery = {},
): string {
  const params = new URLSearchParams();
  if (filtros.estado) params.set("estado", filtros.estado);
  if (filtros.tipo) params.set("tipo", filtros.tipo);
  if (filtros.municipio) params.set("municipio", filtros.municipio);
  if (filtros.precioMin) params.set("precioMin", filtros.precioMin);
  if (filtros.precioMax) params.set("precioMax", filtros.precioMax);
  if (filtros.habitacionesMin) params.set("habitacionesMin", filtros.habitacionesMin);
  if (filtros.destacado !== undefined) params.set("destacado", String(filtros.destacado));
  if (filtros.sort) params.set("sort", filtros.sort);
  if (opciones.cursor) params.set("cursor", opciones.cursor);
  if (opciones.limit !== undefined) params.set("limit", String(opciones.limit));

  const query = params.toString();
  return query.length === 0 ? "" : `?${query}`;
}

/**
 * Filtros validados a partir de parámetros de URL o de un `FormData`.
 * Los valores desconocidos o inválidos se descartan (nunca se manda una query
 * que el backend rechazaría con un 400).
 */
export function leerFiltros(params: Parametros): FiltrosInmuebles {
  const filtros: FiltrosParciales = {};

  const estado = elegido(params.get("estado"), ESTADOS);
  const tipo = elegido(params.get("tipo"), TIPOS);
  const sort = elegido(params.get("sort"), ORDENES);
  const municipio = texto(params.get("municipio"), 120);
  const destacadoRaw = params.get("destacado");

  if (estado) filtros.estado = estado;
  if (tipo) filtros.tipo = tipo;
  if (sort) filtros.sort = sort;
  if (municipio) filtros.municipio = municipio;
  if (destacadoRaw === "true" || destacadoRaw === "false") {
    filtros.destacado = destacadoRaw === "true";
  }

  const precioMin = numeroEntero(params.get("precioMin"));
  const precioMax = numeroEntero(params.get("precioMax"));
  const habitacionesMin = numeroEntero(params.get("habitacionesMin"));
  if (precioMin !== undefined) filtros.precioMin = precioMin;
  if (precioMax !== undefined) filtros.precioMax = precioMax;
  if (habitacionesMin !== undefined) filtros.habitacionesMin = habitacionesMin;

  return filtros;
}
