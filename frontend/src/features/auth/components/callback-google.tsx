'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { mensajeDeError } from '@/features/auth/mensajes';
import { useSesion } from '@/features/auth/session';
import { apiExchangeGoogleCode } from '@/lib/api';

type CallbackGoogleProps = Readonly<{
  code?: string;
  error?: string;
}>;

const MENSAJES_GOOGLE: Readonly<Record<string, string>> = {
  access_denied: 'Has cancelado el acceso con Google.',
  missing_email: 'Tu cuenta de Google no comparte un email con la aplicación.',
  account_disabled: 'Esta cuenta está desactivada.',
  account_conflict: 'Ese email ya está vinculado a otra cuenta.',
  unsupported: 'No se ha podido completar el acceso con Google.',
};

function mensajeDeFallo(error: string | undefined): string {
  if (error !== undefined) {
    return MENSAJES_GOOGLE[error] ?? MENSAJES_GOOGLE.unsupported;
  }
  return 'No hemos recibido el código de Google. Vuelve a intentarlo.';
}

/** Segundo paso del flujo Google: canjea `?code=` por la sesión. */
export function CallbackGoogle({ code, error }: CallbackGoogleProps) {
  const router = useRouter();
  const { iniciarSesion } = useSesion();
  // El fallo sin código se deriva del prop (nada que sincronizar en un effect).
  const falloInicial = code === undefined ? mensajeDeFallo(error) : null;
  const [falloDelCanje, setFalloDelCanje] = useState<string | null>(null);
  const fallo = falloInicial ?? falloDelCanje;

  useEffect(() => {
    if (code === undefined) {
      return;
    }

    let activo = true;
    async function canjear(codigo: string): Promise<void> {
      try {
        const sesion = await apiExchangeGoogleCode(codigo);
        if (!activo) return;
        iniciarSesion(sesion);
        router.replace('/');
      } catch (causa) {
        if (activo) setFalloDelCanje(mensajeDeError(causa));
      }
    }

    void canjear(code);
    return () => {
      activo = false;
    };
  }, [code, iniciarSesion, router]);

  if (fallo !== null) {
    return (
      <div className="flex w-full max-w-md flex-col gap-4 rounded-lg border border-borde bg-superficie p-6 sm:p-8">
        <h1 className="font-display text-2xl text-texto">No se ha podido entrar</h1>
        <p role="alert" aria-live="assertive" className="text-sm text-acento-hover">
          {fallo}
        </p>
        <Link
          href="/login"
          className="rounded-md bg-acento-nogal px-4 py-2 text-center text-sm font-medium text-superficie transition-colors hover:bg-acento-hover focus:ring-2 focus:ring-acento-nogal outline-none"
        >
          Volver a iniciar sesión
        </Link>
      </div>
    );
  }

  return (
    <div
      role="status"
      className="flex w-full max-w-md flex-col items-center gap-3 rounded-lg border border-borde bg-superficie p-6 sm:p-8"
    >
      <p className="font-display text-xl text-texto">Entrando con Google…</p>
      <p className="text-sm text-texto-suave">Estamos completando tu acceso.</p>
    </div>
  );
}
