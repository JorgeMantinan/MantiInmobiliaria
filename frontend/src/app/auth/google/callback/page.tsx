import type { Metadata } from 'next';
import { CallbackGoogle } from '@/features/auth/components/callback-google';

export const metadata: Metadata = {
  title: 'Acceso con Google | MantiInmobiliaria',
  description: 'Completando el inicio de sesión con Google.',
};

/**
 * Destino de la redirección del backend
 * (`${FRONTEND_URL}/auth/google/callback?code=...` o `?error=...`).
 * Al leer `searchParams` la ruta se renderiza en cada petición.
 */
export default async function PaginaCallbackGoogle(props: PageProps<'/auth/google/callback'>) {
  const parametros = await props.searchParams;
  const code = typeof parametros.code === 'string' ? parametros.code : undefined;
  const error = typeof parametros.error === 'string' ? parametros.error : undefined;

  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <CallbackGoogle code={code} error={error} />
    </div>
  );
}
