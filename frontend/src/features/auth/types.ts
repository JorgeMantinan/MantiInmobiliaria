/**
 * Tipos del contrato de autenticación, ajustados EXACTOS a
 * `docs/openapi/openapi.yaml` (`UsuarioSummary`, `AuthResponse`, …).
 *
 * Módulo hoja: sólo tipos, sin imports (evita ciclos entre `lib/api.ts`
 * y `lib/auth.ts`).
 */

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
