/**
 * Tipos del contrato de autenticación.
 *
 * `Usuario`, `AuthResponse` y `ErrorCampo` se declaran en `lib/tipos.ts` (los
 * usa la capa `lib/` y ésta no puede importar de `features/`); aquí se
 * reexportan para no romper a los consumidores del feature.
 *
 * Ajustados EXACTOS a `docs/openapi/openapi.yaml` (`UsuarioSummary`,
 * `AuthResponse`, `RegistroRequest`, `LoginRequest`).
 */

export type { AuthResponse, ErrorCampo, Usuario } from '@/lib/tipos';

export type Credenciales = Readonly<{ email: string; password: string }>;

export type DatosRegistro = Readonly<{ nombre: string; email: string; password: string }>;

export type SolicitudPasswordOlvidada = Readonly<{ email: string }>;

export type SolicitudPasswordRestablecida = Readonly<{ token: string; password: string }>;
