/**
 * DTOs de `/api/v1/alertas` (issue #96).
 *
 * Sólo tipos: la serialización de filtros vive en `serializacion.ts` y las
 * llamadas en `api.ts`.
 */
import type { EstadoInmueble, TipoInmueble } from '@/features/inmuebles/types';

/**
 * Filtros guardados en una alerta: los MISMOS del listado público, pero con
 * los numéricos como número (así viajan en el body JSON de la API; el listado
 * los trae como texto porque llegan de la URL).
 */
export type FiltrosAlerta = Readonly<{
  estado?: EstadoInmueble;
  tipo?: TipoInmueble;
  municipio?: string;
  precioMin?: number;
  precioMax?: number;
  habitacionesMin?: number;
  destacado?: boolean;
}>;

/** `AlertaResponse` del backend. */
export type AlertaBusqueda = Readonly<{
  id: number;
  nombre: string;
  filtros: FiltrosAlerta;
  activa: boolean;
  /** ISO-8601 del último aviso; null si aún no avisó. */
  ultimoAvisoEn: string | null;
  creadoEn: string;
  actualizadoEn: string;
}>;

/** Body del PATCH: ambos campos opcionales (edición parcial). */
export type AlertaCambios = Readonly<{
  nombre?: string;
  activa?: boolean;
}>;

/**
 * `AlertaPage` del backend (follow-up #98): envelope del listado, igual que
 * `InmueblePage`/`SwipePage`. Sin paginación real (techo de 50 por usuario),
 * por eso no hay `nextCursor`.
 */
export type AlertaPage = Readonly<{
  content: readonly AlertaBusqueda[];
  totalElements: number;
}>;
