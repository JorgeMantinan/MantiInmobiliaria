/**
 * DTOs de `GET /api/v1/inmuebles` ajustados EXACTOS a `docs/openapi/openapi.yaml`
 * (`InmuebleSummary`, `InmuebleResponse`, `InmueblePage`).
 *
 * Sólo tipos: la lógica de consulta vive en `filtros.ts` y las llamadas en `api.ts`.
 */

/** Estado comercial de un inmueble. */
export type EstadoInmueble = "VENTA" | "ALQUILER";

/** Tipo de inmueble admitido por el backend. */
export type TipoInmueble =
  | "PISO"
  | "CASA"
  | "ATICO"
  | "DUPLEX"
  | "ESTUDIO"
  | "LOCAL"
  | "TERRENO";

/** Orden por precio admitido por el endpoint (`sort`). */
export type OrdenPrecio = "precio_asc" | "precio_desc";

/** `InmuebleSummary`: fila ligera de listado (sin `descripcion` ni `direccion`). */
export type InmuebleResumen = Readonly<{
  id: number;
  titulo: string;
  precio: number;
  tipo: TipoInmueble;
  estado: EstadoInmueble;
  superficieM2: number | null;
  habitaciones: number | null;
  banios: number | null;
  municipio: string;
  provincia: string;
  imagenUrl: string | null;
  destacado: boolean;
  lat: number;
  lng: number;
  createdAt: string;
}>;

/** `InmuebleResponse`: detalle completo de la ficha. */
export type InmuebleDetalle = Readonly<{
  id: number;
  titulo: string;
  descripcion: string | null;
  precio: number;
  superficieM2: number | null;
  habitaciones: number | null;
  banios: number | null;
  tipo: TipoInmueble;
  estado: EstadoInmueble;
  direccion: string;
  codigoPostal: string | null;
  municipio: string;
  provincia: string;
  /** Código INE de 5 dígitos; null si no está catalogado (precios MIVAU). */
  codigoIne: string | null;
  lat: number;
  lng: number;
  imagenUrl: string | null;
  destacado: boolean;
  createdAt: string;
  updatedAt: string | null;
}>;

/** `InmueblePage`: página paginada por cursor. */
export type PaginaInmuebles = Readonly<{
  content: readonly InmuebleResumen[];
  nextCursor: string | null;
  totalElements: number;
}>;

/**
 * Vista del listado público: tarjetas (`lista`) o mapa con un marcador por
 * inmueble cargado. Viaja en la URL (`?vista=mapa`) para que el enlace sea
 * compartible; NO es un filtro, no se envía al backend.
 */
export type VistaListado = "lista" | "mapa";

/**
 * Filtros del listado público. Los numéricos viajan como texto porque
 * llegan de la URL y sólo se serializan a la query.
 */
export type FiltrosInmuebles = Readonly<{
  estado?: EstadoInmueble;
  tipo?: TipoInmueble;
  municipio?: string;
  precioMin?: string;
  precioMax?: string;
  habitacionesMin?: string;
  destacado?: boolean;
  sort?: OrdenPrecio;
}>;
