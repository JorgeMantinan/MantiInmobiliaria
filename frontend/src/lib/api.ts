/**
 * Cliente fetch tipado de la API Java (Spring Boot).
 *
 * - Base URL y access token en memoria viven en `lib/auth.ts` (módulo hoja).
 * - `credentials: 'include'` en todas las peticiones para que viaje la cookie
 *   httpOnly de refresh (path `/api/v1/auth`).
 * - 401 ⇒ UN único reintento tras `POST /auth/refresh`; sin bucles: la marca
 *   `yaReintentado` impide un segundo refresh. Si el refresh rechaza el
 *   servidor (401/403) se anuncia la pérdida de sesión; si falla la RED (0)
 *   la sesión en memoria se conserva.
 */
import type { ErrorCampo } from "@/features/auth/types";
import {
  API_BASE,
  anunciarPerdidaDeSesion,
  obtenerAccessToken,
  refrescarSesion,
} from "@/lib/auth";

/** URL base de la API (definida en `lib/auth.ts`; guard de producción incluido). */
export { API_BASE };

export type OpcionesApi = Readonly<{
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Readonly<Record<string, string>>;
  /** Marca interna: la petición ya pasó por el reintento de refresh. */
  yaReintentado?: boolean;
  /** true ⇒ nunca intenta refresh (endpoints de credenciales: login/registro). */
  sinRefresco?: boolean;
}>;

type ProblemDetail = Readonly<{
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: readonly ErrorCampo[];
}>;

/** Excepción tipada: todo error de la API (RFC 9457) se parsea aquí. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly errors: readonly ErrorCampo[];

  constructor(status: number, title: string, detail: string, errors: readonly ErrorCampo[] = []) {
    super(`${title}: ${detail}`);
    this.name = "ApiError";
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.errors = errors;
  }
}

export function esApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

// ---------------------------------------------------------------------------
// Núcleo
// ---------------------------------------------------------------------------

/**
 * Combina `ruta` con la base. Sólo se aceptan rutas RELATIVAS: una URL
 * absoluta (`https://…`, `//host/…`, `mailto:`…) es un error de programación
 * y aquí se rechaza explícitamente.
 */
export function combinarUrl(ruta: string): string {
  if (/^[a-z][a-z0-9+.-]*:/i.test(ruta) || ruta.startsWith("//")) {
    throw new Error("ruta externa no permitida");
  }
  return `${API_BASE}${ruta.startsWith("/") ? "" : "/"}${ruta}`;
}

function esEndpointDeCredenciales(ruta: string): boolean {
  return (
    ruta.endsWith("/auth/login") ||
    ruta.endsWith("/auth/register") ||
    ruta.endsWith("/auth/oauth2/token")
  );
}

function cabecerasPara(opciones: OpcionesApi): Record<string, string> {
  const cabeceras: Record<string, string> = { Accept: "application/json" };
  if (opciones.body !== undefined) {
    cabeceras["Content-Type"] = "application/json";
  }
  const token = obtenerAccessToken();
  if (token !== null) {
    cabeceras.Authorization = `Bearer ${token}`;
  }
  return { ...cabeceras, ...opciones.headers };
}

async function leerCuerpo<T>(respuesta: Response): Promise<T | undefined> {
  if (respuesta.status === 204) {
    return undefined;
  }
  const texto = await respuesta.text();
  if (texto.length === 0) {
    return undefined;
  }
  try {
    return JSON.parse(texto) as T;
  } catch {
    return undefined;
  }
}

async function apiErrorDesde(respuesta: Response): Promise<ApiError> {
  const problema = (await leerCuerpo<ProblemDetail>(respuesta)) ?? {};
  return new ApiError(
    respuesta.status,
    problema.title ?? "Error de la API",
    problema.detail ?? `El servidor ha devuelto un error (${respuesta.status}).`,
    problema.errors ?? [],
  );
}

/** Traduce el resultado de un refresh fallido a `ApiError` (nunca notifica sesión). */
function errorDeRefresh(status: number): ApiError {
  if (status === 0) {
    // Red caída: la sesión en memoria sigue siendo válida, no se anuncia.
    return new ApiError(0, "Error de conexión", "No se ha podido contactar con el servidor.");
  }
  if (status === 401 || status === 403) {
    anunciarPerdidaDeSesion();
    return new ApiError(401, "Sesión caducada", "Tu sesión ha caducado; vuelve a iniciar sesión.");
  }
  return new ApiError(status, "Error de la API", `No se ha podido renovar la sesión (${status}).`);
}

/**
 * Fetch tipado con base URL, cookie de credenciales, Bearer y un único
 * reintento en 401 tras refrescar la sesión.
 */
export async function apiFetch<T>(ruta: string, opciones: OpcionesApi = {}): Promise<T> {
  const respuesta = await peticion(ruta, opciones);

  if (respuesta.ok) {
    return (await leerCuerpo<T>(respuesta)) as T;
  }

  if (
    respuesta.status === 401 &&
    !opciones.yaReintentado &&
    !opciones.sinRefresco &&
    !esEndpointDeCredenciales(ruta)
  ) {
    const resultado = await refrescarSesion();
    if (!resultado.ok) {
      throw errorDeRefresh(resultado.status);
    }
    // Un sólo reintento: si vuelve a salir 401, la sesión ya no sirve.
    return apiFetch<T>(ruta, { ...opciones, yaReintentado: true });
  }

  if (respuesta.status === 401 && opciones.yaReintentado) {
    anunciarPerdidaDeSesion();
  }

  throw await apiErrorDesde(respuesta);
}

async function peticion(ruta: string, opciones: OpcionesApi): Promise<Response> {
  // Fuera del try: un error de `combinarUrl` es de programación, no de red.
  const url = combinarUrl(ruta);
  try {
    return await fetch(url, {
      method: opciones.method ?? "GET",
      credentials: "include",
      headers: cabecerasPara(opciones),
      body: opciones.body === undefined ? undefined : JSON.stringify(opciones.body),
    });
  } catch {
    throw new ApiError(0, "Error de conexión", "No se ha podido contactar con el servidor.");
  }
}
