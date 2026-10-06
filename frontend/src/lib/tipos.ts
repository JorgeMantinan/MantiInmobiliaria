/**
 * Tipos compartidos del cliente API.
 *
 * Viven en `lib/` porque `lib/api.ts` y `lib/auth.ts` los usan y la capa `lib/`
 * **no** puede depender de `features/`. `features/auth/types.ts` los reexporta
 * para que los consumidores sigan importándolos desde el feature.
 *
 * Ajustados EXACTOS a `docs/openapi/openapi.yaml`.
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

export type ErrorCampo = Readonly<{ field?: string; message: string }>;
