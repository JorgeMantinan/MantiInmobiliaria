import type { Metadata } from 'next';
import { RegistroForm } from '@/features/auth/components/registro-form';

export const metadata: Metadata = {
  title: 'Crear cuenta | MantiInmobiliaria',
  description: 'Regístrate en MantiInmobiliaria con tu email o continúa con Google.',
};

export default function RegistroPage() {
  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 sm:p-8">
        <h1 className="font-display text-2xl text-texto">Crear cuenta</h1>
        <p className="mt-1 text-sm text-texto-suave">
          Al registrarte entrarás directamente a tu cuenta.
        </p>
        <RegistroForm />
      </section>
    </div>
  );
}
