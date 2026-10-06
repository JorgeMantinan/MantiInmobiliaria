/**
 * Cliente fetch tipado de la API Java (Spring Boot).
 *
 * - Access token JWT **en memoria** (variable de módulo): nunca en
 *   localStorage/sessionStorage (skill `config-secrets`/`seguridad`).
 * - `credentials: 'include'` en todas las peticiones para que viaje la cookie
 *   httpOnly de refresh (path `/api/v1/auth`).
 * - 401 ⇒ UN único reintento tras `POST /auth/refresh`; si el refresh falla se
 *   limpia la sesión y se lanza `ApiError` de sesión caducada (sin bucles).
 */

const BASE_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(
  /\/+$/,
  "",
);

/** URL base de la API (para redirecciones fuera de `apiFetch`, p. ej. OAuth2). */
export const API_BASE = BASE_URL;

/** Ruta RAÍZ del backend (no está bajo `/api/v1`). Concatenación a propósito:
 *  el template literal con interpolación confunde al lint de Next. */
export const RUTA_OAUTH2_GOOGLE = API_BASE + "/oauth2/authorization/google";

const RUTAS = {
  registro: "/api/v1/auth/register",
  login: "/api/v1/auth/login",
  refresh: "/api/v1/auth/refresh",
  logout: "/api/v1/auth/logout",
  yo: "/api/v1/auth/me",
  canjeGoogle: "/api/v1/auth/oauth2/token",
} as const;

/** Endpoints autenticados por cookie: exigen `X-Requested-With: XMLHttpRequest`. */
const CABECERA_CSRF = { "X-Requested-With": "XMLHttpRequest" } as const;

// ---------------------------------------------------------------------------
// Tipos (ajustados EXACTOS a docs/openapi/openapi.yaml)
// ---------------------------------------------------------------------------

export type Usuario = Readonly<{
  id: number;
  email: string;
  nombre: string;
  rol: string;
  activo: boolean;
  createdAt: string;
}>;

export type AuthResponse = Readonly<{
  accessToken: string;
  expiresIn: number;
  user: Usuario;
}>;

export type Credenciales = Readonly<{ email: string; password: string }>;

export type DatosRegistro = Readonly<{ nombre: string; email: string; password: string }>;

export type ErrorCampo = Readonly<{ field?: string; message: string }>;

type ProblemDetail = Readonly<{
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: readonly ErrorCampo[];
}>;

