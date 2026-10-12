/**
 * Endpoints de autenticación (`/api/v1/auth`): sólo orquestan `apiFetch` y el
 * store de sesión. El resto de la app consume esta capa, no `lib/api.ts`.
 */
import { apiFetch, combinarUrl } from "@/lib/api";
import { API_BASE, CABECERA_CSRF, anunciarPerdidaDeSesion, guardarSesion } from "@/lib/auth";
import type { AuthResponse, Credenciales, DatosRegistro, SolicitudPasswordOlvidada, SolicitudPasswordRestablecida, Usuario } from "./types";

const RUTAS = {
  registro: "/api/v1/auth/register",
  login: "/api/v1/auth/login",
  logout: "/api/v1/auth/logout",
  yo: "/api/v1/auth/me",
  canjeGoogle: "/api/v1/auth/oauth2/token",
  passwordOlvidada: "/api/v1/auth/password/forgot",
  passwordRestablecida: "/api/v1/auth/password/reset",
} as const;

export async function apiRegister(datos: DatosRegistro): Promise<AuthResponse> {
  const sesion = await apiFetch<AuthResponse>(RUTAS.registro, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
  guardarSesion(sesion);
  return sesion;
}

export async function apiLogin(datos: Credenciales): Promise<AuthResponse> {
  const sesion = await apiFetch<AuthResponse>(RUTAS.login, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
  guardarSesion(sesion);
  return sesion;
}

/** Cierra la sesión: borra la cookie en el servidor y avisa a los observadores. */
export async function apiLogout(): Promise<void> {
  try {
    await fetch(combinarUrl(RUTAS.logout), {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", ...CABECERA_CSRF },
    });
  } finally {
    anunciarPerdidaDeSesion();
  }
}

export function apiMe(): Promise<Usuario> {
  return apiFetch<Usuario>(RUTAS.yo);
}

export async function apiExchangeGoogleCode(code: string): Promise<AuthResponse> {
  const sesion = await apiFetch<AuthResponse>(RUTAS.canjeGoogle, {
    method: "POST",
    body: { code },
    sinRefresco: true,
  });
  guardarSesion(sesion);
  return sesion;
}

/**
 * Pide el enlace de restablecimiento (issue #57). El backend responde SIEMPRE
 * `202` aunque el email no exista (sin enumeración de cuentas).
 */
export function apiForgotPassword(datos: SolicitudPasswordOlvidada): Promise<void> {
  return apiFetch<void>(RUTAS.passwordOlvidada, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
}

/**
 * Canjea el token opaco del email por la contraseña nueva (`204`).
 * `400 token-invalido` si el enlace caducó, no existe o ya se usó.
 */
export function apiResetPassword(datos: SolicitudPasswordRestablecida): Promise<void> {
  return apiFetch<void>(RUTAS.passwordRestablecida, {
    method: "POST",
    body: datos,
    sinRefresco: true,
  });
}

/** Primer paso OAuth2: navega a la ruta RAÍZ del backend (no bajo `/api/v1`). */
export function iniciarSesionConGoogle(): void {
  // Concatenación a propósito: el template literal con interpolación
  // confunde al lint de Next (`no-location-assign-relative-destination`).
  window.location.href = API_BASE + "/oauth2/authorization/google";
}
