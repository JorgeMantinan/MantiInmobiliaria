'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { mensajeDeError } from '@/features/auth/mensajes';
import { useSesion } from '@/features/auth/session';
import { apiLogin, iniciarSesionConGoogle } from '@/lib/api';

const AVISO = 'text-sm text-acento-hover';

/** Formulario de login email/password + acceso con Google. */
export function LoginForm() {
  const router = useRouter();
  const { iniciarSesion } = useSesion();
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>): Promise<void> {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const email = String(datos.get('email') ?? '');
    const password = String(datos.get('password') ?? '');

    setError(null);
    setEnviando(true);
    try {
      const sesion = await apiLogin({ email, password });
      iniciarSesion(sesion);
      router.push('/');
    } catch (causa) {
      setError(mensajeDeError(causa));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={(evento) => void enviar(evento)} className="mt-6 flex flex-col gap-4">
      <Campo
        id="login-email"
        name="email"
        etiqueta="Email"
        type="email"
        autoComplete="email"
        required
      />
      <Campo
        id="login-password"
        name="password"
        etiqueta="Contraseña"
        type="password"
        autoComplete="current-password"
        required
      />

      {error !== null ? (
        <p role="alert" aria-live="assertive" className={AVISO}>
          {error}
        </p>
      ) : null}

      <Boton type="submit" disabled={enviando}>
        {enviando ? 'Entrando…' : 'Iniciar sesión'}
      </Boton>

      <button
        type="button"
        onClick={iniciarSesionConGoogle}
        className="flex w-full items-center justify-center gap-2 rounded-md border border-borde bg-superficie px-4 py-2 text-sm font-medium text-texto transition-colors hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal outline-none"
      >
        <svg viewBox="0 0 48 48" width="16" height="16" aria-hidden="true" focusable="false">
          <path
            fill="#EA4335"
            d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
          />
          <path
            fill="#4285F4"
            d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
          />
          <path
            fill="#FBBC05"
            d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
          />
          <path
            fill="#34A853"
            d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
          />
        </svg>
        Continuar con Google
      </button>

      <p className="text-sm text-texto-suave">
        ¿No tienes cuenta?{' '}
        <Link href="/registro" className="text-acento-nogal underline underline-offset-4">
          Crear cuenta
        </Link>
      </p>
    </form>
  );
}