export type OpcionesApi = Readonly<{
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Readonly<Record<string, string>>;
  /** Marca interna: la petición ya pasó por el reintento de refresh. */
  yaReintentado?: boolean;
  /** true ⇒ nunca intenta refresh (endpoints de credenciales: login/registro). */
  sinRefresco?: boolean;
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
// Sesión en memoria + observadores (para que el contexto reaccione a un
// caducado detectado por cualquier petición, no sólo por /auth/me)
// ---------------------------------------------------------------------------

let accessToken: string | null = null;

type ObservadorSesion = () => void;
const observadores = new Set<ObservadorSesion>();

/** Se suscribe a la pérdida de sesión. Devuelve la función de baja. */
export function observarPerdidaDeSesion(observador: ObservadorSesion): () => void {
  observadores.add(observador);
  return () => {
    observadores.delete(observador);
  };
}

export function haySesion(): boolean {
  return accessToken !== null;
}

/** Guarda la sesión devuelta por login/registro/refresh/canje de Google. */
export function guardarSesion(auth: AuthResponse): void {
  accessToken = auth.accessToken;
}

/** Borra el access token en memoria. */
export function limpiarSesionLocal(): void {
  accessToken = null;
}

function anunciarPerdidaDeSesion(): void {
  accessToken = null;
  observadores.forEach((observador) => observador());
}

// ---------------------------------------------------------------------------
// Refresh (con deduplicación de peticiones concurrentes)
// ---------------------------------------------------------------------------

let refreshEnCurso: Promise<AuthResponse | null> | null = null;

async function realizarRefresh(): Promise<AuthResponse> {
  let respuesta: Response;
  try {
    respuesta = await fetch(combinarUrl(RUTAS.refresh), {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", ...CABECERA_CSRF },
    });
  } catch {
    throw new ApiError(0, "Error de conexión", "No se ha podido contactar con el servidor.");
  }
  if (!respuesta.ok) {
    throw await apiErrorDesde(respuesta);
  }
  const auth = (await leerCuerpo<AuthResponse>(respuesta)) as AuthResponse | undefined;
  if (!auth) {
    throw new ApiError(respuesta.status, "Respuesta inesperada", "El servidor no devolvió la sesión.");
  }
  guardarSesion(auth);
  return auth;
}

/** Refresca la sesión compartiendo la petición entre llamadas concurrentes. */
function refrescarSesion(): Promise<AuthResponse | null> {
  refreshEnCurso ??= realizarRefresh()
    .catch(() => null)
    .finally(() => {
      refreshEnCurso = null;
    });
  return refreshEnCurso;
}

/**
 * Restaura la sesión tras un F5: rota la cookie de refresh y deja el nuevo
 * access token en memoria. `null` si no hay sesión (no lanza excepción).
 */
export async function bootstrapSession(): Promise<AuthResponse | null> {
  const auth = await refrescarSesion();
  if (auth === null) {
    limpiarSesionLocal();
  }
  return auth;
}

// ---------------------------------------------------------------------------
// Núcleo
// ---------------------------------------------------------------------------

function combinarUrl(ruta: string): string {
  if (/^https?:\/\//.test(ruta)) {
    return ruta;
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

function cabecerasPara(ruta: string, opciones: OpcionesApi): Record<string, string> {
  const cabeceras: Record<string, string> = { Accept: "application/json" };
  if (opciones.body !== undefined) {
    cabeceras["Content-Type"] = "application/json";
  }
  if (accessToken !== null) {
    cabeceras.Authorization = `Bearer ${accessToken}`;
  }
  if (ruta.endsWith("/auth/refresh") || ruta.endsWith("/auth/logout")) {
    Object.assign(cabeceras, CABECERA_CSRF);
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
    const renovada = await refrescarSesion();
    if (renovada !== null) {
      return apiFetch<T>(ruta, { ...opciones, yaReintentado: true });
    }
    anunciarPerdidaDeSesion();
    throw new ApiError(401, "Sesión caducada", "Tu sesión ha caducado; vuelve a iniciar sesión.");
  }

  throw await apiErrorDesde(respuesta);
}

async function peticion(ruta: string, opciones: OpcionesApi): Promise<Response> {
  try {
    return await fetch(combinarUrl(ruta), {
      method: opciones.method ?? "GET",
      credentials: "include",
      headers: cabecerasPara(ruta, opciones),
      body: opciones.body === undefined ? undefined : JSON.stringify(opciones.body),
    });
  } catch {
    throw new ApiError(0, "Error de conexión", "No se ha podido contactar con el servidor.");
  }
}

// ---------------------------------------------------------------------------
// Endpoints de autenticación
// ---------------------------------------------------------------------------

export async function apiRegister(datos: DatosRegistro): Promise<AuthResponse> {
  const auth = await apiFetch<AuthResponse>(RUTAS.registro, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
  guardarSesion(auth);
  return auth;
}

export async function apiLogin(datos: Credenciales): Promise<AuthResponse> {
  const auth = await apiFetch<AuthResponse>(RUTAS.login, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
  guardarSesion(auth);
  return auth;
}

/** Cierra la sesión: borra la cookie en el servidor y el token en memoria. */
export async function apiLogout(): Promise<void> {
  try {
    await fetch(combinarUrl(RUTAS.logout), {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", ...CABECERA_CSRF },
    });
  } finally {
    limpiarSesionLocal();
  }
}

export function apiMe(): Promise<Usuario> {
  return apiFetch<Usuario>(RUTAS.yo);
}

export async function apiExchangeGoogleCode(code: string): Promise<AuthResponse> {
  const auth = await apiFetch<AuthResponse>(RUTAS.canjeGoogle, {
    method: "POST",
    body: { code },
    sinRefresco: true,
  });
  guardarSesion(auth);
  return auth;
}

/** Primer paso OAuth2: navega a la ruta RAÍZ del backend (no bajo /api/v1). */
export function iniciarSesionConGoogle(): void {
  window.location.href = RUTA_OAUTH2_GOOGLE;
}
