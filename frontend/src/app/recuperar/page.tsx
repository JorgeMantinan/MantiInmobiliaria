import type { Metadata } from 'next';
import { FormularioRecuperar } from '@/features/auth/components/formulario-recuperar';

export const metadata: Metadata = {
  title: 'Recuperar contraseña | MantiInmobiliaria',
  description:
    'Te enviaremos un enlace por email para restablecer la contraseña de tu cuenta.',
};

/**
 * Solicitud del enlace de restablecimiento (#57). Página fina: sólo metadata y
 * layout; la interacción vive en el cliente `FormularioRecuperar`.
 */
export default function RecuperarPage() {
  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <FormularioRecuperar />
    </div>
  );
}
