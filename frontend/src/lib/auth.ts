/**
 * Sesión de autenticación en el cliente.
 *
 * - Access token JWT **en memoria** (variable de módulo): nunca en
 *   localStorage/sessionStorage (skill `config-secrets`/`seguridad`).
 * - Cookie httpOnly de refresh: se rota con `POST /api/v1/auth/refresh`.
 * - Módulo HOJA: sólo importa tipos. `lib/api.ts` depende de aquí (api → auth),
 *   nunca al revés, para no crear un ciclo de imports.
 */
import type { AuthResponse } from "@/lib/tipos";

// ---------------------------------------------------------------------------
// URL base de la API (definida aquí: el refresh necesita el origen)
// ---------------------------------------------------------------------------

const configurado = process.env.NEXT_PUBLIC_API_URL;

if (process.env.NODE_ENV === "production") {
  if (configurado === undefined || configurado === "") {
    // En Vercel/hosting debe existir: mejor fallar el build que apuntar a localhost.
    throw new Error("NEXT_PUBLIC_API_URL es obligatoria en producción");
  }
  // En producción el tráfico a la API debe ir cifrado; sólo se admite
  // http:// sobre localhost para poder ejecutar `next build` en local.
  const esLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?(\/|$)/.test(configurado);
  if (!configurado.startsWith("https://") && !esLocal) {
    throw new Error("NEXT_PUBLIC_API_URL debe empezar por https:// en producción");
  }
}

/** URL base de la API (reexportada por `lib/api.ts` para sus consumidores). */
export const API_BASE = (configurado ?? "http://localhost:8080").replace(/\/+$/, "");

/** Endpoints autenticados por cookie: exigen `X-Requested-With: XMLHttpRequest`. */
export const CABECERA_CSRF = { "X-Requested-With": "XMLHttpRequest" } as const;

// ---------------------------------------------------------------------------
// Store de sesión + observadores
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

export function obtenerAccessToken(): string | null {
  return accessToken;
}

export function haySesion(): boolean {
  return accessToken !== null;
}

/** Guarda la sesión devuelta por login/registro/refresh/canje de Google. */
export function guardarSesion(sesion: AuthResponse): void {
  accessToken = sesion.accessToken;
}

/** Borra el access token en memoria (sin avisar: uso local puntual). */
export function limpiarSesionLocal(): void {
  accessToken = null;
}

/**
 * Borra el token y avisa a los observadores (logout, sesión revocada o
 * caducada tras reintentar). Éste es el camino "oficial" de cierre.
 */
export function anunciarPerdidaDeSesion(): void {
  accessToken = null;
  observadores.forEach((observador) => observador());
}

// ---------------------------------------------------------------------------
// Refresh de la cookie (con deduplicación y lock entre pestañas)
// ---------------------------------------------------------------------------

/**
 * Resultado del refresh SIN excepciones: `status: 0` distingue un fallo de
 * RED (la sesión en memoria sigue válida) de un rechazo del servidor (401/403,
 * la cookie ya no vale). `lib/api.ts` lo traduce a `ApiError`.
 */
export type ResultadoRefresh =
  | Readonly<{ ok: true; sesion: AuthResponse }>
  | Readonly<{ ok: false; status: number }>;

const RUTA_REFRESH = "/api/v1/auth/refresh";
const NOMBRE_LOCK = "manti-refresh";

let refreshEnCurso: Promise<ResultadoRefresh> | null = null;

async function peticionRefresh(): Promise<ResultadoRefresh> {
  let respuesta: Response;
  try {
    respuesta = await fetch(API_BASE + RUTA_REFRESH, {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", ...CABECERA_CSRF },
    });
  } catch {
    return { ok: false, status: 0 };
  }

  if (!respuesta.ok) {
    return { ok: false, status: respuesta.status };
  }

  try {
    const sesion = (await respuesta.json()) as AuthResponse | null;
    if (sesion === null || typeof sesion.accessToken !== "string") {
      // 200 con payload inválido: fallo de protocolo. `status: 0` para no
      // fabricar un `ApiError(200)` imposible ni revocar la sesión.
      return { ok: false, status: 0 };
    }
    guardarSesion(sesion);
    return { ok: true, sesion };
  } catch {
    // JSON ilegible o vacío: mismo tratamiento que un fallo de red.
    return { ok: false, status: 0 };
  }
}

/**
 * Rota el refresh token.
 *
 * El backend practica rotación estricta: si DOS pestañas refrescan a la vez,
 * la segunda reutiliza un token ya rotado y el backend revoca TODA la familia
 * (logout en cascada). Web Locks serializa el refresh entre pestañas del mismo
 * origen; si el navegador no lo soporta (o no hay cookie) seguimos el camino
 * actual sin lock. La ventana de gracia en el backend queda como issue.
 */
export function realizarRefresh(): Promise<ResultadoRefresh> {
  if (typeof navigator !== "undefined" && "locks" in navigator && navigator.locks !== undefined) {
    // lib.dom tipa `request<T>` con T = retorno del callback, así que aquí
    // inferiría `Promise<Promise<…>>`; el runtime aplana la promesa y el
    // `.then(identity)` deja el tipo correcto. Si el LOCK rechaza (AbortError,
    // soporte parcial…) se degrada al camino sin lock: `realizarRefresh` nunca
    // debe lanzar una excepción cruda.
    return navigator.locks
      .request(NOMBRE_LOCK, () => peticionRefresh())
      .then((resultado) => resultado)
      .catch(() => peticionRefresh());
  }
  return peticionRefresh();
}

/** Refresca la sesión compartiendo UNA petición entre llamadas concurrentes. */
export function refrescarSesion(): Promise<ResultadoRefresh> {
  refreshEnCurso ??= realizarRefresh().finally(() => {
    refreshEnCurso = null;
  });
  return refreshEnCurso;
}

/**
 * Restaura la sesión tras un F5: rota la cookie de refresh y deja el nuevo
 * access token en memoria. `null` si no hay sesión (nunca lanza excepción).
 */
export async function bootstrapSession(): Promise<AuthResponse | null> {
  const resultado = await refrescarSesion();
  if (resultado.ok) {
    return resultado.sesion;
  }
  // Fallo de red (0): la memoria sigue válida. Rechazo del servidor: limpiar.
  if (resultado.status !== 0) {
    limpiarSesionLocal();
  }
  return null;
}
