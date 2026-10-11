/**
 * Acceso al API público de precios de zona (GET) vía `lib/api.ts`.
 *
 * Endpoint anónimo: sólo lectura de datos abiertos del Ministerio. Un 404
 * significa "ese municipio no tiene ningún dato publicado" y la ficha oculta
 * la sección en vez de mostrar un error.
 */
import { apiFetch } from "@/lib/api";
import type { PreciosZona } from "./types";

/** `GET /api/v1/mercado?codigoINE=#####` — precios oficiales de la zona. */
export async function obtenerPreciosZona(codigoIne: string): Promise<PreciosZona> {
  return apiFetch<PreciosZona>(`/api/v1/mercado?codigoINE=${encodeURIComponent(codigoIne)}`);
}
