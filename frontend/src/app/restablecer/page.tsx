import type { Metadata } from 'next';
import { FormularioRestablecer } from '@/features/auth/components/formulario-restablecer';

export const metadata: Metadata = {
  title: 'Restablecer contraseña | MantiInmobiliaria',
  description: 'Elige una contraseña nueva para tu cuenta de MantiInmobiliaria.',
};

/**
 * Destino del enlace del email (`${FRONTEND_URL}/restablecer?token=...`).
 * Al leer `searchParams` la ruta se renderiza en cada petición; el token se
 * captura en el cliente y se limpia de la URL con `history.replaceState`.
 */
export default async function RestablecerPage(props: PageProps<'/restablecer'>) {
  const parametros = await props.searchParams;
  const token = typeof parametros.token === 'string' ? parametros.token : undefined;

  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <FormularioRestablecer token={token} />
    </div>
  );
}
