import type { Metadata } from 'next';
import { LoginForm } from '@/features/auth/components/login-form';

export const metadata: Metadata = {
  title: 'Iniciar sesión | MantiInmobiliaria',
  description: 'Accede a tu cuenta de MantiInmobiliaria con email y contraseña o con Google.',
};

export default function LoginPage() {
  return (
    <div className="flex flex-1 items-center justify-center bg-fondo px-4 py-12">
      <section className="w-full max-w-md rounded-lg border border-borde bg-superficie p-6 sm:p-8">
        <h1 className="font-display text-2xl text-texto">Iniciar sesión</h1>
        <p className="mt-1 text-sm text-texto-suave">
          Accede para guardar favoritos y contactar con profesionales.
        </p>
        <LoginForm />
      </section>
    </div>
  );
}
