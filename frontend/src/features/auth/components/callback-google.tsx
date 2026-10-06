'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { apiExchangeGoogleCode } from '@/features/auth/api';
import { AVISO_ERROR, mensajeDeError } from '@/features/auth/mensajes';
import { useSesion } from '@/features/auth/session';

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

const MENSAJE_SIN_CODIGO = 'No hemos recibido el código de Google. Vuelve a intentarlo.';

function mensajeDeFallo(error: string | undefined): string {
  if (error === undefined) {
    return MENSAJE_SIN_CODIGO;
  }
  // Object.hasOwn: `?error=constructor` no debe devolver un método de Object.
  return Object.hasOwn(MENSAJES_GOOGLE, error)
    ? MENSAJES_GOOGLE[error]
    : MENSAJES_GOOGLE.unsupported;
}

/** Segundo paso del flujo Google: canjea `?code=` por la sesión. */
export function CallbackGoogle({ code, error }: CallbackGoogleProps) {
  const router = useRouter();
  const { iniciarSesion } = useSesion();
  const yaCanjeado = useRef(false);
  // Se decide en el primer render: al limpiar la URL, `code` pasa a undefined
  // y NO debe re-interpretarse como "llegamos sin código".
  const [llegoSinCodigo] = useState(() => code === undefined);
  const [falloDelCanje, setFalloDelCanje] = useState<string | null>(null);
  const fallo = llegoSinCodigo ? mensajeDeFallo(error) : falloDelCanje;

  // Sin código (p. ej. `?error=` de Google) no hay nada que canjear y la URL
  // queda residual: se vuelve al inicio.
  useEffect(() => {
    if (llegoSinCodigo) {
      router.replace('/');
    }
  }, [llegoSinCodigo, router]);

  useEffect(() => {
    if (code === undefined || yaCanjeado.current) {
      return;
    }
    yaCanjeado.current = true;

    // Limpia `?code=` ANTES de canjear (el código es de un solo uso: un F5 no
    // debe reintentarlo). `history.replaceState` y no `router.replace` para no
    // re-disparar este effect.
    window.history.replaceState(null, '', '/auth/google/callback');

    // Sin cleanup a propósito: el canje debe completarse aunque StrictMode
    // vuelva a montar el effect (el ref evita el segundo envío) y aunque el
    // usuario abandone la página (la sesión se guarda de todos modos).
    async function canjear(codigo: string): Promise<void> {
      try {
        const sesion = await apiExchangeGoogleCode(codigo);
        iniciarSesion(sesion);
        router.replace('/');
      } catch (causa) {
        setFalloDelCanje(mensajeDeError(causa));
      }
    }

    void canjear(code);
  }, [code, iniciarSesion, router]);

  if (fallo !== null) {
    return (
      <div className="flex w-full max-w-md flex-col gap-4 rounded-lg border border-borde bg-superficie p-6 sm:p-8">
        <h1 className="font-display text-2xl text-texto">No se ha podido entrar</h1>
        <p role="alert" aria-live="assertive" className={AVISO_ERROR}>
          {fallo}
        </p>
        <Link
          href="/login"
          className="rounded-md bg-acento-nogal px-4 py-2 text-center text-sm font-medium text-superficie transition-colors outline-none focus:ring-2 focus:ring-acento-nogal hover:bg-acento-hover"
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
