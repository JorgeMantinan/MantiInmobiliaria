/**
 * Acceso al API público de inmuebles (GET) vía `lib/api.ts`.
 *
 * El cliente siempre pasa por `apiFetch`: base URL, cookie de credenciales,
 * Bearer si hay sesión y un único refresh en 401 (los endpoints de inmuebles
 * son anónimos, pero así el código es uniforme).
 */
import { apiFetch } from "@/lib/api";
import { construirQueryInmuebles, LIMITE_PAGINA } from "./filtros";
import type { FiltrosInmuebles, InmuebleDetalle, PaginaInmuebles } from "./types";

type OpcionesListado = Readonly<{
  /** Cursor opaco de la página anterior (`nextCursor`). */
  cursor?: string | null;
  /** Tamaño de página; por defecto `LIMITE_PAGINA`. */
  limit?: number;
}>;

/** `GET /api/v1/inmuebles` — página de resultados con filtros y cursor. */
export async function listarInmuebles(
  filtros: FiltrosInmuebles,
  opciones: OpcionesListado = {},
): Promise<PaginaInmuebles> {
  const query = construirQueryInmuebles(filtros, {
    cursor: opciones.cursor,
    limit: opciones.limit ?? LIMITE_PAGINA,
  });
  return apiFetch<PaginaInmuebles>(`/inmuebles${query}`);
}

/** `GET /api/v1/inmuebles/{id}` — detalle completo; 404 ⇒ `ApiError`. */
export async function obtenerInmueble(id: number): Promise<InmuebleDetalle> {
  return apiFetch<InmuebleDetalle>(`/inmuebles/${id}`);
}
