/**
 * Construcción y lectura de la query del listado de inmuebles.
 *
 * Un único punto de verdad para la URL (filtros sincronizados con `useRouter`)
 * y para la query que se manda a `GET /api/v1/inmuebles`, así el navegador y
 * la API ven exactamente lo mismo.
 */
import type {
  EstadoInmueble,
  FiltrosInmuebles,
  OrdenPrecio,
  TipoInmueble,
  VistaListado,
} from "./types";

/** Tamaño de página por defecto que usa el cliente (el backend admite 1..50). */
export const LIMITE_PAGINA = 20;

/**
 * Techo de precio consultable en euros. El backend no acota (BigDecimal), pero
 * un precio por encima de esto es un error de dedo y sólo genera queries sin
 * sentido; el input también lo declara como `max`.
 */
export const PRECIO_MAXIMO_CONSULTA = 100_000_000;

/**
 * Techo de `habitacionesMin`: el backend lo recibe como `Short`, así que por
 * encima de 32767 Spring ya responde 400 por no poder convertirlo. 0..100
 * cubre cualquier vivienda real y además evita ese 400.
 */
export const HABITACIONES_MAX = 100;

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

/** Sólo dígitos dentro de `[0, maximo]`; fuera de rango o sucio ⇒ se ignora. */
function enteroEnRango(valor: string | null, maximo: number): string | undefined {
  if (valor === null) return undefined;
  const limpio = valor.trim();
  if (!/^\d+$/.test(limpio)) return undefined;
  return Number(limpio) <= maximo ? limpio : undefined;
}

function texto(valor: string | null, maximo: number): string | undefined {
  if (valor === null) return undefined;
  const limpio = valor.trim();
  if (limpio.length === 0) return undefined;
  // El backend acota el municipio a 120: recortamos aquí en vez de mandar un 400.
  return limpio.slice(0, maximo);
}

/**
 * Querystring de `GET /api/v1/inmuebles` con los parámetros definidos, en
 * orden estable. Devuelve `''` sin filtros o `?a=b&c=d`.
 *
 * Nunca serializa un rango invertido: el backend responde 400 si
 * `precioMin > precioMax`, así que en ese caso no se envía ninguno de los dos.
 */
export function construirQueryInmuebles(
  filtros: FiltrosInmuebles,
  opciones: OpcionesQuery = {},
): string {
  const params = new URLSearchParams();
  if (filtros.estado) params.set("estado", filtros.estado);
  if (filtros.tipo) params.set("tipo", filtros.tipo);
  if (filtros.municipio) params.set("municipio", filtros.municipio);
  if (!rangoPrecioInvertido(filtros)) {
    if (filtros.precioMin) params.set("precioMin", filtros.precioMin);
    if (filtros.precioMax) params.set("precioMax", filtros.precioMax);
  }
  if (filtros.habitacionesMin) params.set("habitacionesMin", filtros.habitacionesMin);
  if (filtros.destacado !== undefined) params.set("destacado", String(filtros.destacado));
  if (filtros.sort) params.set("sort", filtros.sort);
  if (opciones.cursor) params.set("cursor", opciones.cursor);
  if (opciones.limit !== undefined) params.set("limit", String(opciones.limit));

  const query = params.toString();
  return query.length === 0 ? "" : `?${query}`;
}

/** `true` cuando los filtros traen `precioMin > precioMax` (query inválida). */
function rangoPrecioInvertido(filtros: FiltrosInmuebles): boolean {
  return (
    filtros.precioMin !== undefined &&
    filtros.precioMax !== undefined &&
    Number(filtros.precioMin) > Number(filtros.precioMax)
  );
}

/**
 * `true` si unos parámetros brutos (URL o `FormData`) traen un rango de precios
 * invertido. La barra de filtros lo usa para avisar y NO navegar en ese caso.
 */
export function rangoPrecioInconsistente(params: Parametros): boolean {
  const minimo = enteroEnRango(params.get("precioMin"), PRECIO_MAXIMO_CONSULTA);
  const maximo = enteroEnRango(params.get("precioMax"), PRECIO_MAXIMO_CONSULTA);
  return minimo !== undefined && maximo !== undefined && Number(minimo) > Number(maximo);
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

  const precioMin = enteroEnRango(params.get("precioMin"), PRECIO_MAXIMO_CONSULTA);
  const precioMax = enteroEnRango(params.get("precioMax"), PRECIO_MAXIMO_CONSULTA);
  const habitacionesMin = enteroEnRango(params.get("habitacionesMin"), HABITACIONES_MAX);
  if (precioMin !== undefined) filtros.precioMin = precioMin;
  if (precioMax !== undefined) filtros.precioMax = precioMax;
  if (habitacionesMin !== undefined) filtros.habitacionesMin = habitacionesMin;

  // El backend responde 400 si precioMin > precioMax: descartamos el par
  // completo (en vez de sólo uno) para no mandar una query que se sabe inválida.
  if (rangoPrecioInvertido(filtros)) {
    delete filtros.precioMin;
    delete filtros.precioMax;
  }

  return filtros;
}

/**
 * Vista del listado leída de la URL (`?vista=mapa`). Cualquier valor distinto
 * de `mapa` cae a `lista`: el enlace siempre es válido y compartible.
 */
export function leerVista(params: Parametros): VistaListado {
  return params.get("vista") === "mapa" ? "mapa" : "lista";
}

/**
 * Query de navegación del listado: los filtros serializados MÁS la vista, que
 * así se conserva al aplicar o limpiar filtros. La vista NO viaja a la query
 * del backend (`construirQueryInmuebles` la ignora), sólo vive en la URL.
 */
export function queryListado(filtros: FiltrosInmuebles, vista: VistaListado): string {
  const params = new URLSearchParams(construirQueryInmuebles(filtros).replace(/^\?/, ""));
  if (vista === "mapa") params.set("vista", "mapa");
  const query = params.toString();
  return query.length === 0 ? "" : `?${query}`;
}
