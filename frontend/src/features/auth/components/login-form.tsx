'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { GoogleIcon } from '@/components/ui/google-icon';
import { Boton } from '@/components/ui/boton';
import { Campo } from '@/components/ui/campo';
import { apiLogin, iniciarSesionConGoogle } from '@/features/auth/api';
import { AVISO_ERROR, mensajeDeError } from '@/features/auth/mensajes';
import { useSesion } from '@/features/auth/session';

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
    // noValidate: la validación accesible (aria-describedby + role="alert") es
    // la nuestra; required/type se mantienen para lectores de pantalla.
    <form
      onSubmit={(evento) => void enviar(evento)}
      noValidate
      className="mt-6 flex flex-col gap-4"
    >
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

      <p className="-mt-2 text-right text-sm">
        <Link
          href="/recuperar"
          className="rounded-md text-acento-nogal underline underline-offset-4 outline-none focus:ring-2 focus:ring-acento-nogal"
        >
          ¿Olvidaste tu contraseña?
        </Link>
      </p>

      {error !== null ? (
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {error}
        </p>
      ) : null}

      <Boton type="submit" disabled={enviando}>
        {enviando ? 'Entrando…' : 'Iniciar sesión'}
      </Boton>

      <button
        type="button"
        onClick={iniciarSesionConGoogle}
        className="flex w-full items-center justify-center gap-2 rounded-md border border-borde bg-superficie px-4 py-2 text-sm font-medium text-texto transition-colors outline-none hover:bg-superficie-alta focus:ring-2 focus:ring-acento-nogal"
      >
        <GoogleIcon />
        Continuar con Google
      </button>

      <p className="text-sm text-texto-suave">
        ¿No tienes cuenta?{' '}
        <Link
          href="/registro"
          className="rounded-md text-acento-nogal underline underline-offset-4 outline-none focus:ring-2 focus:ring-acento-nogal"
        >
          Crear cuenta
        </Link>
      </p>
    </form>
  );
}
